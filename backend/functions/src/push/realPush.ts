/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan */
import {getApp} from "firebase-admin/app";
import {Firestore} from "firebase-admin/firestore";
import {getMessaging} from "firebase-admin/messaging";
import {logger} from "firebase-functions/logger";
import {createPushTokenStore} from "./pushTokenStore";
import {PushMessage, PushToUserDeps, sendPushToUser} from "./sendPushToUser";

/**
 * Dependensi NYATA untuk [sendPushToUser]: token dari Firestore, pengiriman lewat FCM Admin SDK. Di bawah Functions
 * emulator FCM tidak dipanggil; sebagai gantinya meninggalkan jejak tes di `_test_fcm_send_log` (token, title, body),
 * dipakai tes emulator notifikasi (`functions.notify_*`) untuk memverifikasi pemanggilan tanpa FCM nyata.
 *
 * @param {Firestore} db Firestore (database drivemate-database).
 * @return {PushToUserDeps} Dependensi.
 */
export function createRealPushDeps(db: Firestore): PushToUserDeps {
  return {
    ...createPushTokenStore(db),
    async sendOne(token: string, message: PushMessage): Promise<void> {
      if (process.env.FUNCTIONS_EMULATOR === "true") {
        logger.info("push: dilewati (Functions emulator)");
        await db.collection("_test_fcm_send_log").add({
          token,
          title: message.notification.title,
          body: message.notification.body,
          sentAtMillis: Date.now(),
        });
        return;
      }
      await getMessaging(getApp()).send({token, ...message});
    },
    sleep: (ms: number) => new Promise((resolve) => setTimeout(resolve, ms)),
    log: (event, fields) => logger.info(event, fields),
  };
}

/**
 * @param {Firestore} db Firestore.
 * @param {string} uid Pengguna.
 * @return {Promise<boolean>} True bila pengguna punya setidaknya satu token terdaftar.
 */
export async function hasPushTokens(db: Firestore, uid: string): Promise<boolean> {
  return (await createPushTokenStore(db).listTokens(uid)).length > 0;
}

/**
 * Push umum (judul+isi saja, channel bawaan) ke semua perangkat pengguna.
 *
 * @param {Firestore} db Firestore.
 * @param {string} uid Penerima.
 * @param {string} title Judul.
 * @param {string} body Isi.
 * @return {Promise<number>} Jumlah perangkat yang menerima.
 */
export async function pushGeneral(db: Firestore, uid: string, title: string, body: string): Promise<number> {
  return (await sendPushToUser(createRealPushDeps(db), uid, {notification: {title, body}})).delivered;
}

/**
 * Gerbang token/pengiriman untuk fungsi push chat & pekerjaan: `hasTokens` (cek keberadaan sebelum mengklaim idempotensi)
 * dan `pushToUser` (semua perangkat; mengembalikan jumlah terkirim).
 *
 * @param {Firestore} db Firestore.
 * @return {Object} Gerbang.
 */
export function createPushGateway(db: Firestore) {
  const real = createRealPushDeps(db);
  return {
    async hasTokens(uids: string[]): Promise<Record<string, boolean>> {
      const entries = await Promise.all(uids.map(async (u) => [u, (await real.listTokens(u)).length > 0] as const));
      return Object.fromEntries(entries);
    },
    async pushToUser(uid: string, message: PushMessage): Promise<number> {
      return (await sendPushToUser(real, uid, message)).delivered;
    },
  };
}
