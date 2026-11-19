# Cloud-Server (Cloudflare Worker)

`worker.js` läuft unter `cloud.skycat223.de`: Online-Konten, verschlüsselte Backups, E-Mail-Codes (Resend), öffentliche Profile, Bestenlisten, Admin und die Web-App unter `/app`.

Die Web-App (`../web/`) ist in `worker.js` eingebettet: `node embed.mjs worker.new.js` baut sie neu ein, `node split.mjs` holt sie wieder heraus.

Geheimnisse (`RESEND_KEY`, `EMAIL_SECRET`) liegen nur als geheime Variablen bei Cloudflare, nicht im Code.

**Tests:** `tests/localsrv.mjs` startet einen lokalen Server mit nachgebautem Mailversand, `test_reg.py` und `test_android.py` prüfen die Web-App im WebKit-Browser (Playwright).
