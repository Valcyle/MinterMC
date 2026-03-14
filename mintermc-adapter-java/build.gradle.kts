plugins {
    id("java-library")
}

dependencies {
    implementation(project(":mintermc-core"))
    
    // MCProtocolLib (JE)
    implementation("com.github.steveice10:MCProtocolLib:1.20.4-1") // Update version as needed for 1.20+
    
    // Event library used by MCProtocolLib
    implementation("com.github.steveice10:packetlib:3.0")
}
