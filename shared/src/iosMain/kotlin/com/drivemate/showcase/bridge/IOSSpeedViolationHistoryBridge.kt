package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.safety.SpeedViolationDay
import com.drivemate.showcase.domain.safety.buildSpeedViolationDays
import com.drivemate.showcase.domain.usecase.safety.GetSpeedViolationsUseCase
import com.drivemate.showcase.domain.usecase.user.GetAllDriverDisplayNamesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Riwayat pelanggaran batas kecepatan untuk iOS — padanan
 * `AdminSpeedViolationsViewModel` Android (C1 pada PRD §7, Blok 3.3 / Blok 6.5).
 *
 * ## Nol logika riwayat di sini, dan nol di Swift
 * Pengelompokan per hari, pengurutan, dan penyisipan nama semuanya di
 * [buildSpeedViolationDays] (`commonMain`) — fungsi yang SAMA yang dipanggil ViewModel
 * Android, dengan 17 test di `SpeedViolationHistoryTest`. Yang ada di file ini hanya
 * penjahitan: baca, resolusi nama, format label.
 *
 * ## Kenapa nama diselesaikan di sini, bukan disimpan di dokumen
 * Dokumen `speed_violations` hanya menyimpan `driverUid` — dan itu benar: catatan audit
 * tidak boleh membeku dengan nama lama saat seseorang berganti nama. Konsekuensinya nama
 * bisa gagal diselesaikan; barisnya tetap tampil dengan penanda, dan UID **tidak pernah**
 * muncul di layar sebagai gantinya.
 *
 * Kegagalan baca nama **tidak** menggagalkan layar (`emptyMap()`), sama seperti Android:
 * baris tanpa nama masih memberi tahu ada pelanggaran, sedangkan layar error tidak memberi
 * tahu apa pun. Kegagalan baca pelanggaran LAIN — itu data inti layar ini, jadi dilaporkan.
 *
 * ## Kenapa label tanggal/jam dibentuk di Kotlin
 * `epochDay * 86400` adalah tengah malam UTC. `DateFormatter` Swift dengan timezone
 * default akan merender tanggal SEBELUMNYA di perangkat mana pun di barat UTC — jebakan
 * yang sudah didokumentasikan di [IOSWorkHistoryDaySnapshot]. Dengan label dibentuk di
 * sini, kedua platform memakai [IOSDriverTimezone] yang sama dan Swift tidak punya
 * kesempatan untuk salah.
 */
internal class IOSSpeedViolationHistoryBridge(
    private val scope: CoroutineScope,
    private val getViolations: GetSpeedViolationsUseCase,
    private val getAllDriverDisplayNames: GetAllDriverDisplayNamesUseCase,
    private val nowMillis: () -> Long = { TimeUtil.nowEpochMillis() }
) {

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }

    fun load(completion: (IOSSpeedViolationHistorySnapshot?, String?) -> Unit) {
        scope.launch {
            val windowDays = GetSpeedViolationsUseCase.DEFAULT_WINDOW_DAYS
            val limit = GetSpeedViolationsUseCase.DEFAULT_LIMIT
            val since = nowMillis() - windowDays * DAY_MS

            val page = when (val res = getViolations(since, limit)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    // Pesannya diteruskan apa adanya, bukan diganti teks generik: kalau
                    // ini PERMISSION_DENIED (mis. akun yang bukan admin), itu keterangan
                    // yang membedakan "tidak boleh" dari "tidak ada".
                    completion(null, res.throwable.message ?: "Failed to load speed violations.")
                    return@launch
                }
            }

            val names = when (val res = getAllDriverDisplayNames()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> emptyMap()
            }

            val days = buildSpeedViolationDays(page.records, names) {
                TimeUtil.epochDayOf(it, IOSDriverTimezone).toInt()
            }

            completion(
                IOSSpeedViolationHistorySnapshot(
                    days = days.map { it.toIosSnapshot() },
                    totalCount = page.records.size,
                    windowDays = windowDays,
                    // Dihitung dari jumlah dokumen MENTAH, bukan dari baris yang berhasil
                    // dipetakan — lihat KDoc `SpeedViolationPage`.
                    truncated = page.isTruncatedAt(limit),
                    droppedCount = page.droppedCount
                ),
                null
            )
        }
    }
}

/**
 * Satu halaman riwayat, siap dirender.
 *
 * [truncated] dan [droppedCount] dibawa terpisah dari [totalCount] karena ketiganya
 * menjawab pertanyaan berbeda, dan dua yang terakhir HARUS disebut di layar: pada layar
 * bukti keselamatan, "tidak ada catatan", "catatannya lebih banyak tapi dipangkas", dan
 * "ada catatan yang tidak bisa dibaca" adalah tiga hal yang tidak boleh terlihat sama.
 */
data class IOSSpeedViolationHistorySnapshot(
    val days: List<IOSSpeedViolationDaySnapshot>,
    val totalCount: Int,
    val windowDays: Int,
    val truncated: Boolean,
    val droppedCount: Int
)

/** Pelanggaran satu hari kalender, terbaru lebih dulu. */
data class IOSSpeedViolationDaySnapshot(
    val dateEpochDay: Int,
    /**
     * Label tanggal yang sudah jadi (YYYY/MM/DD), dibentuk Kotlin.
     *
     * `formatYmdFromEpochDay` sengaja tidak diberi timezone: epochDay sudah besaran
     * KALENDER, bukan besaran instan, jadi mengonversinya "ke suatu zona" tidak punya arti
     * dan hanya mengundang orang berikutnya untuk "memperbaikinya".
     */
    val dateLabel: String,
    val rows: List<IOSSpeedViolationRowSnapshot>
)

/**
 * Satu baris pelanggaran.
 *
 * Angka kecepatan dibulatkan di Kotlin dengan `roundToInt()` — sama seperti layar Android
 * (`AdminSpeedViolationsScreen.ViolationRow`). Membulatkannya di Swift akan membuat dua
 * platform bisa berbeda pendapat soal 79,5 km/jam pada layar yang isinya bukti.
 *
 * Tidak ada `driverUid` di sini, dan itu disengaja: satu-satunya identitas yang menghadap
 * layar adalah [driverName]. Membawa UID-nya akan membuat "tampilkan UID kalau nama
 * kosong" menjadi satu baris kode yang sangat mudah ditulis.
 */
data class IOSSpeedViolationRowSnapshot(
    val id: String,
    /** `null` bila nama belum/gagal diselesaikan — UI menampilkan penanda, bukan UID. */
    val driverName: String?,
    val speedKph: Int,
    val speedLimitKph: Int,
    val excessKph: Int,
    /** HH:mm di Asia/Jakarta. */
    val timeLabel: String
)

internal fun SpeedViolationDay.toIosSnapshot(): IOSSpeedViolationDaySnapshot =
    IOSSpeedViolationDaySnapshot(
        dateEpochDay = dateEpochDay,
        dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay),
        rows = rows.map { row ->
            IOSSpeedViolationRowSnapshot(
                id = row.record.id,
                driverName = row.driverName,
                speedKph = row.record.speedKph.roundToInt(),
                speedLimitKph = row.record.speedLimitKph.roundToInt(),
                excessKph = row.record.excessKph.roundToInt(),
                timeLabel = TimeUtil.formatHm(row.record.occurredAtMillis, IOSDriverTimezone)
            )
        }
    )
