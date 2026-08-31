import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type ContextItem = { title?: string; body?: string; subject?: string; classTitle?: string };
type Mode = "chat" | "summary" | "questions" | "concept-map";
type Provider = "auto" | "mistral" | "free";

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

function cleanText(value: unknown, max = 10000) {
  return String(value ?? "")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

function words(text: string) {
  return text.toLocaleLowerCase("es")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .split(/[^a-z0-9áéíóúüñ]+/i)
    .filter((word) => word.length >= 4 && !STOP_WORDS.has(word));
}

function sentences(text: string) {
  return text.split(/(?<=[.!?])\s+|\n+/)
    .map((sentence) => sentence.trim())
    .filter((sentence) => sentence.length >= 35 && sentence.length <= 700);
}

function rankSentences(text: string, prompt: string, limit: number) {
  const query = new Set(words(prompt));
  return sentences(text).slice(0, 180).map((sentence, index) => {
    const sentenceWords = words(sentence);
    const overlap = sentenceWords.reduce((score, word) => score + (query.has(word) ? 3 : 0), 0);
    const richness = new Set(sentenceWords).size / Math.max(1, sentenceWords.length);
    return { sentence, score: overlap + richness + Math.max(0, 1.5 - index * 0.015) };
  }).sort((a, b) => b.score - a.score).slice(0, limit).map((item) => item.sentence);
}

function sourceLabel(item: ContextItem, index: number) {
  const parts = [item.subject, item.classTitle, item.title].filter(Boolean);
  return parts.length ? parts.join(" · ") : `Fuente ${index + 1}`;
}

function normalizeContext(raw: unknown): ContextItem[] {
  return Array.isArray(raw) ? raw.slice(0, 12).map((item: any) => ({
    title: cleanText(item?.title, 200),
    subject: cleanText(item?.subject, 200),
    classTitle: cleanText(item?.classTitle, 200),
    body: cleanText(item?.body, 10000),
  })) : [];
}

function buildSummary(items: ContextItem[], prompt: string) {
  if (!items.length) return "TuNot necesita apuntes, transcripciones o materiales de NotCan para generar un resumen gratuito y fiel a tus fuentes.";
  const lines: string[] = [];
  for (const [index, item] of items.entries()) {
    const top = rankSentences(cleanText(item.body, 9000), prompt, 2);
    if (top.length) lines.push(`• ${sourceLabel(item, index)}: ${top.join(" ")}`);
  }
  return lines.length ? `Resumen de TuNot basado en tus materiales:\n\n${lines.join("\n\n")}` : "No encontré suficiente texto legible en los materiales seleccionados.";
}

function buildQuestions(items: ContextItem[], prompt: string) {
  const selected = rankSentences(items.map((item) => cleanText(item.body, 9000)).filter(Boolean).join(" "), prompt, 10);
  if (!selected.length) return "TuNot necesita material de estudio para crear preguntas sin inventar contenido.";
  return selected.map((sentence, index) => `${index + 1}. **Pregunta:** ¿Qué afirma o explica el material sobre este punto?\n   **Respuesta:** ${sentence.length > 320 ? `${sentence.slice(0, 317)}…` : sentence}`).join("\n\n");
}

function buildConceptMap(items: ContextItem[], prompt: string) {
  if (!items.length) return "TuNot necesita materiales para construir un mapa conceptual fiel a tus fuentes.";
  const central = cleanText(prompt, 120) || items[0]?.subject || items[0]?.title || "Tema de estudio";
  const branches = items.slice(0, 8).map((item, index) => {
    const top = rankSentences(cleanText(item.body, 9000), prompt, 2);
    return `- **${sourceLabel(item, index)}**\n  - ${top[0] || "Sin contenido suficiente"}${top[1] ? `\n  - ${top[1]}` : ""}`;
  });
  return `## ${central}\n\n${branches.join("\n")}`;
}

function buildChat(items: ContextItem[], prompt: string) {
  if (!items.length) return "TuNot está disponible en modo gratuito, pero necesita que actives tus apuntes como contexto para responder sin una API externa.";
  const combined = items.map((item, index) => `${sourceLabel(item, index)}. ${cleanText(item.body, 9000)}`).join(" ");
  const selected = rankSentences(combined, prompt, 7);
  if (!selected.length) return "No encontré en tus materiales información suficiente para responder con seguridad.";
  return `Según tus materiales de NotCan:\n\n${selected.map((sentence) => `• ${sentence}`).join("\n\n")}`;
}

function freeAnswer(mode: Mode, items: ContextItem[], prompt: string) {
  return mode === "summary" ? buildSummary(items, prompt)
    : mode === "questions" ? buildQuestions(items, prompt)
    : mode === "concept-map" ? buildConceptMap(items, prompt)
    : buildChat(items, prompt);
}

function buildMistralPrompt(mode: Mode, items: ContextItem[], prompt: string) {
  const task = mode === "summary" ? "Resume el material con estructura clara y útil para estudiar."
    : mode === "questions" ? "Crea 10 preguntas de estudio con sus respuestas; evita inventar datos."
    : mode === "concept-map" ? "Construye un mapa conceptual textual legible con concepto central, ramas y relaciones."
    : "Responde la consulta del estudiante con precisión académica y claridad pedagógica.";
  const context = items.map((item, index) => `[Fuente ${index + 1}] ${sourceLabel(item, index)}\n${cleanText(item.body, 9000)}`).join("\n\n");
  return `Eres TuNot, asistente académico de NotCan. Responde en español. No inventes citas, páginas, autores ni referencias. Distingue el material proporcionado de conocimiento general y conserva literalmente los textos entre comillas cuando debas citarlos.\n\nTAREA: ${task}\n\nSOLICITUD:\n${prompt}${context ? `\n\nMATERIAL DE NOTCAN:\n${context}` : ""}`;
}

function extractMistralText(root: any): string {
  const outputs = Array.isArray(root?.outputs) ? root.outputs : [];
  const parts: string[] = [];
  for (const output of outputs) {
    if (output?.type && output.type !== "message.output") continue;
    const content = output?.content;
    if (typeof content === "string" && content.trim()) parts.push(content.trim());
    if (Array.isArray(content)) {
      for (const chunk of content) {
        if (typeof chunk === "string" && chunk.trim()) parts.push(chunk.trim());
        else if (chunk && typeof chunk === "object") {
          const text = String(chunk.text ?? chunk.content ?? "").trim();
          if (text) parts.push(text);
        }
      }
    }
  }
  return parts.join("\n").trim();
}

async function askMistral(body: any, mode: Mode, items: ContextItem[], prompt: string) {
  const apiKey = cleanText(body?.mistralApiKey, 500) || Deno.env.get("MISTRAL_API_KEY") || "";
  const agentId = cleanText(body?.mistralAgentId, 500) || Deno.env.get("MISTRAL_AGENT_ID") || "";
  if (!apiKey || !agentId) throw new Error("Configura el Agent ID y la API key de Mistral en NotCan AI.");

  const response = await fetch("https://api.mistral.ai/v1/conversations", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "Accept": "application/json",
    },
    body: JSON.stringify({ agent_id: agentId, inputs: buildMistralPrompt(mode, items, prompt), store: false }),
  });
  const text = await response.text();
  let root: any = {};
  try { root = JSON.parse(text || "{}"); } catch { /* handled below */ }
  if (!response.ok) {
    const message = String(root?.message ?? root?.detail ?? text ?? `HTTP ${response.status}`).slice(0, 600);
    throw new Error(`Mistral (${response.status}): ${message}`);
  }
  const answer = extractMistralText(root);
  if (!answer) throw new Error("Mistral respondió sin contenido de texto.");
  return { answer, model: "Mistral Agent", provider: "mistral", sourceCount: items.length };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  try {
    const body = await req.json();
    const prompt = cleanText(body?.prompt, 12000);
    if (!prompt) throw new Error("Escribe una pregunta.");
    const mode = (["chat", "summary", "questions", "concept-map"].includes(String(body?.mode)) ? String(body.mode) : "chat") as Mode;
    const provider = (["auto", "mistral", "free"].includes(String(body?.provider)) ? String(body.provider) : "auto") as Provider;
    const context = normalizeContext(body?.context);
    const hasMistral = Boolean(cleanText(body?.mistralApiKey, 500) && cleanText(body?.mistralAgentId, 500)) || Boolean(Deno.env.get("MISTRAL_API_KEY") && Deno.env.get("MISTRAL_AGENT_ID"));

    if (provider === "mistral" || (provider === "auto" && hasMistral)) {
      return json(await askMistral(body, mode, context, prompt));
    }

    return json({
      answer: freeAnswer(mode, context, prompt),
      model: "TuNot gratuito · fuentes de NotCan",
      provider: "free",
      sourceCount: context.length,
    });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : String(error) }, 400);
  }
});

const STOP_WORDS = new Set([
  "para", "como", "pero", "porque", "cuando", "donde", "desde", "hasta", "sobre", "entre",
  "este", "esta", "estos", "estas", "esto", "tambien", "tiene", "tienen", "hacer", "puede",
  "pueden", "cual", "cuales", "quien", "quienes", "segun", "todo", "toda", "todos", "todas",
  "unos", "unas", "solo", "sino", "mismo", "misma", "mismos", "mismas", "muy", "mas",
]);
