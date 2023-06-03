package com.fsryan.cicd

import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@ExtendWith(MockKExtension::class)
class CICDCommitInspectorTest {

    @MockK private lateinit var mockVersionControlApi: VersionControlApi
    private lateinit var inspectorUnderTest: CICDCommitInspector

    @BeforeEach
    fun initialzeInspectorUnderTest() {
        inspectorUnderTest = CICDCommitInspector(mockVersionControlApi)
    }

    @ParameterizedTest(name = "[{index}] [expected commit]  {0}")
    @MethodSource("bumpVersionStringInput")
    fun bumpVersionCommitString(config: VersionStringTestConfig) = with(config) {
        val actual = inspectorUnderTest.bumpVersionCommitString(
            versionString = inputVersionString,
            projectQualifier = inputProjectQualifier,
            versionSpecifier = inputVersionSpecifier
        )
        assertEquals(expectedCommitBumpVersionString, actual)
    }

    @ParameterizedTest(name = "[{index}] [expected tag] {0}")
    @MethodSource("bumpVersionStringInput")
    fun bumpVersionTagString(config: VersionStringTestConfig) = with(config) {
        val actual = inspectorUnderTest.bumpVersionTagString(
            versionString = inputVersionString,
            projectQualifier = inputProjectQualifier,
            versionSpecifier = inputVersionSpecifier
        )
        assertEquals(expectedTagBumpVersionString, actual)
    }

    data class VersionStringTestConfig(
        val inputVersionString: String,
        val inputProjectQualifier: String?,
        val inputVersionSpecifier: String,
        val expectedCommitBumpVersionString: String,
        val expectedTagBumpVersionString: String
    ) {
        override fun toString(): String {
            return "versionString: $inputVersionString; projectQualifier: $inputProjectQualifier; and versionSpecifier: $inputVersionSpecifier -> expectedCommitBumpVersionString: $expectedCommitBumpVersionString; expectedTagBumpVersionString: $expectedTagBumpVersionString"
        }
    }

    companion object {

        @JvmStatic
        fun bumpVersionStringInput() = listOf(
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = null,
                inputVersionSpecifier = "",
                expectedCommitBumpVersionString = "bump version to 4.1.5",
                expectedTagBumpVersionString = "4.1.5"
            ),
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = null,
                inputVersionSpecifier = " ",
                expectedCommitBumpVersionString = "bump version to 4.1.5",
                expectedTagBumpVersionString = "4.1.5"
            ),
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = "",
                inputVersionSpecifier = "",
                expectedCommitBumpVersionString = "bump version to 4.1.5",
                expectedTagBumpVersionString = "4.1.5"
            ),
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = " ",
                inputVersionSpecifier = " ",
                expectedCommitBumpVersionString = "bump version to 4.1.5",
                expectedTagBumpVersionString = "4.1.5"
            ),
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = "app",
                inputVersionSpecifier = "",
                expectedCommitBumpVersionString = "bump app version to 4.1.5",
                expectedTagBumpVersionString = "app/4.1.5"
            ),
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = "app",
                inputVersionSpecifier = " ",
                expectedCommitBumpVersionString = "bump app version to 4.1.5",
                expectedTagBumpVersionString = "app/4.1.5"
            ),
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = "",
                inputVersionSpecifier = "fd",
                expectedCommitBumpVersionString = "bump fd version to 4.1.5",
                expectedTagBumpVersionString = "fd/4.1.5"
            ),
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = " ",
                inputVersionSpecifier = "fd",
                expectedCommitBumpVersionString = "bump fd version to 4.1.5",
                expectedTagBumpVersionString = "fd/4.1.5"
            ),
            VersionStringTestConfig(
                inputVersionString = "4.1.5",
                inputProjectQualifier = "app",
                inputVersionSpecifier = "fd",
                expectedCommitBumpVersionString = "bump fd app version to 4.1.5",
                expectedTagBumpVersionString = "app-fd/4.1.5"
            )
        )
    }
}