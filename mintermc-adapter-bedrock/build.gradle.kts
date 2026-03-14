plugins {
    id("java-library")
}

dependencies {
    implementation(project(":mintermc-core"))
    
    // Cloudburst Protocol (BE)
    implementation("org.cloudburstmc.protocol:bedrock:3.0.0.Beta1-SNAPSHOT") // Example version
}
