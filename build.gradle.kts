plugins {
    id("java")
    id("maven-publish")
}

val jitPackBuild = System.getenv("JITPACK") == "true"
val remoteRepository = runCatching {
    providers.exec {
        commandLine("git", "config", "--get", "remote.origin.url")
    }.standardOutput.asText.get().trim()
}.getOrNull()?.let { remote ->
    Regex("github\\.com[/:]([^/]+/[^/]+?)(?:\\.git)?$")
        .find(remote)
        ?.groupValues
        ?.get(1)
}
val githubRepository = System.getenv("GITHUB_REPOSITORY")
    ?: project.findProperty("githubRepository") as String?
    ?: remoteRepository
val githubProjectUrl = githubRepository?.let { "https://github.com/$it" }
val publicationArtifactId = if (jitPackBuild) System.getenv("ARTIFACT") else null
if (jitPackBuild) {
    group = System.getenv("GROUP") ?: group
    version = System.getenv("VERSION") ?: version
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
    testAnnotationProcessor(files(sourceSets.main.get().output))
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
    from(rootProject.file("NOTICE")) {
        into("META-INF")
    }
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
        "benchmark.benchmarks.*JmhBenchmark.*",
        "-wi", "3",
        "-i", "5",
        "-f", "1",
        "-tu", "ns"
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = publicationArtifactId ?: rootProject.name
            version = project.version.toString()
            pom {
                name.set(rootProject.name.replaceFirstChar(Char::uppercaseChar))
                description.set("Fast, lightweight event bus for Java")
                githubProjectUrl?.let(url::set)
                licenses {
                    license {
                        name.set("GNU Lesser General Public License v3.0 only")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.html")
                        distribution.set("repo")
                    }
                }
                if (githubProjectUrl != null) {
                    scm {
                        connection.set("scm:git:$githubProjectUrl.git")
                        developerConnection.set("scm:git:ssh://git@github.com/$githubRepository.git")
                        url.set(githubProjectUrl)
                    }
                }
            }
        }
    }

    repositories {
        if (githubRepository != null) {
            maven("https://maven.pkg.github.com/$githubRepository") {
                name = "GitHubPackages"
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                        ?: System.getProperty("gpr.user")
                    password = System.getenv("GITHUB_TOKEN")
                        ?: System.getProperty("gpr.token")
                }
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
