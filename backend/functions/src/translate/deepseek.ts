/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan prompt/skema */
import {TranslateConfig} from "./config";
import {PromptMessages} from "./prompt";

/** Galat penyedia; [code] aman dicatat di log (tanpa isi pesan). */
export class ProviderError extends Error {
  /**
   * @param {string} code Kode galat singkat.
   * @param {boolean} retryable Apakah layak dicoba ulang.
   */
  constructor(readonly code: string, readonly retryable: boolean) {
    super(code);
  }
}

export type FetchLike = (
  url: string,
  init: {
    method: string;
    headers: Record<string, string>;
    body: string;
    signal: AbortSignal;
  }
) => Promise<{
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
}>;

/**
 * Satu panggilan chat-completions (format OpenAI-compatible) dengan timeout.
 *
 * @param {object} args Argumen panggilan.
 * @param {TranslateConfig} args.config Config.
 * @param {string} args.apiKey Kunci API (dari Secret Manager; jangan dicatat).
 * @param {PromptMessages} args.prompt Pesan system + user.
 * @param {FetchLike} args.fetchImpl fetch (bawaan Node 20, atau mock di tes).
 * @return {Promise<string>} Isi `choices[0].message.content`.
 */
async function callOnce(args: {
  config: TranslateConfig;
  apiKey: string;
  prompt: PromptMessages;
  fetchImpl: FetchLike;
}): Promise<string> {
  const {config, apiKey, prompt, fetchImpl} = args;
  const p = config.provider;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), p.timeoutMs);
  try {
    const res = await fetchImpl(p.baseUrl + p.path, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model: p.model,
        messages: [
          {role: "system", content: prompt.system},
          {role: "user", content: prompt.user},
        ],
        response_format: {type: "json_object"},
        temperature: p.temperature,
        max_tokens: p.maxTokens,
        stream: false,
        ...p.extraBody,
      }),
      signal: controller.signal,
    });
    if (!res.ok) {
      throw new ProviderError(`http_${res.status}`, res.status >= 500 || res.status === 429);
    }
    const json = (await res.json()) as {
      choices?: Array<{message?: {content?: string | null}}>;
    };
    const content = json?.choices?.[0]?.message?.content;
    if (typeof content !== "string" || content.trim().length === 0) {
      // Dokumentasi DeepSeek: mode JSON "may occasionally return empty content".
      throw new ProviderError("empty_content", true);
    }
    return content;
  } catch (e) {
    if (e instanceof ProviderError) throw e;
    const name = (e as {name?: string})?.name;
    throw new ProviderError(name === "AbortError" ? "timeout" : "network", true);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Panggil penyedia dengan retry terbatas (config.provider.maxRetries) untuk
 * galat yang layak dicoba ulang.
 *
 * @param {object} args Argumen (lihat callOnce).
 * @return {Promise<string>} Isi balasan model.
 */
export async function callProvider(args: {
  config: TranslateConfig;
  apiKey: string;
  prompt: PromptMessages;
  fetchImpl: FetchLike;
}): Promise<string> {
  let lastError: ProviderError | undefined;
  for (let attempt = 0; attempt <= args.config.provider.maxRetries; attempt++) {
    try {
      return await callOnce(args);
    } catch (e) {
      lastError = e as ProviderError;
      if (!lastError.retryable) break;
    }
  }
  throw lastError ?? new ProviderError("unknown", false);
}
