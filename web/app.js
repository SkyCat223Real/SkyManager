import * as core from "/app/core.js";

const $ = (s, el = document) => el.querySelector(s);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const app = $("#app"), nav = $("#nav");
const store = { get: (k, d) => { try { const v = localStorage.getItem("sky_" + k); return v == null ? d : JSON.parse(v); } catch { return d; } }, set: (k, v) => localStorage.setItem("sky_" + k, JSON.stringify(v)), del: k => localStorage.removeItem("sky_" + k) };
let model = null, tab = "start", sub = {};
let serverOps = [], pending = store.get("pending", []);
const LISTMAP = { hw: "homework", notes: "notes", todos: "todos" };

// ---- Bearbeiten (wird verschluesselt an den Server geschickt, die Android-App uebernimmt es)
function refreshView() {
  if (!model || !model.base) return;
  const ops = [...serverOps, ...pending];
  model.touched = new Set();
  for (const o of ops) if (o.itemId) model.touched.add(o.itemId); else if (o.item) model.touched.add(o.item.id);
  for (const [list, key] of Object.entries(LISTMAP)) { const arr = model.base[list].map(x => ({ ...x })); core.applyOps(arr, list, ops); model[key] = arr; }
}
async function fetchServerOps() {
  const s = store.get("session", null), key = await keyGet();
  if (!s || !key) return;
  try {
    const ch = (await api("/api/changes", { token: s.token })).changes, ops = [];
    for (const c of ch) { const pl = await core.decryptBackup(key, c.data); if (pl) ops.push(...core.parseChanges(pl)); }
    serverOps = ops; store.set("srvops", ops);
  } catch { serverOps = store.get("srvops", []); }
}
async function pushPending() {
  if (!pending.length) return true;
  const s = store.get("session", null), key = await keyGet();
  if (!s || !key) return false;
  try {
    const batch = pending.slice(0, 100);
    const data = await core.encryptText(key, core.changesPayload(batch));
    await api("/api/changes", { method: "POST", token: s.token, body: { data } });
    serverOps = [...serverOps, ...batch]; store.set("srvops", serverOps);
    pending = pending.slice(batch.length); store.set("pending", pending);
    return true;
  } catch (e) {
    if (String(e.message).includes("encrypt") || e.name === "InvalidAccessError") toast("Bitte einmal ab- und wieder anmelden, dann klappt das Bearbeiten.");
    return false;
  }
}
async function change(op) {
  op.id = core.newId(); op.ts = Date.now();
  pending.push(op); store.set("pending", pending);
  refreshView(); render();
  if (await pushPending()) {
    if (model.webOnly) { if (serverOps.length >= 12) await compact(); toast("Gespeichert ✓"); }
    else toast("Gespeichert ✓ – die Android-App übernimmt es beim nächsten Start");
  } else toast("Offline gespeichert, wird später übertragen");
}
const mark = id => !model.webOnly && model.touched && model.touched.has(id) ? ' <span class="pill" title="wartet auf die Android-App">⏳</span>' : "";

// ---- Reines iPhone-Konto: offene Aenderungen ins Backup schreiben (ersetzt die Android-App)
async function compact() {
  if (!model || !model.webOnly || !model.raw) return false;
  const s = store.get("session", null), key = await keyGet();
  if (!s || !key) return false;
  try {
    const ch = (await api("/api/changes", { token: s.token })).changes, ops = [];
    if (!ch.length) return false;
    for (const c of ch) { const pl = await core.decryptBackup(key, c.data); if (pl) ops.push(...core.parseChanges(pl)); }
    const r = core.applyToExport(model.raw, ops);
    const data = await core.encryptText(key, r.text);
    const b = (await api("/api/backups", { method: "POST", token: s.token, body: { data, app: core.WEB_TAG, note: "iPhone" } })).backup;
    for (const c of ch) await api("/api/changes/" + c.id, { method: "DELETE", token: s.token });
    store.set("blob", data); store.set("meta", b);
    setModel(r.text, b);
    serverOps = []; store.set("srvops", []);
    refreshView();
    return true;
  } catch { return false; }
}
function setModel(plain, meta) {
  model = core.parseExport(plain); model.meta = meta; model.raw = plain;
  model.webOnly = !!meta && meta.app === core.WEB_TAG;
  model.base = { hw: model.homework, notes: model.notes, todos: model.todos };
}
/** Legt das Start-Backup fuer ein Konto ohne Android-App an. */
async function startWebAccount(name) {
  const s = store.get("session", null), key = await keyGet();
  const data = await core.encryptText(key, core.starterExport(name));
  const b = (await api("/api/backups", { method: "POST", token: s.token, body: { data, app: core.WEB_TAG, note: "Start (iPhone)" } })).backup;
  store.set("blob", data); store.set("meta", b);
  return b;
}
function form(title, fields, onSave) {
  const d = shell(title, `<div style="overflow:auto">${fields}<button class="btn" id="fs">Speichern</button></div>`);
  d.style.touchAction = "auto";
  $("#fs", d).onclick = () => { if (onSave(d) !== false) d.remove(); };
  return d;
}

function toast(t) { const d = document.createElement("div"); d.className = "toast"; d.textContent = t; document.body.appendChild(d); setTimeout(() => d.remove(), 2600); }

// ---- Schluessel sicher im Browser (IndexedDB, nicht exportierbar)
function idb() { return new Promise((res, rej) => { const r = indexedDB.open("skymanager", 1); r.onupgradeneeded = () => r.result.createObjectStore("keys"); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); }); }
async function keyPut(k) { const db = await idb(); return new Promise((res, rej) => { const t = db.transaction("keys", "readwrite"); t.objectStore("keys").put(k, "enc"); t.oncomplete = res; t.onerror = rej; }); }
async function keyGet() { try { const db = await idb(); return await new Promise(res => { const r = db.transaction("keys").objectStore("keys").get("enc"); r.onsuccess = () => res(r.result || null); r.onerror = () => res(null); }); } catch { return null; } }
async function keyDel() { try { const db = await idb(); db.transaction("keys", "readwrite").objectStore("keys").delete("enc"); } catch { } }

async function api(path, opt = {}) {
  const r = await fetch(path, { method: opt.method || "GET", headers: { "content-type": "application/json", ...(opt.token ? { authorization: "Bearer " + opt.token } : {}) }, body: opt.body ? JSON.stringify(opt.body) : undefined });
  let j = {}; try { j = await r.json(); } catch { }
  if (!r.ok || !j.ok) throw new Error(j.error || ("Fehler " + r.status));
  return j;
}

