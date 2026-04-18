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
            artifactId = "Flora"
            version = project.version.toString()
        }
    }
}
