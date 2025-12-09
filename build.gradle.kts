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

version = vglVersion

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "VGL",
            "Implementation-Version" to project.version
        )
    }
}

// Strip UTF-8 BOM from Java source files to avoid compiler errors on Windows
tasks.register("stripBom") {
    doLast {
        val javaRoots = listOf(file("src/main/java"), file("src/test/java"))
        javaRoots.forEach { root ->
            if (!root.exists()) return@forEach
            fileTree(root).matching { include("**/*.java") }.forEach { f ->
                val text = f.readText(Charsets.UTF_8)
                if (text.isNotEmpty() && text[0] == '\uFEFF') {
                    val cleaned = text.trimStart('\uFEFF')
                    f.writeText(cleaned, Charsets.UTF_8)
                    println("[stripBom] Removed BOM from: ${f.relativeTo(projectDir)}")
                }
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("stripBom")
}

// Quick JavaExec tasks to run the CLI for manual inspection (status/help)
val runtimeCp = sourceSets["main"].runtimeClasspath

tasks.register<JavaExec>("vglStatus") {
    group = "verification"
    description = "Run vgl status (default verbosity) using the main class"
    classpath = runtimeCp
    mainClass.set("com.vgl.cli.VglMain")
    args = listOf("status")
}

tasks.register<JavaExec>("vglStatusV") {
    group = "verification"
    description = "Run vgl status -v"
    classpath = runtimeCp
    mainClass.set("com.vgl.cli.VglMain")
    args = listOf("status", "-v")
}

tasks.register<JavaExec>("vglStatusVV") {
    group = "verification"
    description = "Run vgl status -vv"
    classpath = runtimeCp
    mainClass.set("com.vgl.cli.VglMain")
    args = listOf("status", "-vv")
}

tasks.register<JavaExec>("vglHelp") {
    group = "help"
    description = "Run vgl help"
    classpath = runtimeCp
    mainClass.set("com.vgl.cli.VglMain")
    args = listOf("help")
}

tasks.register<JavaExec>("vglHelpV") {
    group = "help"
    description = "Run vgl help -v"
    classpath = runtimeCp
    mainClass.set("com.vgl.cli.VglMain")
    args = listOf("help", "-v")
}

tasks.register<JavaExec>("vglHelpVV") {
    group = "help"
    description = "Run vgl help -vv"
    classpath = runtimeCp
    mainClass.set("com.vgl.cli.VglMain")
    args = listOf("help", "-vv")
}

// Test task to run a single output-only test that prints status/help using the test harness.
tasks.register<Test>("manualOutputTest") {
    group = "verification"
    description = "Run ManualOutputRunner test which prints status/help output to console"
    useJUnitPlatform()
    include("**/ManualOutputRunner*")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    testLogging.showStandardStreams = true
}

// Ensure the wrapper task uses a compatible Gradle version
tasks.wrapper {
    gradleVersion = "8.3" // Set to a stable version
    distributionType = Wrapper.DistributionType.BIN
}
