import fsryan.BuildProperties.evaluateProperty

buildscript {
    fsryan.BuildProperties.initializeWith(rootProject)
    repositories {
        if (hasProperty("com.fsryan.includeMavenLocal")) {
            mavenLocal()
        }
        maven {
            url = uri("s3://fsryan-maven-repo/release")
            credentials(AwsCredentials::class) {
                accessKey = evaluateProperty(propName = "com.fsryan.aws_access_key", envVarName = "AWS_ACCESS_KEY")
                secretKey = evaluateProperty(propName = "com.fsryan.aws_secret_key", envVarName = "AWS_SECRET_KEY")
            }
        }
        maven {
            url = uri("s3://fsryan-maven-repo/snapshot")
            credentials(AwsCredentials::class) {
                accessKey = evaluateProperty(propName = "com.fsryan.aws_access_key", envVarName = "AWS_ACCESS_KEY")
                secretKey = evaluateProperty(propName = "com.fsryan.aws_secret_key", envVarName = "AWS_SECRET_KEY")
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