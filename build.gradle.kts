// --- VGL: decouple build from test, add explicit check task ---
// Remove 'test' from 'build' dependencies so 'build' does not run tests automatically
gradle.taskGraph.whenReady {
    val buildTask = tasks.findByName("build")
    if (buildTask != null) {
        buildTask.setDependsOn(buildTask.dependsOn.filter { it != "test" })
    }
}

// Ensure the existing 'check' task depends on 'test' (do not register a new one)
tasks.named("check") {
    dependsOn("test")
    group = "verification"
    description = "Runs all tests."
}
val vglVersion = "0.3.12"

plugins {
    application
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

application {
    mainClass.set("com.vgl.cli.VglMain")
    applicationDefaultJvmArgs = listOf("-Dvgl.version=${project.version}")
}

version = vglVersion

tasks.test {
    dependsOn("installDist")
    useJUnitPlatform {
        excludeTags("integration")
    }
    // Do not force includeTestsMatching("*") here; this allows --tests CLI filtering to work
    // Removed include("**/*.class") to allow default test discovery and filtering

    // Show test output in real-time
    testLogging {
        events("standardOut")
        showStandardStreams = true
        showExceptions = false
    }
}

tasks.withType<Test> {
    forkEvery = 1
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    doFirst {
        file("$buildDir/testHome").apply { if (!exists()) mkdirs() }
        file("$buildDir/testTemp").apply { if (!exists()) mkdirs() }
    }
    systemProperty("user.home", file("$buildDir/testHome").absolutePath)
    systemProperty("vgl.test.base", file("$buildDir/testTemp").absolutePath)
    systemProperty("vgl.noninteractive", "true")
    systemProperty("junit.jupiter.execution.timeout.default", "1m")
}

tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
    dependsOn(tasks.named("installDist"))
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    testLogging {
        events("standardOut")
        showStandardStreams = true
        showExceptions = true
    }
}

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
            "Implementation-Version" to project.version
        )
    }
}

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

tasks.register<Test>("manualOutputTest") {
    group = "verification"
    description = "Run ManualOutputRunner test which prints status/help output to console"
    useJUnitPlatform()
    include("**/ManualOutputRunner*")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    testLogging.showStandardStreams = true
}

tasks.wrapper {
    gradleVersion = "8.3"
    distributionType = Wrapper.DistributionType.BIN
}