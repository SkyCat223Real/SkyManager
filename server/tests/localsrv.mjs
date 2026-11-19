import http from "node:http";
import worker from "../worker.js";
const m = new Map(); let lastMail = null;
const env = { RESEND_KEY: "re_test", EMAIL_SECRET: "lokal", MAIL_FETCH: async (u, o) => { lastMail = JSON.parse(o.body); return new Response("{}", { status: 200 }); },
  SKY: { async get(k) { const e = m.get(k); return e ? e.val : null; }, async put(k, v) { m.set(k, { val: String(v) }); }, async delete(k) { m.delete(k); }, async list(o = {}) { return { keys: [...m.keys()].filter(k => k.startsWith(o.prefix || "")).map(name => ({ name })), list_complete: true }; } } };
http.createServer(async (req, res) => {
  if (req.url === "/__lastmail") { res.end(JSON.stringify(lastMail)); return; }
  if (req.url === "/__dump") { res.end(JSON.stringify(Object.fromEntries([...m].map(([k, v]) => [k, v.val])))); return; }
  const chunks = []; for await (const c of req) chunks.push(c);
  const r = await worker.fetch(new Request("http://127.0.0.1:" + process.argv[2] + req.url, { method: req.method, headers: req.headers, body: ["GET", "HEAD"].includes(req.method) ? undefined : Buffer.concat(chunks) }), env, { waitUntil() {} });
  res.writeHead(r.status, Object.fromEntries(r.headers)); res.end(Buffer.from(await r.arrayBuffer()));
}).listen(+process.argv[2], "127.0.0.1", () => console.log("bereit"));
