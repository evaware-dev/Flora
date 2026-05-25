plugins {
    id("java")
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testImplementation("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs lightweight benchmark harness."
    dependsOn(tasks.testClasses)
    mainClass.set("benchmark.Benchmarks")
    classpath = sourceSets.test.get().runtimeClasspath
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs JMH benchmarks."
    dependsOn(tasks.testClasses)
    mainClass.set("org.openjdk.jmh.Main")
    classpath = sourceSets.test.get().runtimeClasspath
    args(
        "benchmark.FloraVsBlazingJmhBenchmark.*",
        "-wi", "3",
        "-i", "5",
        "-f", "1",
        "-tu", "ns"
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.github.evaware-dev"
            artifactId = "flora"
            version = project.version.toString()
        }
    }

    repositories {
        maven("https://maven.pkg.github.com/evaware-dev/Flora") {
            name = "GitHubPackages"
            credentials {
                username = project.findProperty("systemProp.gpr.user") as String?
                password = project.findProperty("systemProp.gpr.token") as String?
            }
        }
    }
}

tasks.register("release") {
    group = "publishing"
    description = "Upload new version: update version, commit, tag, push"

    doLast {
        val newVersion = project.findProperty("newVersion") as String?
            ?: throw GradleException("./gradlew release -PnewVersion=<version>")
            
        val propertiesFile = file("gradle.properties")
        val propertiesContent = propertiesFile.readText()
        val updatedContent = propertiesContent.replace(Regex("version=.*"), "version=$newVersion")
        propertiesFile.writeText(updatedContent)
        
        println("Version updated to $newVersion in gradle.properties")
        
        fun git(vararg args: String): String {
            val process = ProcessBuilder("git", *args).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode != 0 && args[0] != "tag") {
                 throw GradleException("Error: git ${args.joinToString(" ")}")
            }
            return output
        }
        
        val status = git("status", "--porcelain")
        if (status.isNotEmpty()) {
            println("Saving")
            git("add", "gradle.properties")
            git("commit", "-m", "Update $newVersion")
        }
        
        println("Creating tag v$newVersion...")
        try { git("tag", "-d", "v$newVersion") } catch (e: Exception) {}
        git("tag", "-a", "v$newVersion", "-m", "Update $newVersion")
        
        println("Push")
        val branch = git("branch", "--show-current")
        git("push", "origin", branch)
        git("push", "origin", "v$newVersion")
        
        println("\nRelease")
        println("Upload package: ./gradlew publish")
    }
}
