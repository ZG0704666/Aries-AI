package com.ai.phoneagent.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun compare_handlesUppercaseReleaseTagAgainstCurrentAlphaVersion() {
        assertTrue(VersionComparator.compare("V1.4.3", "v1.4.2-xyla.alpha") > 0)
    }

    @Test
    fun compare_ignoresSuffixesAndUsesNumericSegments() {
        assertEquals(0, VersionComparator.compare("v1.4.2-release", "1.4.2"))
    }
}