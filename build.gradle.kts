import fsryan.BuildProperties.evaluateProperty

buildscript {
    val props = fsryan.BuildProperties
    props.initializeWith(rootProject)
    repositories {
        if (hasProperty("com.fsryan.includeMavenLocal")) {
            mavenLocal()
        }
        maven {
            url = uri("s3://fsryan-maven-repo/release")
            credentials(AwsCredentials::class) {
                accessKey = props.prop(rootProject, propName = "com.fsryan.aws_access_key", envVarName = "AWS_ACCESS_KEY")
                secretKey = props.prop(rootProject, propName = "com.fsryan.aws_secret_key", envVarName = "AWS_SECRET_KEY")
            }
        }
        maven {
            url = uri("s3://fsryan-maven-repo/snapshot")
            credentials(AwsCredentials::class) {
                accessKey = props.prop(rootProject, propName = "com.fsryan.aws_access_key", envVarName = "AWS_ACCESS_KEY")
                secretKey = props.prop(rootProject, propName = "com.fsryan.aws_secret_key", envVarName = "AWS_SECRET_KEY")
            }
        }
        mavenCentral()
    }
    dependencies {
        classpath(fsryan.Deps.Plugin.FSRyan.gradlePublishing)
        with(fsryan.Deps.Plugin.JetBrains) {
            classpath(dokka)
            classpath(kotlin)
        }
    }
}

repositories {
    mavenCentral()
}