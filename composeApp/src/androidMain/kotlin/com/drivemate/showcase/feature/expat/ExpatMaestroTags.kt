package com.drivemate.showcase.feature.expat

/**
 * Test tag untuk keempat layar peran Expat.
 *
 * Sebelum ini peran Expat adalah SATU-SATUNYA dari tiga peran yang nol test tag — Driver dan
 * Admin sudah punya `dm_driver_home_root` / `dm_admin_home_root`, Expat tidak punya padanannya.
 * Akibatnya flow Maestro dan agent MCP harus berpegang pada teks terlihat ("Expat Dashboard"),
 * yang langsung rusak begitu perangkat berbahasa Indonesia atau Jepang — dua locale yang justru
 * dipakai pengguna nyata aplikasi ini.
 *
 * Penamaan mengikuti konvensi yang sudah ada: `dm_<peran>_<layar>_<elemen>`, sejajar dengan
 * `AuthMaestroTags` / `DriverMaestroTags` / `AdminMaestroTags` dan dengan `IOSAccessibilityIDs`
 * di sisi iOS.
 */
object ExpatMaestroTags {
    const val HOME_ROOT = "dm_expat_home_root"
    const val CHAT_UNREAD_BADGE = "dm_expat_chat_unread_badge"
    const val HOME_HERO = "dm_expat_home_hero"
    const val HOME_HERO_ACTION = "dm_expat_home_hero_action"
    const val HOME_SUMMARY = "dm_expat_home_summary"
    const val HOME_ATTENTION = "dm_expat_home_attention"
    const val HOME_ANNOUNCEMENT = "dm_expat_home_announcement"
    const val HOME_AVATAR = "dm_expat_home_avatar"
    const val HOME_BELL = "dm_expat_home_bell"
    const val DRIVER_DETAIL_ROOT = "dm_expat_driver_detail_root"
    const val REVIEW_ROOT = "dm_expat_review_root"
    const val WORKDAY_DETAIL_ROOT = "dm_expat_workday_detail_root"
    const val REVIEW_DAY_ITEM = "dm_expat_review_day_item"
    const val WORKDAY_APPROVE = "dm_expat_workday_approve"
    const val WORKDAY_REJECT = "dm_expat_workday_reject"
}
