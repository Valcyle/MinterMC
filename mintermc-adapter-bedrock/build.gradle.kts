plugins {
    id("java-library")
}

dependencies {
    implementation(project(":mintermc-core"))
    implementation(project(":mintermc-auth"))
    
    // Cloudburst Protocol (BE)
    implementation("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta15-SNAPSHOT")
    implementation("org.cloudburstmc.netty:netty-transport-nethernet:2.0.0.CR6-SNAPSHOT")
    implementation("dev.opencollab:libdatachannel-java:0.24.6.0-20261022.190039-8")
    // libdatachannel ships one native jar per platform and every jar stores the
    // library at the same class path, so only the host platform may be included.
    val nativeClassifier = run {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        when {
            os.contains("win") && (arch.contains("aarch64") || arch.contains("arm64")) -> "windows-aarch64"
            os.contains("win") -> "windows-x86_64"
            os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64")) -> "macos-arm64"
            os.contains("mac") -> "macos-x86_64"
            os.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64")) -> "linux-glibc-aarch64"
            os.contains("linux") -> "linux-glibc-x86_64"
            else -> null
        }
    }
    if (nativeClassifier != null) {
        runtimeOnly("dev.opencollab:libdatachannel-java:0.24.6.0-20261022.190039-8:$nativeClassifier")
    }
    implementation("org.bitbucket.b_c:jose4j:0.9.6")
    implementation("com.nimbusds:nimbus-jose-jwt:10.8")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}
