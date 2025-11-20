val vglVersion = "0.3.12"

plugins {
    application
    java
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(23)) }
}

repositories { mavenCentral() }

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
