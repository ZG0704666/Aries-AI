package com.ai.phoneagent

import com.ai.phoneagent.vdiso.ShizukuVirtualDisplayEngine
import io.mockk.mockk
import kotlin.test.assertFailsWith
import org.junit.Test

class VirtualDisplayControllerTest {

    @Test
    fun `initialization is idempotent for same engine and rejects a second instance`() {
        val engine = mockk<ShizukuVirtualDisplayEngine>(relaxed = true)
        VirtualDisplayController.initialize(engine)
        VirtualDisplayController.initialize(engine)

        assertFailsWith<IllegalStateException> {
            VirtualDisplayController.initialize(mockk(relaxed = true))
        }
    }
}
