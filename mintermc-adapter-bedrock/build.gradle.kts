plugins {
    id("java-library")
}

dependencies {
    implementation(project(":mintermc-core"))
    
    // Cloudburst Protocol (BE)
    implementation("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta15-SNAPSHOT")
    implementation("org.bitbucket.b_c:jose4j:0.9.6")
    implementation("com.nimbusds:nimbus-jose-jwt:10.8")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}
