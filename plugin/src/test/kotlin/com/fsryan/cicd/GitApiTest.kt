package com.fsryan.cicd

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import kotlin.math.exp

@ExtendWith(MockKExtension::class)
class GitApiTest {

    @MockK private lateinit var mockCommandRunner: CommandRunner
    private lateinit var apiUnderTest: GitApi

    @BeforeEach
    fun initialize() {
        apiUnderTest = GitApi(mockCommandRunner)
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("findLastTagWithPrefixInput")
    fun findLastTagWithPrefix(config: FindLastTagWithPrefixTestConfig) = with(config) {
        configure(this@GitApiTest)
        val actual = apiUnderTest.findLastTagWithPrefix(prefix = inputPrefix, prefixDelimiter = inputPrefixDelimiter)
        assertEquals(expected, actual)
    }

    @ParameterizedTest(name = "[{index}] tagName = {0}, remote = {1} -> expected git command args: {2}")
    @MethodSource("deleteTagInput")
    fun deleteTag(tagName: String, remote: Boolean, expectedArgs: Array<String>) {
        every { mockCommandRunner.runCommand(arguments = anyVararg()) } returns ""
        apiUnderTest.deleteTag(tagName, remote = remote)
        verify { mockCommandRunner.runCommand(*expectedArgs) }
    }

    @ParameterizedTest(name = "[{index}] tagName = {0}, commitToTag = {1} -> expected git command args: {2}")
    @MethodSource("tagInput")
    fun tag(tagName: String, commitToTag: String?, expectedArgs: Array<String>) {
        every { mockCommandRunner.runCommand(arguments = anyVararg()) } returns ""
        apiUnderTest.tag(tagName, commitToTag)
        verify { mockCommandRunner.runCommand(*expectedArgs) }
    }

    data class FindLastTagWithPrefixTestConfig(
        val desc: String,
        val inputPrefix: String = "app",
        val inputPrefixDelimiter: String = "/",
        val gitCommitCountResponse: Int = 0,
        val gitForEachRefResponse: String = "",
        val gitLogResponses: List<String> = emptyList(),
        val expected: String? = null
    ) {
        fun configure(test: GitApiTest) {
            with(test.mockCommandRunner) {
                every {
                    runCommand(eq("for-each-ref"), eq("refs/tags"))
                } returns gitForEachRefResponse
                every {
                   runCommand("rev-list", "--count", "HEAD")
                } returns gitCommitCountResponse.toString()
                gitLogResponses.forEachIndexed { idx, response ->
                    every {
                        runCommand(
                            eq("log"),
                            eq("--skip=$idx"),
                            eq("--max-count=1"),
                            eq("--pretty=format:\"%H\""),
                            eq("--no-patch")
                        )
                    } returns response
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun findLastTagWithPrefixInput() = listOf(
            FindLastTagWithPrefixTestConfig(
                desc = "No tags should return null result"
            ),
            FindLastTagWithPrefixTestConfig(
                desc = "Tags existing, but no matching tags should send back null",
                gitForEachRefResponse = "ec7dea93d7a1ce999f58e9b0228f893cb2ec41a7 commit\trefs/tags/NOTAPP/0.0.1"
            ),
            FindLastTagWithPrefixTestConfig(
                desc = "Matching tags exist, but no commits returns null",
                gitForEachRefResponse = "ec7dea93d7a1ce999f58e9b0228f893cb2ec41a7 commit\trefs/tags/app/0.0.1"
            ),
            FindLastTagWithPrefixTestConfig(
                desc = "Matching tags exist, but no matching commits returns null",
                gitForEachRefResponse = "ec7dea93d7a1ce999f58e9b0228f893cb2ec41a7 commit\trefs/tags/app/0.0.1",
                gitCommitCountResponse = 2,
                gitLogResponses = listOf(
                    "094fa69562838a95879df9c4cffabde4a3af1880",
                    "bcf74006f59bb886c6186e0c5e9c2182ade5d2cd"
                )
            ),
            FindLastTagWithPrefixTestConfig(
                desc = "Matching tags exist, matching first commit returns the correct tag name",
                gitForEachRefResponse = "094fa69562838a95879df9c4cffabde4a3af1880 commit\trefs/tags/app/0.0.1",
                gitCommitCountResponse = 2,
                gitLogResponses = listOf(
                    "094fa69562838a95879df9c4cffabde4a3af1880",
                    "bcf74006f59bb886c6186e0c5e9c2182ade5d2cd"
                ),
                expected = "app/0.0.1"
            ),
            FindLastTagWithPrefixTestConfig(
                desc = "Matching tags exist, matching second commit returns the correct tag name",
                gitForEachRefResponse = "bcf74006f59bb886c6186e0c5e9c2182ade5d2cd commit\trefs/tags/app/0.0.2",
                gitCommitCountResponse = 2,
                gitLogResponses = listOf(
                    "094fa69562838a95879df9c4cffabde4a3af1880",
                    "bcf74006f59bb886c6186e0c5e9c2182ade5d2cd"
                ),
                expected = "app/0.0.2"
            ),
            FindLastTagWithPrefixTestConfig(
                desc = "Multiple tags exist for the same commit, highest value wins",
                gitForEachRefResponse = "bcf74006f59bb886c6186e0c5e9c2182ade5d2cd commit\trefs/tags/app/0.0.2"
                + "\nbcf74006f59bb886c6186e0c5e9c2182ade5d2cd commit\trefs/tags/app/0.0.3",
                gitCommitCountResponse = 2,
                gitLogResponses = listOf(
                    "094fa69562838a95879df9c4cffabde4a3af1880",
                    "bcf74006f59bb886c6186e0c5e9c2182ade5d2cd"
                ),
                expected = "app/0.0.3"
            )
        )

        @JvmStatic
        fun deleteTagInput() = listOf(
            arguments("the_tag/0.1.2-build.0", true, arrayOf("push", "--delete", "origin", "the_tag/0.1.2-build.0")),
            arguments("the_tag/0.1.2-build.0", false, arrayOf("tag", "--delete", "the_tag/0.1.2-build.0"))
        )

        @JvmStatic
        fun tagInput() = listOf(
            UUID.randomUUID().toString().let{ commitToTag ->
                arguments("the_tag/0.1.2-build.0", commitToTag, arrayOf("tag", "the_tag/0.1.2-build.0", commitToTag))
            },
            arguments("the_tag/0.1.2-build.0", null, arrayOf("tag", "the_tag/0.1.2-build.0"))
        )
    }
}