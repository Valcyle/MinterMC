plugins {
    id("java-library")
}

dependencies {
    implementation(project(":mintermc-core"))
    
    // Cloudburst Protocol (BE)
    implementation("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta12-SNAPSHOT")
}