// ---- Laden: neuestes Backup holen (sonst das gespeicherte, offline)
async function load(fresh) {
  const key = await keyGet(), s = store.get("session", null);
  if (!key || !s) return false;
  let blob = store.get("blob", null), meta = store.get("meta", null);
  if (fresh || !blob) {
    try {
      const l = (await api("/api/backups", { token: s.token })).backups;
      if (!l.length) { model = { empty: true }; return true; }
      if (!meta || l[0].id !== meta.id || !blob) { blob = (await api("/api/backups/" + l[0].id, { token: s.token })).data; meta = l[0]; store.set("blob", blob); store.set("meta", meta); }
    } catch (e) {
      if (/angemeldet|Passwort/.test(e.message)) { await logout(); toast("Bitte neu anmelden"); return false; }
      if (!blob) throw e;
      toast("Offline: zeige den letzten Stand");
    }
  }
  const plain = await core.decryptBackup(key, blob);
  if (plain == null) throw new Error("Backup ließ sich nicht entschlüsseln");
  setModel(plain, meta);
  await fetchServerOps();
  await pushPending();
  if (model.webOnly && serverOps.length) await compact();
  refreshView();
  return true;
}
async function logout() { const s = store.get("session", null); store.del("session"); store.del("blob"); store.del("meta"); store.del("pending"); store.del("srvops"); pending = []; serverOps = []; await keyDel(); model = null; if (s) try { await api("/api/logout", { method: "POST", token: s.token }); } catch { } }

// ---- Anmelden
function loginView(msg) {
  nav.hidden = true;
  app.innerHTML = `<div style="text-align:center;margin-top:8vh"><img src="/app/icon.png" alt="" width="84" height="84" style="border-radius:22px"><h1>SkyManager</h1><div class="dim">Deine Daten aus der App, auf dem iPhone</div></div>
  <div class="card"><h2>Mit Online-Konto anmelden</h2>
  <input id="u" autocomplete="username" autocapitalize="none" placeholder="Benutzername"><input id="p" type="password" autocomplete="current-password" placeholder="Passwort">
  <button class="btn" id="go">Anmelden</button><div id="m" class="small err" style="margin-top:8px">${esc(msg || "")}</div></div>
  <button class="btn sec" id="reg">✨ Neues Konto erstellen</button>
  <div class="card small dim">Deine Daten werden mit deinem Passwort verschlüsselt und erst hier im Browser entschlüsselt. Hast du SkyManager auf Android, kannst du dich dort mit demselben Konto anmelden.</div>
  <button class="btn sec" id="guest">Ohne Konto: nur Spiele und Werkzeuge</button>`;
  $("#go").onclick = async () => {
    const u = core.normUser($("#u").value), p = $("#p").value, m = $("#m");
    if (!core.validUser(u) || !p) { m.textContent = "Bitte Benutzername und Passwort eingeben"; return; }
    $("#go").disabled = true; m.className = "small dim"; m.textContent = "Schlüssel werden berechnet …";
    try {
      const k = await core.deriveKeys(u, p);
      const t = (await api("/api/login", { method: "POST", body: { user: u, auth: k.authHex } })).token;
      await keyPut(k.encKey); store.set("session", { user: u, token: t }); store.del("blob"); store.del("meta");
      m.textContent = "Lade dein Backup …";
      await load(true); tab = "start"; render();
    } catch (e) { m.className = "small err"; m.textContent = e.message; $("#go").disabled = false; }
  };
  $("#p").onkeydown = e => { if (e.key === "Enter") $("#go").click(); };
  $("#guest").onclick = () => { store.set("guest", true); model = { guest: true }; tab = "games"; render(); };
  $("#reg").onclick = registerView;
}

function registerView() {
  nav.hidden = true;
  app.innerHTML = `<div style="text-align:center;margin-top:5vh"><img src="/app/icon.png" alt="" width="70" height="70" style="border-radius:18px"><h1>Konto erstellen</h1></div>
  <div class="card">
  <input id="ru" autocomplete="username" autocapitalize="none" maxlength="24" placeholder="Benutzername (a-z, 0-9, _ . -)">
  <input id="re" type="email" autocomplete="email" autocapitalize="none" placeholder="E-Mail">
  <button class="btn sec" id="rc">📧 Code an diese E-Mail senden</button>
  <input id="rk" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6-stelliger Code aus der Mail">
  <input id="rp" type="password" autocomplete="new-password" placeholder="Passwort (mind. 8 Zeichen)">
  <input id="rp2" type="password" autocomplete="new-password" placeholder="Passwort wiederholen">
  <button class="btn" id="rgo">Konto erstellen</button>
  <div id="rm" class="small err" style="margin-top:8px"></div></div>
  <div class="card small dim"><b>Merk dir dein Passwort gut:</b> Deine Daten werden damit verschlüsselt. Ohne Passwort kann sie niemand öffnen, auch wir nicht.<br><br>Datenschutz: Deine E-Mail nutzen wir nur für Bestätigungscodes und damit jede Person nur ein Konto hat. Sie wird verschlüsselt gespeichert und mit dem Konto gelöscht. Bist du jünger als 16, frag bitte deine Eltern.</div>
  <button class="btn sec" id="rback">Zurück zur Anmeldung</button>`;
  const m = $("#rm");
  $("#rback").onclick = () => loginView();
  $("#rc").onclick = async () => {
    const e = $("#re").value.trim();
    if (!e) { m.textContent = "Bitte zuerst deine E-Mail eingeben"; return; }
    $("#rc").disabled = true;
    try { const r = await api("/api/email/code", { method: "POST", body: { email: e, purpose: "register" } }); m.className = "small acc"; m.textContent = "Code geschickt an " + r.to + ". Schau auch im Spam-Ordner."; }
    catch (x) { m.className = "small err"; m.textContent = x.message; }
    setTimeout(() => { $("#rc").disabled = false; }, 60000);
  };
  $("#rgo").onclick = async () => {
    const u = core.normUser($("#ru").value), e = $("#re").value.trim(), c = $("#rk").value.trim(), p = $("#rp").value, p2 = $("#rp2").value;
    const bad = core.checkSignup(u, e, c, p, p2);
    if (bad) { m.className = "small err"; m.textContent = bad; return; }
    $("#rgo").disabled = true; m.className = "small dim"; m.textContent = "Schlüssel werden berechnet …";
    try {
      const k = await core.deriveKeys(u, p);
      await api("/api/register", { method: "POST", body: { user: u, auth: k.authHex, email: e, emailCode: c } });
      m.textContent = "Konto erstellt, melde an …";
      const t = (await api("/api/login", { method: "POST", body: { user: u, auth: k.authHex } })).token;
      await keyPut(k.encKey); store.set("session", { user: u, token: t }); store.del("blob"); store.del("meta"); store.del("guest");
      await startWebAccount(u);
      await load(true); tab = "start"; render();
      toast("Willkommen bei SkyManager! 🎉");
    } catch (x) { m.className = "small err"; m.textContent = x.message; $("#rgo").disabled = false; }
  };
}

