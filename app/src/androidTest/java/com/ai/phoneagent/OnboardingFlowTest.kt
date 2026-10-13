package com.ai.phoneagent

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.ai.phoneagent.core.designsystem.theme.AriesMaterialTheme
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.ui.onboarding.OnboardingFlowMode
import com.ai.phoneagent.ui.onboarding.OnboardingScreen
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class OnboardingFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun welcomeContinuesToAgreementWithoutChangingUserData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = GlobalContext.get().get<AppPreferencesRepository>()
        compose.setContent {
            AriesMaterialTheme {
                OnboardingScreen(OnboardingFlowMode.ONBOARDING, preferences, {}, {})
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.ui_v2_back)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.onboarding_welcome_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.ui_v2_agreement_heading)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.ui_v2_back)).performClick()
        compose.onNodeWithText(context.getString(R.string.onboarding_welcome_action)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.ui_v2_back)).assertDoesNotExist()
    }
}
