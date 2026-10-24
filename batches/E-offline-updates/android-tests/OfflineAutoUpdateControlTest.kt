package com.drivemate.app.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.drivemate.app.data.OfflineAutoUpdates
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineAutoUpdateControlTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var ctx: Context
    @Before fun prepare() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("offline_auto_updates", 0).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }
    @Test fun switchShowsSavedOffStateWithoutCancellationMessage() {
        compose.setContent { MaterialTheme { OfflineAutoUpdateControl() } }
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.onNode(isToggleable()).performClick().assertIsOff()
        compose.runOnIdle { assertFalse(OfflineAutoUpdates.enabled(ctx)) }
        compose.onAllNodesWithText("cancel", substring = true, ignoreCase = true).assertCountEquals(0)
    }
    @Test fun regionSearchUpdatesAsUserTypesAndKeepsSizes() {
        compose.setContent { MaterialTheme {
            OfflineRegionSearchControl { query ->
                listOf("Alpha County" to 100_000_000L, "Beta County" to 200_000_000L)
                    .filter { it.first.contains(query, ignoreCase = true) }
            }
        } }
        compose.onNodeWithText("Search regions").performTextInput("PHA")
        compose.onNodeWithText("Alpha County · 100 MB").assertIsDisplayed()
        compose.onAllNodesWithText("Beta County", substring = true).assertCountEquals(0)
    }
    @Test fun existingApprovalAppearsWhenOpeningSettings() {
        OfflineAutoUpdates.setEnabled(ctx, true)
        compose.setContent { MaterialTheme { OfflineAutoUpdateControl() } }
        compose.onNode(isToggleable()).assertIsOn()
    }
}
