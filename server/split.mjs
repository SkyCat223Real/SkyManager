import fs from "node:fs";
const src = fs.readFileSync("worker.js", "utf8");
const line = src.split("\n")[26];
const obj = JSON.parse(line.replace(/^const WEB = /, "").replace(/;\s*$/, ""));
fs.mkdirSync("web", { recursive: true });
for (const [k, v] of Object.entries(obj)) {
  const keys = Object.keys(v).filter(x => x !== "body");
  const name = k === "/app" ? "index.html" : k.replace(/^\/app\//, "");
  if (v.b64) fs.writeFileSync("web/" + name, Buffer.from(v.body, "base64")); else fs.writeFileSync("web/" + name, v.body);
  console.log(k, "->", name, keys.join(","), v.body.length);
}
