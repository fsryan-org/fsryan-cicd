
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
}

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
    versionName = "0.0.1"
    awsAccessKeyId = evaluateProperty(propName = "com.fsryan.aws_access_key", envVarName = "AWS_ACCESS_KEY")
    awsSecretKey = evaluateProperty(propName = "com.fsryan.aws_secret_key", envVarName = "AWS_SECRET_KEY")
    releaseRepoName = "release"
    releaseRepoUrl = "s3://fsryan-maven-repo/release"
    snapshotRepoName = "snapshot"
    snapshotRepoUrl = "s3://fsryan-maven-repo/snapshot"
    description = "The basic tasks for CI/CD in a gradle build that builds through Azure Pipelines"
//    extraPomProperties = mapOf(
//        "gitrev" to GitApi(project).headCommitHash(short = true)
//    )
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