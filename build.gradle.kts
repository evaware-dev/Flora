plugins {
    id("java")
    `maven-publish`
}

group = "com.github.evaware-dev"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "com.github.evaware-dev"
            artifactId = "flora"
            version = "1.0"
        }
    }

    repositories {
        maven {
            name = "Local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}