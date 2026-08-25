package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.AroScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.HisabKhataTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AroScannerUnitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aroScreen_displaysTitleAnd3DScannerTile() {
        var scannerClicked = false

        composeTestRule.setContent {
            HisabKhataTheme {
                AroScreen(
                    onBackClick = {},
                    onScannerClick = { scannerClicked = true },
                    onHisabAiClick = {},
                    onQuickActionClick = {}
                )
            }
        }

        // Verify Aro Screen is displayed
        composeTestRule.onNodeWithTag("aro_screen").assertExists()

        // Verify 3D Scanner Tile is displayed with label "Scanner"
        composeTestRule.onNodeWithTag("scanner_tile").assertExists().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Scanner")[0].assertExists()

        // Perform click on 3D Scanner Tile
        composeTestRule.onNodeWithTag("scanner_tile").performClick()
        assertTrue("Scanner click handler should be executed", scannerClicked)
    }

    @Test
    fun scannerScreen_displaysCameraViewAndControlButtons() {
        var backClicked = false

        composeTestRule.setContent {
            HisabKhataTheme {
                ScannerScreen(
                    onBackClick = { backClicked = true }
                )
            }
        }

        // Verify Scanner Screen is displayed
        composeTestRule.onNodeWithTag("scanner_screen").assertExists()

        // Verify capture button and demo scan buttons exist
        composeTestRule.onNodeWithTag("scanner_capture_button").assertExists()
        composeTestRule.onNodeWithTag("scanner_demo_button").assertExists()
    }
}