// ---- Rahmen
const TABS = [["start", "🏠", "Start"], ["school", "🎓", "Schule"], ["events", "📅", "Termine"], ["games", "🎮", "Spiele"], ["more", "☰", "Mehr"]];
function render() {
  if (!model) return loginView();
  const tabs = model.guest ? TABS.filter(t => t[0] === "games" || t[0] === "more") : TABS;
  if (!tabs.some(t => t[0] === tab)) tab = tabs[0][0];
  nav.hidden = false;
  nav.innerHTML = tabs.map(t => `<button data-t="${t[0]}" class="${t[0] === tab ? "on" : ""}"><span>${t[1]}</span>${t[2]}</button>`).join("");
  nav.querySelectorAll("button").forEach(b => b.onclick = () => { tab = b.dataset.t; render(); scrollTo(0, 0); });
  ({ start: startView, school: schoolView, events: eventsView, games: gamesView, more: moreView })[tab]();
}

function standLine() {
  if (!model || !model.meta) return "";
  return `<div class="small dim" style="margin-top:6px">Stand: Backup vom ${new Date(model.meta.created).toLocaleString("de-DE", { dateStyle: "short", timeStyle: "short" })} <button class="pill" id="refresh">↻ aktualisieren</button></div>`;
}
function bindRefresh() { const b = $("#refresh"); if (b) b.onclick = async () => { b.textContent = "…"; try { await load(true); render(); toast("Aktuell ✓"); } catch (e) { toast(e.message); b.textContent = "↻ aktualisieren"; } }; }
function emptyView() {
  app.innerHTML = `<div class="card hero"><h2>Noch keine Daten</h2><div class="dim">Nutzt du SkyManager auf Android? Dann öffne dort Mehr → Online-Konto → <b>Jetzt sichern</b> und tippe hier auf Aktualisieren.</div><button class="btn" id="r2">↻ Aktualisieren</button></div>
  <div class="card"><h2>📱 Nur auf dem iPhone?</h2><div class="dim">Dann starte hier mit leeren Listen. Aufgaben, Notizen und To-dos werden direkt in deinem Konto gespeichert.</div><button class="btn sec" id="r3">Hier starten</button></div>`;
  $("#r2").onclick = async () => { await load(true); render(); };
  $("#r3").onclick = async () => {
    if (!confirm("Hast du schon Daten in der Android-App? Dann sichere sie lieber dort. Hier mit leeren Listen starten?")) return;
    try { const s = store.get("session", null); await startWebAccount(s ? s.user : ""); await load(true); render(); } catch (x) { toast(x.message); }
  };
}

function startView() {
  if (model.empty) return emptyView();
  const p = model.profile, e = model.engage, today = core.todayEpochDay();
  const span = e.nextXp ? (e.nextXp - e.levelXp) : 1, prog = e.nextXp ? Math.min(100, Math.round((e.xp - e.levelXp) * 100 / span)) : 100;
  const wd = (new Date().getDay() + 6) % 7, nowMin = new Date().getHours() * 60 + new Date().getMinutes();
  let todayRows = "";
  if (wd < 5) for (let i = 1; i <= 8; i++) { const s = model.slots[wd + "-" + i]; if (s) todayRows += `<div class="item row"><span class="pill">${i}.</span><span class="grow ${nowMin >= core.START[i - 1] && nowMin < core.END[i - 1] ? "acc" : ""}">${esc(s)}</span><span class="small dim">${core.fmtMin(core.START[i - 1])}</span></div>`; }
  const hw = core.sortHomework(model.homework).map(core.describe).filter(h => !h.done).slice(0, 3);
  const ev = core.upcomingEvents(model.events, today, 3);
  app.innerHTML = `<h1>Hi${p.name ? " " + esc(p.name.split(" ")[0]) : ""} 👋</h1>${standLine()}
  <div class="card hero"><div class="row"><div class="grow"><div class="small dim">LEVEL</div><div style="font-size:22px;font-weight:800">${esc(e.levelName)}</div>${p.title ? `<div class="small acc">${esc(p.title)}</div>` : ""}</div><div style="font-size:34px">${esc(p.avatar || "🌌")}</div></div>
  <div class="bar"><i style="width:${prog}%"></i></div><div class="small dim" style="margin-top:4px">${e.xp} XP${e.nextXp ? " · noch " + (e.nextXp - e.xp) + " bis zum nächsten Level" : " · höchstes Level"}</div>
  <div class="stats"><div class="stat"><b>${e.xp}</b><span class="small dim">XP</span></div><div class="stat"><b>${e.coins}</b><span class="small dim">Münzen</span></div><div class="stat"><b>${e.streak}🔥</b><span class="small dim">Serie</span></div></div></div>
  <div class="card"><h2>Heute${wd < 5 ? " · " + core.DAYS_LONG[wd] : ""}</h2>${wd >= 5 ? '<div class="dim">Wochenende 🎉</div>' : (todayRows || '<div class="dim">Kein Unterricht eingetragen</div>')}</div>
  <div class="card"><h2>Nächste Aufgaben</h2>${hw.length ? hw.map(h => `<div class="item row"><span class="grow">${esc(h.title)}</span>${h.day != null ? `<span class="small ${h.day < today ? "err" : "dim"}">${h.day === today ? "heute" : core.fmtDay(h.day)}</span>` : ""}</div>`).join("") : '<div class="dim">Alles erledigt ✅</div>'}</div>
  <div class="card"><h2>Nächste Termine</h2>${ev.length ? ev.map(x => `<div class="item row"><span class="grow">${esc(x.title || "Termin")}</span><span class="small dim">${x.date === today ? "heute" : core.fmtDay(x.date)}${x.allDay ? "" : " · " + core.fmtMin(x.start)}</span></div>`).join("") : '<div class="dim">Keine Termine</div>'}</div>`;
  bindRefresh();
}

