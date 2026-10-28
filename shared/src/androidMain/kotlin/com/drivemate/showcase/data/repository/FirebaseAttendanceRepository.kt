package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.core.util.attendanceDailyDocId
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.FirestoreServiceImpl
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.AttendanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone

class FirebaseAttendanceRepository(
    private val fs: FirestoreService
) : AttendanceRepository {

    private companion object {
        const val TAG = "DM/Attendance"
        const val ATTENDANCE_DAILY = "attendance_daily"
        val ATTENDANCE_ZONE: TimeZone = TimeZone.of("Asia/Jakarta")
    }

    // attendances (auto-id)
    override suspend fun checkIn(att: Attendance): AppResult<Unit> = try {
        val uid = fs.currentUserUidOrThrow()
        if (att.driverUid != uid) {
            DMLog.w(TAG, "checkIn() driverUid mismatch: payload=${att.driverUid} auth=$uid")
        }
        DMLog.i(TAG, "checkIn() for uid=$uid @${att.timestampMillis}")
        // Allowed keys: driverUid, timestampMillis, type, note, latitude, longitude, locationName (optional)
        val payload = mutableMapOf<String, Any>(
            "driverUid" to uid,
            "timestampMillis" to att.timestampMillis,
            "type" to "IN",
            "note" to (att.note ?: "IN")
        )
        att.latitude?.let { payload["latitude"] = it }
        att.longitude?.let { payload["longitude"] = it }
        att.locationName?.takeIf { it.isNotBlank() }?.let { payload["locationName"] = it }
        DMLog.i(TAG, "checkIn payload keys=${payload.keys}")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.addDocument("attendances", payload)
        }
        DMLog.d(TAG, "checkIn() success ✅")
        AppResult.success(Unit)
    } catch (t: TimeoutCancellationException) {
        DMLog.e(TAG, "checkIn() timed out: ${t.message}", t)
        AppResult.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "checkIn() failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun checkOut(
        timestampMillis: Long,
        locationName: String?,
        latitude: Double?,
        longitude: Double?
    ): AppResult<Unit> = try {
        // ✅ UID-first: must match Firestore rules (driverUid == request.auth.uid)
        val uid = fs.currentUserUidOrThrow()
        DMLog.i(TAG, "checkOut() for uid=$uid @$timestampMillis")

        val payload = mutableMapOf<String, Any>(
            "driverUid" to uid,
            "timestampMillis" to timestampMillis,
            "type" to "OUT",
            "note" to "OUT"
            // latitude/longitude optional; add if caller provides
        )
        locationName?.takeIf { it.isNotBlank() }?.let { payload["locationName"] = it }
        latitude?.let { payload["latitude"] = it }
        longitude?.let { payload["longitude"] = it }
        DMLog.i(TAG, "checkOut payload keys=${payload.keys}")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.addDocument("attendances", payload)
        }
        DMLog.d(TAG, "checkOut() success ✅")
        AppResult.success(Unit)
    } catch (t: TimeoutCancellationException) {
        DMLog.e(TAG, "checkOut() timed out: ${t.message}", t)
        AppResult.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "checkOut() failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun upsertTodayClockIn(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> = try {
        val uid = fs.currentUserUidOrThrow()
        if (driverUid.isNotBlank() && driverUid != uid) {
            DMLog.w(TAG, "upsertTodayClockIn() driverUid mismatch: payload=$driverUid auth=$uid")
        }
        val docId = attendanceDailyDocId(uid, epochDay)
        val impl = fs as? FirestoreServiceImpl
            ?: return AppResult.failure(IllegalStateException("FirestoreServiceImpl required"))

        val updated = withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            impl.runTransaction { tx ->
                val ref = impl.documentRef("$ATTENDANCE_DAILY/$docId")
                val snap = tx.get(ref)
                val existingIn = snap.getLong("clockInMillis")
                val existingOut = snap.getLong("clockOutMillis")
                if (existingIn != null) {
                    throw IllegalStateException("AlreadyClockedIn")
                }
                val payload = mutableMapOf<String, Any?>(
                    "driverUid" to uid,
                    "epochDay" to epochDay,
                    "clockInMillis" to nowMillis,
                    "updatedAtMillis" to nowMillis
                )
                if (snap.exists() && existingOut != null) {
                    payload["clockOutMillis"] = existingOut
                }
                tx.set(ref, payload, com.google.firebase.firestore.SetOptions.merge())
                TodayAttendance(
                    driverUid = uid,
                    epochDay = epochDay,
                    clockInMillis = nowMillis,
                    clockOutMillis = existingOut,
                    updatedAtMillis = nowMillis
                )
            }
        }
        AppResult.success(updated)
    } catch (t: TimeoutCancellationException) {
        DMLog.e(TAG, "upsertTodayClockIn() timed out: ${t.message}", t)
        AppResult.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "upsertTodayClockIn() failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun upsertTodayClockOut(
        driverUid: String,
        epochDay: Long,
        nowMillis: Long
    ): AppResult<TodayAttendance> = try {
        val uid = fs.currentUserUidOrThrow()
        if (driverUid.isNotBlank() && driverUid != uid) {
            DMLog.w(TAG, "upsertTodayClockOut() driverUid mismatch: payload=$driverUid auth=$uid")
        }
        val docId = attendanceDailyDocId(uid, epochDay)
        val impl = fs as? FirestoreServiceImpl
            ?: return AppResult.failure(IllegalStateException("FirestoreServiceImpl required"))

        val updated = withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            impl.runTransaction { tx ->
                val ref = impl.documentRef("$ATTENDANCE_DAILY/$docId")
                val snap = tx.get(ref)
                val existingIn = snap.getLong("clockInMillis")
                val existingOut = snap.getLong("clockOutMillis")
                if (existingIn == null) {
                    throw IllegalStateException("NotClockedIn")
                }
                if (existingOut != null) {
                    throw IllegalStateException("AlreadyClockedOut")
                }
                val payload = mapOf(
                    "driverUid" to uid,
                    "epochDay" to epochDay,
                    "clockOutMillis" to nowMillis,
                    "updatedAtMillis" to nowMillis
                )
                tx.set(ref, payload, com.google.firebase.firestore.SetOptions.merge())
                TodayAttendance(
                    driverUid = uid,
                    epochDay = epochDay,
                    clockInMillis = existingIn,
                    clockOutMillis = nowMillis,
                    updatedAtMillis = nowMillis
                )
            }
        }
        AppResult.success(updated)
    } catch (t: TimeoutCancellationException) {
        DMLog.e(TAG, "upsertTodayClockOut() timed out: ${t.message}", t)
        AppResult.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "upsertTodayClockOut() failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getTodayAttendance(
        driverUid: String,
        epochDay: Long
    ): AppResult<TodayAttendance?> = try {
        val uid = fs.currentUserUidOrThrow()
        if (driverUid.isNotBlank() && driverUid != uid) {
            DMLog.w(TAG, "getTodayAttendance() driverUid mismatch: payload=$driverUid auth=$uid")
        }
        val docId = attendanceDailyDocId(uid, epochDay)
        val raw = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap(ATTENDANCE_DAILY, docId)
        } ?: return AppResult.success(null)
        val clockIn = (raw["clockInMillis"] as? Number)?.toLong()
        val clockOut = (raw["clockOutMillis"] as? Number)?.toLong()
        val updatedAt = (raw["updatedAtMillis"] as? Number)?.toLong() ?: 0L
        AppResult.success(
            TodayAttendance(
                driverUid = uid,
                epochDay = epochDay,
                clockInMillis = clockIn,
                clockOutMillis = clockOut,
                updatedAtMillis = updatedAt
            )
        )
    } catch (t: TimeoutCancellationException) {
        DMLog.e(TAG, "getTodayAttendance() timed out: ${t.message}", t)
        AppResult.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getTodayAttendance() failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getAttendancesByEpochDayRange(
        driverUid: String,
        startEpochDay: Long,
        endEpochDay: Long
    ): AppResult<List<Attendance>> = try {
        val uid = fs.currentUserUidOrThrow()
        if (driverUid.isNotBlank() && driverUid != uid) {
            DMLog.w(TAG, "getAttendancesByEpochDayRange() driverUid mismatch: payload=$driverUid auth=$uid")
        }
        val startMillis = TimeUtil.parseHm("00:00", startEpochDay, ATTENDANCE_ZONE)
        val endMillisExclusive = TimeUtil.parseHm("00:00", endEpochDay + 1L, ATTENDANCE_ZONE)
        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "attendances",
                where = listOf(
                    "driverUid" to uid,
                    "timestampMillis>=" to startMillis,
                    "timestampMillis<" to endMillisExclusive
                ),
                orderBy = "timestampMillis",
                limit = 500
            )
        }
        val list = docs.mapNotNull { doc ->
            val raw = doc.data ?: return@mapNotNull null
            val ts = (raw["timestampMillis"] as? Number)?.toLong() ?: return@mapNotNull null
            val eventType = (raw["type"] as? String) ?: (raw["note"] as? String)
            Attendance(
                driverUid = (raw["driverUid"] as? String) ?: uid,
                timestampMillis = ts,
                latitude = (raw["latitude"] as? Number)?.toDouble(),
                longitude = (raw["longitude"] as? Number)?.toDouble(),
                note = eventType,
                locationName = raw["locationName"] as? String
            )
        }
        AppResult.success(list)
    } catch (t: TimeoutCancellationException) {
        DMLog.e(TAG, "getAttendancesByEpochDayRange() timed out: ${t.message}", t)
        AppResult.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getAttendancesByEpochDayRange() failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getToday(driverUid: String): AppResult<Attendance?> {
        return try {
            val latest = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                when (fs) {
                    is FirestoreServiceImpl -> fs.getLatestAttendanceForUid(driverUid)
                    else -> {
                        // Fallback path for non-Android implementations:
                        // align with primary query: driverUid filter + timestamp desc + limit.
                        val docs = fs.query(
                            collection = "attendances",
                            where = listOf("driverUid" to driverUid),
                            orderBy = "-timestampMillis",
                            limit = 50
                        )
                        docs.maxByOrNull {
                            val data = it.data ?: return@maxByOrNull Long.MIN_VALUE
                            (data["timestampMillis"] as? Number)?.toLong() ?: Long.MIN_VALUE
                        }
                    }
                }
            }
            val raw = latest?.data ?: return AppResult.success(null)

            val ts = (raw["timestampMillis"] as? Number)?.toLong() ?: return AppResult.success(null)
            val zone = TimeZone.of("Asia/Jakarta")
            val sameDay = TimeUtil.epochDayOf(ts, zone) == TimeUtil.todayEpochDay(zone)
            if (!sameDay) return AppResult.success(null)

            val mapped = Attendance(
                driverUid = (raw["driverUid"] as? String) ?: return AppResult.success(null),
                timestampMillis = ts,
                latitude = (raw["latitude"] as? Number)?.toDouble(),
                longitude = (raw["longitude"] as? Number)?.toDouble(),
                locationName = raw["locationName"] as? String?,
                note = when ((raw["type"] as? String)) {
                    "OUT" -> "OUT"
                    else  -> (raw["note"] as? String)
                }
            )
            DMLog.d(TAG, "getToday() result = ${if (mapped.note == "OUT") "OUT" else "IN"}")
            AppResult.success(mapped)
        } catch (t: TimeoutCancellationException) {
            DMLog.e(TAG, "getToday() timed out: ${t.message}", t)
            AppResult.failure(t)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "getToday() failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }
}
