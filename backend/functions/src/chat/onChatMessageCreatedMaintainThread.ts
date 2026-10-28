/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {getApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {logger} from "firebase-functions/logger";
import {maintainChatThread} from "./chatCore";
import {shortHash} from "../logHash";

const DATABASE_ID = "drivemate-database";
const REGION = "asia-southeast2";

export const onChatMessageCreatedMaintainThread = onDocumentCreated(
  {document: "chat_threads/{threadId}/messages/{messageId}", database: DATABASE_ID, region: REGION},
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const {threadId, messageId} = event.params;
    const db = getFirestore(getApp(), DATABASE_ID);
    await maintainChatThread(db, threadId, messageId, snap.data() as Record<string, unknown>);
    logger.info("chat.thread.maintain", {thread: shortHash(threadId), message: shortHash(messageId)});
  }
);
