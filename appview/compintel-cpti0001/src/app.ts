// compintel.etzhayyim.com — Competitive Intelligence Dashboard (thin edge facade).
// Business logic: 40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/compintel_worker_main.py
// LangGraph loop: fetch_signals → analyze_pricing → analyze_product → analyze_hiring → score_threat → store_snapshot
// Weekly refresh: LangServer BPMN-contract timer Monday 08:00 JST
// No PII — all data is public competitive intelligence.

interface SecretBinding { get(): Promise<string>; }
interface Env {
  DISPATCHER_URL?: string;
  DISPATCHER_INTERNAL_SECRET?: string | SecretBinding;
  APP_NANOID?: string;
}

const APP = "compintel";
const NSID_PREFIX = "com.etzhayyim.apps.compintel.";

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);

    if (url.pathname === "/health" || url.pathname === "/_app/meta") {
      return json({
        ok: true,
        actor: "did:web:compintel.etzhayyim.com",
        nanoid: env.APP_NANOID ?? "cpti0001",
        execution: "edge-bpmn+langgraph-langserver",
        businessLogic: "40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/compintel_worker_main.py",
        bpmn: "etzhayyim-root/00-contracts/bpmn/com/etzhayyim/compintel",
        adr: "90-docs/adr/2605072000-langgraph-agent-loop-pattern.md",
        integrations: ["ads.etzhayyim.com", "resend", "news.etzhayyim.com"],
        schedule: "weekly Monday 08:00 JST",
      });
    }

    const nsid = url.pathname.startsWith("/xrpc/")
      ? url.pathname.slice("/xrpc/".length)
      : "";

    if (nsid.startsWith(NSID_PREFIX) && (req.method === "POST" || req.method === "GET")) {
      const body = await bodyWithQuery(req, url);
      if (body.__invalidJson) return json({ error: "InvalidJson" }, 400);
      return proxyToDispatcher(env, nsid, body);
    }

    return json({ error: "NotFound", message: `${APP} not found` }, 404);
  },
} satisfies ExportedHandler<Env>;

async function bodyWithQuery(req: Request, url: URL): Promise<Record<string, unknown>> {
  let body: Record<string, unknown> = {};
  if (req.method === "POST") {
    const text = await req.text();
    try { body = text ? JSON.parse(text) : {}; } catch { return { __invalidJson: true }; }
  }
  for (const [k, v] of url.searchParams) if (!(k in body)) body[k] = v;
  return body;
}

async function proxyToDispatcher(env: Env, nsid: string, body: Record<string, unknown>): Promise<Response> {
  const base = (env.DISPATCHER_URL ?? "https://dispatcher.etzhayyim.com").replace(/\/+$/, "");
  const headers: Record<string, string> = { "content-type": "application/json" };
  const trust = await internalTrustSecret(env);
  if (trust) headers["x-internal-trust"] = trust;
  const resp = await fetch(`${base}/xrpc/${nsid}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
  const text = await resp.text();
  return new Response(text, {
    status: resp.status,
    headers: { "content-type": resp.headers.get("content-type") ?? "application/json", "cache-control": "no-store" },
  });
}

async function internalTrustSecret(env: Env): Promise<string> {
  const binding = env.DISPATCHER_INTERNAL_SECRET;
  if (!binding) return "";
  try { return typeof binding === "string" ? binding : await binding.get(); } catch { return ""; }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}