function schoolView() {
  if (model.empty) return emptyView();
  const wd = (new Date().getDay() + 6) % 7, nowMin = new Date().getHours() * 60 + new Date().getMinutes(), today = core.todayEpochDay();
  let rows = "";
  for (let i = 1; i <= 8; i++) {
    rows += `<tr><th>${i}<div class="small dim" style="font-weight:400">${core.fmtMin(core.START[i - 1])}</div></th>`;
    for (let d = 0; d < 5; d++) { const s = model.slots[d + "-" + i]; rows += `<td class="${s ? "" : "e"} ${d === wd && nowMin >= core.START[i - 1] && nowMin < core.END[i - 1] ? "now" : ""}" title="${esc(s || "")}">${esc(core.shortSubject(s || ""))}</td>`; }
    rows += "</tr>";
  }
  const hwRaw = core.sortHomework(model.homework), hw = hwRaw.map(core.describe);
  app.innerHTML = `<h1>Schule</h1>${standLine()}
  <div class="card"><h2>Stundenplan</h2><div class="scroll"><table class="tt"><tr><th></th>${core.DAYS.map((d, i) => `<th class="${i === wd ? "acc" : ""}">${d}</th>`).join("")}</tr>${rows}</table></div></div>
  <div class="card"><div class="row"><h2 class="grow">Aufgaben</h2><button class="pill" id="addhw">+ Aufgabe</button></div>${hw.length ? hw.map((h, i) => `<div class="item row ${h.done ? "done" : ""}"><span data-tg="${esc(hwRaw[i].id)}" style="cursor:pointer;font-size:20px">${h.done ? "✅" : "⬜"}</span><span class="grow">${esc(h.title)}${mark(hwRaw[i].id)}</span>${h.day != null ? `<span class="small ${!h.done && h.day < today ? "err" : "dim"}">${core.fmtDay(h.day)}</span>` : ""}<span data-del="${esc(hwRaw[i].id)}" style="cursor:pointer;padding-left:6px">🗑</span></div>`).join("") : '<div class="dim">Keine Aufgaben</div>'}</div>
  ${listCard("Noten", model.grades, g => `${esc(core.describe(g).title)} <b class="acc">${esc(g.grade ?? g.note ?? g.value ?? "")}</b>`)}`;
  bindRefresh();
  app.querySelectorAll("[data-tg]").forEach(b => b.onclick = () => { const it = model.homework.find(x => x.id === b.dataset.tg); if (it) change({ list: "hw", op: "set", itemId: it.id, fields: { done: !core.describe(it).done } }); });
  app.querySelectorAll("[data-del]").forEach(b => b.onclick = () => { if (confirm("Aufgabe löschen?")) change({ list: "hw", op: "del", itemId: b.dataset.del }); });
  $("#addhw").onclick = () => { const fd = form("Neue Aufgabe", `<input id="t" maxlength="120" placeholder="Was ist zu tun?"><input id="s" maxlength="18" placeholder="Fach (optional)"><div class="small dim" style="margin-top:8px">Fällig am</div><input id="d" type="date"><div class="chips" id="ty"><button class="chip on" data-ty="ha">Hausaufgabe</button><button class="chip" data-ty="test">Test</button><button class="chip" data-ty="ref">Referat</button></div>`, d => {
    const t = $("#t", d).value.trim(); if (!t) { toast("Bitte einen Titel eingeben"); return false; }
    const dv = $("#d", d).value, due = dv ? Math.floor(Date.UTC(+dv.slice(0, 4), +dv.slice(5, 7) - 1, +dv.slice(8, 10)) / 86400000) : -1;
    const ty = (d.querySelector("[data-ty].on") || {}).dataset?.ty || "ha";
    change({ list: "hw", op: "add", item: { id: core.newId(), title: t, subject: $("#s", d).value, due, type: ty, done: false, ts: Date.now() } });
  });
  fd.querySelectorAll("[data-ty]").forEach(b => b.onclick = () => fd.querySelectorAll("[data-ty]").forEach(x => x.classList.toggle("on", x === b))); };
}

function listCard(title, list, fmt) {
  return `<div class="card"><h2>${title}</h2>${list.length ? list.map(x => `<div class="item">${fmt ? fmt(x) : esc(core.describe(x).title)}</div>`).join("") : '<div class="dim">Nichts eingetragen</div>'}</div>`;
}

function eventsView() {
  if (model.empty) return emptyView();
  const today = core.todayEpochDay(), ev = core.upcomingEvents(model.events, today, 100);
  let html = "", last = null;
  for (const x of ev) {
    if (x.date !== last) { html += `<div class="small acc" style="margin-top:10px">${x.date === today ? "Heute" : x.date === today + 1 ? "Morgen" : core.fmtDay(x.date)}</div>`; last = x.date; }
    html += `<div class="item row"><span class="grow">${esc(x.title || "Termin")}${x.note ? `<div class="small dim">${esc(x.note)}</div>` : ""}</span><span class="small dim">${x.allDay ? "ganztägig" : core.fmtMin(x.start) + (x.end ? "–" + core.fmtMin(x.end) : "")}</span></div>`;
  }
  const dayLine = x => { const d = core.describe(x); return `${esc(d.title)}${d.day != null ? ` <span class="small dim">· ${core.fmtDay(d.day)}${d.day >= today ? " · in " + (d.day - today) + " Tagen" : ""}</span>` : ""}`; };
  app.innerHTML = `<h1>Termine</h1>${standLine()}<div class="card"><h2>Kommende Termine</h2>${html || '<div class="dim">Keine kommenden Termine</div>'}</div>
  ${listCard("Countdowns", model.countdowns, dayLine)}${listCard("Geburtstage", model.birthdays, dayLine)}`;
  bindRefresh();
}

