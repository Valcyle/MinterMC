plugins {
    id("java")
    id("application")
}

dependencies {
    // Use the generated gRPC stubs from the protocol module
    implementation(project(":mintermc-protocol"))

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.12")
}

application {
    mainClass.set("net.mcsmash.mintermc.testclient.TestClient")
}
