package com.example.hisab.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HisabFeedbackTest {
    @Test
    fun repeatedFeedbackIsCoalescedAndNewFeedbackReplacesIt() = runTest {
        val state = SnackbarHostState()
        launch { state.showFeedback("Saved", FeedbackKind.SUCCESS) }
        runCurrent()
        launch { state.showFeedback("Saved", FeedbackKind.SUCCESS) }
        runCurrent()
        assertEquals("Saved", state.currentSnackbarData?.visuals?.message)
        assertEquals(SnackbarDuration.Short, state.currentSnackbarData?.visuals?.duration)

        launch { state.showFeedback("Please retry", FeedbackKind.WARNING) }
        runCurrent()
        assertEquals("Please retry", state.currentSnackbarData?.visuals?.message)
        assertEquals(SnackbarDuration.Long, state.currentSnackbarData?.visuals?.duration)
        state.currentSnackbarData?.dismiss()
        runCurrent()
        assertNull(state.currentSnackbarData)
    }
}
