package com.drivemate.showcase.domain.safety

/**
 * Riwayat pelanggaran batas kecepatan — C1 pada PRD §7 (Blok 3.3).
 *
 * Koleksi `speed_violations` terisi sejak 27 Agustus 2026 dan belum pernah ditampilkan:
 * aplikasi mengumpulkan bukti keselamatan yang tidak pernah dibaca siapa pun. Berkas ini
 * adalah sisi BACA-nya, dan sengaja seluruhnya murni — nol pembacaan jam sistem, nol
 * Firestore — supaya pengelompokan dan pengurutannya benar-benar teruji di mesin ini.
 *
 * Yang **tidak** ada di sini: nama driver. Dokumen pelanggaran hanya menyimpan `driverUid`
 * (lihat `buildSpeedViolationPayload`), jadi nama diselesaikan di lapisan atas dan
 * disuntikkan lewat [buildSpeedViolationDays]. Menyimpan nama di dalam dokumen akan
 * membuat catatan audit membeku dengan nama lama saat seseorang berganti nama.
 */

/** Satu dokumen `speed_violations` yang sudah dibaca, beserta id dokumennya. */
data class SpeedViolationRecord(
    val id: String,
    val driverUid: String,
    val speedKph: Double,
    val speedLimitKph: Double,
    val occurredAtMillis: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /**
     * Kelebihan kecepatan di atas batas, dalam km/jam. Nol bila tidak melebihi.
     *
     * Dibawa sebagai turunan, bukan field dokumen: menyimpannya berarti dua angka yang
     * bisa berbeda pendapat, dan yang tersimpan tidak ikut terkoreksi kalau batasnya
     * ternyata salah dicatat.
     */
    val excessKph: Double get() = (speedKph - speedLimitKph).coerceAtLeast(0.0)
}

/**
 * Petakan satu dokumen mentah menjadi [SpeedViolationRecord], atau `null` bila cacat.
 *
 * Keempat field wajibnya sama dengan yang dituntut `firestore.rules` pada `create`
 * (`hasAll(['driverUid','speedKph','speedLimitKph','occurredAtMillis'])`), jadi dokumen
 * yang lolos aturan selalu lolos di sini juga — yang gugur hanya dokumen yang disunting
 * tangan lewat Console.
 *
 * **Tidak ada nilai default untuk kecepatan.** Dokumen tanpa `speedKph` digugurkan, bukan
 * dibaca sebagai 0 km/jam: baris pelanggaran dengan angka karangan lebih berbahaya daripada
 * baris yang tidak muncul, karena ia terlihat seperti bukti.
 */
fun mapSpeedViolationRecordOrNull(id: String, data: Map<String, Any?>?): SpeedViolationRecord? {
    if (data == null) return null
    val driverUid = (data["driverUid"] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val speedKph = (data["speedKph"] as? Number)?.toDouble() ?: return null
    val limitKph = (data["speedLimitKph"] as? Number)?.toDouble() ?: return null
    val occurredAt = (data["occurredAtMillis"] as? Number)?.toLong()?.takeIf { it > 0L }
        ?: return null
    return SpeedViolationRecord(
        id = id,
        driverUid = driverUid,
        speedKph = speedKph,
        speedLimitKph = limitKph,
        occurredAtMillis = occurredAt,
        latitude = (data["latitude"] as? Number)?.toDouble(),
        longitude = (data["longitude"] as? Number)?.toDouble(),
    )
}

/**
 * Satu halaman hasil bacaan, beserta jumlah dokumen **mentah** yang dikembalikan query.
 *
 * [rawDocCount] dibawa terpisah dari `records.size` karena keduanya menjawab pertanyaan yang
 * berbeda, dan yang menentukan "daftar ini terpotong" adalah yang mentah. Kalau query
 * mengembalikan 200 dokumen dan 5 di antaranya cacat, `records.size` menjadi 195 — dan
 * menghitung terpotong dari angka itu membuat layar mengaku lengkap justru pada kasus paling
 * berisiko: jendela yang penuh DAN datanya berantakan.
 */
data class SpeedViolationPage(
    val records: List<SpeedViolationRecord>,
    val rawDocCount: Int,
) {
    /** Dokumen yang digugurkan [mapSpeedViolationRecordOrNull]. Disebut di layar, bukan didiamkan. */
    val droppedCount: Int get() = (rawDocCount - records.size).coerceAtLeast(0)

    /** Query menyentuh batas pemuatan, jadi mungkin ada yang lebih tua yang tidak terbaca. */
    fun isTruncatedAt(limit: Int): Boolean = limit > 0 && rawDocCount >= limit
}

/** Satu baris riwayat: catatan plus nama yang ditampilkan. */
data class SpeedViolationRow(
    val record: SpeedViolationRecord,
    /** `null` bila nama belum/gagal diselesaikan — UI menampilkan penanda, bukan UID. */
    val driverName: String?,
)

/** Pelanggaran satu hari kalender, terbaru lebih dulu. */
data class SpeedViolationDay(
    val dateEpochDay: Int,
    val rows: List<SpeedViolationRow>,
)

/**
 * Kelompokkan catatan menjadi hari kalender, terbaru lebih dulu di kedua tingkat.
 *
 * [epochDayOf] disuntikkan alih-alih memanggil `TimeUtil` di sini: konversi
 * milidetik→hari bergantung zona waktu, dan fungsi yang membaca zona sistem sendiri tidak
 * bisa diuji untuk perilaku lintas-tengah-malam.
 *
 * Diurutkan ulang di sini walau query Firestore sudah `orderBy` menurun. Bukan pekerjaan
 * ganda yang sia-sia: sekali dokumen digugurkan oleh [mapSpeedViolationRecordOrNull] atau
 * datang dari sumber lain (cache, test, penggabungan dua halaman), urutan query tidak lagi
 * menjadi jaminan — dan riwayat keselamatan yang urutannya salah membuat orang menyimpulkan
 * tren yang tidak ada.
 */
fun buildSpeedViolationDays(
    records: List<SpeedViolationRecord>,
    driverNames: Map<String, String>,
    epochDayOf: (Long) -> Int,
): List<SpeedViolationDay> =
    records
        .groupBy { epochDayOf(it.occurredAtMillis) }
        .map { (day, sameDay) ->
            SpeedViolationDay(
                dateEpochDay = day,
                rows = sameDay
                    .sortedWith(
                        compareByDescending<SpeedViolationRecord> { it.occurredAtMillis }
                            // Id sebagai pemutus seri supaya urutannya stabil antar
                            // pemuatan; dua pelanggaran pada milidetik yang sama mustahil
                            // dibedakan oleh waktu saja.
                            .thenBy { it.id }
                    )
                    .map { SpeedViolationRow(it, driverNames[it.driverUid]) },
            )
        }
        .sortedByDescending { it.dateEpochDay }
