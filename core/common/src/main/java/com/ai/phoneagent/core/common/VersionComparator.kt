package com.ai.phoneagent.core.common

object VersionComparator {
    private val numberRegex = Regex("""\d+""")

    private data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val build: Int,
    )

    private fun parse(v: String): ParsedVersion {
        val normalized =
            v.trim()
                .removePrefix("v")
                .removePrefix("V")

        val plusIdx = normalized.indexOf('+')
        val base = if (plusIdx >= 0) normalized.substring(0, plusIdx) else normalized
        val buildPart = if (plusIdx >= 0) normalized.substring(plusIdx + 1) else ""

        val baseNumbers = numberRegex.findAll(base).map { it.value.toIntOrNull() ?: 0 }.toList()
        val build =
            numberRegex.find(buildPart)?.value?.toIntOrNull()
                ?: baseNumbers.getOrNull(3)
                ?: 0

        val major = baseNumbers.getOrNull(0) ?: 0
        val minor = baseNumbers.getOrNull(1) ?: 0
        val patch = baseNumbers.getOrNull(2) ?: 0

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
