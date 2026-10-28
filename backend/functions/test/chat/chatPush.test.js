/**
 * Push notifikasi pesan chat: logika inti dengan dependensi palsu. Tanpa jaringan, tanpa FCM, tanpa emulator.
 */
const {pushChatMessage, clipBody} = require("../../lib/chat/chatPush");

const SECRET = "RAHASIA-ISI-PESAN-123";
const T = "uidA__uidB";

function deps(over = {}) {
  const sent = [];
  const logs = [];
  const base = {
    sent,
    logs,
    getMessage: jest.fn(async () => ({translationStatus: undefined})),
    claimPush: jest.fn(async () => true),
    getDirectory: jest.fn(async () => ({
      uidA: {role: "driver", displayName: "Driver Satu"},
      uidB: {role: "expat", displayName: "Expat Satu"},
    })),
    hasTokens: jest.fn(async (uids) => Object.fromEntries(uids.map((u) => [u, u === "uidB"]))),
    pushToUser: jest.fn(async (uid, m) => {
      sent.push({uid, ...m});
      return 1;
    }),
    sleep: jest.fn(async () => undefined),
    now: () => 1_000,
    log: (event, fields) => logs.push({event, fields}),
  };
  return Object.assign(base, over);
}

const msg = (over = {}) => ({
  senderUid: "uidA",
  body: "Selamat pagi Pak",
  participantUids: ["uidA", "uidB"],
  ...over,
});

describe("push chat: penerima, payload, bahasa", () => {
  test("pasangan beda bahasa: tunggu terjemahan lalu kirim teks terjemahan bahasa penerima", async () => {
    const reads = [
      {}, // pembacaan awal: cek apakah pesan sudah dihapus
      {},
      {translationStatus: "done", translations: {ja: {text: "おはようございます", uncertain: false}}},
    ];
    const d = deps({getMessage: jest.fn(async () => reads.shift() || reads[0])});
    const r = await pushChatMessage(d, T, "m1", msg());
    expect(r.outcome).toBe("sent");
    expect(d.sent).toHaveLength(1);
    const p = d.sent[0];
    expect(p.uid).toBe("uidB"); // token/multi-device/retry/pembersihan ada di sendPushToUser (tes terpisah)
    expect(p.notification).toEqual({title: "Driver Satu", body: "おはようございます"});
    expect(p.data).toEqual({type: "chat", threadId: T, messageId: "m1", senderUid: "uidA", senderName: "Driver Satu"});
    expect(p.android.priority).toBe("high");
    expect(p.android.notification).toEqual({channelId: "chat", tag: T});
    expect(d.sleep).toHaveBeenCalledTimes(1);
  });

  test("pasangan sama bahasa: kirim teks asli langsung tanpa menunggu", async () => {
    const d = deps({
      getDirectory: jest.fn(async () => ({
        uidA: {role: "driver", displayName: "Driver Satu"},
        uidB: {role: "admin", displayName: "Admin Satu"},
      })),
    });
    await pushChatMessage(d, T, "m1", msg());
    expect(d.sent[0].notification.body).toBe("Selamat pagi Pak");
    expect(d.sleep).not.toHaveBeenCalled();
    expect(d.getMessage).toHaveBeenCalledTimes(1); // hanya cek "sudah dihapus?"
  });

  test.each(["failed", "skipped"])("terjemahan %s: kirim teks asli", async (status) => {
    const d = deps({getMessage: jest.fn(async () => ({translationStatus: status}))});
    await pushChatMessage(d, T, "m1", msg());
    expect(d.sent[0].notification.body).toBe("Selamat pagi Pak");
  });

  test("terjemahan tak kunjung datang: batas waktu ±10 dtk lalu teks asli", async () => {
    const d = deps({getMessage: jest.fn(async () => ({}))});
    const r = await pushChatMessage(d, T, "m1", msg());
    expect(r.outcome).toBe("sent");
    expect(d.sent[0].notification.body).toBe("Selamat pagi Pak");
    expect(d.sleep.mock.calls.length).toBeLessThanOrEqual(10);
    expect(d.sleep.mock.calls.length).toBeGreaterThanOrEqual(8);
  });

  test("isi panjang dipotong ±80 karakter dengan elipsis; spasi/baris dirapikan", async () => {
    expect(clipBody("a\n\n  b")).toBe("a b");
    const clipped = clipBody("x".repeat(200));
    expect(clipped.length).toBeLessThanOrEqual(80);
    expect(clipped.endsWith("…")).toBe(true);
    expect(clipBody("pendek")).toBe("pendek");
  });

  test("judul jatuh ke teks generik terlokalisasi bila nama pengirim tak ada", async () => {
    const d = deps({
      getDirectory: jest.fn(async () => ({uidA: {role: "driver"}, uidB: {role: "expat", displayName: "Expat Satu"}})),
    });
    await pushChatMessage(d, T, "m1", msg());
    expect(d.sent[0].notification.title).toBe("新着メッセージ"); // penerima expat -> ja
  });
});

