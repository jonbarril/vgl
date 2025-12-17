import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.File
import java.lang.Runtime

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
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13") // Ensure SLF4J logger binding is present during tests
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

// Force all test JVMs to use an isolated test home and test base directory
// to avoid touching the developer's real user home or repos during tests.
tasks.withType<Test> {
    // Run each test class in its own JVM so a hung class doesn't block others.
    forkEvery = 1
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    // Ensure directories exist before tests run
    doFirst {
        file("$buildDir/testHome").apply { if (!exists()) mkdirs() }
        file("$buildDir/testTemp").apply { if (!exists()) mkdirs() }
    }

    // Redirect user.home to an isolated directory inside the build folder
    systemProperty("user.home", file("$buildDir/testHome").absolutePath)

    // Provide a dedicated base directory that tests can use for remote/local repo scaffolding
    systemProperty("vgl.test.base", file("$buildDir/testTemp").absolutePath)
    // Ensure tests run in non-interactive mode so code paths that prompt won't block
    systemProperty("vgl.noninteractive", "true")
    // Fail individual tests that run too long by default (1 minute)
    systemProperty("junit.jupiter.execution.timeout.default", "1m")

    // Log slow tests and capture timings via a TestListener
    val _testStartTimes = mutableMapOf<String, Long>()
    // Monitor to detect long-running tests that might hang outside JUnit's thread preemption.
    val monitor = Executors.newSingleThreadScheduledExecutor()
    var monitorFuture: java.util.concurrent.ScheduledFuture<*>? = null

    fun collectHangDiagnostics(reason: String) {
        try {
            val outdir = file("$buildDir/reports/hangs")
            if (!outdir.exists()) outdir.mkdirs()
            val now = System.currentTimeMillis()
            val dumpFile = File(outdir, "hang-${now}.txt")
            dumpFile.appendText("Hang diagnostics triggered: $reason\n")
            try {
                val listPb = ProcessBuilder("jcmd", "-l").redirectErrorStream(true).start()
                val listText = listPb.inputStream.bufferedReader().readText()
                dumpFile.appendText("jcmd -l output:\n")
                dumpFile.appendText(listText)
                val pidRegex = Regex("^(\\d+)\\s", RegexOption.MULTILINE)
                val pids = pidRegex.findAll(listText).map { it.groupValues[1] }.toList()
                for (pid in pids) {
                    dumpFile.appendText("\n\n=== Thread dump for PID $pid ===\n")
                    try {
                        val pb = ProcessBuilder("jcmd", pid, "Thread.print").redirectErrorStream(true).start()
                        dumpFile.appendText(pb.inputStream.bufferedReader().readText())
                    } catch (ie: Exception) {
                        dumpFile.appendText("Failed to run jcmd Thread.print for $pid: ${ie.message}\n")
                    }
                }
            } catch (e: Exception) {
                dumpFile.appendText("Failed to run jcmd: ${e.message}\n")
            }
            logger.lifecycle("Wrote hang diagnostics to ${dumpFile.absolutePath}")
        } catch (e: Exception) {
            logger.warn("Failed to collect hang diagnostics: ${e.message}")
        }
    }

    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeTest(descriptor: org.gradle.api.tasks.testing.TestDescriptor) {
            _testStartTimes[descriptor.displayName] = System.nanoTime()
        }

        override fun afterTest(descriptor: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            val start = _testStartTimes.remove(descriptor.displayName)
            if (start != null) {
                    val elapsedMs = (System.nanoTime() - start) / 1_000_000L
                if (elapsedMs > 15_000L) {
                    logger.lifecycle("SLOW TEST: ${descriptor.displayName} took ${elapsedMs}ms (threshold 15s)")
                }
                // If a test has likely hung ( > 1m ), attempt to capture thread dumps for diagnostics.
                if (elapsedMs > 60_000L) {
                    logger.lifecycle("HANG LIKELY: ${descriptor.displayName} took ${elapsedMs}ms - capturing diagnostics (1m threshold)...")
                    try {
                        val outdir = file("$buildDir/reports/hangs")
                        if (!outdir.exists()) outdir.mkdirs()
                        val now = System.currentTimeMillis()
                        val dumpFile = File(outdir, "hang-${now}.txt")
                        // Try to list Java processes via jcmd and then request Thread.print for each PID.
                        try {
                            val listPb = ProcessBuilder("jcmd", "-l").redirectErrorStream(true).start()
                            val listText = listPb.inputStream.bufferedReader().readText()
                            dumpFile.appendText("jcmd -l output:\n")
                            dumpFile.appendText(listText)
                            val pidRegex = Regex("^(\\d+)\\s", RegexOption.MULTILINE)
                            val pids = pidRegex.findAll(listText).map { it.groupValues[1] }.toList()
                            for (pid in pids) {
                                dumpFile.appendText("\n\n=== Thread dump for PID $pid ===\n")
                                try {
                                    val pb = ProcessBuilder("jcmd", pid, "Thread.print").redirectErrorStream(true).start()
                                    dumpFile.appendText(pb.inputStream.bufferedReader().readText())
                                } catch (ie: Exception) {
                                    dumpFile.appendText("Failed to run jcmd Thread.print for $pid: ${ie.message}\n")
                                }
                            }
                        } catch (e: Exception) {
                            dumpFile.appendText("Failed to run jcmd: ${e.message}\n")
                        }
                        logger.lifecycle("Wrote hang diagnostics to ${dumpFile.absolutePath}")
                    } catch (e: Exception) {
                        logger.warn("Failed to collect hang diagnostics: ${e.message}")
                    }
                }
            }
        }

        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {
            // Start monitor when root suite begins
            if (suite.parent == null) {
                monitorFuture = monitor.scheduleAtFixedRate({
                    try {
                        val nowNs = System.nanoTime()
                        for ((name, start) in _testStartTimes.entries) {
                            val elapsedMs = (nowNs - start) / 1_000_000L
                            if (elapsedMs > 60_000L) {
                                            logger.lifecycle("MONITOR: Detected long-running test '$name' (${elapsedMs}ms). Collecting diagnostics and aborting build (1m threshold).")
                                            collectHangDiagnostics("Long-running test: $name (${elapsedMs}ms)")
                                // Forcefully halt the JVM so the CI/watchdog and logs contain the dumps.
                                Runtime.getRuntime().halt(99)
                            }
                        }
                    } catch (t: Throwable) {
                        logger.warn("Monitor thread failed: ${t.message}")
                    }
                }, 5, 5, TimeUnit.SECONDS)
            }
        }

        override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            // Stop monitor once root suite finishes
            if (suite.parent == null) {
                try {
                    monitorFuture?.cancel(true)
                    monitor.shutdownNow()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    })
}

// Help/status manual checks are available via the `manualOutputTest` task or the JavaExec helpers:
// - Run the console-printing test: `./gradlew manualOutputTest`
// - Run the CLI directly: `./gradlew vglStatus` or `./gradlew vglHelp`

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
