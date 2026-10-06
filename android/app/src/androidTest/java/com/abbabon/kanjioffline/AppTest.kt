package com.abbabon.kanjioffline

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanRecents() {
        runBlocking { Recents(context.recentsStore).clear() }
    }

    private fun processTextIntent(text: String) =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_PROCESS_TEXT
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        }

    @Test
    fun tappingARowOpensDetailAndRecordsRecent() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("search"), 10_000)
        compose.onNodeWithTag("search").performTextInput("water")
        compose.waitUntilAtLeastOneExists(hasTestTag("row:水"), 10_000)
        compose.onNodeWithTag("row:水").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("detail-kanji"), 5_000)
        compose.waitForIdle()

        // on a phone the list is hidden behind the detail: back returns to it (two-pane keeps both)
        if (compose.onAllNodesWithTag("search").fetchSemanticsNodes().isEmpty()) {
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntilAtLeastOneExists(hasTestTag("search"), 5_000)
        }
        compose.onNodeWithTag("search").performTextClearance()
        compose.waitUntilAtLeastOneExists(hasTestTag("recent-header"), 5_000)
        compose.onNodeWithTag("row:水").assertExists()
        scenario.close()
    }

    @Test
    fun processTextIntentSearchesForTheSelection() {
        val scenario = ActivityScenario.launch<MainActivity>(processTextIntent("水"))
        compose.waitUntilAtLeastOneExists(hasTestTag("row:水"), 10_000)
        compose.onNodeWithTag("search").assertTextContains("水")
        scenario.close()
    }

    // Review Focus 5: rotation re-delivers the launch intent; it must not overwrite what the user typed since
    @Test
    fun recreatingKeepsTheTypedQueryInsteadOfReapplyingTheSelection() {
        val scenario = ActivityScenario.launch<MainActivity>(processTextIntent("水"))
        compose.waitUntilAtLeastOneExists(hasTestTag("row:水"), 10_000)
        compose.onNodeWithTag("search").performTextReplacement("sun")
        compose.waitUntilAtLeastOneExists(hasTestTag("row:日"), 10_000)
        scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasTestTag("search"), 10_000)
        compose.onNodeWithTag("search").assertTextContains("sun")
        scenario.close()
    }
}
