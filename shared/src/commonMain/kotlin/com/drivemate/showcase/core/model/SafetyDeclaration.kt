package com.drivemate.showcase.core.model

data class SafetyDeclaration(
    val fitToWork: Boolean,
    val sleepHours: Int? = null,
    val noAlcoholOrDrugs: Boolean,
    val noSevereStress: Boolean = false,
    val willFollowSpeedLimit: Boolean,
    val willFollowRestBreaks: Boolean,
    val willNotUsePhoneWhileDriving: Boolean,
    val willWearSeatbelt: Boolean,
    val hazardAwareness: String? = null,
    val weatherCondition: String? = null,
    val routeRiskNote: String? = null,
    val incidentReport: String? = null,
    val confirmed: Boolean
)
