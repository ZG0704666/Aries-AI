package com.ai.phoneagent.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ai.phoneagent.AutomationLiveNotification
import com.ai.phoneagent.VirtualScreenPreviewOverlay
import com.ai.phoneagent.core.cache.ScreenshotOverlayGuard
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StatefulSingletonScopeTest : KoinTest {

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Application>())
            modules(appModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `stateful production components resolve to one process instance`() {
        assertSame(get<AutomationLiveNotification>(), get<AutomationLiveNotification>())
        assertSame(get<VirtualScreenPreviewOverlay>(), get<VirtualScreenPreviewOverlay>())
        assertSame(get<ScreenshotOverlayGuard>(), get<ScreenshotOverlayGuard>())
    }
}
