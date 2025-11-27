val vglVersion = "0.3.12"

plugins {
    application
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23)) // Updated to Java 23
    }
}

repositories {
    mavenCentral()
}

dependencies {
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

application {
    mainClass.set("com.vgl.cli.VglMain")
    applicationDefaultJvmArgs = listOf("-Dvgl.version=${project.version}")
}

tasks.test {
    useJUnitPlatform()
    filter {
        includeTestsMatching("*")
    }
    include("**/*.class")
    
    // Show test output in real-time
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

version = vglVersion

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "VGL",
            "Implementation-Version" to project.version
        )
    }
}

// Ensure the wrapper task uses a compatible Gradle version
tasks.wrapper {
    gradleVersion = "8.3" // Set to a stable version
    distributionType = Wrapper.DistributionType.BIN
}
