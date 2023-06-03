package com.fsryan.cicd

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.process.internal.ExecException

class FSRyanCICDPlugin : Plugin<Project> {

    private val releaseBranchPrefix: String = "release"
    private val developBranchPrefix: String = "develop"
    private val devLaunchBranchPrefix: String = "main"
    private val cicdReleaseDependentTasks: Set<String>
        get() = ext.cicdReleaseDependentTasks
    private val cicdDevelopDependentTasks: Set<String>
        get() = ext.cicdDevelopDependentTasks

    private lateinit var ext: FSRyanCICDExtension
    private lateinit var vcApi: VersionControlApi
    private lateinit var commitInspector: CICDCommitInspector
    private lateinit var projectQualifier: String
    private val lastVersionMap: Map<String, SemanticVersion> by lazy {
        when (ext.versionSpecifiers.isEmpty()) {
            true -> mapOf(
                "" to SemanticVersion.parse(commitInspector.findLastVersion(projectQualifier = projectQualifier))
            )
            false -> ext.versionSpecifiers.map { versionSpecifier ->
                versionSpecifier to SemanticVersion.parse(
                    commitInspector.findLastVersion(
                        projectQualifier = projectQualifier,
                        versionSpecifier = versionSpecifier
                    )
                )
            }.toMap()
        }.also {
            it.forEach { (versionSpecifier, semanticVersion) ->
                val specifier = if (versionSpecifier.isEmpty()) "" else "$versionSpecifier "
                fsryanCICDLog("last ${specifier}version: ${branchSpecificVersionName(semanticVersion)}")
            }
        }
    }
    lateinit var branch: String

    private var dryRunMode: Boolean = false

    private val isDefaultVersionSpecifier: Boolean
        get() = lastVersionMap.size == 1 && lastVersionMap.containsKey("")

    /**
     * Given a [SemanticVersion], creates the branch-specific version code for
     * that branch.
     */
    fun branchSpecificVersionName(semanticVersion: SemanticVersion): String {
        return semanticVersion.versionString(
            includeBuildNumber = !branch.isReleaseBranch(),
            includeDashQualifier = !branch.isReleaseBranch()
        )
    }

    fun releaseNotes(versionSpecifier: String = ""): String {
        val semanticVersion = lastVersionMap[versionSpecifier]
        if (semanticVersion == null) {
            fsryanCICDLog("Could not find version for versionSpecifier '$versionSpecifier'")
            return ""
        }
        val ret = commitInspector.createReleaseNotes(
            projectQualifier = projectQualifier,
            versionSpecifier = versionSpecifier,
            lastVersion = semanticVersion.versionString()
        )
        return ret
    }

    override fun apply(project: Project) {
        ext = project.extensions.create("fsryanCICD", FSRyanCICDExtension::class.java)
        vcApi = GitApi(project)
        commitInspector = CICDCommitInspector(vcApi = vcApi)
        projectQualifier = project.name
        dryRunMode = project.hasProperty("com.fsryan.cicd.dryRunMode")

        fsryanCICDLog("dryRunMode = $dryRunMode")
        fsryanCICDLog("projectQualifier = $projectQualifier")

        branch = (project.findProperty("com.fsryan.branch.override") ?: vcApi.currentBranchName()).toString()
        fsryanCICDLog("current branch is: $branch; branch name overridden? ${project.hasProperty("com.fsryan.branch.override")}")
        fsryanCICDLog("is develop: ${branch.isDevelopBranch()}; is release: ${branch.isReleaseBranch()}; is dev launch: ${branch.isDevLaunchBranch()}")

        project.afterEvaluate {
            val description = createCICDTaskDescription()
            fsryanCICDLog("CI/CD task will: $description")
            val cicdTask: Task = project.createPerformCICDTask(description)

            if (branch.isDevelopBranch()) {
                ensureCICDTaskDependencyAdded(project, cicdDevelopDependentTasks, cicdTask)
            }
            if (branch.isReleaseBranch()) {
                ensureCICDTaskDependencyAdded(project, cicdReleaseDependentTasks, cicdTask)
            }

            project.createPrintReleaseNotesTasks()
            if (branch.isDevelopBranch() || branch.isReleaseBranch()) {
                performModification("set CI/CD task dependent upon ${project.findPrintReleaseNotesTask().path} task") {
                    cicdTask.dependsOn(project.findPrintReleaseNotesTask())
                }
            }
        }
    }

