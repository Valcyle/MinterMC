plugins {
    id("java")
    id("application")
    // id("com.gradleup.shadow") version "8.3.6"
}

application {
    mainClass.set("net.mcsmash.mintermc.server.MinterServer")
}


dependencies {
    implementation(project(":mintermc-core"))
    
    // Include adapters so they are packaged and available at runtime
    implementation(project(":mintermc-adapter-java"))
    implementation(project(":mintermc-adapter-bedrock"))
    
    // Logging and other utilities for the server
    implementation("org.slf4j:slf4j-simple:2.0.12")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "net.mcsmash.mintermc.server.MinterServer"
        )
    }
}
