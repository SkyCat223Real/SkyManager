// SkyManager Web – Kern (laeuft im Browser und in Node). Keine Oberflaeche hier.
// Gleiche Verfahren wie die Android-App: PBKDF2-SHA256 (150000 Runden, 512 Bit), AES-GCM, gzip.

export const ITERATIONS = 150000;
export const LEVEL_XP = [0, 100, 250, 500, 1000, 2000, 4000, 7500, 15000, 30000];
export const LEVEL_NAMES = ["Neuling", "Anfänger", "Entdecker", "Kenner", "Profi", "Experte", "Meister", "Champion", "Legende", "⭐ SkyMaster"];
export const DAYS = ["Mo", "Di", "Mi", "Do", "Fr"];
export const DAYS_LONG = ["Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag"];
export const START = [465, 515, 580, 630, 690, 740, 830, 880];
export const END = [510, 560, 625, 675, 735, 785, 875, 925];

export function normUser(u) { return String(u == null ? "" : u).trim().toLowerCase(); }
export function validUser(u) { return /^[a-z0-9_.-]{3,24}$/.test(normUser(u)); }
export function hex(bytes) { return [...new Uint8Array(bytes)].map(b => b.toString(16).padStart(2, "0")).join(""); }
export function b64urlBytes(s) {
  s = String(s).replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** Wie CloudMath.keys: {authHex, encKey}. encKey ist nicht exportierbar (bleibt im Browser). */
export async function deriveKeys(user, password, iterations = ITERATIONS, extractable = false) {
  const enc = new TextEncoder();
  const base = await crypto.subtle.importKey("raw", enc.encode(String(password)), "PBKDF2", false, ["deriveBits"]);
  const bits = new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", salt: enc.encode("skymanager-cloud:" + normUser(user)), iterations, hash: "SHA-256" }, base, 512));
  const encKey = await crypto.subtle.importKey("raw", bits.slice(32, 64), { name: "AES-GCM" }, extractable, ["encrypt", "decrypt"]);
  return { authHex: hex(bits.slice(0, 32)), encKey };
}

async function gunzip(bytes) {
  const ds = new DecompressionStream("gzip");
  const stream = new Blob([bytes]).stream().pipeThrough(ds);
  return await new Response(stream).text();
}

/** Wie CloudMath.decrypt: SKYB1_<IV(12)+Geheimtext+Tag>. null bei falschem Passwort oder Veraenderung. */
export async function decryptBackup(encKey, blob) {
  try {
    if (typeof blob !== "string" || !blob.startsWith("SKYB1_")) return null;
    const all = b64urlBytes(blob.slice(6));
    if (all.length < 29) return null;
    const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: all.slice(0, 12) }, encKey, all.slice(12));
    return await gunzip(new Uint8Array(plain));
  } catch { return null; }
}

/** Android-Einstellungen ({t, v}) in normale Werte. */
export function decodePrefs(o) {
  const out = {};
  if (!o || typeof o !== "object") return out;
  for (const [k, e] of Object.entries(o)) {
    if (!e || typeof e !== "object" || !("v" in e)) continue;
    out[k] = e.t === "ss" ? (Array.isArray(e.v) ? e.v.map(String) : []) : e.v;
  }
  return out;
}

function jsonList(s) { try { const a = JSON.parse(s || "[]"); return Array.isArray(a) ? a.filter(x => x && typeof x === "object") : []; } catch { return []; } }
function jsonObj(s) { try { const o = JSON.parse(s || "{}"); return o && typeof o === "object" && !Array.isArray(o) ? o : {}; } catch { return {}; } }

export function levelIndex(xp) { for (let i = LEVEL_XP.length - 1; i >= 0; i--) if (xp >= LEVEL_XP[i]) return i; return 0; }

