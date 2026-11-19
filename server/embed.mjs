import fs from "node:fs"; import crypto from "node:crypto";
const src = fs.readFileSync("worker.js", "utf8").split("\n");
const old = JSON.parse(src[26].replace(/^const WEB = /, "").replace(/;\s*$/, ""));
const names = { "/app": "index.html", "/app/app.js": "app.js", "/app/core.js": "core.js", "/app/sw.js": "sw.js", "/app/manifest.json": "manifest.json", "/app/icon.png": "icon.png" };
const h = crypto.createHash("sha256"); for (const f of ["index.html", "app.js", "core.js", "manifest.json"]) h.update(fs.readFileSync("web/" + f));
const ver = h.digest("hex").slice(0, 12);
let sw = fs.readFileSync("web/sw.js", "utf8").replace(/skymanager-web-[0-9a-f]+/, "skymanager-web-" + ver); fs.writeFileSync("web/sw.js", sw);
const out = {};
for (const [k, v] of Object.entries(old)) { const f = "web/" + names[k]; out[k] = v.b64 ? { ...v, body: fs.readFileSync(f).toString("base64") } : { ...v, body: fs.readFileSync(f, "utf8") }; }
src[26] = "const WEB = " + JSON.stringify(out) + ";";
fs.writeFileSync(process.argv[2] || "worker.new.js", src.join("\n"));
console.log("eingebettet, Web-Version", ver);
