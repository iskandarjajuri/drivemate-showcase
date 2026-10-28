/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan prompt/skema */
import {getApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {logger} from "firebase-functions/logger";
import {defineSecret} from "firebase-functions/params";
import {loadTranslateConfig, TranslateConfig} from "./config";
import {FetchLike} from "./deepseek";
import {createFirestoreTranslateStore} from "./firestoreStore";
import {translateChatMessage} from "./translateMessage";

// Kunci HANYA di Secret Manager: `firebase functions:secrets:set DEEPSEEK_API_KEY`.
const deepseekApiKey = defineSecret("DEEPSEEK_API_KEY");

const DATABASE_ID = "drivemate-database";

export const onChatMessageCreatedTranslate = onDocumentCreated(
  {
    document: "chat_threads/{threadId}/messages/{messageId}",
    database: DATABASE_ID,
    region: "asia-southeast2",
    secrets: [deepseekApiKey],
    timeoutSeconds: 30,
    memory: "256MiB",
    maxInstances: 3,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    let config: TranslateConfig;
    try {
      config = loadTranslateConfig();
    } catch (e) {
      logger.error("translate.config_invalid", {message: (e as Error).message});
      return;
    }
    if (!config.enabled) return;

    try {
      const db = getFirestore(getApp(), DATABASE_ID);
      await translateChatMessage(
        {
          config,
          store: createFirestoreTranslateStore(db),
          apiKey: deepseekApiKey.value(),
          fetchImpl: fetch as unknown as FetchLike,
          now: () => Date.now(),
          log: (name, fields) => logger.info(name, fields),
        },
        event.params.threadId,
        event.params.messageId,
        snap.data()
      );
    } catch (e) {
      // Jangan lempar: galat di sini tidak boleh memicu retry tak berujung.
      logger.error("translate.unexpected", {
        threadId: event.params.threadId,
        messageId: event.params.messageId,
        errorClass: (e as Error)?.name ?? "unknown",
      });
    }
  }
);
