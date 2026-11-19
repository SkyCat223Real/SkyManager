import asyncio, subprocess, os, json, urllib.request, re
from playwright.async_api import async_playwright
PORT = 18931; SRV = "http://127.0.0.1:%d" % PORT
srv = subprocess.Popen(["node", "localsrv.mjs", str(PORT)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True); srv.stdout.readline()
res = []; errs = []
def check(n, ok): res.append(bool(ok)); print(("OK     " if ok else "FEHLER ") + n)
def code(): return re.search(r"\b(\d{6})\b", json.loads(urllib.request.urlopen(SRV + "/__lastmail").read())["text"]).group(1)
def dump(): return json.loads(urllib.request.urlopen(SRV + "/__dump").read())
async def main():
    async with async_playwright() as p:
        b = await p.webkit.launch(env={k: v for k, v in os.environ.items() if "proxy" not in k.lower()})
        ctx = await b.new_context(**p.devices["iPhone 14"]); pg = await ctx.new_page()
        pg.on("pageerror", lambda e: errs.append(str(e))); pg.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
        await pg.goto(SRV + "/app"); await pg.click("#reg")
        await pg.fill("#ru", "iphonetest"); await pg.fill("#re", "Iphone.Test@Example.de"); await pg.click("#rc")
        await pg.wait_for_selector("text=Code geschickt an", timeout=10000)
        check("Code anfordern im Browser: Mail verschickt, Adresse verdeckt angezeigt", "ip***@example.de" in await pg.text_content("#rm"))
        await pg.fill("#rk", "12"); await pg.fill("#rp", "geheim-123"); await pg.fill("#rp2", "geheim-123"); await pg.click("#rgo")
        check("Eingaben werden geprüft (zu kurzer Code)", "6-stelligen Code" in await pg.text_content("#rm"))
        await pg.fill("#rk", code()); await pg.fill("#rp2", "anders-123"); await pg.click("#rgo")
        check("Passwörter müssen übereinstimmen", "stimmen nicht" in await pg.text_content("#rm"))
        await pg.fill("#rp2", "geheim-123"); await pg.click("#rgo")
        await pg.wait_for_selector("nav button[data-t=more]", timeout=60000); await pg.wait_for_timeout(500)
        d = dump()
        check("Konto angelegt, E-Mail nur verschlüsselt gespeichert, Start-Backup als iPhone-Konto vorhanden", "u:iphonetest" in d and "iphone.test" not in json.dumps(d).lower() and json.loads(d["b:iphonetest"])[0]["app"] == "web")
        await pg.click("nav button[data-t=more]"); await pg.click("#addt"); await pg.fill(".game #t", "Erstes To-do"); await pg.click(".game #fs")
        await pg.wait_for_timeout(1500); body = await pg.text_content("#app")
        check("To-do angelegt, ohne ⏳ (keine Android-App nötig)", "Erstes To-do" in body and "⏳" not in body)
        await pg.click("nav button[data-t=school]"); await pg.click("#addhw"); await pg.fill(".game #t", "Mathe S. 40"); await pg.click(".game #fs"); await pg.wait_for_timeout(1200)
        await pg.reload(); await pg.wait_for_selector("nav button[data-t=more]", timeout=30000); await pg.wait_for_timeout(1500)
        await pg.click("nav button[data-t=more]"); body = await pg.text_content("#app")
        d = dump(); bl = json.loads(d["b:iphonetest"])
        check("Nach dem Neuladen: Änderungen ins Backup geschrieben (neues iPhone-Backup), keine offenen Pakete mehr", "Erstes To-do" in body and len(bl) >= 2 and bl[0]["note"] == "iPhone" and json.loads(d.get("c:iphonetest", "[]")) == [])
        for i in range(13):
            await pg.click("#addt"); await pg.fill(".game #t", f"Aufgabe {i}"); await pg.click(".game #fs"); await pg.wait_for_timeout(700)
        await pg.wait_for_timeout(1500); d = dump()
        check("Viele Änderungen: spätestens nach 12 automatisch ins Backup übernommen (offene Pakete bleiben klein)", len(json.loads(d.get("c:iphonetest", "[]"))) < 12)
        await pg.screenshot(path="/home/claude/rebuild/shot_more.png")
        # Backup im Android-Format pruefen (vorher neu laden: offene Pakete werden dabei uebernommen)
        await pg.reload(); await pg.wait_for_selector("nav button[data-t=more]", timeout=30000); await pg.wait_for_timeout(2000)
        plain = await pg.evaluate("""async () => { const core = await import('/app/core.js'); const db = await new Promise(r => { const q = indexedDB.open('skymanager', 1); q.onsuccess = () => r(q.result); });
          const key = await new Promise(r => { const t = db.transaction('keys').objectStore('keys').get('enc'); t.onsuccess = () => r(t.result); });
          const s = JSON.parse(localStorage.getItem('sky_session')); const l = await (await fetch('/api/backups', { headers: { authorization: 'Bearer ' + s.token } })).json();
          const d = await (await fetch('/api/backups/' + l.backups[0].id, { headers: { authorization: 'Bearer ' + s.token } })).json();
          return await core.decryptBackup(key, d.data); }""")
        j = json.loads(plain)
        todos = json.loads(j["db"]["todos"]["v"])
        check("Backup im Format der Android-App (app, version 2, db-Einträge mit t/v), alle 14 To-dos + 1 Aufgabe drin", j["app"] == "SkyManager" and j["version"] == 2 and j["db"]["todos"]["t"] == "s" and len(todos) == 14 and len(json.loads(j["db"]["hw"]["v"])) == 1)
        open("/home/claude/rebuild/web_export.json", "w").write(plain)
        await pg.click("#logout") if await pg.query_selector("#logout") else None
        ctx2 = await b.new_context(**p.devices["iPhone 14"]); pg2 = await ctx2.new_page(); pg2.on("pageerror", lambda e: errs.append(str(e)))
        await pg2.goto(SRV + "/app"); await pg2.click("#reg"); await pg2.fill("#re", "iphone.test+zwei@example.de"); await pg2.click("#rc"); await pg2.wait_for_timeout(1500)
        check("Zweites Konto mit derselben E-Mail (+Zusatz) wird verweigert", "schon zu einem Konto" in await pg2.text_content("#rm"))
        await b.close()
    check("Keine JavaScript-Fehler", not errs)
    print("ALLE %d TESTS BESTANDEN" % len(res) if all(res) else "FEHLGESCHLAGEN", errs[:3])
try: asyncio.run(main())
finally: srv.kill()
