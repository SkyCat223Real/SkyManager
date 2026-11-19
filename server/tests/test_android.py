import asyncio, subprocess, os, json, urllib.request, re
from playwright.async_api import async_playwright
PORT = 18932; SRV = "http://127.0.0.1:%d" % PORT
srv = subprocess.Popen(["node", "localsrv.mjs", str(PORT)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True); srv.stdout.readline()
res = []; errs = []
def check(n, ok): res.append(bool(ok)); print(("OK     " if ok else "FEHLER ") + n)
async def main():
    async with async_playwright() as p:
        b = await p.webkit.launch(env={k: v for k, v in os.environ.items() if "proxy" not in k.lower()})
        pg = await (await b.new_context(**p.devices["iPhone 14"])).new_page(); pg.on("pageerror", lambda e: errs.append(str(e)))
        await pg.goto(SRV + "/app")
        # Konto wie von der Android-App: Registrierung mit Code, Backup mit app = Versionsnummer
        out = await pg.evaluate("""async () => { const core = await import('/app/core.js');
          const post = async (u, b, t) => (await fetch(u, { method: 'POST', headers: { 'content-type': 'application/json', ...(t ? { authorization: 'Bearer ' + t } : {}) }, body: JSON.stringify(b) })).json();
          await post('/api/email/code', { email: 'andro@example.de', purpose: 'register' });
          const mail = await (await fetch('/__lastmail')).json(); const code = mail.text.match(/\\b(\\d{6})\\b/)[1];
          const k = await core.deriveKeys('androtest', 'android-123');
          await post('/api/register', { user: 'androtest', auth: k.authHex, email: 'andro@example.de', emailCode: code });
          const t = (await post('/api/login', { user: 'androtest', auth: k.authHex })).token;
          const ex = core.starterExport('Andro'); const data = await core.encryptText(k.encKey, ex);
          return (await post('/api/backups', { data, app: '9.43.0', note: 'Auto' }, t)).ok; }""")
        check("Android-Konto mit Backup (App 9.43.0) vorbereitet", out)
        await pg.fill("#u", "androtest"); await pg.fill("#p", "android-123"); await pg.click("#go")
        await pg.wait_for_selector("nav button[data-t=more]", timeout=60000)
        await pg.click("nav button[data-t=more]"); await pg.click("#addt"); await pg.fill(".game #t", "Für Android"); await pg.click(".game #fs"); await pg.wait_for_timeout(1500)
        body = await pg.text_content("#app")
        await pg.reload(); await pg.wait_for_selector("nav button[data-t=more]", timeout=30000); await pg.wait_for_timeout(1500)
        d = json.loads(urllib.request.urlopen(SRV + "/__dump").read())
        check("Wie bisher: To-do mit ⏳, wartet als Paket auf die Android-App, Browser schreibt KEIN eigenes Backup", "Für Android" in body and "⏳" in body and len(json.loads(d["c:androtest"])) == 1 and len(json.loads(d["b:androtest"])) == 1)
        await b.close()
    check("Keine JavaScript-Fehler", not errs)
    print("ALLE %d TESTS BESTANDEN" % len(res) if all(res) else "FEHLGESCHLAGEN", errs[:3])
try: asyncio.run(main())
finally: srv.kill()
