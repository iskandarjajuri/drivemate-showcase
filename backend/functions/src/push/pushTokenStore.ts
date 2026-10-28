/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {createHash} from "crypto";
import {Firestore, Timestamp} from "firebase-admin/firestore";
import {PushToUserDeps, TokenRecord} from "./sendPushToUser";

const STALE_AFTER_MS = 60 * 86400000;
const QUERY_LIMIT = 8;

/**
 * @param {string} token Token FCM.
 * @return {string} ID dokumen `fcm_tokens` = sha256(token) heksadesimal huruf kecil (sama dengan klien/rules).
 */
export function tokenDocId(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

/**
 * Binding Firestore untuk token FCM privat (Admin SDK; mem-bypass rules): HANYA dokumen `fcm_tokens`.
 *
 * @param {Firestore} db Firestore (database drivemate-database).
 * @return {Pick<PushToUserDeps, "listTokens" | "removeToken">} Store untuk [sendPushToUser].
 */
export function createPushTokenStore(db: Firestore): Pick<PushToUserDeps, "listTokens" | "removeToken"> {
  return {
    async listTokens(uid: string): Promise<TokenRecord[]> {
      const snap = await db.collection("fcm_tokens").where("uid", "==", uid).orderBy("updatedAt", "desc").limit(QUERY_LIMIT).get();
      const out: TokenRecord[] = [];
      const stale: Array<Promise<unknown>> = [];
      const now = Date.now();
      for (const d of snap.docs) {
        const data = d.data();
        const at = data.updatedAt instanceof Timestamp ? data.updatedAt.toMillis() : 0;
        if (typeof data.token !== "string" || data.token.length === 0) continue;
        if (at > 0 && now - at > STALE_AFTER_MS) {
          stale.push(d.ref.delete()); // pembersihan basi (>60 hari) sambil lalu, tanpa fungsi terjadwal
          continue;
        }
        out.push({id: d.id, token: data.token, source: "doc", updatedAtMillis: at});
      }
      await Promise.all(stale);
      return out;
    },

    async removeToken(rec: TokenRecord): Promise<void> {
      await db.collection("fcm_tokens").doc(rec.id).delete();
    },
  };
}