    private fun ensureCICDTaskDependencyAdded(project: Project, tasks: Set<String>, cicdTask: Task) {
        tasks.forEach { dependentTask ->
            project.tasks.findByName(dependentTask)?.also { task ->
                performModification("set CI/CD task dependent upon ${task.path}") {
                    cicdTask.dependsOn(task)
                }
            } ?: project.tasks.findByPath(dependentTask)?.also { task ->
                performModification("set CI/CD task dependent upon ${task.path}") {
                    cicdTask.dependsOn(task)
                }
            }
        }
        project.tasks.whenTaskAdded {
            if (cicdDevelopDependentTasks.contains(name)) {
                performModification("set CI/CD task dependent upon $path") {
                    cicdTask.dependsOn(path)
                }
            }
            if (cicdDevelopDependentTasks.contains(path)) {
                performModification("set CI/CD task dependent upon $path") {
                    cicdTask.dependsOn(path)
                }
            }
        }
    }

    private fun Project.findPrintReleaseNotesTask(versionSpecifier: String = ""): Task {
        return checkNotNull(tasks.findByName(releaseNotesTaskName(versionSpecifier)))
    }

    private fun createCICDTaskDescription(): String {
        return when {
            branch.isDevelopBranch() -> lastVersionMap.toList()
                .joinToString { (versionSpecifier, semanticVersion) ->
                    val prefix = if (versionSpecifier.isEmpty()) "" else "$versionSpecifier/"
                    "$prefix${semanticVersion.versionString()} -> $prefix${semanticVersion.nextBuildNumber().versionString()}"
                }.let { desc ->
                    "Perform tasks $cicdDevelopDependentTasks and bump version on branch: $branch; $desc"
                }
            branch.isReleaseBranch() -> lastVersionMap.toList()
                .joinToString { (versionSpecifier, semanticVersion) ->
                    val prefix = if (versionSpecifier.isEmpty()) "" else "$versionSpecifier/"
                    "$prefix${semanticVersion.versionString(includeBuildNumber = false, includeDashQualifier = false)}"
                }.let { desc ->
                    "Perform tasks $cicdReleaseDependentTasks and create tag for version: $desc"
                }
            branch.isDevLaunchBranch() -> lastVersionMap.toList()
                .joinToString { (versionSpecifier, semanticVersion) ->
                    val prefix = if (versionSpecifier.isEmpty()) "" else "$versionSpecifier/"
                    "$prefix${semanticVersion.nextPatch()}"
                }.let { desc -> "Create next dev/release branches for versions: $desc" }
            else -> "Do nothing because the branch has no special behavior triggers: $branch"
        }
    }

    private fun Project.createPerformCICDTask(taskDescription: String) = tasks.create("performCICD") {
        description = taskDescription
        group = "FSRyan CI/CD"
        doLast {
            when {
                branch.isDevelopBranch() -> bumpVersion(versionPart = "buildNumber", pushTag = enablePush()) { nextBuildNumber() }
                branch.isReleaseBranch() -> publishReleaseTagAndBeginNextPatchVersion(pushTag = enablePush())
                else -> fsryanCICDLog("There is no configuration for CI/CD from branch: $branch")
            }

            fsryanCICDLog("Perform CI/CD task completed")
        }
    }

    private fun Project.enablePush(): Boolean {
        return findProperty("com.fsryan.cicd.enablePush")?.toString().orEmpty().toBoolean()
    }

