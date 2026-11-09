plugins {
    id("java-library")
}

dependencies {
    implementation(project(":mintermc-core"))
    implementation(project(":mintermc-auth"))
    
    // MCProtocolLib (JE)
    implementation("com.github.GeyserMC:MCProtocollib:1.21.4-1")
}

