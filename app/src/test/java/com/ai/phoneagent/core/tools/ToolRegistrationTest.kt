package com.ai.phoneagent.core.tools

import android.content.Context
import com.ai.phoneagent.core.tools.extended.ExtendedAppMapping
import com.ai.phoneagent.core.tools.file.FileToolExecutor
import com.ai.phoneagent.core.tools.network.NetworkToolExecutor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Test

class ToolRegistrationTest {

    private val context = mockk<Context>(relaxed = true).also {
        every { it.applicationContext } returns it
    }
    private val handler = mockk<AIToolHandler>(relaxed = true)
    private val fileToolExecutor = mockk<FileToolExecutor>(relaxed = true)
    private val networkToolExecutor = mockk<NetworkToolExecutor>(relaxed = true)
    private val appPackageManager = mockk<AppPackageManager>(relaxed = true)
    private val extendedAppMapping = mockk<ExtendedAppMapping>(relaxed = true)

    private fun registration(): ToolRegistration =
        ToolRegistration(
            handler = handler,
            context = context,
            fileToolExecutor = fileToolExecutor,
            networkToolExecutor = networkToolExecutor,
            appPackageManager = appPackageManager,
            extendedAppMapping = extendedAppMapping,
        )

    @Test
    fun `registerAllTools repeated call is idempotent`() {
        val registration = registration()

        registration.registerAllTools()
        registration.registerAllTools()

        verify(exactly = 1) {
            handler.registerTool(
                name = "tap",
                dangerCheck = any(),
                descriptionGenerator = any(),
                executor = any(),
            )
        }
        verify(exactly = 1) {
            handler.registerTool(
                name = "compress",
                dangerCheck = any(),
                descriptionGenerator = any(),
                executor = any(),
            )
        }
        verify(exactly = 1) { appPackageManager.initializeCache(context) }
    }

    @Test
    fun `registration failure is preserved and partial tools are not registered again`() {
        val originalFailure = IllegalStateException("registration failed")
        every {
            handler.registerTool(
                name = "swipe",
                dangerCheck = any(),
                descriptionGenerator = any(),
                executor = any(),
            )
        } throws originalFailure
        val registration = registration()

        val first = runCatching { registration.registerAllTools() }.exceptionOrNull()
        val second = runCatching { registration.registerAllTools() }.exceptionOrNull()

        assertSame(originalFailure, first)
        assertSame(originalFailure, second)
        verify(exactly = 1) {
            handler.registerTool(
                name = "tap",
                dangerCheck = any(),
                descriptionGenerator = any(),
                executor = any(),
            )
        }
        verify(exactly = 1) {
            handler.registerTool(
                name = "swipe",
                dangerCheck = any(),
                descriptionGenerator = any(),
                executor = any(),
            )
        }
        verify(exactly = 0) {
            handler.registerTool(
                name = "screenshot",
                dangerCheck = any(),
                descriptionGenerator = any(),
                executor = any(),
            )
        }
    }
}
