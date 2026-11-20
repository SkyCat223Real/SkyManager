# Android-App (Erweiterungen ab 9.44)

Die App wird auf Basis der offiziellen Release-APK **v9.43.0** gebaut. Neue Funktionen liegen als Java-Quellcode in `src/` und kommen als zusätzliche `classes3.dex` in die APK.

| Datei | Funktion |
| --- | --- |
| `DcTracker*` | 💬 Discord-Tracker (9.44) |
| `ModLog*` | 🛡️ Mod-Log (9.45) |
| `MyDayWidget`, `WidgetMath`, `res-add/` | 📱 Homescreen-Widget „Mein Tag“ (9.46) |
| `RbxGroup*`, `RbxGroups` | 👥 Roblox-Gruppen (9.47) |
| `Apply*`, `Applications` | 📋 Bewerbungen (9.48) |
| `Event*` | 🎉 Event-Planer (9.49) |
| `RbxPlayer*` | 🔎 Roblox-Spieler-Check mit Zweitkonto-Einschätzung (9.50) |
| `Giveaway*` | 🎁 Giveaways mit fairer Ziehung (9.51) |
| `Rules*`, `HookPost` | 📜 Regelwerk mit Vorlagen und Discord-Post (9.52) |
| `NewTools` | Menü: neue Werkzeuge vorne, der Rest kommt unverändert aus `NewToolsBase` (9.43) |

**Bauen:** `build.sh` (braucht apktool, ecj, Android-Build-Tools, dex2jar und den Signaturschlüssel, der **nicht** im Repo liegt).

**Tests:** `tests/src/LogicTests.java`, `Logic2Tests.java` und `Logic3Tests.java` (82 Tests, laufen auf normalem Java mit org.json), `tests/fixtures/` enthält echte Antworten von Discord und Roblox.
