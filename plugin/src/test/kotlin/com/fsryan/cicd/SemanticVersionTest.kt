package com.fsryan.cicd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class SemanticVersionTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("parseInput")
    fun parse(config: SemanticVersionParseTestConfig) = with(config) {
        val actual = SemanticVersion.parse(
            fromString = input,
            majorDecimalPlace = inputMajorDecimalPlace,
            minorDecimalPlace = inputMinorDecimalPlace,
            patchDecimalPlace = inputPatchDecimalPlace
        )
        assertEquals(expectedMaj, actual.major)
        assertEquals(expectedMin, actual.minor)
        assertEquals(expectedP, actual.patch)
        assertEquals(expectedBN, actual.buildNumber)
        assertEquals(expectedC, actual.code)
        assertEquals(expectedN, actual.name)
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("nextBuildNumberInput")
    fun nextBuildNumber(input: String, expected: String) {
        val semanticVersion = SemanticVersion.parse(input)
        val actual = semanticVersion.nextBuildNumber()
        assertEquals(expected, actual.name)
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("nextPatchInput")
    fun nextPatch(input: String, expected: String) {
        val semanticVersion = SemanticVersion.parse(input)
        val actual = semanticVersion.nextPatch()
        assertEquals(expected, actual.name)
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("nextMinorInput")
    fun nextMinor(input: String, expected: String) {
        val semanticVersion = SemanticVersion.parse(input)
        val actual = semanticVersion.nextMinor()
        assertEquals(expected, actual.name)
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("nextMajorInput")
    fun nextMajor(input: String, expected: String) {
        val semanticVersion = SemanticVersion.parse(input)
        val actual = semanticVersion.nextMajor()
        assertEquals(expected, actual.name)
    }

    data class SemanticVersionParseTestConfig(
        val input: String,
        val inputMajorDecimalPlace: Int = SemanticVersion.DEFAULT_MAJOR_DECIMAL_PLACE,
        val inputMinorDecimalPlace: Int = SemanticVersion.DEFAULT_MINOR_DECIMAL_PLACE,
        val inputPatchDecimalPlace: Int = SemanticVersion.DEFAULT_PATCH_DECIMAL_PLACE,
        val expectedMaj: Int,
        val expectedMin: Int,
        val expectedP: Int,
        val expectedBN: Int,
        val expectedC: Int,
        val expectedN: String
    )

    companion object {
        @JvmStatic
        fun parseInput() = listOf(
            SemanticVersionParseTestConfig(
                input  = "0.0.1",
                expectedMaj = 0,
                expectedMin = 0,
                expectedP = 1,
                expectedBN = 0,
                expectedC = 100,
                expectedN = "0.0.1-build.0"
            ),
            SemanticVersionParseTestConfig(
                input  = "0.0.11",
                expectedMaj = 0,
                expectedMin = 0,
                expectedP = 11,
                expectedBN = 0,
                expectedC = 1100,
                expectedN = "0.0.11-build.0"
            ),
            SemanticVersionParseTestConfig(
                input  = "0.0.11-build.5",
                expectedMaj = 0,
                expectedMin = 0,
                expectedP = 11,
                expectedBN = 5,
                expectedC = 1105,
                expectedN = "0.0.11-build.5"
            ),
            SemanticVersionParseTestConfig(
                input  = "0.1.12-build.99",
                expectedMaj = 0,
                expectedMin = 1,
                expectedP = 12,
                expectedBN = 99,
                expectedC = 11299,
                expectedN = "0.1.12-build.99"
            ),
            SemanticVersionParseTestConfig(
                input  = "0.99.99-build.99",
                expectedMaj = 0,
                expectedMin = 99,
                expectedP = 99,
                expectedBN = 99,
                expectedC = 999999,
                expectedN = "0.99.99-build.99"
            ),
            SemanticVersionParseTestConfig(
                input  = "1.99.99-build.99",
                expectedMaj = 1,
                expectedMin = 99,
                expectedP = 99,
                expectedBN = 99,
                expectedC = 1999999,
                expectedN = "1.99.99-build.99"
            ),
            SemanticVersionParseTestConfig(
                input  = "99.99.99-build.99",
                expectedMaj = 99,
                expectedMin = 99,
                expectedP = 99,
                expectedBN = 99,
                expectedC = 99999999,
                expectedN = "99.99.99-build.99"
            ),
            SemanticVersionParseTestConfig(
                input  = "0.5.105-build.1",
                expectedMaj = 0,
                expectedMin = 5,
                expectedP = 105,
                expectedBN = 1,
                expectedC = 510501,
                expectedN = "0.5.105-build.1",
                // This gives us three digits for a patch version
                inputMajorDecimalPlace = 8,
                inputMinorDecimalPlace = 6,
                inputPatchDecimalPlace = 3
            ),
            SemanticVersionParseTestConfig(
                input  = "0.5.5-build.106",
                expectedMaj = 0,
                expectedMin = 5,
                expectedP = 5,
                expectedBN = 106,
                expectedC = 505106,
                expectedN = "0.5.5-build.106",
                // This gives us three digits for a build version
                inputMajorDecimalPlace = 8,
                inputMinorDecimalPlace = 6,
                inputPatchDecimalPlace = 4
            ),
            SemanticVersionParseTestConfig(
                input  = "1.5.5-custom.6",
                expectedMaj = 1,
                expectedMin = 5,
                expectedP = 5,
                expectedBN = 6,
                expectedC = 1050506,
                expectedN = "1.5.5-custom.6"
            )
        )

        @JvmStatic
        fun nextBuildNumberInput() = listOf(
            arguments("0.0.0-build.0", "0.0.0-build.1"),
            arguments("0.0.0-build.9", "0.0.0-build.10")
        )

        @JvmStatic
        fun nextPatchInput() = listOf(
            arguments("0.0.0-build.0", "0.0.1-build.0"),
            arguments("0.0.0-build.9", "0.0.1-build.0"),
            arguments("0.0.9-build.9", "0.0.10-build.0")
        )

        @JvmStatic
        fun nextMinorInput() = listOf(
            arguments("0.0.0-build.0", "0.1.0-build.0"),
            arguments("0.0.0-build.9", "0.1.0-build.0"),
            arguments("0.0.9-build.0", "0.1.0-build.0"),
            arguments("0.9.0-build.0", "0.10.0-build.0")
        )

        @JvmStatic
        fun nextMajorInput() = listOf(
            arguments("0.0.0-build.0", "1.0.0-build.0"),
            arguments("0.0.0-build.9", "1.0.0-build.0"),
            arguments("0.0.9-build.9", "1.0.0-build.0"),
            arguments("0.9.9-build.9", "1.0.0-build.0"),
            arguments("9.9.9-build.0", "10.0.0-build.0")
        )
    }
}