/** Ein Android-Backup (Db.export) in ein Modell fuer die Anzeige. */
export function parseExport(text) {
  const j = typeof text === "string" ? JSON.parse(text) : text;
  if (!j || j.app !== "SkyManager") throw new Error("Kein SkyManager-Backup");
  const db = decodePrefs(j.db), eng = decodePrefs(j.engage), prof = decodePrefs(j.profile), cal = decodePrefs(j.calendar);
  const xp = Number(eng.xp) || 0, li = levelIndex(xp);
  const tt = jsonObj(db.tt).slots || {};
  const slots = {};
  for (const [k, v] of Object.entries(tt)) if (/^[0-4]-[1-8]$/.test(k) && String(v).trim()) slots[k] = String(v).trim().slice(0, 18);
  return {
    created: Number(j.created) || 0,
    profile: { name: String(prof.name || ""), bio: String(prof.bio || ""), avatar: String(prof.avatar || ""), title: String(prof.title || ""), status: String(prof.status || "") },
    engage: { xp, coins: Number(eng.coins) || 0, streak: Number(eng.streak) || 0, level: li, levelName: LEVEL_NAMES[li], nextXp: li + 1 < LEVEL_XP.length ? LEVEL_XP[li + 1] : null, levelXp: LEVEL_XP[li] },
    slots,
    homework: jsonList(db.hw),
    notes: jsonList(db.notes),
    todos: jsonList(db.todos),
    countdowns: jsonList(db.countdowns),
    birthdays: jsonList(db.birthdays),
    grades: jsonList(db.grades),
    events: jsonList(cal.events).map(e => ({ ...e, date: Number(e.date) })).filter(e => Number.isFinite(e.date)),
  };
}

// Kurzformen fuer den Stundenplan (enge Zellen auf dem Handy), volle Namen bleiben sonst ueberall
const SHORT = { geschichte: "Gesch.", informatik: "Info", erdkunde: "Erdk.", mathematik: "Mathe", biologie: "Bio", religion: "Reli", "französisch": "Franz.", spanisch: "Span.", wirtschaft: "Wirt.", sozialkunde: "Sozi", politik: "Poli", philosophie: "Philo", englisch: "Engl.", deutsch: "Deutsch", "naturwissenschaften": "NaWi", gemeinschaftskunde: "GK", ethik: "Ethik" };
export function shortSubject(s, max = 7) {
  const t = String(s ?? "").trim();
  if (t.length <= max) return t;
  const k = SHORT[t.toLowerCase()];
  if (k) return k;
  return t.slice(0, max - 1) + ".";
}

export function dateOf(epochDay) { return new Date(Number(epochDay) * 86400000); }
export function fmtDay(epochDay) { const d = dateOf(epochDay); return String(d.getUTCDate()).padStart(2, "0") + "." + String(d.getUTCMonth() + 1).padStart(2, "0") + "." + d.getUTCFullYear(); }
export function fmtMin(m) { m = Number(m) || 0; return String(Math.floor(m / 60)).padStart(2, "0") + ":" + String(m % 60).padStart(2, "0"); }
export function todayEpochDay(now = Date.now(), tzOffsetMin = new Date(now).getTimezoneOffset()) { return Math.floor((now - tzOffsetMin * 60000) / 86400000); }

/** Ein Eintrag mit unbekannten Feldern lesbar machen: Titel, Datum, erledigt. */
export function describe(e) {
  const title = [e.title, e.name, e.subject, e.text, e.desc, e.note].find(x => typeof x === "string" && x.trim()) || "Eintrag";
  const day = [e.due, e.day, e.date].find(x => Number.isFinite(Number(x)) && Number(x) > 1000 && Number(x) < 100000);
  const done = e.done === true || e.done === 1 || e.done === "1" || e.checked === true;
  return { title: String(title).trim(), day: day == null ? null : Number(day), done };
}

/** Aufgaben sortiert: offene zuerst nach Faelligkeit, erledigte hinten. */
export function sortHomework(list) {
  return [...list].sort((a, b) => {
    const da = describe(a), db = describe(b);
    if (da.done !== db.done) return da.done ? 1 : -1;
    return (da.day ?? 1e9) - (db.day ?? 1e9);
  });
}

/** Termine ab heute, sortiert nach Tag und Uhrzeit. */
export function upcomingEvents(events, today, max = 50) {
  return events.filter(e => e.date >= today).sort((a, b) => a.date - b.date || (a.allDay ? -1 : 0) - (b.allDay ? -1 : 0) || (a.start || 0) - (b.start || 0)).slice(0, max);
}