describe("push chat: pengaman", () => {
  test("pesan terhapus / sistem / pengirim bukan peserta: tidak mengirim dan tidak mengklaim", async () => {
    for (const bad of [{deleted: true}, {isSystem: true}, {senderUid: "uidX"}, {body: ""}]) {
      const d = deps();
      const r = await pushChatMessage(d, T, "m1", msg(bad));
      expect(r.outcome).toBe("skipped");
      expect(d.claimPush).not.toHaveBeenCalled();
      expect(d.pushToUser).not.toHaveBeenCalled();
    }
  });

  test("pesan dihapus setelah diklaim/ saat menunggu terjemahan: isi TIDAK dikirim lewat push", async () => {
    const d = deps({getMessage: jest.fn(async () => ({deleted: true}))});
    const r = await pushChatMessage(d, T, "m1", msg());
    expect(r.outcome).toBe("skipped");
    expect(d.pushToUser).not.toHaveBeenCalled();

    // dihapus ketika sedang menunggu terjemahan (pembacaan ke-2)
    const reads = [{}, {deleted: true}];
    const d2 = deps({getMessage: jest.fn(async () => reads.shift() || {deleted: true})});
    const r2 = await pushChatMessage(d2, T, "m1", msg());
    expect(r2.outcome).toBe("skipped");
    expect(d2.pushToUser).not.toHaveBeenCalled();
  });

  test("penerima tanpa token: tidak mengklaim dan tidak mengirim", async () => {
    const d = deps({hasTokens: jest.fn(async (uids) => Object.fromEntries(uids.map((u) => [u, false])))});
    const r = await pushChatMessage(d, T, "m1", msg());
    expect(r.outcome).toBe("no_recipient");
    expect(d.claimPush).not.toHaveBeenCalled();
  });

  test("idempoten: klaim sudah diambil (retry/duplikat) -> tidak mengirim ulang", async () => {
    const d = deps({claimPush: jest.fn(async () => false)});
    const r = await pushChatMessage(d, T, "m1", msg());
    expect(r.outcome).toBe("already_sent");
    expect(d.pushToUser).not.toHaveBeenCalled();
  });

  test("galat pengiriman tidak menjatuhkan fungsi dan tidak dihitung terkirim (pembersihan token mati di sendPushToUser)", async () => {
    const d = deps({pushToUser: jest.fn(async () => {
      throw Object.assign(new Error("x"), {code: "messaging/internal-error"});
    })});
    const r = await pushChatMessage(d, T, "m1", msg());
    expect(r).toEqual({outcome: "sent", delivered: 0});
    expect(d.logs.some((l) => l.event === "chat.push.send_failed")).toBe(true);
  });

  test("log tidak pernah memuat isi pesan, token penuh, atau threadId mentah", async () => {
    const d = deps();
    await pushChatMessage(d, T, "m1", msg({body: SECRET}));
    const dump = JSON.stringify(d.logs);
    expect(dump).not.toContain(SECRET);
    expect(dump).not.toContain(T);
    expect(d.logs.some((l) => l.event === "chat.push")).toBe(true);
  });
});
