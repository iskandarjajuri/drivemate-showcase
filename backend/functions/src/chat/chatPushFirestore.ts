/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {Firestore} from "firebase-admin/firestore";
import {DirectoryEntry} from "./chatPush";

/**
 * Binding Firestore untuk push chat (Admin SDK; mem-bypass rules). Hanya function ini yang menulis
 * `pushSentAt`; klien tak bisa memicunya karena rules `update` pesan hanya mengizinkan tombstone.
 *
 * @param {Firestore} db Firestore (database drivemate-database).
 * @return {Object} Store untuk [pushChatMessage].
 */
export function createFirestorePushStore(db: Firestore) {
  const msgRef = (threadId: string, messageId: string) =>
    db.collection("chat_threads").doc(threadId).collection("messages").doc(messageId);

  return {
    async getMessage(threadId: string, messageId: string): Promise<Record<string, unknown> | undefined> {
      return (await msgRef(threadId, messageId).get()).data();
    },

    // Transaksi: tulis `pushSentAt` hanya bila belum ada (at-most-once; aman terhadap retry/duplikat).
    async claimPush(threadId: string, messageId: string, nowMillis: number): Promise<boolean> {
      const ref = msgRef(threadId, messageId);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists || snap.data()?.pushSentAt !== undefined) return false;
        tx.update(ref, {pushSentAt: nowMillis});
        return true;
      });
    },

    async getDirectory(uids: string[]): Promise<Record<string, DirectoryEntry | undefined>> {
      const out: Record<string, DirectoryEntry | undefined> = {};
      if (uids.length === 0) return out;
      const snaps = await db.getAll(...uids.map((u) => db.collection("user_directory").doc(u)));
      for (const s of snaps) {
        const d = s.data();
        out[s.id] = d ? {role: d.role, displayName: d.displayName, lang: d.lang} : undefined;
      }
      return out;
    },
  };
}
