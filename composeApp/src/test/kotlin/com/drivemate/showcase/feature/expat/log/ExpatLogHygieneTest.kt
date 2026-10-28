package com.drivemate.showcase.feature.expat.log

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Penjaga log layar Expat (aturan proyek: tidak ada data pribadi di log). Membaca semua sumber di folder `feature/expat` dan
 * menolak pemanggilan log yang memuat identitas. ID hari kerja berbentuk `{driverUid}_{epochDay}`, jadi `id=$id`
 * ikut dilarang; catatan penolakan adalah teks bebas pengguna; pesan mentah pengecualian backend bisa memuat path
 * dokumen. Pola dipilih agar pemindai ini sendiri teruji (lihat tes di bawah).
 */
class ExpatLogHygieneTest {

    private val forbidden = listOf(
        "authUid" to Regex("""\bauthUid\b"""),
        "driverUid" to Regex("""\bdriverUid\b(?!\.length)"""),
        "workdayId" to Regex("""\bworkdayId\b"""),
        "docId" to Regex("""\bdocId\b"""),
        "uid" to Regex("""\buid\b"""),
        "email" to Regex("""\bemail\b"""),
        "id interpolasi" to Regex("""\$\{?(id|wd\.id|data\.id|item\.id)\b"""),
        "catatan penolakan" to Regex("""\$\{?note\b(?!\.length)"""),
        "pesan mentah backend" to Regex("""\$\{?msg\b|throwable\.message""")
    )

    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/androidMain/kotlin/com/drivemate/showcase/feature/expat"),
            File("composeApp/src/androidMain/kotlin/com/drivemate/showcase/feature/expat")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("feature/expat tidak ditemukan; dicoba: ${candidates.map { it.absolutePath }}")
    }

    /** Rentang `[mulai, akhir)` tiap pemanggilan `prefix(` dengan kurung seimbang; string literal dilewati. */
    private fun callSpans(text: String, prefix: Regex): List<String> {
        val out = mutableListOf<String>()
        for (m in prefix.findAll(text)) {
            var i = m.range.last + 1
            var depth = 1
            var inString = false
            while (depth > 0 && i < text.length) {
                val c = text[i]
                if (c == '"' && text[i - 1] != '\\') inString = !inString
                else if (!inString) {
                    if (c == '(') depth++
                    if (c == ')') depth--
                }
                i++
            }
            out += text.substring(m.range.first, i)
        }
        return out
    }

    /** Pemanggilan log + isi fungsi helper log (`logReview`/`logSummaryFetch`) tempat pesan dirakit. */
    private fun logSpans(text: String): List<String> {
        val calls = callSpans(text, Regex("""(DMLog\.[vdiwe]|UiErrorLogger\.e)\("""))
        val helpers = Regex("""private fun (logReview|logSummaryFetch)\(""").findAll(text).map { m ->
            val open = text.indexOf('{', m.range.last)
            var depth = 0
            var i = open
            do {
                if (text[i] == '{') depth++
                if (text[i] == '}') depth--
                i++
            } while (depth > 0 && i < text.length)
            text.substring(m.range.first, i)
        }.toList()
        return calls + helpers
    }

    private fun violations(file: String, text: String): List<String> =
        logSpans(text).flatMap { span ->
            forbidden.filter { (_, re) -> re.containsMatchIn(span) }
                .map { (label, _) -> "$file: $label dalam: ${span.lineSequence().first().trim()}" }
        }

    @Test
    fun `log layar Expat tidak memuat identitas, catatan penolakan, atau pesan mentah backend`() {
        val files = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val spanCount = files.sumOf { logSpans(it.readText()).size }
        assertTrue(spanCount >= 10, "pemindai hanya menemukan $spanCount pemanggilan log; pola rusak?")
        val found = files.flatMap { violations(it.name, it.readText()) }
        assertEquals(emptyList(), found, "log memuat data pribadi:\n" + found.joinToString("\n"))
    }

    @Test
    fun `pemindai menangkap pelanggaran yang pernah ada`() {
        val bad = """
            DMLog.i("DM/Expat", "Load rolling review: driverUid(uid)=${'$'}driverUid (last 3 months)")
            DMLog.w(TAG, "Reject start; id=${'$'}id; note='${'$'}note'")
            DMLog.e(TAG, "Load failed: ${'$'}msg", res.throwable)
            DMLog.d("DM/Expat", "Toggle WorkDay id=${'$'}{wd.id}, checked=${'$'}isChecked")
            private fun logReview(event: String, authUid: String) {
                val base = "[DRIVER_HISTORY] ${'$'}event authUid=${'$'}authUid"
                DMLog.i(TAG, base)
            }
        """.trimIndent()
        val labels = violations("bad.kt", bad).map { it.substringAfter(": ").substringBefore(" dalam") }.toSet()
        assertTrue(
            labels.containsAll(setOf("driverUid", "uid", "id interpolasi", "catatan penolakan", "pesan mentah backend", "authUid")),
            "pemindai melewatkan pelanggaran: $labels"
        )
    }

    @Test
    fun `pemindai tidak menandai log yang bersih`() {
        val good = """
            DMLog.w(TAG, "Reject start; noteLength=${'$'}{note.length}")
            DMLog.w(TAG, "Reject load: driver id is not a UID (length=${'$'}{driverUid.length})")
            DMLog.i(TAG, "[DRIVER_HISTORY] approve_start role=${'$'}currentRole count=${'$'}{ids.size}")
        """.trimIndent()
        assertEquals(emptyList(), violations("good.kt", good))
    }
}
