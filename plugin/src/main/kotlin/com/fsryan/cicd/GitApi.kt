package com.fsryan.cicd

import org.gradle.api.Project
import java.io.ByteArrayOutputStream

/**
 * Provides abstractions to perform git operations in the project.
 */
class GitApi(private val commandRunner: CommandRunner): VersionControlApi {

    constructor(project: Project): this(object: CommandRunner {
        override fun runCommand(vararg arguments: String): String {
            return ByteArrayOutputStream().use { output ->
                project.exec {
                    executable = "git"
                    args = arguments.asList()
                    standardOutput = output
                    isIgnoreExitValue = false
                }.rethrowFailure()
                output.toString().trim()
            }
        }
    })

    override fun headCommitHash(short: Boolean): String {
        val shaLen = if (short) "--short=7" else "--short=40"
        return runCommand("rev-parse", shaLen, "HEAD")
    }

    override fun currentBranchName(abbrevRef: String): String {
        return runCommand("rev-parse", "--abbrev-ref", abbrevRef)
    }

    override fun findLastPrettyCommitIncludingTextInSubject(expectedText: String): String {
        var commitOffset = 0
        val totalCount = commitCount()
        while (commitOffset < totalCount) {
            val ret = lastPrettyAbbrevCommitsFromOffset(commitOffset, 10)
                .firstOrNull { it.contains(expectedText) }
            if (ret != null) {
                return ret
            }
            commitOffset += 10
        }
        throw IllegalStateException("No commit with text '$expectedText' found")
    }

    /**
     * Finds the last commit from HEAD whose subject contains the input regex.
     * Commits are looked at 10-at-a-time down to the root. If no matching
     * commit is found, then an [IllegalStateException] is thrown.
     */
    override fun findLastPrettyCommitIncludingRegexInSubject(regex: Regex): String {
        var commitOffset = 0
        val totalCount = commitCount()
        while (commitOffset < totalCount) {
            val ret = lastPrettyAbbrevCommitsFromOffset(commitOffset, 10)
                .firstOrNull { regex.containsMatchIn(it) }
            if (ret != null) {
                return ret
            }
            commitOffset += 10
        }
        throw IllegalStateException("No commit with regex pattern '${regex.pattern}' found")
    }

    override fun findTagMatchesRegex(regex: Regex, tag : String): String {
        if (regex.containsMatchIn(tag)){
            return tag
        }
        throw IllegalStateException("No tag with regex pattern '${regex.pattern}' found")
    }

    override fun findLastTagWithPrefix(prefix: String, prefixDelimiter: String): String? {
        val tagMap = mutableMapOf<String, MutableSet<String>>()    // commit -> set of tags
        runCommand("for-each-ref", "refs/tags")
            .replace("'", "")
            .split("\n")
            .asSequence()
            .filter { it.contains("refs/tags/$prefix$prefixDelimiter") }
            .map { line ->
                val split = line.split(" ")
                val ref = split[0]
                val tag = split[1].substring("commit\trefs/tags/".length)
                ref to tag
            }.forEach { (commit, tag) ->
                var current = tagMap[commit]
                if (current == null) {
                    current = mutableSetOf()
                    tagMap[commit] = current
                }
                current.add(tag)
            }

        if (tagMap.isEmpty()) {
            return null
        }

        val totalCount = commitCount()
        if (totalCount == 0) {
            return null
        }

        var commitOffset = 0
        while (commitOffset < totalCount) {
            val commit = runCommand(
                "log",
                "--skip=$commitOffset",
                "--max-count=1",
                "--pretty=format:\"%H\"",
                "--no-patch"
            ).replace("\"", "")
            val tags = tagMap[commit] ?: emptySet()
            if (tags.isNotEmpty()) {
                return when (tags.size) {
                    1 -> tags.first()
                    else -> tags.maxBy { tag ->
                        val version = tag.substring("$prefix$prefixDelimiter".length)
                        val semanticVersion = SemanticVersion.parse(version)
                        semanticVersion.code
                    }
                }
            }
            commitOffset += 1
        }
        return null
    }

    override fun commitOfTag(tag: String): String {
        return runCommand("rev-list", "-n", "1", tag)
    }

    override fun getCommitsBetweenTags(tag : String): List<String> {
        return runCommand(
            "log",
            "--pretty=oneline",
            "HEAD...$tag"
        ).split("\n")
    }

    override fun getFullCommitsBetweenTags(startTag : String, endTag : String?): String {
        val headTag = endTag?:"HEAD"
        return runCommand(
            "log",
            "--pretty=full",
            "$headTag...$startTag"
        )
    }

    override fun getCurrentCommitIfTagged(): String {
        return runCommand(
            "describe",
            "--exact-match",
            "--tags",
            "HEAD")
    }

