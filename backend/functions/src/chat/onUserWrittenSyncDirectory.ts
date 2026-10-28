/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {getApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {onDocumentWritten} from "firebase-functions/v2/firestore";
import {logger} from "firebase-functions/logger";
import {normalizeDirectoryRole} from "./chatCore";

const DATABASE_ID = "drivemate-database";
const REGION = "asia-southeast2";

/**
 * @param {Object} data Data dokumen user.
 * @return {string} Nama tampil (field nyata `name`, bukan displayName).
 */
function displayNameOf(data: Record<string, unknown>): string {
  return typeof data.name === "string" ? data.name : "";
}

/**
 * @param {Object} data Data dokumen user.
 * @return {string} Peran ternormalisasi dari field nyata `type`.
 */
function roleOf(data: Record<string, unknown>): string {
  return normalizeDirectoryRole(data.type);
}

/**
 * Sinkronkan `users/{uid}` -> `user_directory/{uid}` (direktori kontak minimal).
 * Field sensitif (email, fcmToken, dll.) TIDAK disalin.
 */
export const onUserWrittenSyncDirectory = onDocumentWritten(
  {document: "users/{uid}", database: DATABASE_ID, region: REGION},
  async (event) => {
    const uid = event.params.uid;
    const db = getFirestore(getApp(), DATABASE_ID);
    const dirRef = db.collection("user_directory").doc(uid);
    const after = event.data?.after;

    if (!after || !after.exists) {
      const snap = await dirRef.get();
      if (snap.exists) {
        await dirRef.delete();
        logger.info("directory.sync.delete", {uid});
      }
      return;
    }

    const data = after.data() as Record<string, unknown>;
    const payload: Record<string, unknown> = {
      uid,
      displayName: displayNameOf(data),
      role: roleOf(data),
      active: typeof data.active === "boolean" ? data.active : true,
      updatedAtMillis: Date.now(),
    };
    const lang = data.lang;
    if (typeof lang === "string" && (lang === "ja" || lang === "id")) {
      payload.lang = lang;
    }

    await dirRef.set(payload, {merge: true});
    logger.info("directory.sync.write", {uid, role: payload.role});
  }
);
