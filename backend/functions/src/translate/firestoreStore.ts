/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan prompt/skema */
import {FieldValue, Firestore} from "firebase-admin/firestore";
import {effectiveLang, TranslateStore, TranslationFields} from "./translateMessage";

/**
 * @param {Firestore} db Firestore (database drivemate-database).
 * @return {TranslateStore} Store berbasis Firestore (Admin SDK).
 */
export function createFirestoreTranslateStore(db: Firestore): TranslateStore {
  return {
    async consumeQuota(uid, dayKey, limit) {
      const ref = db.collection("translation_usage").doc(`${uid}_${dayKey}`);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const count = snap.exists ? Number(snap.data()?.count ?? 0) : 0;
        if (count >= limit) return false;
        tx.set(ref, {uid, dayKey, count: count + 1, updatedAt: FieldValue.serverTimestamp()});
        return true;
      });
    },

    async writeResult(threadId, messageId, fields: TranslationFields) {
      const ref = db
        .collection("chat_threads").doc(threadId)
        .collection("messages").doc(messageId);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists) return false;
        const existing = snap.data();
        if (existing?.translationStatus !== undefined || existing?.translations !== undefined) {
          return false;
        }
        // update() HANYA dengan field terjemahan; body & field lain tak tersentuh.
        const payload: Record<string, unknown> = {};
        for (const [k, v] of Object.entries(fields)) {
          if (v !== undefined) payload[k] = v;
        }
        tx.update(ref, payload);
        return true;
      });
    },

    async getParticipantLangs(participantUids) {
      return Promise.all(
        participantUids.map(async (uid) => {
          const snap = await db.collection("user_directory").doc(uid).get();
          return effectiveLang(snap.exists ? snap.data() : undefined);
        })
      );
    },
  };
}
