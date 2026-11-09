plugins {
    id("java-library")
}

dependencies {
    // Microsoft/Xbox authentication for Java and Bedrock.
    // MinecraftAuth is LGPL-3.0 and is kept as a separate runtime dependency.
    implementation("net.raphimc:MinecraftAuth:5.0.2")
    implementation("org.slf4j:slf4j-api:2.0.12")
}
