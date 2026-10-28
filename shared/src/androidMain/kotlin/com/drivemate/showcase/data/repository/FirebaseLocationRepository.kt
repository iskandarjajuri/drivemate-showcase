package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.LocationWritePayloadResult
import com.drivemate.showcase.core.model.buildLocationWritePayload
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.speedKphFromMps
import com.drivemate.showcase.core.util.speedMpsFromKph
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.repository.LocationRepository
import com.drivemate.showcase.data.firestore.FsDoc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout

class FirebaseLocationRepository(
    private val fs: FirestoreService
) : LocationRepository {

    private companion object { const val TAG = "DM/LocationWrite" }

    private fun mapDocToLocation(d: FsDoc): Location? {
        val m = d.data ?: return null
        val uid = (m["driverUid"] as? String) ?: return null
        val lat = (m["latitude"] as? Number)?.toDouble()
        val lon = (m["longitude"] as? Number)?.toDouble()
        val speedMps = (m["speedMps"] as? Number)?.toDouble()
        val speedKph = (m["speedKph"] as? Number)?.toDouble()
        val resolvedMps = speedMps ?: speedMpsFromKph(speedKph)
        val resolvedKph = speedKph ?: speedKphFromMps(speedMps)
        val hdg = (m["headingDeg"] as? Number)?.toDouble()
        val acc = (m["accuracyM"] as? Number)?.toDouble()
        val ts  = (m["updatedAtMillis"] as? Number)?.toLong() ?: 0L
        val name = m["locationName"] as? String?

        return Location(
            driverUid = uid,
            latitude = lat,
            longitude = lon,
            speedMps = resolvedMps,
            speedKph = resolvedKph,
            headingDeg = hdg,
            accuracyM = acc,
            updatedAtMillis = ts,
            locationName = name
        )
    }

    override suspend fun updateDriverLocation(loc: Location): AppResult<Unit> = try {
        val uid = fs.currentUserUidOrThrow()
        // Bentuk dokumen + clamp timestamp + turunan kecepatan ada di commonMain supaya iOS
        // menulis dokumen yang IDENTIK. `firestore.rules` memvalidasi bentuk ini ketat, jadi
        // dua penyusun payload yang terpisah = dua kesempatan ditolak PERMISSION_DENIED
        // karena satu field yang salah.
        val built = buildLocationWritePayload(
            location = loc,
            authUid = uid,
            nowMillis = com.drivemate.showcase.core.util.TimeUtil.nowEpochMillis()
        )
        if (built is LocationWritePayloadResult.Rejected) {
            return AppResult.failure(IllegalArgumentException("invalid location: ${built.error}"))
        }
        val ready = built as LocationWritePayloadResult.Ready
        val path = ready.path
        val payload = ready.fields

        DMLog.i(TAG, "write locations payload keys=${payload.keys} path=$path")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.setDocument(path, payload, merge = true)
        }
        DMLog.d(TAG, "write locations success ✅ path=$path")
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "write locations failed: ${t.message} (Explain with AI)", t)
        AppResult.failure(t)
    }

    override suspend fun clearDriverLocation(): AppResult<Unit> = try {
        val uid = fs.currentUserUidOrThrow()
        val path = "locations/$uid"
        DMLog.i(TAG, "delete locations snapshot path=$path")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.deleteDocument(path)
        }
        DMLog.d(TAG, "delete locations snapshot success ✅ path=$path")
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "delete locations snapshot failed: ${t.message} (Explain with AI)", t)
        AppResult.failure(t)
    }

    override fun observeAllDrivers(): Flow<AppResult<List<Location>>> = callbackFlow {
        DMLog.i("DM/LocationObserve", "observeAllDrivers(): attaching locations listener (ordered by updatedAtMillis DESC)")
        val reg = fs.listenCollectionOrderBy(
            collection = "locations",
            orderByField = "updatedAtMillis",
            orderDesc = true,
            // TODO_VERIFY: listener is intentionally unbounded until admin map paging/capping is designed.
            limit = null,
            onChange = { docs ->
                val list = docs.mapNotNull { mapDocToLocation(it) }
                DMLog.d("DM/LocationObserve", "locations snapshot → ${list.size} drivers")
                trySend(AppResult.Success(list)).isSuccess
            },
            onError = { err ->
                DMLog.e("DM/LocationObserve", "locations listener error: ${err.message} (Explain with AI)", err)
                trySend(AppResult.Failure(err)).isSuccess
            }
        )
        awaitClose {
            DMLog.i("DM/LocationObserve", "observeAllDrivers(): detaching listener")
            reg.remove()
        }
    }
}