function moreView() {
  const s = store.get("session", null);
  const notes = model.guest || model.empty ? "" : `<div class="card"><div class="row"><h2 class="grow">Notizen</h2><button class="pill" id="addn">+ Notiz</button></div>${model.notes.length ? model.notes.map(n => `<div class="item row"><div class="grow"><b>${esc(n.title || "Notiz")}</b>${mark(n.id)}<div class="small dim" style="white-space:pre-wrap">${esc((n.text || "").slice(0, 400))}</div></div><span data-dn="${esc(n.id)}" style="cursor:pointer">🗑</span></div>`).join("") : '<div class="dim">Keine Notizen</div>'}</div>
  <div class="card"><div class="row"><h2 class="grow">To-dos</h2><button class="pill" id="addt">+ To-do</button></div>${model.todos.length ? model.todos.map(t => `<div class="item row ${t.done ? "done" : ""}"><span data-tt="${esc(t.id)}" style="cursor:pointer;font-size:20px">${t.done ? "✅" : "⬜"}</span><span class="grow">${esc(core.describe(t).title)}${mark(t.id)}</span><span data-dt="${esc(t.id)}" style="cursor:pointer">🗑</span></div>`).join("") : '<div class="dim">Keine To-dos</div>'}</div>`;
  app.innerHTML = `<h1>Mehr</h1>${notes}
  <div class="grid2">${s ? '<div class="tile" id="t-prof"><span>🌐</span>Profil bearbeiten</div>' : ""}<div class="tile" id="t-cipher"><span>🔐</span>Geheimsprache</div><div class="tile" id="t-timer"><span>⏱️</span>Timer</div></div>
  <div class="card"><h2>Konto</h2>${s ? `<div class="dim">Angemeldet als <b>${esc(s.user)}</b></div><button class="btn sec" id="out">Abmelden</button>` : `<div class="dim">Ohne Konto</div><button class="btn" id="in">Anmelden</button>`}
  <div class="small dim" style="margin-top:10px">Aufgaben, Notizen und To-dos kannst du hier bearbeiten, die Android-App übernimmt das beim nächsten Start. Alles andere bearbeitest du in der App. Auf dem iPhone: Teilen → <b>Zum Home-Bildschirm</b>, dann startet SkyManager wie eine App.</div></div>`;
  $("#t-cipher").onclick = cipherView; $("#t-timer").onclick = timerView;
  if ($("#addn")) {
    $("#addn").onclick = () => form("Neue Notiz", `<input id="t" maxlength="60" placeholder="Titel"><textarea id="x" rows="6" maxlength="4000" placeholder="Text"></textarea>`, d => { const t = $("#t", d).value, x = $("#x", d).value; if (!t.trim() && !x.trim()) { toast("Bitte etwas eingeben"); return false; } change({ list: "notes", op: "add", item: { id: core.newId(), title: t, text: x, pinned: false, ts: Date.now() } }); });
    $("#addt").onclick = () => form("Neues To-do", `<input id="t" maxlength="120" placeholder="Was willst du erledigen?">`, d => { const t = $("#t", d).value.trim(); if (!t) { toast("Bitte etwas eingeben"); return false; } change({ list: "todos", op: "add", item: { id: core.newId(), title: t, done: false, ts: Date.now() } }); });
    app.querySelectorAll("[data-dn]").forEach(b => b.onclick = () => { if (confirm("Notiz löschen?")) change({ list: "notes", op: "del", itemId: b.dataset.dn }); });
    app.querySelectorAll("[data-tt]").forEach(b => b.onclick = () => { const t = model.todos.find(x => x.id === b.dataset.tt); if (t) change({ list: "todos", op: "set", itemId: t.id, fields: { done: !t.done } }); });
    app.querySelectorAll("[data-dt]").forEach(b => b.onclick = () => { if (confirm("To-do löschen?")) change({ list: "todos", op: "del", itemId: b.dataset.dt }); });
  }
  if ($("#t-prof")) $("#t-prof").onclick = profileEditor;
  if ($("#out")) $("#out").onclick = async () => { await logout(); store.del("guest"); render(); };
  if ($("#in")) $("#in").onclick = () => { store.del("guest"); model = null; render(); };
}

// ---- Werkzeuge
function shell(title, body) {
  const d = document.createElement("div"); d.className = "game";
  d.innerHTML = `<div class="top"><button class="back">‹ Zurück</button><b class="grow">${esc(title)}</b><span id="hud" class="dim"></span></div>${body}`;
  document.body.appendChild(d);
  const close = () => { d.remove(); if (d._stop) d._stop(); };
  $(".back", d).onclick = close;
  return d;
}
function cipherView() {
  const d = shell("Geheimsprache", `<div style="overflow:auto"><div class="chips" id="modes"></div><textarea id="in" rows="4" placeholder="Text eingeben …"></textarea><div class="chips"><button class="chip" id="enc">Verschlüsseln</button><button class="chip" id="dec">Entschlüsseln</button><button class="chip" id="cp">Kopieren</button></div><div class="card" id="out" style="white-space:pre-wrap;word-break:break-word;min-height:60px"></div></div>`);
  d.style.touchAction = "auto";
  const modes = [["morse", "Morse"], ["caesar", "Caesar (3)"], ["rot13", "ROT13"], ["bin", "Binär"], ["mirror", "Spiegel"]];
  let mode = "morse";
  const draw = () => { $("#modes", d).innerHTML = modes.map(m => `<button class="chip ${m[0] === mode ? "on" : ""}" data-m="${m[0]}">${m[1]}</button>`).join(""); d.querySelectorAll("[data-m]").forEach(b => b.onclick = () => { mode = b.dataset.m; draw(); }); };
  draw();
  const run = enc => { const t = $("#in", d).value; let r;
    if (mode === "morse") r = enc ? core.toMorse(t) : core.fromMorse(t);
    else if (mode === "caesar") r = core.caesar(t, enc ? 3 : -3);
    else if (mode === "rot13") r = core.caesar(t, 13);
    else if (mode === "bin") r = enc ? core.toBinary(t) : (core.fromBinary(t) ?? "Das ist kein gültiger Binärcode (je 8 Nullen/Einsen, mit Leerzeichen getrennt)");
    else r = core.mirror(t);
    $("#out", d).textContent = r; };
  $("#enc", d).onclick = () => run(true); $("#dec", d).onclick = () => run(false);
  $("#cp", d).onclick = async () => { try { await navigator.clipboard.writeText($("#out", d).textContent); toast("Kopiert"); } catch { toast("Kopieren nicht möglich"); } };
}
function beep() { try { const c = new (window.AudioContext || window.webkitAudioContext)(); [0, .3, .6].forEach(t => { const o = c.createOscillator(), g = c.createGain(); o.frequency.value = 880; o.connect(g); g.connect(c.destination); g.gain.setValueAtTime(.3, c.currentTime + t); g.gain.exponentialRampToValueAtTime(.001, c.currentTime + t + .25); o.start(c.currentTime + t); o.stop(c.currentTime + t + .26); }); } catch { } if (navigator.vibrate) navigator.vibrate([200, 100, 200]); }
function timerView() {
  const d = shell("Timer", `<div class="big" id="tv">05:00</div><div class="chips" style="justify-content:center">${[1, 3, 5, 10, 15, 25, 45, 60].map(m => `<button class="chip" data-m="${m}">${m} Min</button>`).join("")}</div><button class="btn" id="ss">Start</button><button class="btn sec" id="rs">Zurücksetzen</button>`);
  let dur = 300000, end = 0, left = dur, iv = null;
  const show = ms => { const s = Math.ceil(ms / 1000); $("#tv", d).textContent = String(Math.floor(s / 60)).padStart(2, "0") + ":" + String(s % 60).padStart(2, "0"); };
  const tick = () => { left = Math.max(0, end - Date.now()); show(left); if (left <= 0) { clearInterval(iv); iv = null; $("#ss", d).textContent = "Start"; beep(); toast("⏰ Zeit ist um!"); left = dur; } };
  d.querySelectorAll("[data-m]").forEach(b => b.onclick = () => { if (iv) return; dur = left = +b.dataset.m * 60000; show(left); });
  $("#ss", d).onclick = () => { if (iv) { clearInterval(iv); iv = null; left = Math.max(0, end - Date.now()); $("#ss", d).textContent = "Weiter"; } else { end = Date.now() + left; iv = setInterval(tick, 200); $("#ss", d).textContent = "Pause"; } };
  $("#rs", d).onclick = () => { clearInterval(iv); iv = null; left = dur; show(left); $("#ss", d).textContent = "Start"; };
  d._stop = () => clearInterval(iv);
  show(left);
}

