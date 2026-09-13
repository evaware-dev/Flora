plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject)
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs lightweight benchmark harness."
    dependsOn(tasks.classes)
    mainClass.set("benchmark.Benchmarks")
    classpath = sourceSets.main.get().runtimeClasspath
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs JMH benchmarks."
    dependsOn(tasks.classes)
    mainClass.set("org.openjdk.jmh.Main")
    classpath = sourceSets.main.get().runtimeClasspath
    val target = (project.findProperty("include") ?: "benchmark.benchmarks.*JmhBenchmark.*").toString()
    args(
        target,
        "-wi", "3",
        "-i", "5",
        "-f", "1",
        "-tu", "ns"
    )
}
