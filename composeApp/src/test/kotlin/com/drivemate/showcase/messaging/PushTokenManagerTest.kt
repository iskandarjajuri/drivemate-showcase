package com.drivemate.showcase.messaging

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.sha256Hex
import com.drivemate.showcase.domain.repository.PushTokenRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeRepo(var failUnregister: Boolean = false, var failRegister: Boolean = false) : PushTokenRepository {
    val ops = mutableListOf<String>()
    override suspend fun register(uid: String, token: String, appVersion: String): AppResult<Unit> {
        ops += "register:$uid:${token.take(3)}"
        return if (failRegister) AppResult.failure(RuntimeException("denied")) else AppResult.success(Unit)
    }
    override suspend fun unregister(docId: String): AppResult<Unit> {
        ops += "unregister:${docId.take(6)}"
        return if (failUnregister) AppResult.failure(RuntimeException("denied")) else AppResult.success(Unit)
    }
}

private class FakeSource(
    var token: String = "tokA-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    val ops: MutableList<String>,
    var failDelete: Boolean = false
) : PushTokenSource {
    override suspend fun current(): String { ops += "current"; return token }
    override suspend fun delete() {
        ops += "deleteToken"
        if (failDelete) throw RuntimeException("offline")
    }
}

private class FakeStore : PushTokenStore {
    override var lastDocId: String? = null
    override var rotationDone: Boolean = false
    override var pendingDeleteToken: Boolean = false
}

class PushTokenManagerTest {
    private fun mgr(
        repo: FakeRepo,
        store: FakeStore = FakeStore(),
        source: FakeSource = FakeSource(ops = repo.ops),
        onSignedOut: () -> Unit = {}
    ) = PushTokenManager(repo, source, store, appVersion = "1.0", onSignedOut = onSignedOut)

