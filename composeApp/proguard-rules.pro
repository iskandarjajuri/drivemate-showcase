# Aturan R8 untuk build release (Issue #492). Prinsip: sesedikit mungkin, tiap aturan berkomentar.
# Pustaka AndroidX/Firebase/Maps/Koin membawa consumer rules sendiri; jangan menduplikasinya di sini.
# Komponen yang dideklarasikan di AndroidManifest.xml dipertahankan otomatis oleh AAPT.
#
# Sengaja TIDAK ada aturan keep untuk (diperiksa 2026-10-23 lewat pembacaan kode + hasil R8):
#  - DTO Firestore: semua tulis/baca memakai Map dan mapper manual (tanpa toObject()/set(dataClass)),
#    jadi tidak ada serialisasi POJO berbasis refleksi. JIKA nanti ada toObject()/set(objek), tambahkan
#    keep untuk kelas itu (nama field dipakai sebagai kunci dokumen) -- jangan mematikan R8.
#  - kotlinx.serialization: hanya dependensi, tanpa plugin dan tanpa @Serializable.
#  - Koin: DSL tanpa refleksi.
#  - Worker WorkManager: consumer rules work-runtime mempertahankan nama dan konstruktor (diverifikasi di mapping.txt).
#  - Enum: proguard-android-optimize.txt sudah mempertahankan values()/valueOf().
# Bila ada crash khusus rilis: cari kelas yang hilang/berganti nama di mapping.txt dan tambahkan keep
# yang spesifik DI SINI, lengkap dengan komentar alasannya.

# Stack trace dari rilis harus bisa di-retrace dengan mapping.txt (mapping.txt TIDAK boleh di-commit).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
