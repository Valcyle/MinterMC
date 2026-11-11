plugins {
    id("java")
    id("application")
    // id("com.gradleup.shadow") version "8.3.6"
}

application {
    mainClass.set("net.mcsmash.mintermc.server.MinterServer")
}

distributions {
    main {
        contents {
            from(rootProject.file("LICENSE"))
            from(rootProject.file("THIRD_PARTY_NOTICES.md"))
            from(rootProject.file("CHANGELOG.md"))
            from(rootProject.file("README.md"))
            from(rootProject.file("README.ja.md"))
            from(rootProject.file("LICENSES")) {
                into("LICENSES")
            }
        }
    }
}


dependencies {
    implementation(project(":mintermc-core"))
    implementation(project(":mintermc-auth"))
    
    // Include adapters so they are packaged and available at runtime
    implementation(project(":mintermc-adapter-java"))
    implementation(project(":mintermc-adapter-bedrock"))
    
    // Logging and other utilities for the server
    implementation("org.slf4j:slf4j-simple:2.0.12")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "net.mcsmash.mintermc.server.MinterServer"
        )
    }
}

tasks.named<JavaExec>("run") {
    // Resolve plugins/ and plugin-data/ from the repository root so the layout
    // matches the documentation.
    workingDir = rootProject.projectDir
}
