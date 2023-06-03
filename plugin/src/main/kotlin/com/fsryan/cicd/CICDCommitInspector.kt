package com.fsryan.cicd

/**
 * Sits one level of abstraction above the [VersionControlApi]. This class
 * performs VCS operations that are specific to FSRyan CICD, including those
 * operations that make it compatible with existing project commits.
 */
class CICDCommitInspector(private val vcApi: VersionControlApi) {

    fun findLastVersion(projectQualifier: String? = null, versionSpecifier: String = ""): String {
        val tagName = findLastVersionTag(projectQualifier, versionSpecifier)
        if (tagName != null) {
            val delimIdx = tagName.lastIndexOf('/')
            if (delimIdx > 0) {
                return tagName.substring(delimIdx + 1)
            }
        }

        val commit = findLastVersionCommitMessage(
            projectQualifier,
            versionSpecifier,
            performLog = true
        )
        val delim = commit.lastIndexOf(" ")
        return commit.substring(delim + 1)
    }

    fun findLastVersionTag(projectQualifier: String? = null, versionSpecifier: String = ""): String? {
        val tagPrefix = versionTagPrefix(projectQualifier = projectQualifier, versionSpecifier = versionSpecifier)
        return vcApi.findLastTagWithPrefix(tagPrefix, prefixDelimiter = "/")
    }

    @Deprecated(message = "we now use tags for versioning")
    fun findLastVersionCommitMessage(
        projectQualifier: String? = null,
        versionSpecifier: String = "",
        performLog: Boolean = false
    ): String {
        var lastThrownException: Exception? = null
        listOfNotNull(
            bumpVersionCommitPrefix(projectQualifier, versionSpecifier),
            bumpVersionCommitPrefix(projectQualifier),
            projectQualifier,
            bumpVersionCommitPrefix(null)
        ).distinct()
        .forEach { prefix ->
            try {
                return findLastVersionCommitWithPrefix(prefix, performLog = performLog)
            } catch (e: Exception) {
                lastThrownException = e
            }
        }
        throw checkNotNull(lastThrownException)
    }

    @Throws(exceptionClasses = [IllegalStateException::class])
    fun findLastVersionCommitWithPrefix(prefix: String, performLog: Boolean = false): String {
        if (performLog) {
            fsryanCICDLog("Searching for version commit with prefix: $prefix")
        }
        return try {
            vcApi.findLastPrettyCommitIncludingRegexInSubject(Regex("$prefix ([0-9])+\\.([0-9])+\\.([0-9])+"))
        } catch (e: Exception) {
            if (performLog) {
                fsryanCICDLog("could not find version commit with prefix: $prefix")
            }
            throw e
        }
    }

    /**
     * There are a bunch of different possible configurations that would lead
     * to different version prefixes. Mostly, this is due to backwards
     * compatibility.
     */
    @Deprecated(message = "we now use tags for versioning")
    fun bumpVersionCommitPrefix(projectQualifier: String?, versionSpecifier: String = "") = when (versionSpecifier.isBlank()) {
        true -> when (projectQualifier.isNullOrBlank()) {
            true -> "bump version to"
            false -> "bump $projectQualifier version to"
        }
        false -> when (projectQualifier.isNullOrBlank()) {
            true -> "bump $versionSpecifier version to"
            false -> "bump $versionSpecifier $projectQualifier version to"
        }
    }

    /**
     * Create the official version bump tag name so that you can create and
     * find tags that specify versions.
     */
    fun bumpVersionTagString(
        versionString: String,
        projectQualifier: String?,
        versionSpecifier: String = ""
    ): String {
        val prefix = versionTagPrefix(projectQualifier = projectQualifier, versionSpecifier =  versionSpecifier)
        return if (prefix.isBlank()) versionString else "$prefix/$versionString"
    }

    fun versionTagPrefix(projectQualifier: String?, versionSpecifier: String = "") = when (versionSpecifier.isBlank()) {
        true -> when (projectQualifier.isNullOrBlank()) {
            true -> ""
            false -> "$projectQualifier"
        }
        false -> when (projectQualifier.isNullOrBlank()) {
            true -> "$versionSpecifier"
            false -> "$projectQualifier-$versionSpecifier"
        }
    }

    /**
     * Create the official version bump string so that you can create and find
     * commits that specify versions.
     */
    @Deprecated(message = "we now use tags for versioning")
    fun bumpVersionCommitString(
        versionString: String,
        projectQualifier: String?,
        versionSpecifier: String = ""
    ): String = when (versionSpecifier.isEmpty()) {
        true -> "${bumpVersionCommitPrefix(projectQualifier)} $versionString"
        false -> "${bumpVersionCommitPrefix(projectQualifier, versionSpecifier)} $versionString"
    }

    /**
     * Inspects the commit history in a compatible way going back from the HEAD
     * revision to the commit last commit containing a version bump (as
     * determined by [bumpVersionCommitPrefix]). This includes all of the
     */
    fun createReleaseNotes(
        projectQualifier: String,
        versionSpecifier: String = "",
        lastVersion : String,
        importantCommitIndicator: String = "Related work items"
    ): String {
        return try {
            val tag = bumpVersionTagString(
                versionString = lastVersion,
                projectQualifier = projectQualifier,
                versionSpecifier = versionSpecifier
            )
            vcApi.getFullCommitsBetweenTags(tag)
        }catch (e : Exception){
            val lastVersionCommit = findLastVersionCommitMessage(
                projectQualifier = projectQualifier,
                versionSpecifier = versionSpecifier
            )
            val delimIdx = lastVersionCommit.indexOf(' ')
            val commitHash = lastVersionCommit.substring(0, delimIdx)
            return vcApi.commitsWithKeyText(
                inclusionFilter = importantCommitIndicator,
                untilRev = commitHash
            )
        }
    }

}