    override fun getPreviousTag(): String {
        return runCommand(
            "describe",
            "--abbrev=0",
            "--tags",
            "`git rev-list --tags --skip=1 --maxcount=1`"
        )
    }
    /**
     * Return the last [count] commits from the [commitOffset] as pretty,
     * one-line commits.
     */
    override fun lastPrettyAbbrevCommitsFromOffset(commitOffset: Int, count: Int): List<String> {
        return runCommand(
            "log",
            "--abbrev-commit",
            "--pretty=oneline",
            "--skip=$commitOffset",
            "--max-count=$count"
        ).split("\n")
    }

    /**
     * Return the number of commits from the HEAD revision to the root.
     */
    override fun commitCount(): Int {
        return runCommand("rev-list", "--count", "HEAD").toInt()
    }

    /**
     * Run an arbitrary git command and return the result as a String.
     */
    override fun runCommand(vararg arguments: String): String {
        return commandRunner.runCommand(*arguments)
    }

    /**
     * Make an empty (no alterations) commit with a commit name. You can supply
     * the author details. Note that if there are actually staged changes, then
     * those changes _WILL_ be committed.
     */
    override fun commitEmptyWithSkipCI(subject: String, authorName: String, authorEmail: String): String = commit(
        allowEmpty = true,
        // [skip ci] is one of the HEAD revision strings that DevOps
        // Pipelines and other CI systems use to filter out builds,
        // allowing you to push to a monitored branch without triggering an
        // infinite loop of CI triggers.
        subject = "[skip ci] $subject",
        authorName = authorName,
        authorEmail = authorEmail
    )

    override fun commit(allowEmpty: Boolean, subject: String, authorName: String, authorEmail: String): String {
        val arguments = when (allowEmpty) {
            true -> arrayOf("commit", "--allow-empty", "--author=$authorName <$authorEmail>", "-m", subject)
            false -> arrayOf("commit", "--author=$authorName <$authorEmail>", "-m", subject)
        }
        return runCommand(*arguments)
    }

    override fun fetchGlobalConfigGitEmail() = fetchConfig("user.email")
    override fun fetchGlobalConfigGitUser() = fetchConfig("user.name")
    override fun fetchConfig(parameter: String) = runCommand("config", parameter)

    // TODO: check that the current branch matches . . . if not, use : syntax
    //  to push onto different remote branch
    override fun push(remote: String, branch: String) = runCommand("push", remote, branch)
    override fun createLocalBranch(name: String) = runCommand("checkout", "-b", name)
    override fun checkout(branch: String) = runCommand("checkout", branch)
    override fun deleteLocalBranch(name: String) = runCommand("branch", "-D", name)

    override fun tag(tagName: String, commitToTag: String?): String {
        return commitToTag?.let { commit ->
            runCommand("tag", tagName, commit)
        } ?: runCommand("tag", tagName)
    }

    override fun deleteTag(tagName: String, remote: Boolean): String {
        return when (remote) {
            true -> runCommand("push", "--delete", "origin", tagName)
            false -> runCommand("tag", "--delete", tagName)
        }
    }

    override fun commitsContainingMessageText(inclusionFilter: String, untilMatches: String?): String {
        val divider = "\n-------\n"
        var commitOffset = 0
        val totalCount = commitCount()
        val buf = StringBuilder()
        while (commitOffset < totalCount) {
            val commit: String = runCommand("log", "--skip=$commitOffset", "--max-count=1", "--pretty=full")
            if (untilMatches != null && commit.contains(untilMatches)) {
                break
            }
            if (commit.contains(inclusionFilter)) {
                buf.append(commit).append(divider)
            }
            commitOffset++
        }
        return when (buf.length >= divider.length) {
            true -> buf.delete(buf.length - divider.length, buf.length)
            false -> buf
        }.toString()
    }

    override fun formatCommitsWithDivider(commits : List<String>): String {
        val divider = "\n-------\n"
        val buf = StringBuilder()
        commits.forEach {
            buf.append(it).append(divider)
        }
        return when (buf.length >= divider.length) {
            true -> buf.delete(buf.length - divider.length, buf.length)
            false -> buf
        }.toString()
    }

    override fun commitsWithKeyText(inclusionFilter: String, untilRev: String): String {
        val divider = "\n-------\n"
        var commitOffset = 0
        val totalCount = commitCount()
        val buf = StringBuilder()
        while (commitOffset < totalCount) {
            val commit: String = runCommand("log", "--skip=$commitOffset", "--max-count=1", "--pretty=full")
            val firstLine = commit.indexOf("\n").let { lineDelimIdx -> commit.substring(0, lineDelimIdx) }
            if (firstLine.contains(untilRev)) {
                break
            }
            if (commit.contains(inclusionFilter)) {
                buf.append(commit).append(divider)
            }
            commitOffset++
        }
        return when (buf.length >= divider.length) {
            true -> buf.delete(buf.length - divider.length, buf.length)
            false -> buf
        }.toString()
    }
}