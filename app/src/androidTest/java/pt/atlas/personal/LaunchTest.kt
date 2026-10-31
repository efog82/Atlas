package pt.atlas.personal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun opensWithMainNavigation() {
        compose.onNodeWithText("Atlas Personal").assertIsDisplayed()
        compose.onNodeWithText("Diário").assertIsDisplayed()
        compose.onNodeWithText("Estudos").assertIsDisplayed()
    }
}
