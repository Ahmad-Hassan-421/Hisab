package com.example.hisab.features.settings.presentation

import com.example.hisab.features.settings.data.AppUpdateResponse
import com.example.hisab.features.settings.domain.UpdateRepository
import com.example.hisab.util.FailureReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UpdateCoordinatorTest {
    private val release = AppUpdateResponse(5, "1.2.2", "Fixes",
        "https://github.com/Ahmad-Hassan-421/Hisab/releases/download/v1.2.2/hisab-v1.2.2.apk", 4096, "a".repeat(64))

    @Test fun coalescesStartupAndPushAndOnlyAutomaticallyPromptsOnce() = runTest {
        val response = CompletableDeferred<AppUpdateResponse?>()
        var calls = 0
        val repository = object : UpdateRepository { override suspend fun getLatestRelease(): AppUpdateResponse? { calls++; return response.await() } }
        val coordinator = UpdateCoordinator(repository, FailureReporter({ true }, {}), backgroundScope, 4, null, true)
        coordinator.check(UpdateTrigger.AUTOMATIC); runCurrent()
        assertTrue(coordinator.state.value.checking); assertFalse(coordinator.state.value.visible)
        coordinator.check(UpdateTrigger.PUSH); assertTrue(coordinator.state.value.visible)
        response.complete(release); runCurrent()
        assertEquals(1, calls); assertEquals(5, coordinator.state.value.release?.versionCode)
        coordinator.dismiss(); coordinator.check(UpdateTrigger.AUTOMATIC); runCurrent()
        assertEquals(1, calls); assertFalse(coordinator.state.value.visible)
        coordinator.check(UpdateTrigger.MANUAL); runCurrent()
        assertEquals(2, calls); assertTrue(coordinator.state.value.visible)
    }

    @Test fun silentFailureCanBeRetriedAndDismissedChecksDoNotReopen() = runTest {
        var fail = true
        val pending = CompletableDeferred<AppUpdateResponse?>()
        val repository = object : UpdateRepository { override suspend fun getLatestRelease(): AppUpdateResponse? {
            if (fail) throw IOException(); return pending.await()
        } }
        val coordinator = UpdateCoordinator(repository, FailureReporter({ false }, {}), backgroundScope, 4, null, true)
        coordinator.check(UpdateTrigger.AUTOMATIC); runCurrent()
        assertFalse(coordinator.state.value.visible); assertNotNull(coordinator.state.value.error)
        fail = false; coordinator.check(UpdateTrigger.MANUAL); runCurrent(); coordinator.dismiss()
        pending.complete(release); runCurrent()
        assertFalse(coordinator.state.value.visible); assertNotNull(coordinator.state.value.release)
    }

    @Test fun minimumSupportedCodeRequiresUpdateWithoutLosingMetadata() = runTest {
        val repository = object : UpdateRepository { override suspend fun getLatestRelease() = release.copy(
            releaseDate = "2026-11-28T00:00:00Z", minimumSupportedVersionCode = 5) }
        val coordinator = UpdateCoordinator(repository, FailureReporter({ true }, {}), backgroundScope, 4, null, true)
        coordinator.check(UpdateTrigger.AUTOMATIC); runCurrent(); coordinator.dismiss()
        assertTrue(coordinator.state.value.visible)
        assertEquals("2026-11-28T00:00:00Z", coordinator.state.value.release?.releaseDate)
        assertTrue(checkNotNull(coordinator.state.value.release).isRequiredFor(4))
    }
}
