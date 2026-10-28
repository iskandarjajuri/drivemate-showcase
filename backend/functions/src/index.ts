/**
 * Cloud Functions entry point (showcase slice).
 *
 * Only the chat plumbing and the automatic Japanese <-> Indonesian message
 * translation are included here. Everything is a Firestore trigger on the
 * named database `drivemate-database`; there are no HTTPS callables, and the
 * app never calls a function directly.
 */
import {initializeApp} from "firebase-admin/app";
import {setGlobalOptions} from "firebase-functions";

setGlobalOptions({region: "asia-southeast2", maxInstances: 10});
initializeApp();

// Automatic JA <-> ID translation of chat messages (OFF unless a provider key
// is configured; see src/translate/README.md).
export {
  onChatMessageCreatedTranslate,
} from "./translate/onChatMessageCreated";

// Chat thread bookkeeping and push notifications.
export {onUserWrittenSyncDirectory} from "./chat/onUserWrittenSyncDirectory"; // eslint-disable-line max-len
export {onChatMessageCreatedMaintainThread} from "./chat/onChatMessageCreatedMaintainThread"; // eslint-disable-line max-len
export {onChatReadMarkerWritten} from "./chat/onChatReadMarkerWritten";
export {onChatMessageDeleted} from "./chat/onChatMessageDeleted";
export {onChatMessageCreatedPush} from "./chat/onChatMessageCreatedPush";