// ---- Oeffentliches Profil bearbeiten
async function fileToJpeg(file, kind) {
  const url = await new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(r.result); r.onerror = rej; r.readAsDataURL(file); });
  const img = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => rej(new Error("Bild lässt sich nicht öffnen")); i.src = url; });
  const b = core.cropBox(img.naturalWidth, img.naturalHeight, kind);
  const cv = document.createElement("canvas"); cv.width = b.ow; cv.height = b.oh;
  cv.getContext("2d").drawImage(img, b.sx, b.sy, b.sw, b.sh, 0, 0, b.ow, b.oh);
  const max = kind === "a" ? 190 * 1024 : 580 * 1024;
  for (const q of [0.86, 0.75, 0.62, 0.5]) { const d = cv.toDataURL("image/jpeg", q).split(",")[1]; if (d.length * 3 / 4 <= max) return d; }
  throw new Error("Bild zu groß");
}
async function profileEditor() {
  const s = store.get("session", null); if (!s) return;
  const d = shell("Profil bearbeiten", `<div id="pe" style="overflow:auto;flex:1">Lade …</div>`);
  d.style.touchAction = "auto";
  const box = $("#pe", d);
  let st;
  const load = async () => { st = await api("/api/profile", { token: s.token }); draw(); };
  const ring = (p, im) => `<div style="position:relative;margin:4px 0 12px"><div style="height:${im.b ? 110 : 0}px;border-radius:16px;overflow:hidden;background:#1b241f">${im.b ? `<img src="/u/${esc(s.user)}/banner.jpg?v=${esc(im.b)}" style="width:100%;height:100%;object-fit:cover">` : ""}</div>
    <div style="width:96px;height:96px;border-radius:50%;margin:${im.b ? "-48px" : "0"} auto 0;${p.frame > 0 ? `background:conic-gradient(${core.FRAME_CSS[p.frame]});padding:4px` : "background:#26332b;padding:2px"};position:relative"><div style="width:100%;height:100%;border-radius:50%;background:#1b241f;display:flex;align-items:center;justify-content:center;font-size:44px;overflow:hidden">${im.a ? `<img src="/u/${esc(s.user)}/avatar.jpg?v=${esc(im.a)}" style="width:100%;height:100%;object-fit:cover">` : esc(p.avatar || "🙂")}</div></div></div>`;
  function draw() {
    if (!st.online) {
      box.innerHTML = `<div class="card"><h2>Dein öffentliches Profil ist offline</h2><div class="dim small">Mit einem öffentlichen Profil bekommst du einen Link (z. B. für deine Discord-Bio). Jeder mit dem Link sieht Name, Bild, Titel, Level und deine Stats.</div><button class="btn" id="on">Online stellen</button></div>`;
      $("#on", d).onclick = async () => {
        const e = model && model.engage ? model.engage : { xp: 0, streak: 0, level: 0 }, pr = model && model.profile ? model.profile : {};
        try { await api("/api/profile", { method: "PUT", token: s.token, body: { name: pr.name || s.user, title: pr.title || "", avatar: (pr.avatar || "").length <= 8 ? pr.avatar : "", level: e.level, xp: e.xp, streak: e.streak, frame: 0, stats: [] } }); toast("Profil ist online ✓"); await load(); } catch (x) { toast(x.message); }
      };
      return;
    }
    const p = st.profile, im = st.images || {};
    box.innerHTML = `${ring(p, im)}
    <div class="chips" style="justify-content:center"><label class="chip">📷 Profilbild<input type="file" accept="image/*" id="fa" hidden></label><label class="chip">🖼 Banner<input type="file" accept="image/*" id="fb" hidden></label>${im.a ? '<button class="chip" id="ra">Profilbild weg</button>' : ""}${im.b ? '<button class="chip" id="rb">Banner weg</button>' : ""}</div>
    <div class="card"><h2>Angaben</h2><input id="pn" maxlength="40" placeholder="Name" value="${esc(p.name)}"><input id="pt" maxlength="40" placeholder="Titel (z. B. Bossjäger)" value="${esc(p.title)}"><input id="pa" maxlength="8" placeholder="Emoji (wenn kein Profilbild)" value="${esc(p.avatar)}"><textarea id="pb" maxlength="200" rows="3" placeholder="Bio (optional)">${esc(p.bio)}</textarea></div>
    <div class="card"><h2>Rahmen</h2><div class="small dim">Level ${esc(core.LEVEL_NAMES[p.level] || "")}: höhere Rahmen schaltest du in der App frei.</div><div class="chips" id="fr">${core.FRAME_NAMES.map((n, i) => `<button class="chip ${i === p.frame ? "on" : ""}" data-f="${i}" ${core.frameUnlocked(i, p.level) ? "" : "disabled style=\"opacity:.45\""}>${core.frameUnlocked(i, p.level) ? "" : "🔒 "}${esc(n)}</button>`).join("")}</div></div>
    <button class="btn" id="save">Speichern</button>
    <div class="card"><div class="small dim">Dein Link</div><div style="word-break:break-all"><a class="acc" href="${esc(st.url)}" target="_blank" rel="noopener">${esc(st.url)}</a></div><button class="btn sec" id="cp">Link kopieren</button><button class="btn sec" id="off">Profil offline nehmen</button></div>
    <div class="small dim" style="margin:8px 0 20px">Level, XP und Stats kommen aus der App. Name, Titel, Bio, Bilder und Rahmen übernimmt die Android-App beim nächsten Start.</div>`;
    let frame = p.frame;
    d.querySelectorAll("[data-f]").forEach(b => b.onclick = () => { frame = +b.dataset.f; d.querySelectorAll("[data-f]").forEach(x => x.classList.toggle("on", x === b)); });
    $("#save", d).onclick = async () => { try { await api("/api/profile/web", { method: "PATCH", token: s.token, body: { name: $("#pn", d).value, title: $("#pt", d).value, avatar: $("#pa", d).value, bio: $("#pb", d).value, frame } }); toast("Gespeichert ✓"); await load(); } catch (x) { toast(x.message); } };
    for (const k of ["a", "b"]) {
      $("#f" + k, d).onchange = async e => { const f = e.target.files[0]; if (!f) return; toast("Bild wird vorbereitet …"); try { const data = await fileToJpeg(f, k); await api("/api/profile/img/" + k, { method: "PUT", token: s.token, body: { data, src: "web" } }); toast(k === "a" ? "Profilbild gespeichert ✓" : "Banner gespeichert ✓"); await load(); } catch (x) { toast(x.message); } };
      const rm = $("#r" + k, d); if (rm) rm.onclick = async () => { try { await api("/api/profile/img/" + k + "?src=web", { method: "DELETE", token: s.token }); await load(); } catch (x) { toast(x.message); } };
    }
    $("#cp", d).onclick = async () => { try { await navigator.clipboard.writeText(st.url); toast("Link kopiert"); } catch { toast("Kopieren nicht möglich"); } };
    $("#off", d).onclick = async () => { if (!confirm("Profil wirklich offline nehmen? Der Link funktioniert dann nicht mehr.")) return; try { await api("/api/profile", { method: "DELETE", token: s.token }); toast("Offline"); await load(); } catch (x) { toast(x.message); } };
  }
  try { await load(); } catch (e) { box.innerHTML = `<div class="err">${esc(e.message)}</div>`; }
}

