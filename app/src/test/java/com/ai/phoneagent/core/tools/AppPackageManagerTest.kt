package com.ai.phoneagent.core.tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.ai.phoneagent.core.cache.MillisClock
import com.ai.phoneagent.core.tools.extended.ExtendedAppMapping
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AppPackageManagerTest {
    private class TestClock(initial: Long = 1L) : MillisClock {
        private val value = AtomicLong(initial)
        override fun nowMillis(): Long = value.get()
        fun advanceBy(delta: Long) = value.addAndGet(delta)
    }

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var mapping: ExtendedAppMapping
    private lateinit var clock: TestClock

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        packageManager = mockk()
        context = mockk()
        every { context.applicationContext } returns context
        every { context.packageManager } returns packageManager
        mapping = mockk()
        every { mapping.getAllMappings() } returns mapOf("extended app" to "com.example.extended")
        every { packageManager.getApplicationLabel(any()) } answers {
            "Label ${firstArg<ApplicationInfo>().packageName.substringAfterLast('.')}"
        }
        clock = TestClock()
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    @Test
    fun `snapshot retains more than 256 applications and all indexes`() {
        every { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) } returns
            applications(320, "com.example.app")
        val manager = AppPackageManager(mapping, clock)

        manager.initializeCache(context)

        assertEquals(320, manager.getAllInstalledApps().size)
        assertEquals("com.example.app319", manager.resolvePackageName("Label app319"))
        assertEquals("Label app319", manager.getAppName("com.example.app319"))
        assertEquals("com.example.extended", manager.resolvePackageName("extended app"))
        assertEquals(clock.nowMillis(), manager.getStats()["lastUpdateTime"])
    }

    @Test
    fun `snapshot refreshes only after validity boundary`() {
        every { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) } returns
            applications(1, "com.example.first") andThen applications(1, "com.example.second")
        val manager = AppPackageManager(mapping, clock)

        manager.initializeCache(context)
        clock.advanceBy(299_999L)
        manager.initializeCache(context)
        verify(exactly = 1) { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) }

        clock.advanceBy(1L)
        manager.initializeCache(context)

        verify(exactly = 2) { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) }
        assertEquals("com.example.second0", manager.getAllInstalledApps().single().first)
        assertEquals(clock.nowMillis(), manager.getStats()["lastUpdateTime"])
    }

    @Test
    fun `resolved package cache does not cross snapshot generations`() {
        val first = applications(1, "com.example.first")
        val second = applications(1, "com.example.second")
        every { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) } returns
            first andThen second
        every { packageManager.getApplicationLabel(any()) } returns "Shared Label"
        val manager = AppPackageManager(mapping, clock)

        manager.initializeCache(context)
        assertEquals("com.example.first0", manager.resolvePackageName("Shared Label"))

        clock.advanceBy(300_000L)
        manager.initializeCache(context)

        assertEquals("com.example.second0", manager.resolvePackageName("Shared Label"))
    }

    @Test
    fun `readers see old complete snapshot until refresh is published`() {
        val refreshEntered = CountDownLatch(1)
        val allowRefresh = CountDownLatch(1)
        val invocation = AtomicInteger()
        every { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) } answers {
            if (invocation.incrementAndGet() == 1) {
                applications(2, "com.example.old")
            } else {
                refreshEntered.countDown()
                assertTrue(allowRefresh.await(10, TimeUnit.SECONDS))
                applications(3, "com.example.new")
            }
        }
        val manager = AppPackageManager(mapping, clock)
        manager.initializeCache(context)
        clock.advanceBy(300_000L)

        val executor = Executors.newSingleThreadExecutor()
        val refresh = executor.submit { manager.initializeCache(context) }
        assertTrue(refreshEntered.await(10, TimeUnit.SECONDS))

        assertEquals(
            listOf("com.example.old0", "com.example.old1"),
            manager.getAllInstalledApps().map { it.first },
        )
        assertEquals("com.example.old1", manager.resolvePackageName("Label old1"))

        allowRefresh.countDown()
        refresh.get(10, TimeUnit.SECONDS)
        executor.shutdownNow()
        assertEquals(3, manager.getAllInstalledApps().size)
        assertEquals("com.example.new2", manager.resolvePackageName("Label new2"))
    }

    @Test
    fun `failed refresh preserves previous snapshot and timestamp`() {
        every { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) } returns
            applications(2, "com.example.stable") andThenThrows IllegalStateException("query failed")
        val manager = AppPackageManager(mapping, clock)
        manager.initializeCache(context)
        val originalTimestamp = manager.getStats()["lastUpdateTime"]
        clock.advanceBy(300_000L)

        manager.initializeCache(context)

        assertEquals(
            listOf("com.example.stable0", "com.example.stable1"),
            manager.getAllInstalledApps().map { it.first },
        )
        assertEquals(originalTimestamp, manager.getStats()["lastUpdateTime"])
        assertEquals("com.example.stable1", manager.resolvePackageName("Label stable1"))
    }

    private fun applications(count: Int, prefix: String): List<ApplicationInfo> =
        List(count) { index ->
            ApplicationInfo().apply {
                packageName = "$prefix$index"
                flags = 0
            }
        }
}
