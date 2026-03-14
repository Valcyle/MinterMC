pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        // For MCProtocolLib and Cloudburst
        maven { url = uri("https://jitpack.io/") }
        maven("https://repo.opencollab.dev/maven-releases/") {
            name = "opencollab-releases"
        }
        maven("https://repo.opencollab.dev/maven-snapshots/") {
            name = "opencollab-snapshots"
        }
    }
}

rootProject.name = "MinterMC"

include("mintermc-plugin-api")
include("mintermc-protocol")
include("mintermc-core")
include("mintermc-adapter-java")
include("mintermc-adapter-bedrock")
include("mintermc-server")
include("mintermc-test-client")
