/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {getApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {onDocumentUpdated} from "firebase-functions/v2/firestore";
import {logger} from "firebase-functions/logger";
import {processChatMessageDeleted} from "./chatCore";
import {shortHash} from "../logHash";

const DATABASE_ID = "drivemate-database";
const REGION = "asia-southeast2";

export const onChatMessageDeleted = onDocumentUpdated(
  {document: "chat_threads/{threadId}/messages/{messageId}", database: DATABASE_ID, region: REGION},
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    if (!before || !after || !after.exists) return;

    const beforeDeleted = before.data()?.deleted === true;
    const afterDeleted = after.data()?.deleted === true;
    if (!afterDeleted || beforeDeleted) return; // hanya transisi false -> true

    const {threadId, messageId} = event.params;
    const db = getFirestore(getApp(), DATABASE_ID);
    await processChatMessageDeleted(db, threadId, messageId, after.data() as Record<string, unknown>);
    logger.info("chat.message.deleted", {thread: shortHash(threadId), message: shortHash(messageId)});
  }
);
