/* eslint-disable max-len -- komentar panjang dipertahankan */
import {createHash} from "crypto";

/**
 * Pseudonim pendek untuk id di log (tinjauan keamanan #9): `threadId` berisi pasangan uid, jadi mencetaknya mentah membocorkan
 * siapa mengobrol dengan siapa kepada pembaca log. Delapan heksadesimal sha256: cukup untuk korelasi antar baris log, bukan
 * anonim (uid bisa dibalik lewat kamus `user_directory`).
 *
 * @param {string} value Id mentah.
 * @return {string} 8 heksadesimal pertama sha256.
 */
export function shortHash(value: string): string {
  return createHash("sha256").update(value).digest("hex").slice(0, 8);
}
