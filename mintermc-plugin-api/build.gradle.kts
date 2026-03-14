plugins {
    id("java-library")
}

dependencies {
    // This module should have ZERO dependencies on Minecraft-specific protocols or gRPC
    // Only pure Java 25 standard library and perhaps very lightweight utilities
}