// ---- Geheimsprache (wie in der App)
const MORSE = { a: ".-", b: "-...", c: "-.-.", d: "-..", e: ".", f: "..-.", g: "--.", h: "....", i: "..", j: ".---", k: "-.-", l: ".-..", m: "--", n: "-.", o: "---", p: ".--.", q: "--.-", r: ".-.", s: "...", t: "-", u: "..-", v: "...-", w: ".--", x: "-..-", y: "-.--", z: "--..", "0": "-----", "1": ".----", "2": "..---", "3": "...--", "4": "....-", "5": ".....", "6": "-....", "7": "--...", "8": "---..", "9": "----.", ".": ".-.-.-", ",": "--..--", "?": "..--..", "!": "-.-.--", "ä": ".-.-", "ö": "---.", "ü": "..--", "ß": "...--.." };
const MORSE_REV = Object.fromEntries(Object.entries(MORSE).map(([k, v]) => [v, k]));
export function toMorse(s) { return String(s).toLowerCase().split(" ").map(w => [...w].map(c => MORSE[c] || "").filter(Boolean).join(" ")).filter(Boolean).join(" / "); }
export function fromMorse(s) { return String(s).trim().split(/\s*\/\s*/).map(w => w.split(/\s+/).map(c => MORSE_REV[c] || "").join("")).join(" "); }
export function caesar(s, k) { k = ((k % 26) + 26) % 26; return String(s).replace(/[a-z]/gi, c => { const b = c <= "Z" ? 65 : 97; return String.fromCharCode((c.charCodeAt(0) - b + k) % 26 + b); }); }
export function toBinary(s) { return [...new TextEncoder().encode(String(s))].map(b => b.toString(2).padStart(8, "0")).join(" "); }
export function fromBinary(s) { try { const bytes = String(s).trim().split(/\s+/).filter(Boolean).map(x => { if (!/^[01]{8}$/.test(x)) throw 0; return parseInt(x, 2); }); return new TextDecoder().decode(new Uint8Array(bytes)); } catch { return null; } }
export function mirror(s) { return [...String(s)].reverse().join(""); }

// ---- 2048 (reine Logik)
export function slideRow(row) {
  const nums = row.filter(x => x), out = []; let gained = 0;
  for (let i = 0; i < nums.length; i++) {
    if (i + 1 < nums.length && nums[i] === nums[i + 1]) { out.push(nums[i] * 2); gained += nums[i] * 2; i++; } else out.push(nums[i]);
  }
  while (out.length < row.length) out.push(0);
  return { row: out, gained };
}
export function move2048(grid, dir) { // dir: 0 links, 1 rechts, 2 hoch, 3 runter
  const n = grid.length, g = grid.map(r => [...r]); let gained = 0, moved = false;
  for (let i = 0; i < n; i++) {
    let line = dir < 2 ? g[i].slice() : g.map(r => r[i]);
    if (dir === 1 || dir === 3) line.reverse();
    const r = slideRow(line); gained += r.gained;
    let res = r.row; if (dir === 1 || dir === 3) res = res.reverse();
    for (let j = 0; j < n; j++) { const old = dir < 2 ? g[i][j] : g[j][i]; if (old !== res[j]) moved = true; if (dir < 2) g[i][j] = res[j]; else g[j][i] = res[j]; }
  }
  return { grid: g, gained, moved };
}
export function canMove(grid) { for (let d = 0; d < 4; d++) if (move2048(grid, d).moved) return true; return false; }


// ---- Aenderungen vom iPhone (gleiche Regeln wie ChangeMath.java)
export const CHANGE_LISTS = ["hw", "notes", "todos"];
export const HW_TYPES = ["ha", "test", "ref"];
export const NOTE_COLOR = -10682578;
const jtrim = s => s.replace(/^[\x00-\x20]+|[\x00-\x20]+$/g, "");
export function cleanText(s, max) {
  if (s == null) return "";
  let t = jtrim(String(s).replace(/[\r\n\t]+/g, " ").replace(/ {2,}/g, " "));
  return t.length > max ? jtrim(t.slice(0, max)) : t;
}
function multi(s, max) { s = s == null ? "" : String(s).replace(/\r/g, ""); return s.length > max ? s.slice(0, max) : s; }
function optBool(v) { return v === true || (typeof v === "string" && v.toLowerCase() === "true"); }
function optLong(v, d) { if (v == null || v === "" || typeof v === "boolean") return d; const n = Number(v); return Number.isFinite(n) ? Math.trunc(n) : d; }
export function validId(s) { return typeof s === "string" && /^[A-Za-z0-9-]{8,64}$/.test(s); }

