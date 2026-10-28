/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {shortHash} from "../logHash";
import type {PushMessage} from "../push/sendPushToUser";
import {effectiveLang} from "../translate/translateMessage";

/** Batas isi notifikasi (karakter). */
const BODY_MAX = 80;
/** Menunggu terjemahan paling lama ±10 dtk (10 pembacaan, jeda 1 dtk). */
const TRANSLATION_WAIT_ATTEMPTS = 10;
const TRANSLATION_WAIT_INTERVAL_MS = 1000;

type Lang = "ja" | "id";
export interface DirectoryEntry {
  lang?: unknown;
  role?: unknown;
  displayName?: unknown;
}

export interface ChatPushDeps {
  /** Baca dokumen pesan terkini (untuk menunggu terjemahan). */
  getMessage(threadId: string, messageId: string): Promise<Record<string, unknown> | undefined>;
  /** Klaim idempoten (transaksi: tulis `pushSentAt` bila belum ada); false bila sudah diklaim. */
  claimPush(threadId: string, messageId: string, nowMillis: number): Promise<boolean>;
  getDirectory(uids: string[]): Promise<Record<string, DirectoryEntry | undefined>>;
  /** Apakah tiap uid punya setidaknya satu token terdaftar (privat: hanya `fcm_tokens`). */
  hasTokens(uids: string[]): Promise<Record<string, boolean>>;
  /** Kirim ke SEMUA perangkat uid (multi-device, retry, pembersihan token mati ada di [sendPushToUser]); mengembalikan jumlah terkirim. */
  pushToUser(uid: string, message: PushMessage): Promise<number>;
  sleep(ms: number): Promise<void>;
  now(): number;
  /** Logger; JANGAN pernah diberi isi pesan, token penuh, atau id mentah. */
  log(event: string, fields: Record<string, unknown>): void;
}

export interface PushResult {
  outcome: "sent" | "skipped" | "no_recipient" | "already_sent";
  delivered: number;
}

/**
 * @param {string} text Isi pesan.
 * @return {string} Satu baris, spasi dirapikan, maksimal BODY_MAX karakter (elipsis bila dipotong).
 */
export function clipBody(text: string): string {
  const one = text.replace(/\s+/g, " ").trim();
  if (one.length <= BODY_MAX) return one;
  return one.slice(0, BODY_MAX - 1).trimEnd() + "…";
}

export {shortHash};

/**
 * @param {Lang | undefined} lang Bahasa penerima.
 * @return {string} Judul generik terlokalisasi bila nama pengirim tak diketahui.
 */
function genericTitle(lang: Lang | undefined): string {
  return lang === "ja" ? "新着メッセージ" : "Pesan baru";
}

/**
 * Menunggu terjemahan untuk [lang] (ditulis function terjemahan pada dokumen yang sama).
 *
 * @param {ChatPushDeps} deps Dependensi.
 * @param {string} threadId Id thread.
 * @param {string} messageId Id pesan.
 * @param {Lang} lang Bahasa penerima.
 * @return {Promise<string | undefined>} Teks terjemahan; "deleted" bila pesan dihapus; undefined (gagal/dilewati/waktu habis).
 */
async function waitForTranslation(
  deps: ChatPushDeps,
  threadId: string,
  messageId: string,
  lang: Lang
): Promise<string | "deleted" | undefined> {
  for (let i = 0; i < TRANSLATION_WAIT_ATTEMPTS; i++) {
    const doc = await deps.getMessage(threadId, messageId);
    if (doc?.deleted === true) return "deleted";
    const status = doc?.translationStatus;
    if (status === "done") {
      const tr = (doc?.translations as Record<string, {text?: unknown}> | undefined)?.[lang];
      return typeof tr?.text === "string" && tr.text.length > 0 ? tr.text : undefined;
    }
    if (status === "failed" || status === "skipped") return undefined;
    if (i < TRANSLATION_WAIT_ATTEMPTS - 1) await deps.sleep(TRANSLATION_WAIT_INTERVAL_MS);
  }
  return undefined;
}

