plugins {
    id("java-library")
}

allprojects {
    group = "net.mcsmash.mintermc"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io/") }
        maven("https://repo.opencollab.dev/maven-releases/") {
            name = "opencollab-releases"
        }
        maven("https://repo.opencollab.dev/maven-snapshots/") {
            name = "opencollab-snapshots"
        }
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        // Enable preview features if needed for Java 25 experimental features in the future
        // options.compilerArgs.add("--enable-preview")
    }
}
