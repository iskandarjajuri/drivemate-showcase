/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {shortHash} from "../chat/chatPush";

/** Batas token yang dikirimi per pengguna (token terbaru): menahan biaya dan dokumen basi. */
export const MAX_TOKENS_PER_USER = 5;
const MAX_RETRIES = 2;
const RETRY_DELAYS_MS = [200, 600];

// Galat SEMENTARA (aman di-retry). `invalid-argument` bisa karena payload; `quota-exceeded` tidak dicoba ulang cepat.
const TRANSIENT_CODES = new Set([
  "messaging/server-unavailable",
  "messaging/internal-error",
  "messaging/unavailable",
]);
// HANYA kode yang pasti berarti "token ini mati". `invalid-argument` sengaja TIDAK ada (bisa dipakai mematikan notifikasi).
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

export interface TokenRecord {
  /** Id dokumen `fcm_tokens` (hash token). */
  id: string;
  token: string;
  source: "doc";
  updatedAtMillis: number;
}

export interface PushMessage {
  notification: {title: string; body: string};
  /** Push umum (mis. absensi/kecepatan) tanpa `data`/channel khusus. */
  data?: Record<string, string>;
  android?: {priority: "high"; notification: {channelId: string; tag: string}};
}

export interface PushToUserDeps {
  /** Token pengguna (hanya dokumen `fcm_tokens`, terbaru dulu). */
  listTokens(uid: string): Promise<TokenRecord[]>;
  /** Hapus token mati (dokumen `fcm_tokens`). */
  removeToken(rec: TokenRecord): Promise<void>;
  /** Satu pengiriman FCM; melempar galat ber-`code`. */
  sendOne(token: string, message: PushMessage): Promise<void>;
  sleep(ms: number): Promise<void>;
  /** Logger; JANGAN pernah diberi token, isi/judul pesan, atau uid mentah. */
  log(event: string, fields: Record<string, unknown>): void;
}

export interface PushToUserResult {
  attempted: number;
  delivered: number;
  removed: number;
}

/**
 * Satu pintu pengirim FCM per pengguna: SEMUA token (multi-device, maks MAX_TOKENS_PER_USER terbaru), retry terbatas
 * (maks 2x) HANYA untuk galat sementara, hapus token yang pasti mati, satu token gagal tidak menghentikan yang lain.
 *
 * @param {PushToUserDeps} deps Dependensi.
 * @param {string} uid Penerima.
 * @param {PushMessage} message Isi push (tanpa token).
 * @return {Promise<PushToUserResult>} Hitungan percobaan/terkirim/dihapus.
 */
export async function sendPushToUser(deps: PushToUserDeps, uid: string, message: PushMessage): Promise<PushToUserResult> {
  const records = (await deps.listTokens(uid)).slice(0, MAX_TOKENS_PER_USER);
  let delivered = 0;
  let removed = 0;
  for (const rec of records) {
    for (let attempt = 0; ; attempt++) {
      try {
        await deps.sendOne(rec.token, message);
        delivered++;
        break;
      } catch (e) {
        const code = (e as {code?: string})?.code ?? "unknown";
        if (TRANSIENT_CODES.has(code) && attempt < MAX_RETRIES) {
          await deps.sleep(RETRY_DELAYS_MS[attempt] ?? 600);
          continue;
        }
        if (DEAD_TOKEN_CODES.has(code)) {
          await deps.removeToken(rec);
          removed++;
        }
        deps.log("push.user.send_failed", {user: shortHash(uid), code, source: rec.source, attempts: attempt + 1});
        break;
      }
    }
  }
  deps.log("push.user", {user: shortHash(uid), attempted: records.length, delivered, removed});
  return {attempted: records.length, delivered, removed};
}