    private fun Project.createPrintReleaseNotesTasks() {
        if (isDefaultVersionSpecifier) {
            tasks.create(releaseNotesTaskName()) {
                description = "Print the release notes since the last version bump to the console"
                group = "FSRyan CI/CD"
                doLast {
                    println("Release Notes:")
                    println(releaseNotes())
                }
            }
        } else {
            val actualTasks = ext.versionSpecifiers.map { vs ->
                tasks.create(releaseNotesTaskName(vs)) {
                    description = "Print the release notes for $vs since the last version bump to the console"
                    group = "FSRyan CI/CD"
                    doLast {
                        println("Release Notes for $vs:")
                        println(releaseNotes(vs))
                    }
                }
            }
            tasks.create(releaseNotesTaskName()) {
                description = "Print the release notes for all version specifiers"
                group = "FSRyan CI/CD"
                setDependsOn(actualTasks)
            }
        }
    }

    private fun releaseNotesTaskName(versionSpecifier: String = ""): String {
        return "print${versionSpecifier.capitalize()}ReleaseNotes"
    }

    private fun bumpVersion(
        versionPart: String,
        ensureBranchUpToDate: Boolean = true,
        commitToTag: String? = null,
        pushTag: Boolean = true,
        bumpVersion: SemanticVersion.() -> SemanticVersion
    ) {
        lastVersionMap.forEach { (versionSpecifier, semanticVersion) ->
            val nextVersion = semanticVersion.bumpVersion()
            fsryanCICDLog("bumping $versionPart: ${semanticVersion.name} -> ${nextVersion.name}")

            if (ensureBranchUpToDate) {
                ensureBranchUpToDate(versionSpecifier, nextVersion)
            }

            val versionString = nextVersion.name
            fsryanCICDLog("Tagging new version bump: ${nextVersion.name} for specifier: $versionSpecifier")
            createVersionTag(
                versionSpecifier = versionSpecifier,
                versionString = versionString,
                commitToTag = commitToTag,
                push = pushTag
            )
        }
    }

    private fun publishReleaseTagAndBeginNextPatchVersion(pushTag: Boolean = true) {
        fsryanCICDLog("publishing release tag and beginning next patch version")
        lastVersionMap.forEach { (versionSpecifier, semanticVersion) ->
            ensureBranchUpToDate(
                versionSpecifier = versionSpecifier,
                nextVersion = semanticVersion.nextPatch()
            )

            if (!semanticVersion.isIntermediateForm()) {
                fsryanCICDLog("Last tag is NOT intermediate form: ${semanticVersion.name}; skipping release--you'll need to release manually")
                return
            }
            val versionTag = commitInspector.findLastVersionTag(projectQualifier, versionSpecifier)
            if (versionTag == null) {
                fsryanCICDLog("Last version DID NOT come from tag; skipping release--you'll need to add a proper tag or release manually")
                return
            }

            val commitToTag = vcApi.commitOfTag(versionTag).also {
                fsryanCICDLog("commit to tag: $it")
            }

            if (commitToTag.isBlank()) {
                fsryanCICDLog("Could not find commit to tag; skipping release--you'll need to release manually")
                return
            }

            createReleaseTag(
                versionSpecifier = versionSpecifier,
                semanticVersion = semanticVersion,
                commitToTag = commitToTag
            )
            deleteRemoteAndLocalTag(versionTag)
            bumpVersion(
                versionPart = "patch",
                ensureBranchUpToDate = false,
                commitToTag = commitToTag,
                pushTag = pushTag
            ) { nextPatch() }
        }
    }

