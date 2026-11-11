plugins {
    id("java-library")
}

allprojects {
    group = "net.mcsmash.mintermc"
    version = "0.1.0"

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
        // The build runs on JDK 25 but the published classes target Java 21 LTS,
        // so users only need a Java 21 runtime.
        options.release.set(21)
        // Enable preview features if needed for Java 25 experimental features in the future
        // options.compilerArgs.add("--enable-preview")
    }
}
