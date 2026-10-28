package com.drivemate.showcase.feature.expat.strings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tes lint string layar Expat. Membaca `strings.xml` tiga bahasa langsung dari sumber dan menjaga:
 * paritas kunci, kesetaraan placeholder, serta gaya teks Jepang (tanpa emoji, tanpa katakana setengah-lebar, tanpa
 * digit lebar-penuh, titik dua lebar-penuh, tanpa istilah teknis internal). Aturan dan alasannya:
 * docs/localization/GLOSSARY.md dan docs/design/expat-ja-ux.md.
 */
class ExpatStringsLintTest {

    private fun resFile(dir: String): File {
        val candidates = listOf(
            File("src/androidMain/res/$dir/strings.xml"),
            File("composeApp/src/androidMain/res/$dir/strings.xml")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("strings.xml tidak ditemukan untuk $dir; dicoba: ${candidates.map { it.absolutePath }}")
    }

    /** Hanya kunci `expat_*`, nama -> nilai mentah (escape Android tidak diurai). */
    private fun load(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile(dir))
        val nodes = doc.getElementsByTagName("string")
        val out = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val e = nodes.item(i)
            val name = e.attributes.getNamedItem("name").nodeValue
            if (name.startsWith("expat_")) out[name] = e.textContent
        }
        // Jamak ikut diperiksa lewat bentuk "other" (satu-satunya bentuk ID/JA), supaya aturan gaya berlaku juga di sana.
        for ((name, items) in loadPlurals(dir)) items["other"]?.let { out[name] = it }
        return out
    }

    /** Kunci `expat_*` bertipe `<plurals>`: nama -> (quantity -> teks mentah). */
    private fun loadPlurals(dir: String): Map<String, Map<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile(dir))
        val nodes = doc.getElementsByTagName("plurals")
        val out = linkedMapOf<String, Map<String, String>>()
        for (i in 0 until nodes.length) {
            val e = nodes.item(i)
            val name = e.attributes.getNamedItem("name").nodeValue
            if (!name.startsWith("expat_")) continue
            val items = linkedMapOf<String, String>()
            for (j in 0 until e.childNodes.length) {
                val c = e.childNodes.item(j)
                if (c.nodeName == "item") items[c.attributes.getNamedItem("quantity").nodeValue] = c.textContent
            }
            out[name] = items
        }
        return out
    }

    private val en by lazy { load("values") }
    private val id by lazy { load("values-in") }
    private val ja by lazy { load("values-ja") }

    private val placeholder = Regex("%(\\d+\\$)?[sdf]")
    private fun placeholders(v: String) = placeholder.findAll(v).map { it.value }.sorted().toList()

    @Test
    fun dasar_adaKunciExpat() {
        assertTrue(en.size > 100, "terlalu sedikit kunci expat_ di EN: ${en.size}")
    }

    @Test
    fun paritasKunci_enIdJa_samaPersis() {
        assertEquals(emptySet(), en.keys - id.keys, "kunci EN hilang di ID")
        assertEquals(emptySet(), en.keys - ja.keys, "kunci EN hilang di JA")
        assertEquals(emptySet(), id.keys - en.keys, "kunci ID tanpa pasangan EN")
        assertEquals(emptySet(), ja.keys - en.keys, "kunci JA tanpa pasangan EN")
    }

    @Test
    fun placeholder_identikDiTigaBahasa() {
        val beda = en.keys.filter { k ->
            val e = placeholders(en.getValue(k))
            e != placeholders(id[k].orEmpty()) || e != placeholders(ja[k].orEmpty())
        }
        assertEquals(emptyList(), beda, "placeholder tidak setara pada: $beda")
    }

    // Emoji dan simbol dingbat (termasuk tanda centang/peringatan/silang), selektor variasi, emoji bidang tambahan.
    private val emoji = Regex("[\\u2600-\\u27BF\\u2B50\\u2B55\\uFE0F\\x{1F300}-\\x{1FAFF}]")

    @Test
    fun tanpaEmoji_diTigaBahasa() {
        for ((lang, map) in listOf("EN" to en, "ID" to id, "JA" to ja)) {
            val kena = map.filterValues { emoji.containsMatchIn(it) }.keys
            assertEquals(emptySet(), kena, "emoji pada $lang: status harus lewat teks, bukan glyph berwarna")
        }
    }

    @Test
    fun jepang_tanpaKatakanaSetengahLebar() {
        val re = Regex("[\\uFF61-\\uFF9F]")
        assertEquals(emptySet(), ja.filterValues { re.containsMatchIn(it) }.keys)
    }

    @Test
    fun jepang_tanpaDigitDanHurufLatinLebarPenuh() {
        val re = Regex("[\\uFF10-\\uFF19\\uFF21-\\uFF3A\\uFF41-\\uFF5A]")
        assertEquals(emptySet(), ja.filterValues { re.containsMatchIn(it) }.keys, "gunakan digit/huruf setengah-lebar")
    }

    @Test
    fun jepang_titikDuaLebarPenuh_bukanAscii() {
        // "08:00" (titik dua di antara angka) sah; ": " atau ":" di akhir teks tidak.
        val re = Regex(":(\\s|$)")
        assertEquals(emptySet(), ja.filterValues { re.containsMatchIn(it) }.keys, "pakai ：(lebar-penuh) setelah label")
    }

    @Test
    fun jepang_tanpaIstilahTeknisInternal() {
        val terlarang = listOf("ルール", "Firestore", "PERMISSION", "Exception", "null")
        val kena = ja.filterValues { v -> terlarang.any { it in v } }.keys
        assertEquals(emptySet(), kena, "istilah teknis internal bocor ke pengguna: $terlarang")
    }

    @Test
    fun enDanId_tanpaIstilahTeknisInternal() {
        val terlarang = listOf("(rules)", "(aturan)", "Firestore", "PERMISSION", "Exception")
        for ((lang, map) in listOf("EN" to en, "ID" to id)) {
            val kena = map.filterValues { v -> terlarang.any { it in v } }.keys
            assertEquals(emptySet(), kena, "istilah teknis di $lang: $terlarang")
        }
    }

    @Test
    fun jepang_kalimatPenjelasan_berakhirTandaBacaJepang() {
        // Kalimat panjang berbentuk です/ます harus ditutup 。 ？ ！ atau kurung penutup; mencegah titik ASCII.
        val ascii = ja.filterValues { Regex("[\\p{L}\\p{N}\\u3040-\\u30FF\\u4E00-\\u9FFF]\\.$").containsMatchIn(it) }.keys
        assertEquals(emptySet(), ascii, "titik ASCII di akhir kalimat JA; pakai 。")
    }

    @Test
    fun jepang_labelStatusMenunggu_satuIstilahGlosarium() {
        // Kunci yang EN-nya "pending" (label status/hitungan, tanpa kata "review") memakai 承認待ち (GLOSSARY.md).
        // Frasa "waiting for review" memang setara 確認待ち dan tidak dipaksa.
        val salah = en.filter { (k, v) ->
            Regex("\\bpending\\b", RegexOption.IGNORE_CASE).containsMatchIn(v) &&
                !v.contains("review", ignoreCase = true) && "確認待ち" in ja[k].orEmpty()
        }.keys
        assertEquals(emptySet(), salah, "label status menunggu harus 承認待ち, bukan 確認待ち")
    }

    @Test
    fun jamak_enPunyaSatuDanLainnya_idJaHanyaLainnya_placeholderSama() {
        val enP = loadPlurals("values")
        val idP = loadPlurals("values-in")
        val jaP = loadPlurals("values-ja")
        assertTrue(enP.isNotEmpty(), "tidak ada plurals expat_ di EN")
        assertEquals(enP.keys, idP.keys, "plurals EN vs ID")
        assertEquals(enP.keys, jaP.keys, "plurals EN vs JA")
        for ((name, items) in enP) {
            assertEquals(setOf("one", "other"), items.keys, "$name: EN harus punya one dan other")
            assertEquals(placeholders(items.getValue("one")), placeholders(items.getValue("other")), "$name: placeholder one vs other")
            assertEquals(setOf("other"), idP.getValue(name).keys, "$name: ID hanya other")
            assertEquals(setOf("other"), jaP.getValue(name).keys, "$name: JA hanya other")
        }
    }

    @Test
    fun istilahIndonesia_konsisten_driverBukanPengemudi() {
        val bocor = id.filterValues { Regex("[Pp]engemudi|[Aa]ntrian|\\b[Rr]eview\\b|direview").containsMatchIn(it) }
        assertEquals(emptyMap(), bocor, "istilah ID tak baku/campur: pakai driver, antrean, tinjau(an)")
    }
}
