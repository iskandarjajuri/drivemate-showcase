/**
 * sendPushToUser: satu pintu pengirim FCM per pengguna (multi-device, retry terbatas, pembersihan token mati).
 * Dependensi palsu; tanpa jaringan/FCM/emulator.
 */
const {sendPushToUser, MAX_TOKENS_PER_USER} = require("../../lib/push/sendPushToUser");

const TOKEN_SECRET = "SECRET-TOKEN-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
const MSG = {notification: {title: "T", body: "isi-pesan-RAHASIA"}, data: {type: "chat"}, android: {priority: "high", notification: {channelId: "chat", tag: "x"}}};
const rec = (id, token = `tok-${id}-${"a".repeat(40)}`, source = "doc") => ({id, token, source, updatedAtMillis: 1});

function deps(records, over = {}) {
  const sentTo = [];
  const logs = [];
  const removed = [];
  const base = {
    sentTo,
    logs,
    removed,
    listTokens: jest.fn(async () => records),
    removeToken: jest.fn(async (r) => {
      removed.push(r.id);
    }),
    sendOne: jest.fn(async (token) => {
      sentTo.push(token);
    }),
    sleep: jest.fn(async () => undefined),
    log: (event, fields) => logs.push({event, fields}),
  };
  return Object.assign(base, over);
}
const err = (code) => Object.assign(new Error("x"), {code});

describe("sendPushToUser", () => {
  test("mengirim ke SEMUA token pengguna (multi-device) dan menghitung terkirim", async () => {
    const d = deps([rec("a"), rec("b"), rec("c")]);
    const r = await sendPushToUser(d, "u1", MSG);
    expect(r).toEqual({attempted: 3, delivered: 3, removed: 0});
    expect(d.sentTo).toHaveLength(3);
  });

  test("tanpa token -> tidak mengirim", async () => {
    const d = deps([]);
    expect(await sendPushToUser(d, "u1", MSG)).toEqual({attempted: 0, delivered: 0, removed: 0});
  });

  test(`dibatasi ${MAX_TOKENS_PER_USER} token terbaru`, async () => {
    const many = Array.from({length: 9}, (_, i) => rec(`t${i}`));
    const d = deps(many);
    const r = await sendPushToUser(d, "u1", MSG);
    expect(r.attempted).toBe(MAX_TOKENS_PER_USER);
  });

  test("galat SEMENTARA di-retry (maks 2x) lalu berhasil; token TIDAK dihapus", async () => {
    let n = 0;
    const d = deps([rec("a")], {
      sendOne: jest.fn(async () => {
        n++;
        if (n < 3) throw err("messaging/server-unavailable");
      }),
    });
    const r = await sendPushToUser(d, "u1", MSG);
    expect(r.delivered).toBe(1);
    expect(d.sendOne).toHaveBeenCalledTimes(3);
    expect(d.sleep).toHaveBeenCalledTimes(2);
    expect(d.removed).toEqual([]);
  });

  test("galat sementara terus-menerus: berhenti setelah retry habis, tidak terkirim, token tetap", async () => {
    const d = deps([rec("a")], {sendOne: jest.fn(async () => {
      throw err("messaging/internal-error");
    })});
    const r = await sendPushToUser(d, "u1", MSG);
    expect(r).toEqual({attempted: 1, delivered: 0, removed: 0});
    expect(d.sendOne).toHaveBeenCalledTimes(3);
    expect(d.removed).toEqual([]);
  });

  test("token mati dihapus; invalid-argument / quota / tak dikenal TIDAK menghapus dan TIDAK di-retry", async () => {
    for (const code of ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]) {
      const d = deps([rec("a"), rec("b")], {sendOne: jest.fn(async (t) => {
        if (t.includes("-a-")) throw err(code);
      })});
      const r = await sendPushToUser(d, "u1", MSG);
      expect(r).toEqual({attempted: 2, delivered: 1, removed: 1});
      expect(d.removed).toEqual(["a"]);
      expect(d.sendOne).toHaveBeenCalledTimes(2); // tanpa retry
    }
    for (const code of ["messaging/invalid-argument", "messaging/quota-exceeded", "unknown"]) {
      const d = deps([rec("a")], {sendOne: jest.fn(async () => {
        throw err(code);
      })});
      const r = await sendPushToUser(d, "u1", MSG);
      expect(r.removed).toBe(0);
      expect(d.removed).toEqual([]);
      expect(d.sendOne).toHaveBeenCalledTimes(1);
    }
  });

  test("satu token gagal tidak menghentikan token lain", async () => {
    const d = deps([rec("a"), rec("b"), rec("c")], {sendOne: jest.fn(async (t) => {
      if (t.includes("-b-")) throw err("messaging/invalid-argument");
    })});
    expect((await sendPushToUser(d, "u1", MSG)).delivered).toBe(2);
  });

  test("log tidak memuat token, isi/judul pesan, atau uid mentah", async () => {
    const d = deps([rec("a", TOKEN_SECRET)], {sendOne: jest.fn(async () => {
      throw err("messaging/registration-token-not-registered");
    })});
    await sendPushToUser(d, "uid-mentah-123", MSG);
    const dump = JSON.stringify(d.logs);
    expect(dump).not.toContain(TOKEN_SECRET);
    expect(dump).not.toContain("isi-pesan-RAHASIA");
    expect(dump).not.toContain("uid-mentah-123");
    expect(d.logs.length).toBeGreaterThan(0);
  });
});
