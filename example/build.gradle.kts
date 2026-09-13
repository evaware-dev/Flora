plugins {
    id("application")
}

application {
    mainClass.set("example.Main")
}

dependencies {
    implementation(rootProject)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}
