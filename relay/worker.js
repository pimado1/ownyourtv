// Receives the anonymous report a TV sends when its owner presses Share, and files it on GitHub: one issue per
// TV model, one comment per report. Nothing is stored here; the GitHub token is a Worker secret, never in the app.

const NAME = /^[A-Za-z0-9 ._+\-()]{1,64}$/;
const PKG = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
const HOME = ["ok", "refused", "blocked", "kept", "already"];
const LABEL = { ok: "works", refused: "home-refused", blocked: "home-blocked", kept: "works", already: "works" };

function clean(r) {
  if (!r || typeof r !== "object" || r.v !== 1) return null;
  const out = {};
  for (const k of ["app", "brand", "maker", "model", "device", "product", "android"]) {
    if (typeof r[k] !== "string" || !NAME.test(r[k])) return null;
    out[k] = r[k];
  }
  out.known = r.known == null ? null : typeof r.known === "string" && /^[a-z0-9._-]{1,80}$/i.test(r.known) ? r.known : null;
  for (const k of ["sdk", "off", "on", "failed"]) {
    if (!Number.isInteger(r[k]) || r[k] < 0 || r[k] > 999) return null;
    out[k] = r[k];
  }
  if (!HOME.includes(r.home)) return null;
  out.home = r.home;
  out.wifi = r.wifi === true;
  out.failedPkgs = Array.isArray(r.failedPkgs) ? r.failedPkgs.filter((p) => typeof p === "string" && PKG.test(p) && p.length < 120).slice(0, 40) : [];
  return out;
}

function table(r) {
  const rows = [
    ["App", r.app], ["Android", `${r.android} (SDK ${r.sdk})`], ["Device / product", `${r.device} / ${r.product}`],
    ["In the TV database", r.known ? `yes, \`${r.known}\`` : "no"], ["Home screen", r.home],
    ["Wi-Fi step needed", r.wifi ? "yes" : "no"], ["Apps switched off / back on", `${r.off} / ${r.on}`], ["Failed steps", String(r.failed)],
  ];
  let md = "| | |\n|---|---|\n" + rows.map(([a, b]) => `| ${a} | ${b} |`).join("\n");
  if (r.failedPkgs.length) md += "\n\nCould not switch off: " + r.failedPkgs.map((p) => `\`${p}\``).join(", ");
  return md;
}

async function gh(env, path, init = {}) {
  const res = await fetch(`https://api.github.com${path}`, {
    ...init,
    headers: { authorization: `Bearer ${env.GITHUB_TOKEN}`, accept: "application/vnd.github+json", "user-agent": "ownyourtv-reports", "content-type": "application/json" },
  });
  if (!res.ok) throw new Error(`${path} ${res.status}`);
  return res.json();
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method !== "POST" || url.pathname !== "/report") return new Response("OwnYourTV reports", { status: 404 });
    const { success } = await env.LIMIT.limit({ key: req.headers.get("cf-connecting-ip") || "?" });
    if (!success) return new Response("slow down", { status: 429 });
    const body = await req.text();
    if (body.length > 4096) return new Response("too big", { status: 413 });
    let r;
    try { r = clean(JSON.parse(body)); } catch { r = null; }
    if (!r) return new Response("bad report", { status: 400 });

    const title = `TV: ${r.brand} ${r.model} (${r.device})`;
    const label = LABEL[r.home];
    try {
      const q = encodeURIComponent(`repo:${env.REPO} is:issue label:tv-report in:title "${title}"`);
      const found = (await gh(env, `/search/issues?q=${q}`)).items.find((i) => i.title === title);
      if (found) {
        await gh(env, `/repos/${env.REPO}/issues/${found.number}/comments`, { method: "POST", body: JSON.stringify({ body: table(r) }) });
        await gh(env, `/repos/${env.REPO}/issues/${found.number}/labels`, { method: "POST", body: JSON.stringify({ labels: [label] }) });
      } else {
        await gh(env, `/repos/${env.REPO}/issues`, {
          method: "POST",
          body: JSON.stringify({ title, labels: ["tv-report", label], body: `Reported from the TV by OwnYourTV (anonymous).\n\n${table(r)}` }),
        });
      }
    } catch (e) {
      return new Response("github error", { status: 502 });
    }
    return new Response("thanks", { status: 200 });
  },
};
