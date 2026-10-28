# Terjemahan chat otomatis (JA ↔ ID)

Cloud Function `onChatMessageCreatedTranslate` (region `asia-southeast2`, database `drivemate-database`)
menerjemahkan pesan baru di `chat_threads/{threadId}/messages/{messageId}` lewat DeepSeek dan menulis
hasilnya kembali ke dokumen pesan. **Status: AKTIF sejak 2026-10-11** (`config.json` → `enabled:true`; dasar: persetujuan perusahaan diterima). Untuk mematikan, set `enabled:false` lalu deploy ulang.

## Alur
1. Guard: fitur aktif, bukan pesan sistem, belum ada `translations`/`translationStatus` (idempotent),
   panjang ≤ `maxChars` (1000), kuota harian per pengirim (`translation_usage/{uid_YYYYMMDD}`, transaksi,
   `quota.perUserPerDay` = 300).
2. Panggil penyedia (`POST {baseUrl}{path}`, `response_format: json_object`, `thinking` lewat `provider.extraBody`).
3. Validasi JSON `{src, register, translation, uncertain}`; tidak sah → 1 retry → `translationStatus:"failed"`.
4. Tulis HANYA `srcLang`, `register`, `translations.{id|ja}`, `translationStatus`, `translatedAtMillis`,
   `translationModel` (bahasa seberang saja; `src:"other"` → `skipped`). Isi pesan asli tidak diubah.
   Klien tidak bisa menulis field ini (rules: `validChatMessageDocument` memakai daftar field tertutup,
   `update` ditolak) — diuji di `functions/test/firestore.chat.rules.test.js`.

## Menyalakan (langkah manual, oleh pemilik proyek)
```
firebase functions:secrets:set DEEPSEEK_API_KEY      # tempel kunci saat diminta; JANGAN ke repo/chat/log
# "enabled": true di src/translate/config.json  (sudah AKTIF; persetujuan perusahaan diterima)
firebase deploy --only functions:onChatMessageCreatedTranslate
```
Rules tidak berubah di PR ini, jadi `firestore:rules` tidak perlu di-deploy karenanya.

## Ganti model / penyedia
Semua di `config.json` → `provider` (`model`, `extraBody`, `temperature`, `maxTokens`, `timeoutMs`,
`maxRetries`, `baseUrl`, `path`). Nilai saat ini diverifikasi dari dokumentasi resmi
(https://api-docs.deepseek.com): model `deepseek-flash` (murah; BUKAN `deepseek-v4-pro`), `thinking: {type:"disabled"}`,
JSON mode `response_format:{type:"json_object"}`. Catatan dokumentasi: thinking aktif secara bawaan; effort
adalah field level atas `reasoning_effort` (`low`/`high`/`max`), BUKAN di dalam `thinking` (bentuk lama di
config ini salah dan diabaikan penyedia sehingga effort jatuh ke `high`). Terjemahan tidak butuh penalaran,
jadi thinking dimatikan: lebih murah, lebih cepat, dan `temperature` kembali berpengaruh (di mode thinking
diabaikan). Bila kualitas register kurang: `{"thinking":{"type":"enabled"},"reasoning_effort":"low"}`.
Mode JSON kadang mengembalikan konten kosong (ditangani sebagai galat yang di-retry).

Pengaturan ini khusus pemanggilan dari aplikasi DriveMate; tidak terkait konfigurasi agent.
Glosarium, `registerMap`, dan `keepAsIs` juga di `config.json`.

## Evaluasi kualitas (manual, tidak di CI)
`DEEPSEEK_API_KEY=... npm run eval:translate` membaca `test/golden/translate.json` dan mencetak tabel untuk
dinilai manusia.

## Biaya, kuota, privasi
- Batas 300 pesan/hari/pengirim dan 1000 karakter/pesan membatasi biaya; `maxInstances` 3.
- Isi pesan dikirim ke penyedia eksternal (DeepSeek) hanya saat `enabled:true`. Log hanya memuat
  threadId, messageId, panjang, bahasa, latensi, kode galat — tidak pernah isi pesan atau kunci.
