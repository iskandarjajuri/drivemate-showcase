/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan prompt/skema */
import {TranslateConfig} from "./config";
import {callProvider, FetchLike, ProviderError} from "./deepseek";
import {buildPrompt} from "./prompt";
import {shortHash} from "../logHash";

export type Lang = "ja" | "id";
export type TranslationStatus = "done" | "failed" | "skipped";

/** Hasil tervalidasi dari model. */
export interface ModelResult {
  src: Lang | "other";
  register: "formal" | "polite" | "casual";
  translation: string;
  uncertain: boolean;
}

/** Satu-satunya field yang boleh ditulis function ke dokumen pesan. */
export interface TranslationFields {
  srcLang?: string;
  register?: string;
  translations?: Partial<Record<Lang, {text: string; uncertain: boolean}>>;
  translationStatus: TranslationStatus;
  translatedAtMillis: number;
  translationModel?: string;
}

export interface TranslateStore {
  /** Naikkan counter harian secara transaksional; false bila kuota habis. */
  consumeQuota(uid: string, dayKey: string, limit: number): Promise<boolean>;
  /** Tulis hasil HANYA bila belum ada translationStatus; false bila sudah ada. */
  writeResult(
    threadId: string,
    messageId: string,
    fields: TranslationFields
  ): Promise<boolean>;
  /** Bahasa efektif tiap peserta dari user_directory (ja/id/undefined). */
  getParticipantLangs(participantUids: string[]): Promise<Array<"ja" | "id" | undefined>>;
}

export interface TranslateDeps {
  config: TranslateConfig;
  store: TranslateStore;
  apiKey: string;
  fetchImpl: FetchLike;
  now: () => number;
  /** Logger; JANGAN pernah diberi isi pesan. */
  log: (event: string, fields: Record<string, unknown>) => void;
}

export interface MessageData {
  senderUid?: unknown;
  body?: unknown;
  isSystem?: unknown;
  deleted?: unknown;
  participantUids?: unknown;
  translations?: unknown;
  translationStatus?: unknown;
}

export type Outcome =
  | "disabled"
  | "ignored"
  | "already_translated"
  | "skipped_too_long"
  | "skipped_quota"
  | "skipped_other_language"
  | "skipped_deleted"
  | "skipped_same_language"
  | "done"
  | "failed";

/**
 * Bahasa efektif dari dokumen user_directory; expat->ja, lainnya->id.
 *
 * @param {Object} dirData Dokumen direktori (lang/role).
 * @return {string} "ja" | "id", atau undefined bila tak diketahui.
 */
export function effectiveLang(
  dirData: {lang?: unknown; role?: unknown} | undefined
): "ja" | "id" | undefined {
  if (dirData && (dirData.lang === "ja" || dirData.lang === "id")) {
    return dirData.lang;
  }
  if (dirData && typeof dirData.role === "string") {
    const r = dirData.role.toLowerCase();
    if (["expat", "expatriate", "expatriat"].includes(r)) return "ja";
    return "id";
  }
  return undefined;
}

const TZ_OFFSET_MS = 7 * 60 * 60 * 1000;

/**
 * @param {number} nowMillis Epoch millis.
 * @return {string} Kunci hari Jakarta YYYYMMDD.
 */
