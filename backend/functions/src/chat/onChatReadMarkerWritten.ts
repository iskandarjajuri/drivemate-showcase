/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {getApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {onDocumentWritten} from "firebase-functions/v2/firestore";
import {logger} from "firebase-functions/logger";
import {resetThreadUnread} from "./chatCore";
import {shortHash} from "../logHash";

const DATABASE_ID = "drivemate-database";
const REGION = "asia-southeast2";

export const onChatReadMarkerWritten = onDocumentWritten(
  {document: "chat_threads/{threadId}/reads/{uid}", database: DATABASE_ID, region: REGION},
  async (event) => {
    const after = event.data?.after;
    if (!after || !after.exists) return;
    const {threadId, uid} = event.params;
    const db = getFirestore(getApp(), DATABASE_ID);
    await resetThreadUnread(db, threadId, uid);
    logger.info("chat.read.reset", {thread: shortHash(threadId), user: shortHash(uid)});
  }
);