// ---- Online-Bestenlisten (Werte kommen aus der Android-App)
const LBG = [["cps", "CPS-Test", "CPS"], ["aim", "Aim-Trainer", "Punkte"], ["typing", "Tipp-Tempo", "WPM"], ["snake", "Snake", "Punkte"], ["g2048", "2048", "Punkte"], ["react", "Reaktion", "ms"]];
function leaderView() {
  const d = shell("Online-Bestenlisten", `<div class="chips" id="lg"></div><div class="card" id="ll" style="overflow:auto;flex:1">Lade …</div><div class="small dim">Mitmachen und Anzeigename stellst du in der Android-App ein (Mehr → Online-Bestenlisten).</div>`);
  d.style.touchAction = "auto";
  let g = "cps";
  const draw = async () => {
    $("#lg", d).innerHTML = LBG.map(x => `<button class="chip ${x[0] === g ? "on" : ""}" data-lg="${x[0]}">${x[1]}</button>`).join("");
    d.querySelectorAll("[data-lg]").forEach(b => b.onclick = () => { g = b.dataset.lg; draw(); });
    const box = $("#ll", d); box.textContent = "Lade …";
    try {
      const s = store.get("session", null), r = await api("/api/leaderboard/" + g, { token: s ? s.token : null }), unit = LBG.find(x => x[0] === g)[2];
      const fmt = v => (g === "cps" ? Number(v).toFixed(1).replace(".", ",") : Number(v).toLocaleString("de-DE")) + " " + unit;
      box.innerHTML = (r.top.length ? r.top.map(e => `<div class="item row" style="${e.me ? "background:var(--card2);border-radius:10px;padding:8px" : ""}"><b style="width:40px">${e.rank <= 3 ? ["🥇", "🥈", "🥉"][e.rank - 1] : e.rank + "."}</b><span class="grow">${esc(e.name)}${e.me ? " (du)" : ""}</span><b class="acc">${esc(fmt(e.v))}</b></div>`).join("") : '<div class="dim">Noch niemand. Sei der Erste!</div>') + (r.me && r.me.rank > 50 ? `<div class="acc" style="margin-top:8px">Du: Platz ${r.me.rank} · ${esc(fmt(r.me.v))}</div>` : "") + (r.low ? '<div class="small dim" style="margin-top:6px">Hier gilt: weniger ist besser.</div>' : "");
    } catch (e) { box.innerHTML = `<div class="err">${esc(e.message)}</div>`; }
  };
  draw();
}

