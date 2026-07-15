package com.ai.phoneagent.di

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyInjectionContractTest {

    @Test
    fun `application resolves startup dependencies from returned Koin container`() {
        val source = sourceFile("com/ai/phoneagent/AriesAgentApp.kt").readText()

        assertFalse("Application startup must not use Koin GlobalContext", source.contains("org.koin.core.context.GlobalContext"))
        val startKoinIndex = source.indexOf("val koin = startKoin")
        assertTrue("Application must retain the Koin instance returned by startKoin", startKoinIndex >= 0)

        listOf(
            "VirtualDisplayController.initialize",
            "koin.get<AutomationLiveNotification>()",
            "koin.get<ImageLoader>()",
            "koin.get<TelemetryHeartbeatManager>()",
        ).forEach { initialization ->
            assertTrue(
                "$initialization must run after Koin startup",
                source.indexOf(initialization) > startKoinIndex,
            )
        }
    }

    @Test
    fun `production DI has explicit stateful singleton bindings and no fallback instances`() {
        val module = sourceFile("com/ai/phoneagent/di/AppModule.kt").readText()
        listOf(
            "single { ShizukuVirtualDisplayEngine() }",
            "single { VirtualScreenPreviewOverlay(get()) }",
            "single { AutomationLiveNotification(androidContext()) }",
            "single { AutomationOverlay(get()) }",
            "single { UIAutomationProgressOverlay(androidContext()) }",
            "single { ScreenshotOverlayGuard(get(), get()) }",
            "ToolRegistration(",
        ).forEach { binding ->
            assertTrue("Missing production singleton binding: $binding", module.contains(binding))
        }

        val mainSourceRoot = sourceFile("com/ai/phoneagent/AriesAgentApp.kt").parentFile
        checkNotNull(mainSourceRoot)
        val fallbackFiles = mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readText().contains("fallbackInstance") }
            .map { it.relativeTo(mainSourceRoot).path }
            .toList()
        assertTrue("Production fallback instances remain: $fallbackFiles", fallbackFiles.isEmpty())
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java", relativePath),
            File("app/src/main/java", relativePath),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Source file not found: $relativePath")
    }
}