/**
 * Kirim push untuk satu pesan chat baru ke penerima (peserta selain pengirim) yang punya token.
 * Idempoten lewat klaim `pushSentAt` (at-most-once). Isi dalam BAHASA PENERIMA: pasangan beda
 * bahasa menunggu terjemahan (≤10 dtk) lalu jatuh ke teks asli.
 *
 * @param {ChatPushDeps} deps Dependensi.
 * @param {string} threadId Id thread.
 * @param {string} messageId Id pesan.
 * @param {Record<string, unknown>} data Data dokumen pesan saat dibuat.
 * @return {Promise<PushResult>} Hasil.
 */
export async function pushChatMessage(
  deps: ChatPushDeps,
  threadId: string,
  messageId: string,
  data: Record<string, unknown>
): Promise<PushResult> {
  const logBase = {thread: shortHash(threadId), message: shortHash(messageId)};
  const senderUid = typeof data.senderUid === "string" ? data.senderUid : "";
  const body = typeof data.body === "string" ? data.body : "";
  const participants = Array.isArray(data.participantUids) ?
    data.participantUids.filter((x): x is string => typeof x === "string") : [];

  if (
    data.deleted === true || data.isSystem === true || !body.trim() ||
    !senderUid || !participants.includes(senderUid)
  ) {
    deps.log("chat.push", {...logBase, outcome: "skipped"});
    return {outcome: "skipped", delivered: 0};
  }

  const recipients = participants.filter((u) => u !== senderUid);
  const has = await deps.hasTokens(recipients);
  const targets = recipients.filter((u) => has[u]);
  if (targets.length === 0) {
    deps.log("chat.push", {...logBase, outcome: "no_recipient", recipients: recipients.length});
    return {outcome: "no_recipient", delivered: 0};
  }

  if (!(await deps.claimPush(threadId, messageId, deps.now()))) {
    deps.log("chat.push", {...logBase, outcome: "already_sent"});
    return {outcome: "already_sent", delivered: 0};
  }

  // Pesan bisa dihapus-untuk-semua sejak dibuat (mis. selama menunggu); isi yang sudah dihapus tak boleh bocor lewat push.
  if ((await deps.getMessage(threadId, messageId))?.deleted === true) {
    deps.log("chat.push", {...logBase, outcome: "skipped"});
    return {outcome: "skipped", delivered: 0};
  }

  const dir = await deps.getDirectory([senderUid, ...targets]);
  const senderLang = effectiveLang(dir[senderUid]);
  const rawSenderName = dir[senderUid]?.displayName;
  const senderName = typeof rawSenderName === "string" ? rawSenderName.trim() : "";

  let delivered = 0;
  let translated = false;
  for (const uid of targets) {
    const lang = effectiveLang(dir[uid]);
    let text = body;
    if (lang && senderLang && lang !== senderLang) {
      const tr = await waitForTranslation(deps, threadId, messageId, lang);
      if (tr === "deleted") {
        deps.log("chat.push", {...logBase, outcome: "skipped"});
        return {outcome: "skipped", delivered};
      }
      if (tr) {
        text = tr;
        translated = true;
      }
    }
    const title = senderName || genericTitle(lang);
    try {
      delivered += await deps.pushToUser(uid, {
        notification: {title, body: clipBody(text)},
        data: {type: "chat", threadId, messageId, senderUid, senderName},
        android: {priority: "high", notification: {channelId: "chat", tag: threadId}},
      });
    } catch (e) {
      deps.log("chat.push.send_failed", {...logBase, code: (e as {code?: string})?.code ?? "unknown"});
    }
  }
  deps.log("chat.push", {...logBase, outcome: "sent", recipients: targets.length, delivered, translated});
  return {outcome: "sent", delivered};
}
