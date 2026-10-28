/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {FieldValue, Firestore} from "firebase-admin/firestore";

const PREVIEW_MAX = 80;

/**
 * @param {unknown} value Nilai yang mungkin list.
 * @return {string[]} Elemen string dari list.
 */
function stringList(value: unknown): string[] {
  return Array.isArray(value) ?
    value.filter((x): x is string => typeof x === "string") :
    [];
}

export type DirectoryRole = "admin" | "driver" | "expat" | "unknown";

/**
 * Normalisasi `users.type` (nilai nyata: admin, driver, expat, expatriate,
 * expatriat; kadang kapital/kosong) ke peran direktori. Dipakai function sinkron
 * dan backfill supaya hasilnya sama.
 *
 * @param {unknown} raw Nilai mentah `users.type`.
 * @return {DirectoryRole} Peran ternormalisasi.
 */
export function normalizeDirectoryRole(raw: unknown): DirectoryRole {
  if (typeof raw !== "string") return "unknown";
  const v = raw.trim().toLowerCase();
  if (v === "admin") return "admin";
  if (v === "driver") return "driver";
  if (v === "expat" || v === "expatriate" || v === "expatriat") return "expat";
  return "unknown";
}

/**
 * @param {unknown} body Isi pesan.
 * @return {string} Pratinjau satu baris, maksimal PREVIEW_MAX karakter.
 */
export function previewOf(body: unknown): string {
  if (typeof body !== "string") return "";
  const singleLine = body.replace(/\s+/g, " ").trim();
  return singleLine.length <= PREVIEW_MAX ?
    singleLine :
    singleLine.slice(0, PREVIEW_MAX);
}

/**
 * Pertahankan dokumen thread saat pesan baru dibuat. Idempoten via
 * `lastMessage.messageId` (retry tidak menggandakan increment unread).
 *
 * @param {Firestore} db Firestore (database drivemate-database).
 * @param {string} threadId Id thread.
 * @param {string} messageId Id pesan.
 * @param {Object} data Data dokumen pesan.
 */
export async function maintainChatThread(
  db: Firestore,
  threadId: string,
  messageId: string,
  data: Record<string, unknown>
): Promise<void> {
  const participantUids = stringList(data.participantUids);
  const senderUid = typeof data.senderUid === "string" ? data.senderUid : "";
  const createdAtMillis =
    typeof data.createdAtMillis === "number" ? data.createdAtMillis : Date.now();
  const recipient = participantUids.find((u) => u !== senderUid);
  const lastMessage = {
    senderUid,
    preview: previewOf(data.body),
    createdAtMillis,
    deleted: data.deleted === true,
    messageId,
  };

  const threadRef = db.collection("chat_threads").doc(threadId);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(threadRef);
    const existing = snap.exists ? snap.data() : undefined;
    const prevLast = (existing?.lastMessage ?? {}) as Record<string, unknown>;
    const alreadyRecorded = prevLast.messageId === messageId;

    if (!snap.exists) {
      const doc: Record<string, unknown> = {
        participantUids,
        lastMessage,
        lastMessageAtMillis: createdAtMillis,
      };
      if (recipient) doc.unread = {[recipient]: 1};
      tx.set(threadRef, doc);
    } else {
      const update: Record<string, unknown> = {
        participantUids,
        lastMessage,
        lastMessageAtMillis: createdAtMillis,
      };
      if (recipient && !alreadyRecorded) {
        update[`unread.${recipient}`] = FieldValue.increment(1);
      }
      tx.update(threadRef, update);
    }
  });
}

/**
 * Reset unread pengguna menjadi 0 saat ia menulis read marker.
 *
 * @param {Firestore} db Firestore (database drivemate-database).
 * @param {string} threadId Id thread.
 * @param {string} uid Uid pengguna.
 */
export async function resetThreadUnread(
  db: Firestore,
  threadId: string,
  uid: string
): Promise<void> {
  const threadRef = db.collection("chat_threads").doc(threadId);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(threadRef);
    if (!snap.exists) return;
    tx.update(threadRef, {[`unread.${uid}`]: 0});
  });
}

/**
 * Bersihkan pesan yang dihapus-untuk-semua: kosongkan body, hapus terjemahan,
 * translationStatus=skipped; tandai lastMessage.deleted bila pesan ini pesan
 * terakhir; kurangi unread penerima bila pesan belum dibaca (tak negatif).
 *
 * @param {Firestore} db Firestore (database drivemate-database).
 * @param {string} threadId Id thread.
 * @param {string} messageId Id pesan.
 * @param {Object} afterData Data dokumen pesan setelah dihapus.
 */
export async function processChatMessageDeleted(
  db: Firestore,
  threadId: string,
  messageId: string,
  afterData: Record<string, unknown>
): Promise<void> {
  const senderUid = typeof afterData.senderUid === "string" ? afterData.senderUid : "";
  const createdAtMillis =
    typeof afterData.createdAtMillis === "number" ? afterData.createdAtMillis : 0;
  const participantUids = stringList(afterData.participantUids);
  const recipient = participantUids.find((u) => u !== senderUid);

  const messageRef = db.collection("chat_threads").doc(threadId)
    .collection("messages").doc(messageId);
  const threadRef = db.collection("chat_threads").doc(threadId);
  const readRef = recipient ?
    db.collection("chat_threads").doc(threadId).collection("reads").doc(recipient) :
    null;

  await db.runTransaction(async (tx) => {
    // BACA dulu — Firestore mengharuskan semua read sebelum write.
    const threadSnap = await tx.get(threadRef);
    const readSnap = readRef ? await tx.get(readRef) : null;

    // TULIS: bersihkan pesan.
    tx.update(messageRef, {
      body: "",
      translationStatus: "skipped",
      translations: FieldValue.delete(),
      srcLang: FieldValue.delete(),
      register: FieldValue.delete(),
      translatedAtMillis: FieldValue.delete(),
      translationModel: FieldValue.delete(),
    });

    if (!threadSnap.exists) return;
    const threadData = threadSnap.data() as Record<string, unknown>;
    const lastMessage = (threadData.lastMessage ?? {}) as Record<string, unknown>;

    if (
      lastMessage.messageId === messageId ||
      lastMessage.createdAtMillis === createdAtMillis
    ) {
      tx.update(threadRef, {
        "lastMessage.deleted": true,
        "lastMessage.preview": "",
      });
    }

    if (recipient && readSnap) {
      const lastReadAtMillis = readSnap.exists ?
        readSnap.data()?.lastReadAtMillis :
        undefined;
      const isRead =
        typeof lastReadAtMillis === "number" && lastReadAtMillis >= createdAtMillis;
      if (!isRead) {
        const unread = (threadData.unread ?? {}) as Record<string, unknown>;
        const current =
          typeof unread[recipient] === "number" ? (unread[recipient] as number) : 0;
        if (current > 0) {
          tx.update(threadRef, {[`unread.${recipient}`]: FieldValue.increment(-1)});
        }
      }
    }
  });
}
