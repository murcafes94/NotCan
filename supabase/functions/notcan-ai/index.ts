import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type ContextItem = {
  title?: string;
  body?: string;
  subject?: string;
  classTitle?: string;
};

type Mode = "chat" | "summary" | "questions" | "concept-map";

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
  return text
    .split(/(?<=[.!?])\s+|\n+/)
    .map((sentence) => sentence.trim())
    .filter((sentence) => sentence.length >= 35 && sentence.length <= 700);
}

function rankSentences(text: string, prompt: string, limit: number) {
  const query = new Set(words(prompt));
  const all = sentences(text).slice(0, 180);
  return all
    .map((sentence, index) => {
      const sentenceWords = words(sentence);
      const overlap = sentenceWords.reduce((score, word) => score + (query.has(word) ? 3 : 0), 0);
      const richness = new Set(sentenceWords).size / Math.max(1, sentenceWords.length);
      const position = Math.max(0, 1.5 - index * 0.015);
      return { sentence, score: overlap + richness + position };
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((item) => item.sentence);
}

function sourceLabel(item: ContextItem, index: number) {
  const parts = [item.subject, item.classTitle, item.title].filter(Boolean);
  return parts.length ? parts.join(" · ") : `Fuente ${index + 1}`;
}

function buildSummary(items: ContextItem[], prompt: string) {
  if (!items.length) return "TuNot necesita apuntes, transcripciones o materiales de NotCan para generar un resumen gratuito y fiel a tus fuentes.";
  const lines: string[] = [];
  for (const [index, item] of items.entries()) {
    const body = cleanText(item.body, 9000);
    if (!body) continue;
    const top = rankSentences(body, prompt, 2);
    if (top.length) lines.push(`• ${sourceLabel(item, index)}: ${top.join(" ")}`);
  }
  return lines.length
    ? `Resumen de TuNot basado en tus materiales:\n\n${lines.join("\n\n")}`
    : "No encontré suficiente texto legible en los materiales seleccionados.";
}

function buildQuestions(items: ContextItem[], prompt: string) {
  const sourceText = items.map((item) => cleanText(item.body, 9000)).filter(Boolean).join(" ");
  const selected = rankSentences(sourceText, prompt, 6);
  if (!selected.length) return "TuNot necesita material de estudio para crear preguntas sin inventar contenido.";
  return selected.map((sentence, index) => {
    const short = sentence.length > 260 ? `${sentence.slice(0, 257)}…` : sentence;
    return `${index + 1}. Pregunta: ¿Qué afirma o explica el material sobre este punto?\n   Respuesta: ${short}`;
  }).join("\n\n");
}

function buildConceptMap(items: ContextItem[], prompt: string) {
  if (!items.length) return "TuNot necesita materiales para construir un mapa conceptual fiel a tus fuentes.";
  const central = cleanText(prompt, 120) || items[0]?.subject || items[0]?.title || "Tema de estudio";
  const branches = items.slice(0, 8).map((item, index) => {
    const body = cleanText(item.body, 9000);
    const top = rankSentences(body, prompt, 2);
    return `├─ ${sourceLabel(item, index)}\n│  ├─ ${top[0] || "Sin contenido suficiente"}${top[1] ? `\n│  └─ ${top[1]}` : ""}`;
  });
  return `NOTCAN_MAP\nConcepto central: ${central}\n${branches.join("\n")}`;
}

function buildChat(items: ContextItem[], prompt: string) {
  if (!items.length) {
    return "TuNot está funcionando en modo gratuito, pero en esta versión responde a partir de tus propios materiales. Activa “Usar mis apuntes como contexto” o añade apuntes/transcripciones para que pueda ayudarte sin depender de una API de pago.";
  }
  const combined = items.map((item, index) => `${sourceLabel(item, index)}. ${cleanText(item.body, 9000)}`).join(" ");
  const selected = rankSentences(combined, prompt, 6);
  if (!selected.length) return "No encontré en tus materiales información suficiente para responder con seguridad.";
  return `Según tus materiales de NotCan:\n\n${selected.map((sentence) => `• ${sentence}`).join("\n\n")}\n\nTuNot ha priorizado tus propias fuentes y no ha añadido datos externos.`;
}

function answer(body: any) {
  const prompt = cleanText(body?.prompt, 12000);
  if (!prompt) throw new Error("Escribe una pregunta.");
  const mode = (["chat", "summary", "questions", "concept-map"].includes(String(body?.mode))
    ? String(body.mode)
    : "chat") as Mode;
  const context: ContextItem[] = Array.isArray(body?.context)
    ? body.context.slice(0, 12).map((item: any) => ({
        title: cleanText(item?.title, 200),
        subject: cleanText(item?.subject, 200),
        classTitle: cleanText(item?.classTitle, 200),
        body: cleanText(item?.body, 10000),
      }))
    : [];

  const text = mode === "summary"
    ? buildSummary(context, prompt)
    : mode === "questions"
      ? buildQuestions(context, prompt)
      : mode === "concept-map"
        ? buildConceptMap(context, prompt)
        : buildChat(context, prompt);

  return {
    answer: text,
    model: "TuNot gratuito · fuentes locales",
    provider: "tunot",
    sourceCount: context.length,
  };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  try {
    return json(answer(await req.json()));
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
