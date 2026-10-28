/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan prompt/skema */
import rawConfig from "./config.json";

export interface GlossaryEntry {
  id: string;
  ja: string;
}

export interface TranslateConfig {
  enabled: boolean;
  maxChars: number;
  quota: {perUserPerDay: number};
  provider: {
    name: string;
    baseUrl: string;
    path: string;
    model: string;
    extraBody: Record<string, unknown>;
    temperature: number;
    maxTokens: number;
    timeoutMs: number;
    maxRetries: number;
  };
  registerMap: Record<string, string>;
  glossary: GlossaryEntry[];
  keepAsIs: string[];
}

/**
 * @param {unknown} v Value to test.
 * @return {boolean} True for a positive finite number.
 */
function positive(v: unknown): boolean {
  return typeof v === "number" && Number.isFinite(v) && v > 0;
}

/**
 * Validasi config terjemahan; melempar Error berisi daftar masalah (tanpa
 * nilai apa pun) bila tidak sah.
 *
 * @param {unknown} raw Parsed config.json.
 * @return {TranslateConfig} Config tervalidasi.
 */
export function validateTranslateConfig(raw: unknown): TranslateConfig {
  const problems: string[] = [];
  const c = raw as Partial<TranslateConfig> | null;
  if (!c || typeof c !== "object") {
    throw new Error("translate config: not an object");
  }
  if (typeof c.enabled !== "boolean") problems.push("enabled");
  if (!positive(c.maxChars)) problems.push("maxChars");
  if (!positive(c.quota?.perUserPerDay)) problems.push("quota.perUserPerDay");
  const p = c.provider;
  if (!p || typeof p.baseUrl !== "string" || !p.baseUrl.startsWith("https://")) {
    problems.push("provider.baseUrl");
  }
  if (!p || typeof p.path !== "string") problems.push("provider.path");
  if (!p || typeof p.model !== "string" || p.model.length === 0) {
    problems.push("provider.model");
  }
  if (!p || typeof p.extraBody !== "object" || p.extraBody === null) {
    problems.push("provider.extraBody");
  }
  if (!p || typeof p.temperature !== "number") problems.push("provider.temperature");
  if (!p || !positive(p.maxTokens)) problems.push("provider.maxTokens");
  if (!p || !positive(p.timeoutMs)) problems.push("provider.timeoutMs");
  if (!p || typeof p.maxRetries !== "number" || p.maxRetries < 0) {
    problems.push("provider.maxRetries");
  }
  if (!c.registerMap || typeof c.registerMap !== "object") {
    problems.push("registerMap");
  }
  if (!Array.isArray(c.glossary)) problems.push("glossary");
  if (!Array.isArray(c.keepAsIs)) problems.push("keepAsIs");
  if (problems.length > 0) {
    throw new Error(`translate config invalid: ${problems.join(", ")}`);
  }
  return c as TranslateConfig;
}

/**
 * @return {TranslateConfig} Config bawaan dari config.json (tervalidasi).
 */
export function loadTranslateConfig(): TranslateConfig {
  return validateTranslateConfig(rawConfig);
}
