val vglVersion = "0.3.12"

version = vglVersion

val vglBuild: String = run {
    try {
        val out = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
        if (out.isBlank()) "unknown" else out
    } catch (_: Exception) {
        "unknown"
    }
}

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
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

application {
    mainClass.set("com.vgl.cli.VglMain")
    applicationDefaultJvmArgs = listOf(
        "-Dvgl.version=${project.version}",
        "-Dvgl.build=$vglBuild"
    )
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
    filter {
        includeTestsMatching("*")
    }
    include("**/*.class")
    
    // Show test output in real-time
    testLogging {
        events("standardOut")
        showStandardStreams = true
        showExceptions = false
    }
}

// Integration test task: run tests tagged with 'integration' and ensure
// the installed distribution exists so the tests can invoke the `vgl` binary.
tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
    dependsOn(tasks.named("installDist"))
    // Use the test output and runtime classpath
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    testLogging {
        events("standardOut")
        showStandardStreams = true
        showExceptions = true
    }
}

// Smoke test task: runs a small, fast subset of tests tagged "smoke".
tasks.register<Test>("smokeTest") {
    useJUnitPlatform {
        includeTags("smoke")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    testLogging {
        events("standardOut")
        showStandardStreams = true
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "VGL",
            "Implementation-Version" to project.version,
            "Implementation-Build" to vglBuild
        )
    }
}

// Ensure the wrapper task uses a compatible Gradle version
tasks.wrapper {
    gradleVersion = "8.3" // Set to a stable version
    distributionType = Wrapper.DistributionType.BIN
}
