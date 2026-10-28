/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {getApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {logger} from "firebase-functions/logger";
import {createPushGateway} from "../push/realPush";
import {pushChatMessage} from "./chatPush";
import {createFirestorePushStore} from "./chatPushFirestore";

const DATABASE_ID = "drivemate-database";
const REGION = "asia-southeast2";

// Push untuk pesan chat baru. Idempoten (klaim `pushSentAt`); trigger hanya onCreate, jadi tulis
// `pushSentAt`/terjemahan pada dokumen yang sama TIDAK memicunya lagi. Pasangan beda bahasa menunggu
// terjemahan ≤10 dtk di dalam function (timeout 30 dtk) lalu jatuh ke teks asli.
export const onChatMessageCreatedPush = onDocumentCreated(
  {
    document: "chat_threads/{threadId}/messages/{messageId}",
    database: DATABASE_ID,
    region: REGION,
    timeoutSeconds: 30,
    memory: "256MiB",
    maxInstances: 3,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const {threadId, messageId} = event.params;
    try {
      const db = getFirestore(getApp(), DATABASE_ID);
      await pushChatMessage(
        {
          ...createFirestorePushStore(db),
          ...createPushGateway(db),
          sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
          now: () => Date.now(),
          log: (name, fields) => logger.info(name, fields),
        },
        threadId,
        messageId,
        snap.data() as Record<string, unknown>
      );
    } catch (e) {
      // Jangan lempar: retry tak berujung; klaim at-most-once menjaga agar tidak ada push ganda.
      logger.error("chat.push.unexpected", {errorClass: (e as Error)?.name ?? "unknown"});
    }
  }
);
