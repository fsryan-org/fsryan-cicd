package com.fsryan.cicd

/**
 * An interface for working with a version control system. The contract is
 * driven by `git` . . . because we use `git`.
 */
interface VersionControlApi {

    /**
     * The revision of the head commit on the current branch
     */
    fun headCommitHash(short: Boolean = true): String

    /**
     * Returns the current branch name as a string
     */
    fun currentBranchName(abbrevRef: String = "HEAD"): String

    /**
     * Finds the last commit, starting at the HEAD of the current branch,
     * working its way into the past, whose subject contains the input text
     * (case sensitive). Commits are evaluated in batches of 10 down to the root
     * commit.
     * @throws IllegalStateException if no matching commit is found
     * @see findLastPrettyCommitIncludingRegexInSubject because you probably
     * should use a regular expression instead.
     */
    fun findLastPrettyCommitIncludingTextInSubject(expectedText: String): String

    /**
     * Finds the last commit, starting at the HEAD of the current branch,
     * working its way into the past, whose subject contains a match of the
     * regex (anywhere). Commits are evaluated in batches of 10 down to the
     * root commit.
     * @throws IllegalStateException if no matching commit is found
     */
    fun findLastPrettyCommitIncludingRegexInSubject(regex: Regex): String

    fun getCurrentCommitIfTagged(): String

    /**
     * This should be a branch-aware tag lookup function. This function looks
     * at the past commits and tries to find a commit that has been tagged with
     * a version string with the input [prefix] and [prefixDelimiter]. For
     * example, if you have a git commit history that looks like this:
     * ```
     * $ git log --skip=0 --max-count=10 --pretty=format:"%H" --no-patch
     * ec7dea93d7a1ce999f58e9b0228f893cb2ec41a7
     * 094fa69562838a95879df9c4cffabde4a3af1880
     * bcf74006f59bb886c6186e0c5e9c2182ade5d2cd
     * dc8ceacd7142af4921af587a424bb2bf31c90869
     * 77845dc452209a5180611894c9eb22ae300ca2a0
     * c10c2daf50334a0c3ab2b313dadf07564341c2ea
     * 1e05724f1d8bff8e62738b293c173a9ba569bd61
     * 9846b6d80758ea867e609147513c1a6de6758112
     * b6bac81a4087df2b327ff369ad5f2e55a8a1ea50
     * df01f9f5e48fa1d5bfa243b15e351dd1d93a9681
     * ```
     * And you have git tags that looks like this:
     * ```
     * $ git for-each-ref refs/tags
     * bcf74006f59bb886c6186e0c5e9c2182ade5d2cd commit	refs/tags/app/4.0.1
     * d04093ba709d69e0df80afbb60050088e05d8f95 commit	refs/tags/app/4.0.2
     * ec7dea93d7a1ce999f58e9b0228f893cb2ec41a7 commit	refs/tags/app/4.0.3
     * ```
     *
     * Then, since commit `d04093ba709d69e0df80afbb60050088e05d8f95` is not
     * reachable from this branch, the `app/4.0.2` tag _WILL NOT_ be found. The
     * algorithm will, however, find `app/4.0.1` and `app/4.0.3` because the
     * commits to which they point are both reachable by traversing the commits
     * of this branch. It will return `app/4.0.3` because the `app/4.0.3` tag
     * references a more recent commit than `app/4.0.1`.
     *
     * If a single commit is marked with multiple matching tags, then the tag
     * which resolves to the higher version will be selected.
     */
    fun findLastTagWithPrefix(prefix: String, prefixDelimiter: String): String?

    fun commitOfTag(tag: String): String

    fun findTagMatchesRegex(regex: Regex, tag : String): String

    fun getCommitsBetweenTags(tag : String) : List<String>

    fun getFullCommitsBetweenTags(startTag: String, endTag: String? = null): String

    fun getPreviousTag() : String
    /**
     * Returns a batch of commits (one pretty commit per entry in the list).
     * The [commitOffset] is from the HEAD revision of the current branch. The
     * output size will be the smaller of the total number of commits after the
     * offset and [count]
     */
    fun lastPrettyAbbrevCommitsFromOffset(commitOffset: Int, count: Int): List<String>

    /**
     * Returns the number of total commits back to the root from the HEAD of
     * the current branch.
     */
    fun commitCount(): Int

    /**
     * Generic interface for running any command against the Version Control
     * System backing this interface. This is for the sake of flexibility, and
     * you can do a lot of harm by using this function, so beware. The
     * [arguments] are the arguments to the VCS command, including options.
     */
    fun runCommand(vararg arguments: String): String

    /**
     * Make an empty (no alterations) commit with a commit name. You can supply
     * the author details. Note that if there are actually staged changes, then
     * those changes _WILL_ be committed.
     */
    fun commitEmptyWithSkipCI(
        subject: String,
        authorName: String = fetchGlobalConfigGitUser(),
        authorEmail: String = fetchGlobalConfigGitEmail()
    ): String

    /**
     * Make a commit. You can supply the author details. Note that if there are
     * actually staged changes, then those changes _WILL_ be committed.
     */
    fun commit(
        allowEmpty: Boolean,
        subject: String,
        authorName: String = fetchGlobalConfigGitUser(),
        authorEmail: String = fetchGlobalConfigGitEmail()
    ): String

    /**
     * TODO: this is a holdover from when we were coding against a specific git
     *  interface. We should definitely change this.
     * The globally-configured committer email.
     */
    fun fetchGlobalConfigGitEmail(): String

    /**
     * TODO: this is a holdover from when we were coding against a specific git
     *  interface. We should definitely change this.
     * The globally-configured committer.
     */
    fun fetchGlobalConfigGitUser(): String

    /**
     * TODO: specify local or global
     * Fetch some configuration parameter
     */
    fun fetchConfig(parameter: String): String

    /**
     * Push the HEAD of the local [branch] to the [remote]'s [branch]
     */
    fun push(remote: String, branch: String): String

    /**
     * Create a branch locally with the name [name].
     */
    fun createLocalBranch(name: String): String

    /**
     * Checks out a branch with the name [branch]
     */
    fun checkout(branch: String): String

    /**
     * Deletes the local branch named [name]
     */
    fun deleteLocalBranch(name: String): String

    /**
     * Creates a tag locally--does not push to a remote
     * @param tagName the name of the tag
     * @param commitToTag the commit to tag, if null, then the HEAD commit
     */
    fun tag(tagName: String, commitToTag: String? = null): String

    /**
     * Deletes a tag
     * @param remote whether to delete the tag remotely (currently only remote
     * named origin)
     */
    fun deleteTag(tagName: String, remote: Boolean): String

    /**
     * Find the commits until there is a string match
     */
    fun commitsContainingMessageText(inclusionFilter: String, untilMatches: String? = null): String

    fun formatCommitsWithDivider(commits : List<String>): String
    /**
     * Find the commits until the revision
     */
    fun commitsWithKeyText(inclusionFilter: String, untilRev: String): String
}