    private fun ensureBranchUpToDate(versionSpecifier: String, nextVersion: SemanticVersion) {
        val tmpBranchName: String = calculateTmpBranchName(versionSpecifier, nextVersion)
        performModification("creating temporary branch: $tmpBranchName") {
            vcApi.createLocalBranch(tmpBranchName).also(::fsryanCICDLog)
        }
        performModification("Ensuring $branch is checked out and up-to-date: first deleting local branch: $branch") {
            try {
                vcApi.deleteLocalBranch(branch).also(::fsryanCICDLog)
            } catch (e: ExecException) {
                fsryanCICDLog(e.message ?: "ExcecException encountered when deleting local $branch")
            }
        }
        performModification("Ensuring $branch is checked out and up-to-date: next checking out remote branch: $branch") {
            vcApi.checkout(branch).also(::fsryanCICDLog)
        }
        performModification("deleting temporary branch: $tmpBranchName") {
            try {
                vcApi.deleteLocalBranch(tmpBranchName).also(::fsryanCICDLog)
            } catch (e: ExecException) {
                fsryanCICDLog(e.message ?: "ExecException encountered when deleting local $tmpBranchName")
            }
        }
    }

    private fun calculateTmpBranchName(versionSpecifier: String, nextVersion: SemanticVersion): String {
        return when (versionSpecifier.isNotBlank()) {
            true -> "tmp-$versionSpecifier-${nextVersion.name}"
            false -> "tmp-${nextVersion.name}"
        }
    }

    private fun deleteRemoteAndLocalTag(versionTag: String, push: Boolean = true) {
        performModification("deleting remote tag: $versionTag") {
            if (push) {
                try {
                    vcApi.deleteTag(versionTag, remote = true).also(::fsryanCICDLog)
                } catch (ee: ExecException) {
                    fsryanCICDLog("ExecException while deleting remote tag: $versionTag")
                }
            } else {
                fsryanCICDLog("Configured to not push--not deleting remote tag")
            }
        }

        performModification("deleting local tag: $versionTag") {
            try {
                vcApi.deleteTag(versionTag, remote = false).also(::fsryanCICDLog)
            } catch (ee: ExecException) {
                fsryanCICDLog(("ExecException while deleting local tag: $versionTag"))
            }
        }
    }

    private fun createReleaseTag(
        versionSpecifier: String,
        semanticVersion: SemanticVersion,
        commitToTag: String,
        push: Boolean = true,
    ) {
        val version = semanticVersion.versionString(
            includeBuildNumber = false,
            includeDashQualifier = false
        )
        createVersionTag(
            versionSpecifier = versionSpecifier,
            versionString = version,
            commitToTag = commitToTag,
            push = push
        )
    }

    private fun createVersionTag(
        versionSpecifier: String,
        versionString: String,
        commitToTag: String? = null,
        push: Boolean = true
    ) {
        performModification("pushing version tag: $versionString; tag will be on commit ${commitToTag ?: "HEAD"}") {
            val tag = commitInspector.bumpVersionTagString(
                versionString = versionString,
                projectQualifier = projectQualifier,
                versionSpecifier = versionSpecifier
            )
            vcApi.tag(tagName = tag, commitToTag = commitToTag).also(::fsryanCICDLog)
            if (push) {
                vcApi.push(remote = "origin", branch = tag).also(::fsryanCICDLog)
            } else {
                fsryanCICDLog("Configured to not push--not creating remote tag: $tag")
            }
        }
    }

    private fun performModification(description: String, modification: () -> Unit) {
        if (dryRunMode) {
            fsryanCICDLog("DRY RUN: would have performed modification: $description")
            return
        }
        fsryanCICDLog("Performing modification: $description")
        modification()
    }

    private fun String.isReleaseBranch() = isOrStartsWithThenDash(releaseBranchPrefix)
    private fun String.isDevelopBranch() = isOrStartsWithThenDash(developBranchPrefix)
    private fun String.isDevLaunchBranch() = isOrStartsWithThenDash(devLaunchBranchPrefix)

    private fun String.isOrStartsWithThenDash(prefix: String) = this == prefix || startsWith("$prefix-")

    private fun String.branchSuffix() = indexOf('-').let { delimIdx ->
        when (delimIdx) {
            -1 -> ""
            (length - 1) -> throw IllegalStateException("error in branch name--cannot end in delimiter: $this")
            else -> substring(delimIdx + 1)
        }
    }
}
