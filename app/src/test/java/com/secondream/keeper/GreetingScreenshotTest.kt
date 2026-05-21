package com.secondream.keeper

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.secondream.keeper.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.secondream.keeper.ui.screens.NoteItemCard
import com.secondream.keeper.data.model.Note

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val mockNote = Note(
        id = 1,
        title = "Summer Vision Notes",
        content = "Capture every idea seamlessly with rich visual attachments, interactive list checkpoints, custom background color templates and settings.",
        colorHex = "#FFD180", // Yellow-orange Note Container
        isPinned = true,
        labels = "Work,Travel",
        checklistJson = "[{\"text\":\"Secure Naples travel tickets\",\"checked\":true},{\"text\":\"Test local SQLite Room DAO queries\",\"checked\":false}]"
    )

    composeTestRule.setContent { 
        MyApplicationTheme { 
            NoteItemCard(
                note = mockNote,
                themeIsDark = false,
                isGoogleConnected = false,
                onClick = {},
                onPinClick = {},
                onTrashClick = {},
                onArchiveClick = {}
            ) 
        } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