    @Test
    fun afterSignOut_membersihkanNotifikasiDanRuteTertunda_walauDeleteTokenGagal() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { lastDocId = "c".repeat(64) }
        var cleaned = 0
        mgr(repo, store, FakeSource(ops = repo.ops, failDelete = true), onSignedOut = { cleaned++ }).afterSignOut()
        assertEquals(1, cleaned) // temuan keamanan #4: isi chat akun lama tak boleh tersisa untuk pengguna berikutnya
        assertTrue(store.pendingDeleteToken)
    }

    @Test
    fun pencabutanTertunda_dipanggilBersamaan_deleteTokenHanyaSekali() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { pendingDeleteToken = true }
        val m = mgr(repo, store)
        kotlinx.coroutines.coroutineScope {
            val a = launch { m.retryPendingRevocation() }
            val b = launch { m.retryPendingRevocation() }
            a.join(); b.join()
        }
        assertEquals(1, repo.ops.count { it == "deleteToken" }) // temuan Opus E-1: init + onSessionStarted tak boleh berlomba
    }

    @Test
    fun sesiBaru_denganPencabutanTertunda_tokenDibatalkanDulu_barudidaftarkan() = runTest {
        // Logout offline akun A meninggalkan token yang sama hidup; rules melarang B mengambil dokumen A -> token harus
        // diganti SEBELUM B mendaftar (temuan keamanan #1).
        val repo = FakeRepo(); val store = FakeStore().apply { rotationDone = true; pendingDeleteToken = true }
        mgr(repo, store).onSessionStarted("u2")
        assertEquals(listOf("deleteToken", "current", "register:u2:tok"), repo.ops)
        assertTrue(!store.pendingDeleteToken)
    }

    @Test
    fun sha256Hex_vektorTetap_huruf_kecil_64() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
        assertEquals(64, sha256Hex("apa saja").length)
    }

    @Test
    fun loginPertama_rotasiPaksaSekali_deleteToken_lalu_daftarTokenBaru() = runTest {
        val repo = FakeRepo(); val store = FakeStore()
        val m = mgr(repo, store)
        m.onSessionStarted("u1")
        // build baru: token lama (yang mungkin terekspos di dokumen users) dibatalkan sekali, lalu token baru didaftarkan
        assertEquals(listOf("deleteToken", "current", "register:u1:tok"), repo.ops)
        assertTrue(store.rotationDone)
        assertEquals(sha256Hex("tokA-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), store.lastDocId)
    }

    @Test
    fun loginBerikutnya_tanpaRotasi_hanyaDaftar() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { rotationDone = true; lastDocId = sha256Hex("tokA-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") }
        mgr(repo, store).onSessionStarted("u1")
        assertEquals(listOf("current", "register:u1:tok"), repo.ops)
    }

    @Test
    fun tokenBerotasi_dokumenLamaDihapus() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { rotationDone = true; lastDocId = "a".repeat(64) }
        mgr(repo, store).onSessionStarted("u1")
        assertTrue(repo.ops.contains("unregister:aaaaaa"))
        assertEquals(sha256Hex("tokA-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), store.lastDocId)
    }

    @Test
    fun logout_urutanWajib_hapusDokumen_signOut_lalu_deleteToken() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { rotationDone = true; lastDocId = "b".repeat(64) }
        val m = mgr(repo, store)
        val order = mutableListOf<String>()
        m.beforeSignOut(); order += repo.ops; order += "SIGN_OUT"
        repo.ops.clear(); m.afterSignOut(); order += repo.ops
        assertEquals(listOf("unregister:bbbbbb", "SIGN_OUT", "deleteToken"), order)
        assertEquals(null, store.lastDocId)
    }

    @Test
    fun gagalHapusDokumen_tidakMenghalangiLogout_danTokenTetapDirotasi() = runTest {
        val repo = FakeRepo(failUnregister = true); val store = FakeStore().apply { rotationDone = true; lastDocId = "c".repeat(64) }
        val m = mgr(repo, store)
        m.beforeSignOut() // tidak melempar
        m.afterSignOut()
        assertTrue(repo.ops.contains("deleteToken"))
    }

    @Test
    fun sesiTakSah_afterSignOutTanpaBeforeSignOut_tetapDeleteToken_idempoten() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { rotationDone = true }
        val m = mgr(repo, store)
        m.afterSignOut(); m.afterSignOut()
        assertEquals(2, repo.ops.count { it == "deleteToken" })
    }

    @Test
    fun onNewToken_mendaftarkanDenganUidSaatMasihLogin() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { rotationDone = true }
        mgr(repo, store).onNewToken("u1", "tokNEW-" + "z".repeat(60))
        assertEquals(listOf("register:u1:tok"), repo.ops)
        assertEquals(sha256Hex("tokNEW-" + "z".repeat(60)), store.lastDocId)
    }

    @Test
    fun gagalDaftar_tidakMelempar_danTidakMenyimpanDokumenSebagaiTerdaftar() = runTest {
        val repo = FakeRepo(failRegister = true); val store = FakeStore().apply { rotationDone = true }
        mgr(repo, store).onSessionStarted("u1")
        assertEquals(null, store.lastDocId)
    }

    @Test
    fun deleteTokenGagalSaatLogout_ditandaiTertunda_lalu_diulangDiStartBerikutnya() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { rotationDone = true }
        val source = FakeSource(ops = repo.ops, failDelete = true)
        val m = PushTokenManager(repo, source, store, appVersion = "1.0")
        m.afterSignOut() // offline: gagal
        assertTrue(store.pendingDeleteToken)
        source.failDelete = false
        m.retryPendingRevocation() // start aplikasi berikutnya
        assertEquals(false, store.pendingDeleteToken)
        assertEquals(2, repo.ops.count { it == "deleteToken" })
    }

    @Test
    fun deleteTokenBerhasil_tidakMeninggalkanTandaTertunda_danRetryTanpaTandaTidakMelakukanApaPun() = runTest {
        val repo = FakeRepo(); val store = FakeStore().apply { rotationDone = true }
        val m = mgr(repo, store)
        m.afterSignOut()
        assertEquals(false, store.pendingDeleteToken)
        repo.ops.clear()
        m.retryPendingRevocation()
        assertEquals(emptyList(), repo.ops)
    }

    @Test
    fun rotasiPaksaGagalOffline_tetapMendaftarkanToken_danDiulangDiStartBerikutnya() = runTest {
        val repo = FakeRepo(); val store = FakeStore()
        val source = FakeSource(ops = repo.ops, failDelete = true)
        PushTokenManager(repo, source, store, appVersion = "1.0").onSessionStarted("u1")
        assertTrue(repo.ops.any { it.startsWith("register:u1") }) // pendaftaran tidak dilewati
        assertEquals(false, store.rotationDone)                   // rotasi diulang pada sesi berikutnya
    }
}