export function jakartaDayKey(nowMillis: number): string {
  const d = new Date(nowMillis + TZ_OFFSET_MS);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}`;
}

// Hanya karakter ASCII yang sah di URL: teks Jepang tak dipisah spasi, jadi `[^\s]` akan menelan 「を確認してください。」 dan
// menandai URL sah sebagai tak bersumber.
const URL_PATTERN = /(?:https?:\/\/|www\.)[A-Za-z0-9\-._~:/?#[\]@!$&*+,;=%]+/gi;

/**
 * Apakah [translation] memuat URL yang tidak ada di [body] (perbandingan tanpa huruf besar/kecil).
 *
 * @param {string} body Isi pesan asli.
 * @param {string} translation Hasil terjemahan model.
 * @return {boolean} true bila ada URL tak bersumber.
 */
export function hasUnsourcedUrl(body: string, translation: string): boolean {
  const source = body.toLowerCase();
  return (translation.match(URL_PATTERN) ?? []).some((u) => !source.includes(u.toLowerCase().replace(/[.,;:!?]+$/, "")));
}

/**
 * Parse + validasi balasan model terhadap skema; null bila tidak sah.
 *
 * @param {string} content Isi balasan mentah.
 * @param {number} maxChars Batas panjang pesan sumber.
 * @return {ModelResult|null} Hasil sah atau null.
 */
export function parseModelResult(content: string, maxChars: number): ModelResult | null {
  let obj: unknown;
  try {
    obj = JSON.parse(content);
  } catch {
    return null;
  }
  const o = obj as Record<string, unknown> | null;
  if (!o || typeof o !== "object") return null;
  const src = o.src;
  const register = o.register;
  const translation = o.translation;
  const uncertain = o.uncertain;
  if (src !== "ja" && src !== "id" && src !== "other") return null;
  if (register !== "formal" && register !== "polite" && register !== "casual") return null;
  if (typeof translation !== "string") return null;
  if (typeof uncertain !== "boolean") return null;
  if (src !== "other" && translation.trim().length === 0) return null;
  if (translation.length > maxChars * 3) return null;
  return {src, register, translation: translation.trim(), uncertain};
}

/**
 * Terjemahkan satu pesan chat yang baru dibuat. Tidak pernah melempar galat:
 * semua kegagalan dipetakan ke Outcome (dan translationStatus "failed" bila
 * sudah sampai tahap penyedia), supaya trigger tidak masuk loop retry.
 *
 * @param {TranslateDeps} deps Dependensi (config, store, fetch, ...).
 * @param {string} threadId Id thread.
 * @param {string} messageId Id pesan.
 * @param {MessageData} data Data dokumen pesan.
 * @return {Promise<Outcome>} Hasil pemrosesan.
 */
export async function translateChatMessage(
  deps: TranslateDeps,
  threadId: string,
  messageId: string,
  data: MessageData
): Promise<Outcome> {
  const {config, store, now, log} = deps;
  const ids = {thread: shortHash(threadId), message: shortHash(messageId)};

  if (!config.enabled) return "disabled";

  const senderUid = data.senderUid;
  const body = data.body;
  if (
    typeof senderUid !== "string" || senderUid.length === 0 ||
    senderUid === "system" || data.isSystem === true ||
    typeof body !== "string" || body.trim().length === 0
  ) {
    return "ignored";
  }
  // Idempotensi: trigger bisa terkirim ganda; sudah diproses -> diam.
  if (data.translations !== undefined || data.translationStatus !== undefined) {
    return "already_translated";
  }

  const startedAt = now();
  const skip = async (outcome: Outcome): Promise<Outcome> => {
    const written = await store.writeResult(threadId, messageId, {
      translationStatus: "skipped",
      translatedAtMillis: now(),
    });
    log("translate.skipped", {...ids, reason: outcome, length: body.length, written});
    return outcome;
  };

  if (data.deleted === true) return "skipped_deleted";

  const participantUids = Array.isArray(data.participantUids) ?
    data.participantUids.filter((x): x is string => typeof x === "string") :
    [];
  const langs = await store.getParticipantLangs(participantUids);
  if (
    langs.length >= 2 &&
    langs[0] !== undefined &&
    langs[1] !== undefined &&
    langs[0] === langs[1]
  ) {
    return skip("skipped_same_language");
  }

  if (body.length > config.maxChars) return skip("skipped_too_long");

  const allowed = await store.consumeQuota(
    senderUid,
    jakartaDayKey(startedAt),
    config.quota.perUserPerDay
  );
  if (!allowed) return skip("skipped_quota");

  let result: ModelResult | null = null;
  let errorCode = "invalid_json";
  try {
    const content = await callProvider({
      config,
      apiKey: deps.apiKey,
      prompt: buildPrompt(config, body),
      fetchImpl: deps.fetchImpl,
    });
    result = parseModelResult(content, config.maxChars);
    if (result === null) {
      // 1 retry untuk JSON rusak (bisa jadi sekadar keluaran sekali jalan).
      const again = await callProvider({
        config,
        apiKey: deps.apiKey,
        prompt: buildPrompt(config, body),
        fetchImpl: deps.fetchImpl,
      });
      result = parseModelResult(again, config.maxChars);
    }
  } catch (e) {
    errorCode = e instanceof ProviderError ? e.code : "unexpected";
  }

  // Keluaran mencurigakan (tinjauan keamanan #2): URL yang tak ada di sumber hampir pasti hasil injeksi (phishing lewat
  // terjemahan/push). Diperlakukan gagal: penerima hanya melihat teks asli.
  if (result !== null && hasUnsourcedUrl(body, result.translation)) {
    result = null;
    errorCode = "suspicious_output";
  }

  const latencyMs = now() - startedAt;
  if (result === null) {
    await store.writeResult(threadId, messageId, {
      translationStatus: "failed",
      translatedAtMillis: now(),
      translationModel: config.provider.model,
    });
    log("translate.failed", {...ids, length: body.length, latencyMs, errorCode});
    return "failed";
  }

  if (result.src === "other") {
    await store.writeResult(threadId, messageId, {
      srcLang: "other",
      register: result.register,
      translationStatus: "skipped",
      translatedAtMillis: now(),
      translationModel: config.provider.model,
    });
    log("translate.skipped", {
      ...ids, reason: "skipped_other_language", length: body.length, latencyMs,
    });
    return "skipped_other_language";
  }

  const target: Lang = result.src === "ja" ? "id" : "ja";
  const written = await store.writeResult(threadId, messageId, {
    srcLang: result.src,
    register: result.register,
    translations: {[target]: {text: result.translation, uncertain: result.uncertain}},
    translationStatus: "done",
    translatedAtMillis: now(),
    translationModel: config.provider.model,
  });
  log("translate.done", {
    ...ids, srcLang: result.src, length: body.length, latencyMs, written,
  });
  return "done";
}
