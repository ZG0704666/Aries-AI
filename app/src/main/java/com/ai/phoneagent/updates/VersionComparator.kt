package com.ai.phoneagent.updates

object VersionComparator {
    private data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val build: Int,
    )

    private fun parse(v: String): ParsedVersion {
        val s0 = v.trim().removePrefix("v")
        val plusIdx = s0.indexOf('+')
        val base = if (plusIdx >= 0) s0.substring(0, plusIdx) else s0
        val build = if (plusIdx >= 0) s0.substring(plusIdx + 1).toIntOrNull() ?: 0 else 0

        val parts = base.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

        return ParsedVersion(major = major, minor = minor, patch = patch, build = build)
    }

    fun compare(v1: String, v2: String): Int {
        val p1 = parse(v1)
        val p2 = parse(v2)

        if (p1.major != p2.major) return p1.major.compareTo(p2.major)
        if (p1.minor != p2.minor) return p1.minor.compareTo(p2.minor)
        if (p1.patch != p2.patch) return p1.patch.compareTo(p2.patch)
        return p1.build.compareTo(p2.build)
    }
}