export function cleanAdd(list, i, color = NOTE_COLOR) {
  if (!i || typeof i !== "object" || !validId(i.id)) return null;
  const ts = optLong(i.ts, 0);
  if (list === "hw") {
    const title = cleanText(i.title, 120); if (!title) return null;
    let type = i.type == null ? "ha" : String(i.type); if (!HW_TYPES.includes(type)) type = "ha";
    let due = optLong(i.due, -1); if (due < -1 || due > 200000) due = -1;
    return { id: i.id, type, subject: cleanText(i.subject, 18), title, due, done: optBool(i.done), ts };
  }
  if (list === "notes") {
    const title = cleanText(i.title, 60), text = multi(i.text, 4000);
    if (!title && !jtrim(text)) return null;
    return { id: i.id, title, text, color, pinned: optBool(i.pinned), ts };
  }
  if (list === "todos") {
    const title = cleanText(i.title, 120); if (!title) return null;
    return { id: i.id, title, done: optBool(i.done), prio: 1, due: -1, ts };
  }
  return null;
}
function settable(list, f) { return list === "notes" ? ["pinned", "title", "text"].includes(f) : (list === "hw" || list === "todos") && f === "done"; }

/** Wendet Aenderungen auf eine Liste an (veraendert items). Rueckgabe: Zahl der wirksamen Aenderungen. */
export function applyOps(items, list, ops, color = NOTE_COLOR) {
  const mine = ops.filter(o => o && o.list === list).sort((a, b) => (optLong(a.ts, 0) - optLong(b.ts, 0)) || (String(a.id) < String(b.id) ? -1 : String(a.id) > String(b.id) ? 1 : 0));
  let n = 0;
  const idx = id => id ? items.findIndex(x => x && x.id === id) : -1;
  for (const o of mine) {
    if (o.op === "add") { const it = cleanAdd(list, o.item, color); if (!it || idx(it.id) >= 0) continue; items.push(it); n++; }
    else if (o.op === "set") {
      const i = idx(o.itemId); const f = o.fields; if (i < 0 || !f || typeof f !== "object") continue;
      let changed = false;
      for (const k of Object.keys(f)) {
        if (!settable(list, k)) continue;
        const v = k === "done" || k === "pinned" ? optBool(f[k]) : k === "title" ? cleanText(f[k], list === "notes" ? 60 : 120) : multi(f[k], 4000);
        if (String(items[i][k]) !== String(v)) { items[i][k] = v; changed = true; }
      }
      if (changed) n++;
    } else if (o.op === "del") { const i = idx(o.itemId); if (i >= 0) { items.splice(i, 1); n++; } }
  }
  return n;
}

export function parseChanges(plain) {
  try {
    const j = JSON.parse(plain);
    if (!j || j.app !== "SkyManager-Changes" || !Array.isArray(j.ops)) return [];
    return j.ops.filter(o => o && validId(o.id) && CHANGE_LISTS.includes(o.list) && ["add", "set", "del"].includes(o.op)).slice(0, 100);
  } catch { return []; }
}

export function newId() { return crypto.randomUUID(); }

function b64url(bytes) { let s = ""; for (const b of bytes) s += String.fromCharCode(b); return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""); }
async function gzip(text) { const st = new Blob([new TextEncoder().encode(text)]).stream().pipeThrough(new CompressionStream("gzip")); return new Uint8Array(await new Response(st).arrayBuffer()); }

/** Verschluesselt wie CloudMath.encrypt: SKYB1_<IV+Geheimtext>, vorher gzip. */
export async function encryptText(encKey, text) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, encKey, await gzip(text)));
  const all = new Uint8Array(12 + ct.length); all.set(iv, 0); all.set(ct, 12);
  return "SKYB1_" + b64url(all);
}
export function changesPayload(ops) { return JSON.stringify({ app: "SkyManager-Changes", v: 1, ops }); }


