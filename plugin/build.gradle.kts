import com.fsryan.cicd.CICDCommitInspector
import com.fsryan.cicd.FSRyanCICDPlugin
import com.fsryan.cicd.GitApi
import com.fsryan.cicd.SemanticVersion
import fsryan.BuildProperties.evaluateProperty
import fsryan.Deps
import fsryan.Deps.Versions

plugins {
    java
    `kotlin-dsl`
    jacoco
    id("maven-publish")
    id("fsryan-gradle-publishing")
    id("org.jetbrains.dokka")
    id("fsryan-cicd")
}

// TODO: upgrade this repo to remove this section
val versionString = CICDCommitInspector(GitApi(project)).findLastVersion(projectQualifier = project.name)
val semanticVersion = SemanticVersion.parse(versionString)
version = plugins.findPlugin(FSRyanCICDPlugin::class)?.branchSpecificVersionName(semanticVersion)
    ?: throw IllegalStateException("no version for project: ${project.name}")

group = "com.fsryan.gradle"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    gradleApi()
    implementation(Deps.Main.JetBrains.kotlinSTDLib)

    testImplementation(Deps.Test.MockK.jvm)

    with(Deps.Test.JUnit5) {
        testImplementation(api)
        testRuntimeOnly(engine)
        testImplementation(params)
        testRuntimeOnly(platformLauncher)
    }
}

afterEvaluate {
    tasks.withType(Test::class).forEach { it.useJUnitPlatform() }
}

fsPublishingConfig {
    developerName = "Ryan Scott"
    developerId = "ryan"
    developerEmail = "ryan@fsryan.com"
    siteUrl = "https://github.com/fsryan-org/fsryan-cicd"
    baseArtifactId = "fsryan-cicd"
    groupId = project.group.toString()
    versionName = project.version.toString()
    releaseBasicUser = evaluateProperty(propName = "com.fsryan.fsryan_maven_repo_user", envVarName = "FSRYAN_MAVEN_REPO_USER")
    releaseBasicPassword = evaluateProperty(propName = "com.fsryan.fsryan_release_password", envVarName = "FSRYAN_MAVEN_RELEASE_REPO_TOKEN")
    snapshotBasicUser = evaluateProperty(propName = "com.fsryan.fsryan_maven_repo_user", envVarName = "FSRYAN_MAVEN_REPO_USER")
    snapshotBasicPassword = evaluateProperty(propName = "com.fsryan.fsryan_snapshot_password", envVarName = "FSRYAN_MAVEN_SNAPSHOT_REPO_TOKEN")
    useBasicCredentials = true
    releaseRepoName = "release"
    releaseRepoUrl = "https://maven.fsryan.com/fsryan-release"
    snapshotRepoName = "snapshot"
    snapshotRepoUrl = "https://maven.fsryan.com/fsryan-snapshot"
    description = "The basic tasks for CI/CD in a gradle build that builds through Azure Pipelines"
    extraPomProperties = mapOf(
        "gitrev" to GitApi(project).headCommitHash(short = true)
    )
}

fsryanCICD {
    cicdReleaseDependentTasks = setOf("jacocoTestReport", "assemble", "release")
    cicdDevelopDependentTasks = setOf("jacocoTestReport", "assemble", "release")
}

jacoco {
    toolVersion = Versions.Plugin.Eclemma.jacoco
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            include("**/com/fsryan/cicd/**")
        }
    )
    dependsOn(tasks.test)
}


kotlin {
    jvmToolchain(11)
}

task("gitConfig") {
    doLast {
        val config = GitApi(project).runCommand("config", "--list")
        println(config)
    }
}