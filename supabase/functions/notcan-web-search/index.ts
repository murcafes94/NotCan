import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type SearchResult = {
  title: string;
  url: string;
  snippet: string;
  engine: string;
};

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

function cleanText(value: unknown, max = 1200) {
  return String(value ?? "")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

function safeUrl(value: unknown) {
  try {
    const url = new URL(String(value ?? ""));
    return url.protocol === "http:" || url.protocol === "https:" ? url.toString() : "";
  } catch {
    return "";
  }
}

function normalizeDuckUrl(raw: string) {
  const href = raw.startsWith("//") ? `https:${raw}` : raw;
  try {
    const parsed = new URL(href, "https://duckduckgo.com/");
    const redirected = parsed.searchParams.get("uddg");
    return safeUrl(redirected ? decodeURIComponent(redirected) : parsed.toString());
  } catch {
    return "";
  }
}

function dedupe(results: SearchResult[], limit: number) {
  const seen = new Set<string>();
  const output: SearchResult[] = [];
  for (const result of results) {
    const url = safeUrl(result.url);
    if (!url || seen.has(url)) continue;
    seen.add(url);
    output.push({
      title: cleanText(result.title, 240) || url,
      url,
      snippet: cleanText(result.snippet, 900),
      engine: cleanText(result.engine, 40) || "web",
    });
    if (output.length >= limit) break;
  }
  return output;
}

async function searchSearXng(base: string, query: string, limit: number): Promise<SearchResult[]> {
  const endpoint = new URL("/search", base.endsWith("/") ? base : `${base}/`);
  endpoint.searchParams.set("q", query);
  endpoint.searchParams.set("format", "json");
  endpoint.searchParams.set("engines", "duckduckgo");
  endpoint.searchParams.set("language", "es");
  endpoint.searchParams.set("safesearch", "1");

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 7000);
  try {
    const response = await fetch(endpoint, {
      signal: controller.signal,
      headers: {
        "Accept": "application/json",
        "User-Agent": "NotCan/0.8 web-search",
      },
    });
    if (!response.ok) throw new Error(`SearXNG ${response.status}`);
    const root = await response.json();
    const results = Array.isArray(root?.results) ? root.results : [];
    return dedupe(results.map((item: any) => ({
      title: item?.title,
      url: item?.url,
      snippet: item?.content ?? item?.snippet,
      engine: Array.isArray(item?.engines) ? item.engines.join(",") : item?.engine ?? "duckduckgo",
    })), limit);
  } finally {
    clearTimeout(timer);
  }
}

async function searchDuckDuckGo(query: string, limit: number): Promise<SearchResult[]> {
  const body = new URLSearchParams({ q: query, kl: "es-es" });
  const response = await fetch("https://html.duckduckgo.com/html/", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "Accept": "text/html,application/xhtml+xml",
      "Accept-Language": "es-ES,es;q=0.9,en;q=0.6",
      "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36",
    },
    body,
  });
  if (!response.ok) throw new Error(`DuckDuckGo ${response.status}`);
  const html = await response.text();
  const results: SearchResult[] = [];

  const blockRe = /<div[^>]+class="[^"]*result[^"]*"[^>]*>([\s\S]*?)(?=<div[^>]+class="[^"]*result[^"]*"|$)/gi;
  let block: RegExpExecArray | null;
  while ((block = blockRe.exec(html)) && results.length < limit * 2) {
    const content = block[1];
    const link = /<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/i.exec(content);
    if (!link) continue;
    const snippet = /<(?:a|div)[^>]+class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)<\/(?:a|div)>/i.exec(content);
    const url = normalizeDuckUrl(link[1].replace(/&amp;/g, "&"));
    if (!url) continue;
    results.push({
      title: cleanText(link[2], 240),
      url,
      snippet: cleanText(snippet?.[1] ?? "", 900),
      engine: "duckduckgo",
    });
  }
  return dedupe(results, limit);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  try {
    const body = await req.json();
    const query = cleanText(body?.q ?? body?.query, 500);
    if (!query) return json({ error: "Escribe una búsqueda" }, 400);
    const limit = Math.max(1, Math.min(8, Number(body?.limit ?? 5) || 5));
    const requested = cleanText(body?.engine, 40).toLowerCase() || "auto";
    const searxngUrl = cleanText(Deno.env.get("SEARXNG_URL"), 500);

    let provider = "duckduckgo";
    let results: SearchResult[] = [];

    if (searxngUrl && requested !== "duckduckgo") {
      try {
        results = await searchSearXng(searxngUrl, query, limit);
        if (results.length) provider = "searxng";
      } catch {
        // DuckDuckGo is the intentional fallback when a private SearXNG instance is unavailable.
      }
    }

    if (!results.length) results = await searchDuckDuckGo(query, limit);

    return json({
      query,
      provider,
      engine: "duckduckgo",
      results,
      count: results.length,
    });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : String(error) }, 502);
  }
});
