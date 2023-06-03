package com.fsryan.cicd

import kotlin.math.pow

interface SemanticVersion {
    val major: Int
    val minor: Int
    val patch: Int
    val dashQualifier: String
    val buildNumber: Int
    val code: Int
    val name: String

    fun versionString(
        includeBuildNumber: Boolean = true,
        includeDashQualifier: Boolean = true
    ): String = when (includeBuildNumber) {
        true -> name
        false -> when (includeDashQualifier) {
            true -> when (dashQualifier.isNotBlank()) {
                true -> "$major.$minor.$patch-$dashQualifier"
                false -> "$major.$minor.$patch"
            }
            false -> "$major.$minor.$patch"
        }
    }

    companion object {
        
        const val DEFAULT_MAJOR_DECIMAL_PLACE = 7
        const val DEFAULT_MINOR_DECIMAL_PLACE = 5
        const val DEFAULT_PATCH_DECIMAL_PLACE = 3
        
        fun create(
            major: Int,
            minor: Int,
            patch: Int,
            buildNumber: Int,
            dashQualifier: String = "build",
            majorDecimalPlace: Int = DEFAULT_MAJOR_DECIMAL_PLACE,
            minorDecimalPlace: Int = DEFAULT_MINOR_DECIMAL_PLACE,
            patchDecimalPlace: Int = DEFAULT_PATCH_DECIMAL_PLACE
        ): SemanticVersion = SemanticVersionImpl(
            major = major,
            minor = minor,
            patch = patch,
            dashQualifier = dashQualifier,
            buildNumber = buildNumber,
            majorDecimalPlace = majorDecimalPlace,
            minorDecimalPlace = minorDecimalPlace,
            patchDecimalPlace = patchDecimalPlace
        )

        fun parse(
            fromString: String,
            majorDecimalPlace: Int = DEFAULT_MAJOR_DECIMAL_PLACE,
            minorDecimalPlace: Int = DEFAULT_MINOR_DECIMAL_PLACE,
            patchDecimalPlace: Int = DEFAULT_PATCH_DECIMAL_PLACE
        ): SemanticVersion {
            var minor = 0
            var patch = 0
            val delim = '.'
            val dashQualifierDelimIdx = fromString.indexOf('-')
            val versionStr = when (dashQualifierDelimIdx) {
                -1 -> fromString
                else -> fromString.subSequence(0, dashQualifierDelimIdx).toString()
            }
            val dashQualifierStr = when (dashQualifierDelimIdx) {
                -1 -> null
                else -> fromString.subSequence(dashQualifierDelimIdx + 1, fromString.length).toString()
            }
            val dashQualifier = dashQualifierStr?.let {
                val buildNumberDelimIdx = it.indexOf('.')
                when (buildNumberDelimIdx < 0) {
                    true -> "build"
                    false -> it.substring(0, buildNumberDelimIdx)
                }
            } ?: "build"
            val buildNumber = dashQualifierStr?.let {
                val buildNumberDelimIdx = it.indexOf('.')
                when (buildNumberDelimIdx < 0 || buildNumberDelimIdx >= it.length - 1) {
                    true -> 0
                    false -> it.substring(buildNumberDelimIdx + 1).toIntOrNull() ?: 0
                }
            } ?: 0
            var delimIdx = versionStr.indexOf(delim)
            val major = when (delimIdx) {
                -1 -> versionStr.toInt()
                else -> versionStr.substring(0, delimIdx).toInt()
            }
            if (delimIdx > 0) {
                var nextDelimIdx = versionStr.indexOf(delim, delimIdx + 1)
                minor = when (nextDelimIdx) {
                    -1 -> versionStr.substring(delimIdx + 1).toInt()
                    else -> versionStr.substring(delimIdx + 1, nextDelimIdx).toInt()
                }

                delimIdx = nextDelimIdx
                if (delimIdx > 0) {
                    val patchStr = versionStr.substring(delimIdx + 1)
                        .replace(regex = Regex("[^0-9].*"), replacement = "")
                    patch = patchStr.toInt()
                }
            }

            if (major > 99) {
                throw IllegalStateException("Major version greater than 99 not supported")
            }

            throwIfVersionTooHigh(major, "Major", 2)
            throwIfVersionTooHigh(minor, "Minor", majorDecimalPlace - minorDecimalPlace)
            throwIfVersionTooHigh(patch, "Patch", minorDecimalPlace - patchDecimalPlace)
            throwIfVersionTooHigh(buildNumber, "Build Number", patchDecimalPlace - 1)
            return create(
                major = major,
                minor = minor,
                patch = patch,
                buildNumber = buildNumber,
                dashQualifier = dashQualifier,
                majorDecimalPlace = majorDecimalPlace,
                minorDecimalPlace = minorDecimalPlace,
                patchDecimalPlace = patchDecimalPlace
            )
        }

        private fun throwIfVersionTooHigh(versionNumber: Int, slotName: String, numDecimalPlaces: Int) {
            val nonInclusiveLimit = 10.0.pow(numDecimalPlaces).toInt()
            if (versionNumber >= nonInclusiveLimit) {
                throw IllegalStateException("$slotName version greater than ${nonInclusiveLimit - 1} not supported")
            }
        }
    }
}

/**
 * The semantic verisoning standard is much more involved than this. I picked a
 * bare-bones implementation that made sense for where we are right now. We can
 * get much more involved with this later.
 */
