package fsryan

object Deps {

    object Versions {
        object Global {
            object JetBrains {
                const val dokka = "1.8.10"
                const val kotlin = "1.8.21"
            }
        }
        object Main {
        }
        object Plugin {
            object Eclemma {
                const val jacoco = "0.8.8"
            }
            object FSRyan {
                const val gradlePublishing = "0.3.0"
            }
        }
        object Test {
            object FSRyan {
                const val testtools = "0.0.4"
            }
            object JUnit5 {
                const val jupiter = "5.8.2"
                const val platform = "1.8.2"
            }
            object MockK {
                const val core = "1.12.4"
            }
        }
    }

    object Main {
        object JetBrains {
            private val globalVersion = Versions.Global.JetBrains
            const val kotlinSTDLib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${globalVersion.kotlin}"
        }
    }
    object Plugin {
        object FSRyan {
            private val version = Versions.Plugin.FSRyan
            const val gradlePublishing = "com.fsryan.gradle:fsryan-gradle-publishing:${version.gradlePublishing}"
        }
        object JetBrains {
            private val version = Versions.Global.JetBrains
            const val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:${version.dokka}"
            const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${version.kotlin}"
        }
    }
    object Test {
        object FSRyan {
            private val testVersion = Versions.Test.FSRyan
            const val junit4JVMTools = "com.fsryan.testtools.jvm:junit4jvmtools:${testVersion.testtools}"
            const val jvmTestTools = "com.fsryan.testtools.jvm:jvmtesttools:${testVersion.testtools}"
        }
        object JUnit5 {
            private val version = Versions.Test.JUnit5
            const val api = "org.junit.jupiter:junit-jupiter-api:${version.jupiter}"
            const val engine = "org.junit.jupiter:junit-jupiter-engine:${version.jupiter}"
            const val params = "org.junit.jupiter:junit-jupiter-params:${version.jupiter}"
            const val platformLauncher = "org.junit.platform:junit-platform-launcher:${version.platform}"
        }
        object MockK {
            private val version = Versions.Test.MockK
            const val jvm = "io.mockk:mockk:${version.core}"
        }
    }
}
