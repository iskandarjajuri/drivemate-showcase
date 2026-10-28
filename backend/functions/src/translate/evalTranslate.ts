/* eslint-disable max-len -- baris panjang dipertahankan demi keterbacaan prompt/skema */
/**
 * Evaluasi MANUAL kualitas terjemahan terhadap golden set. TIDAK dijalankan di CI.
 *
 *   DEEPSEEK_API_KEY=... npm run eval:translate
 *
 * Membaca functions/test/golden/translate.json, memanggil penyedia sungguhan
 * (butuh jaringan + kunci dari env), dan mencetak tabel untuk dinilai manusia.
 * Config yang dipakai = config.json (enabled tidak relevan di sini).
 */
import {readFileSync} from "fs";
import {join} from "path";
import {loadTranslateConfig} from "./config";
import {callProvider, FetchLike} from "./deepseek";
import {parseModelResult} from "./translateMessage";
import {buildPrompt} from "./prompt";

interface GoldenCase {
  id: string;
  category: string;
  from: string;
  text: string;
  expect: string;
}

/**
 * Jalankan evaluasi dan cetak tabel.
 *
 * @return {Promise<void>} Selesai setelah semua kasus dicetak.
 */
async function main(): Promise<void> {
  const apiKey = process.env.DEEPSEEK_API_KEY;
  if (!apiKey) {
    console.error("Set env DEEPSEEK_API_KEY (tidak pernah dibaca dari berkas).");
    process.exit(1);
  }
  const config = loadTranslateConfig();
  const goldenPath = join(__dirname, "..", "..", "test", "golden", "translate.json");
  const cases = JSON.parse(readFileSync(goldenPath, "utf8")) as GoldenCase[];

  console.log(`model=${config.provider.model} kasus=${cases.length}\n`);
  for (const c of cases) {
    const started = Date.now();
    let line: string;
    try {
      const content = await callProvider({
        config,
        apiKey,
        prompt: buildPrompt(config, c.text),
        fetchImpl: fetch as unknown as FetchLike,
      });
      const r = parseModelResult(content, config.maxChars);
      line = r ?
        `${r.src}/${r.register}${r.uncertain ? " (uncertain)" : ""} => ${r.translation}` :
        `JSON TIDAK SAH: ${content.slice(0, 120)}`;
    } catch (e) {
      line = `GALAT: ${(e as Error).message}`;
    }
    console.log(
      `[${c.id}] ${c.category}\n  asli    : ${c.text}\n  hasil   : ${line}\n` +
      `  harapan : ${c.expect}\n  latensi : ${Date.now() - started} ms\n`
    );
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