// ---- Profil-Rahmen (gleich wie ProfilePlusMath / Worker)
export const FRAME_NAMES = ["Kein Rahmen", "Neuling", "Entdecker", "Kenner", "Profi", "Experte", "Meister", "Champion", "Legende", "⭐ SkyMaster"];
export const FRAME_LEVEL = [0, 0, 2, 3, 4, 5, 6, 7, 8, 9];
export const FRAME_CSS = ["", "#9e9e9e,#e0e0e0,#9e9e9e", "#2ecc71,#8bf5b4,#2ecc71", "#2d8cff,#7fd3ff,#2d8cff", "#8e44ff,#d08cff,#8e44ff", "#ff8a00,#ffd166,#ff8a00", "#e53935,#ffc107,#e53935", "#00e5ff,#ff00e5,#00e5ff", "#ffc400,#fff59d,#ffa000,#ffc400", "#ff3b3b,#ffb300,#3ddc84,#2d8cff,#b44cff,#ff3b3b"];
export function frameUnlocked(frame, level) { return frame >= 0 && frame < FRAME_NAMES.length && level >= FRAME_LEVEL[frame]; }

/** Zielgroesse fuer Bilder: Profilbild quadratisch, Banner 3:1. Ausschnitt mittig. */
export function cropBox(w, h, kind) {
  const ratio = kind === "a" ? 1 : 3;
  let cw = w, ch = Math.round(w / ratio);
  if (ch > h) { ch = h; cw = Math.round(h * ratio); }
  const out = kind === "a" ? [320, 320] : [960, 320];
  return { sx: Math.round((w - cw) / 2), sy: Math.round((h - ch) / 2), sw: cw, sh: ch, ow: out[0], oh: out[1] };
}

// ---- Reines iPhone-Konto (ohne Android-App): Start-Backup und Aenderungen direkt ins Backup
export const WEB_TAG = "web";
export const MIN_PW = 8;
const pstr = v => ({ t: "s", v: String(v) });
/** Leeres Backup im gleichen Format wie Db.export der Android-App (nur Daten-Listen und Profilname). */
export function starterExport(name, now = Date.now()) {
  return JSON.stringify({ app: "SkyManager", version: 2, created: now, db: { hw: pstr("[]"), notes: pstr("[]"), todos: pstr("[]") }, profile: { name: pstr(String(name || "").slice(0, 40)) } });
}
/** Wendet Aenderungen auf ein komplettes Backup an (Aufgaben, Notizen, To-dos). Andere Daten bleiben unberuehrt. */
export function applyToExport(text, ops, now = Date.now()) {
  const j = JSON.parse(text);
  if (!j || j.app !== "SkyManager") throw new Error("Kein SkyManager-Backup");
  if (!j.db || typeof j.db !== "object") j.db = {};
  let n = 0;
  for (const l of CHANGE_LISTS) {
    let arr = [];
    try { const e = j.db[l]; arr = e && typeof e.v === "string" ? JSON.parse(e.v) : []; if (!Array.isArray(arr)) arr = []; } catch { arr = []; }
    n += applyOps(arr, l, ops);
    j.db[l] = pstr(JSON.stringify(arr));
  }
  j.created = now;
  return { text: JSON.stringify(j), n };
}
/** Regeln fuer ein neues Konto. null = in Ordnung, sonst die Meldung. */
export function checkSignup(user, email, code, pw, pw2) {
  if (!validUser(user)) return "Benutzername: 3 bis 24 Zeichen, nur a-z, 0-9, _ . -";
  if (!/^[^\s@]+@[^\s@]+\.[a-z]{2,}$/i.test(String(email || "").trim())) return "Bitte eine gültige E-Mail eingeben";
  if (!/^\d{6}$/.test(String(code || "").trim())) return "Bitte den 6-stelligen Code aus der Mail eingeben";
  if (String(pw || "").length < MIN_PW) return "Das Passwort braucht mindestens 8 Zeichen";
  if (pw !== pw2) return "Die Passwörter stimmen nicht überein";
  return null;
}