private data class SemanticVersionImpl(
    override val major: Int,
    override val minor: Int,
    override val patch: Int,
    override val buildNumber: Int,
    override val dashQualifier: String,
    private val majorDecimalPlace: Int = SemanticVersion.DEFAULT_MAJOR_DECIMAL_PLACE,
    private val minorDecimalPlace: Int = SemanticVersion.DEFAULT_MINOR_DECIMAL_PLACE,
    private val patchDecimalPlace: Int = SemanticVersion.DEFAULT_PATCH_DECIMAL_PLACE
): SemanticVersion {

    override fun toString() = "$name ($code)"

    override val name: String
        get() = when (isIntermediateForm()) {
            true -> "$major.$minor.$patch-$dashQualifier.$buildNumber"
            false -> "$major.$minor.$patch.$buildNumber"
        }

    override val code: Int
        get() = major * majorMultiplier + minor * minorMultiplier + patch * patchMultiplier + buildNumber

    private val majorMultiplier: Int
        get() = multiplier("Major", majorDecimalPlace)

    private val minorMultiplier: Int
        get() = multiplier("Minor", minorDecimalPlace)

    private val patchMultiplier: Int
        get() = multiplier("Patch", patchDecimalPlace)

    private fun multiplier(slotName: String, decimalPlace: Int): Int {
        if (decimalPlace < 1) {
            throw IllegalArgumentException("trying to find multiplier for version part '$slotName'")
        }
        return 10.0.pow(decimalPlace - 1).toInt()
    }
}

// TODO: build number
fun SemanticVersion.nextBuildNumber(
    increment: Int = 1,
    majorDecimalPlace: Int = SemanticVersion.DEFAULT_MAJOR_DECIMAL_PLACE,
    minorDecimalPlace: Int = SemanticVersion.DEFAULT_MINOR_DECIMAL_PLACE,
    patchDecimalPlace: Int = SemanticVersion.DEFAULT_PATCH_DECIMAL_PLACE
): SemanticVersion = withUpdated(
    buildNumberIncrement = increment,
    majorDecimalPlace = majorDecimalPlace,
    minorDecimalPlace = minorDecimalPlace,
    patchDecimalPlace = patchDecimalPlace
)
fun SemanticVersion.nextPatch(
    increment: Int = 1,
    majorDecimalPlace: Int = SemanticVersion.DEFAULT_MAJOR_DECIMAL_PLACE,
    minorDecimalPlace: Int = SemanticVersion.DEFAULT_MINOR_DECIMAL_PLACE,
    patchDecimalPlace: Int = SemanticVersion.DEFAULT_PATCH_DECIMAL_PLACE
): SemanticVersion = withUpdated(
    patchIncrement = increment,
    buildNumberIncrement = this.buildNumber * -1,
    majorDecimalPlace = majorDecimalPlace,
    minorDecimalPlace = minorDecimalPlace,
    patchDecimalPlace = patchDecimalPlace
)
fun SemanticVersion.nextMinor(
    increment: Int = 1,
    majorDecimalPlace: Int = SemanticVersion.DEFAULT_MAJOR_DECIMAL_PLACE,
    minorDecimalPlace: Int = SemanticVersion.DEFAULT_MINOR_DECIMAL_PLACE,
    patchDecimalPlace: Int = SemanticVersion.DEFAULT_PATCH_DECIMAL_PLACE
): SemanticVersion = withUpdated(
    minorIncrement = increment,
    patchIncrement = this.patch * -1,
    buildNumberIncrement = this.buildNumber * -1,
    majorDecimalPlace = majorDecimalPlace,
    minorDecimalPlace = minorDecimalPlace,
    patchDecimalPlace = patchDecimalPlace
)
fun SemanticVersion.nextMajor(
    increment: Int = 1,
    majorDecimalPlace: Int = SemanticVersion.DEFAULT_MAJOR_DECIMAL_PLACE,
    minorDecimalPlace: Int = SemanticVersion.DEFAULT_MINOR_DECIMAL_PLACE,
    patchDecimalPlace: Int = SemanticVersion.DEFAULT_PATCH_DECIMAL_PLACE
): SemanticVersion = withUpdated(
    majorIncrement = increment,
    minorIncrement = this.minor * -1,
    patchIncrement = this.patch * -1,
    buildNumberIncrement = this.buildNumber * -1,
    majorDecimalPlace = majorDecimalPlace,
    minorDecimalPlace = minorDecimalPlace,
    patchDecimalPlace = patchDecimalPlace
)
fun SemanticVersion.withDashQualifier(
    qualifier: String,
    majorDecimalPlace: Int = SemanticVersion.DEFAULT_MAJOR_DECIMAL_PLACE,
    minorDecimalPlace: Int = SemanticVersion.DEFAULT_MINOR_DECIMAL_PLACE,
    patchDecimalPlace: Int = SemanticVersion.DEFAULT_PATCH_DECIMAL_PLACE
) = withUpdated(
    dashQualifier = qualifier,
    majorDecimalPlace = majorDecimalPlace,
    minorDecimalPlace = minorDecimalPlace,
    patchDecimalPlace = patchDecimalPlace
)

fun SemanticVersion.withUpdated(
    majorIncrement: Int = 0,
    minorIncrement: Int = 0,
    patchIncrement: Int = 0,
    buildNumberIncrement: Int = 0,
    dashQualifier: String? = null,
    majorDecimalPlace: Int,
    minorDecimalPlace: Int,
    patchDecimalPlace: Int
): SemanticVersion = SemanticVersion.create(
    major = this.major + majorIncrement,
    minor = this.minor + minorIncrement,
    patch = this.patch + patchIncrement,
    buildNumber = this.buildNumber + buildNumberIncrement,
    dashQualifier = dashQualifier ?: this.dashQualifier,
    majorDecimalPlace = majorDecimalPlace,
    minorDecimalPlace = minorDecimalPlace,
    patchDecimalPlace = patchDecimalPlace
)

fun SemanticVersion.isIntermediateForm(): Boolean {
    return dashQualifier.isNotBlank()
}