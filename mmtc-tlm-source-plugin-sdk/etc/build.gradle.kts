import java.time.Instant

plugins {
    `java-library`
}

java.sourceCompatibility = JavaVersion.VERSION_1_8

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

repositories {
    maven {
        url = uri("file://${project.rootProject.projectDir}/lib/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    compileOnly("edu.jhuapl.sd.sig:mmtc-core:@VERSION@")

    implementation(libs.commons.cli)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.jcl)

    testImplementation(files("lib/mmtc-core-@VERSION@.jar"))
    testImplementation(testlibs.junit.jupiter.api)
    testImplementation(testlibs.junit.jupiter.params)
    testImplementation(testlibs.junit.jupiter.engine)
    testImplementation(testlibs.mockito.inline)
}

group = "edu.jhuapl.sd.sig"
version = "1.0.0-SNAPSHOT"
description = "mmtc-plugin-example"

java {
    withJavadocJar()
}

// configure the default jar task to be an uber-jar, as we don't need this build to also produce a thin/unter-jar
tasks.jar {
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy=DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Build-Date" to Instant.now().toString(),
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
