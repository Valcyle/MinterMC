plugins {
    id("java-library")
}

dependencies {
    // Core depends on our API and Protocol
    api(project(":mintermc-plugin-api"))
    api(project(":mintermc-protocol"))
    
    // Logging framework
    implementation("org.slf4j:slf4j-api:2.0.12")
}