// ---- Spiele
const best = (k, v, higher = true) => { const b = store.get("best_" + k, null); if (v != null && (b == null || (higher ? v > b : v < b))) { store.set("best_" + k, v); return v; } return b; };
function gamesView() {
  const G = [["snake", "🐍", "Snake"], ["g2048", "🔢", "2048"], ["cps", "👆", "CPS-Test"], ["react", "⚡", "Reaktion"], ["aim", "🎯", "Aim-Trainer"]];
  app.innerHTML = `<h1>Spiele</h1><div class="tile" id="lbt" style="margin-bottom:10px"><span>🏆</span>Online-Bestenlisten</div><div class="grid2">${G.map(g => `<div class="tile" data-g="${g[0]}"><span>${g[1]}</span>${g[2]}<div class="small dim">${store.get("best_" + g[0], null) != null ? "Bestwert: " + store.get("best_" + g[0]) + (g[0] === "react" ? " ms" : g[0] === "cps" ? " CPS" : "") : "&nbsp;"}</div></div>`).join("")}</div>`;
  app.querySelectorAll("[data-g]").forEach(t => t.onclick = () => ({ snake, g2048, cps, react, aim })[t.dataset.g]());
  $("#lbt").onclick = leaderView;
}
function swipe(el, cb) {
  let sx = 0, sy = 0;
  el.addEventListener("touchstart", e => { sx = e.touches[0].clientX; sy = e.touches[0].clientY; }, { passive: true });
  el.addEventListener("touchend", e => { const dx = e.changedTouches[0].clientX - sx, dy = e.changedTouches[0].clientY - sy; if (Math.max(Math.abs(dx), Math.abs(dy)) < 24) return; cb(Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? 0 : 1) : (dy < 0 ? 2 : 3)); });
  const key = e => { const m = { ArrowLeft: 0, ArrowRight: 1, ArrowUp: 2, ArrowDown: 3 }[e.key]; if (m != null) { e.preventDefault(); cb(m); } };
  document.addEventListener("keydown", key);
  return () => document.removeEventListener("keydown", key);
}
function snake() {
  const d = shell("Snake", `<canvas class="board" id="cv"></canvas><div class="small dim" style="text-align:center">Wischen zum Lenken</div>`);
  const cv = $("#cv", d), N = 18, size = Math.min(innerWidth - 28, innerHeight - 200, 460); cv.width = cv.height = size * devicePixelRatio; cv.style.width = cv.style.height = size + "px";
  const c = cv.getContext("2d"), cell = cv.width / N;
  let s = [[9, 9], [8, 9], [7, 9]], dir = [1, 0], next = dir, food = [13, 9], score = 0, dead = false;
  const place = () => { do { food = [Math.floor(Math.random() * N), Math.floor(Math.random() * N)]; } while (s.some(p => p[0] === food[0] && p[1] === food[1])); };
  const off = swipe(d, m => { const v = [[-1, 0], [1, 0], [0, -1], [0, 1]][m]; if (v[0] !== -dir[0] || v[1] !== -dir[1]) next = v; });
  const draw = () => { c.fillStyle = "#101713"; c.fillRect(0, 0, cv.width, cv.height); c.fillStyle = "#ff6b6b"; c.beginPath(); c.arc((food[0] + .5) * cell, (food[1] + .5) * cell, cell * .4, 0, 7); c.fill(); s.forEach((p, i) => { c.fillStyle = i ? "#2fbf6f" : "#3ddc84"; c.fillRect(p[0] * cell + 1, p[1] * cell + 1, cell - 2, cell - 2); }); $("#hud", d).textContent = score + " · Best " + (best("snake") ?? 0); };
  const iv = setInterval(() => {
    if (dead) return; dir = next;
    const h = [s[0][0] + dir[0], s[0][1] + dir[1]];
    if (h[0] < 0 || h[1] < 0 || h[0] >= N || h[1] >= N || s.some(p => p[0] === h[0] && p[1] === h[1])) { dead = true; best("snake", score); toast("💥 " + score + " Punkte"); setTimeout(() => { s = [[9, 9], [8, 9], [7, 9]]; dir = next = [1, 0]; score = 0; dead = false; place(); }, 1200); return; }
    s.unshift(h); if (h[0] === food[0] && h[1] === food[1]) { score++; place(); } else s.pop(); draw();
  }, 140);
  d._stop = () => { clearInterval(iv); off(); }; draw();
}
function g2048() {
  const d = shell("2048", `<div class="g2048 board" id="b"></div><button class="btn sec" id="new" style="max-width:420px;margin:12px auto 0">Neues Spiel</button>`);
  let g, score;
  const colors = { 2: "#2a3a31", 4: "#2f4a39", 8: "#1f7a4a", 16: "#1f9a58", 32: "#22b866", 64: "#3ddc84", 128: "#c9b23a", 256: "#e0a52b", 512: "#ef8a25", 1024: "#ef5f25", 2048: "#ff3d6e" };
  const spawn = () => { const e = []; g.forEach((r, i) => r.forEach((v, j) => { if (!v) e.push([i, j]); })); if (e.length) { const [i, j] = e[Math.floor(Math.random() * e.length)]; g[i][j] = Math.random() < .9 ? 2 : 4; } };
  const draw = () => { $("#b", d).innerHTML = g.flat().map(v => `<div style="background:${v ? colors[v] || "#ff3d6e" : "#24302a"};color:${v >= 64 && v < 128 ? "#04130a" : "#fff"}">${v || ""}</div>`).join(""); $("#hud", d).textContent = score + " · Best " + (best("g2048") ?? 0); };
  const reset = () => { g = [[0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]; score = 0; spawn(); spawn(); draw(); };
  const off = swipe(d, m => { const r = core.move2048(g, m); if (!r.moved) return; g = r.grid; score += r.gained; spawn(); draw(); if (!core.canMove(g)) { best("g2048", score); toast("Keine Züge mehr · " + score + " Punkte"); } });
  $("#new", d).onclick = () => { best("g2048", score); reset(); };
  d._stop = () => { best("g2048", score); off(); }; reset();
}
function cps() {
  const d = shell("CPS-Test", `<div class="chips" style="justify-content:center"><button class="chip on" data-s="5">5 s</button><button class="chip" data-s="10">10 s</button></div><div class="zone" id="z" style="background:var(--card2)">Tippe zum Starten</div>`);
  let sec = 5, n = 0, t0 = 0, running = false, to = null;
  d.querySelectorAll("[data-s]").forEach(b => b.onclick = () => { if (running) return; sec = +b.dataset.s; d.querySelectorAll("[data-s]").forEach(x => x.classList.toggle("on", x === b)); });
  $("#z", d).addEventListener("touchstart", e => { e.preventDefault(); hit(); }, { passive: false });
  $("#z", d).addEventListener("mousedown", hit);
  function hit() {
    if (!running) { if (Date.now() - t0 < sec * 1000 + 800) return; running = true; n = 0; t0 = Date.now(); to = setTimeout(done, sec * 1000); }
    n++; $("#z", d).textContent = n; $("#z", d).style.background = "var(--acc2)";
  }
  function done() { running = false; const v = Math.round(n / sec * 10) / 10; const b = best("cps", v); $("#z", d).style.background = "var(--card2)"; $("#z", d).innerHTML = `${v} CPS<div class="small dim">Best ${b} · tippe für neue Runde</div>`; }
  d._stop = () => clearTimeout(to);
}
function react() {
  const d = shell("Reaktion", `<div class="zone" id="z" style="background:var(--card2)">Tippe zum Starten</div>`);
  let state = 0, t = 0, to = null; const z = $("#z", d);
  const tap = e => { e.preventDefault();
    if (state === 0 || state === 3) { state = 1; z.style.background = "#7a2b2b"; z.textContent = "Warte auf Grün …"; to = setTimeout(() => { state = 2; t = performance.now(); z.style.background = "var(--acc)"; z.style.color = "var(--on)"; z.textContent = "JETZT!"; }, 1200 + Math.random() * 2800); }
    else if (state === 1) { clearTimeout(to); state = 3; z.style.background = "var(--card2)"; z.textContent = "Zu früh! Tippe nochmal"; }
    else if (state === 2) { const ms = Math.round(performance.now() - t); const b = best("react", ms, false); state = 3; z.style.background = "var(--card2)"; z.style.color = ""; z.innerHTML = `${ms} ms<div class="small dim">Best ${b} ms · tippe für neue Runde</div>`; } };
  z.addEventListener("touchstart", tap, { passive: false }); z.addEventListener("mousedown", tap);
  d._stop = () => clearTimeout(to);
}
function aim() {
  const d = shell("Aim-Trainer", `<div class="zone" id="z" style="background:var(--card2);position:relative;overflow:hidden">Tippe zum Starten (30 s)</div>`);
  const z = $("#z", d); let score = 0, running = false, end = 0, iv = null;
  const spot = () => { z.querySelectorAll(".t").forEach(x => x.remove()); const t = document.createElement("div"); t.className = "t"; const r = z.getBoundingClientRect(), s = 56; t.style.cssText = `position:absolute;width:${s}px;height:${s}px;border-radius:50%;background:radial-gradient(circle,#fff 0 18%,#ff6b6b 19% 45%,#fff 46% 60%,#ff6b6b 61%);left:${Math.random() * (r.width - s)}px;top:${Math.random() * (r.height - s)}px`; const hit = e => { e.preventDefault(); e.stopPropagation(); if (!running) return; score += 10; spot(); }; t.addEventListener("touchstart", hit, { passive: false }); t.addEventListener("mousedown", hit); z.appendChild(t); };
  const startTap = e => { if (running) { e.preventDefault(); score = Math.max(0, score - 3); return; } e.preventDefault(); running = true; score = 0; end = Date.now() + 30000; z.textContent = ""; spot(); iv = setInterval(() => { const l = Math.max(0, end - Date.now()); $("#hud", d).textContent = score + " · " + Math.ceil(l / 1000) + " s"; if (!l) { clearInterval(iv); running = false; z.querySelectorAll(".t").forEach(x => x.remove()); const b = best("aim", score); z.innerHTML = `${score} Punkte<div class="small dim">Best ${b} · tippe für neue Runde</div>`; } }, 100); };
  z.addEventListener("touchstart", startTap, { passive: false }); z.addEventListener("mousedown", startTap);
  d._stop = () => clearInterval(iv);
}

// ---- Start
(async () => {
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("/app/sw.js").catch(() => { });
  try { if (await load(false)) { render(); load(true).then(ok => { if (ok) render(); }).catch(() => { }); return; } } catch (e) { return loginView(e.message); }
  if (store.get("guest", false)) { model = { guest: true }; tab = "games"; render(); return; }
  loginView();
})();
