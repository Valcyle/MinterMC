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
        maven { url = uri("https://repo.opencollab.dev/main/") }
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
