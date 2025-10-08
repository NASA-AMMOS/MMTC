import java.io.ByteArrayOutputStream
import java.time.Instant

plugins {
    id("java-conventions")
    `maven-publish`
}

val precompiledJniSpiceClasses by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    precompiledJniSpiceClasses(project(mapOf(
        "path" to ":jnispice",
        "configuration" to "precompiledClasses"
    )))

    // provides javax.xml.bind classes
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("com.sun.xml.bind:jaxb-impl:4.0.2")

    implementation("commons-beanutils:commons-beanutils:1.11.0")
    implementation("org.apache.commons:commons-configuration2:2.12.0")
    implementation("com.google.guava:guava:33.4.8-jre")

    implementation("org.jdbi:jdbi3-core:3.39.1")
    implementation("org.jdbi:jdbi3-sqlite:3.39.1")
    implementation(libs.log4j.slf4j) // jdbi3-core uses slf4j-api, and we need to provide it a logging implementation
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")

    implementation(libs.commons.cli)
    implementation(libs.commons.csv)
    implementation(libs.commons.lang3)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.jcl)
    implementation(libs.commons.io)

    testImplementation(testlibs.junit.jupiter.api)
    testImplementation(testlibs.junit.jupiter.params)
    testImplementation(testlibs.junit.jupiter.engine)
    testRuntimeOnly(testlibs.junit.platform.launcher)
    testImplementation(testlibs.mockito.inline)
}

description = "mmtc-core"

java {
    withJavadocJar()
    withSourcesJar()
}

configurations.getByName("compileClasspath") {
    extendsFrom(precompiledJniSpiceClasses)
}

configurations.getByName("runtimeClasspath") {
    extendsFrom(precompiledJniSpiceClasses)
}

configurations.getByName("testCompileClasspath") {
    extendsFrom(precompiledJniSpiceClasses)
}

configurations.getByName("testRuntimeClasspath") {
    extendsFrom(precompiledJniSpiceClasses)
}

val uberJar = tasks.register<Jar>("uberJar") {
    archiveClassifier.set("app")

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)

    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy=DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
                "Main-Class" to "edu.jhuapl.sd.sig.mmtc.app.MmtcCli",
                "Build-Date" to Instant.now().toString(),
                "Implementation-Version" to project.version,
                "Implementation-Title" to project.name,
                "Multi-Release" to "true"
        )
    }
}

val writeVersionDescriptionFile = tasks.register("writeVersionDescriptionFile") {
    // set this task to never be up-to-date, so it is always rerun.  this ensures the build date and commit hash accurately represent the origin of the output artifacts of this build
    outputs.upToDateWhen { false }

    doLast {
        val versionString = "version=" + project.version
        val buildDate = "buildDate=" + Instant.now().toString()

        val execStdOut = ByteArrayOutputStream()

        exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = execStdOut
        }

        val commit = "commit=" + execStdOut.toString()

        project.projectDir.resolve("src/main/resources/version-description.properties")
            .writeText(arrayOf(versionString, buildDate, commit).joinToString("\n"))
    }
}

tasks.getByName("cleanWriteVersionDescriptionFile") {
    doLast {
        project.projectDir.resolve("src/main/resources/version-description.properties").delete()
    }
}

tasks.clean {
    dependsOn(tasks.getByName("cleanWriteVersionDescriptionFile"))
}

tasks.processResources {
    dependsOn(writeVersionDescriptionFile)
}

tasks.build {
    dependsOn(uberJar)
}

publishing {
    publications {
        create<MavenPublication>("mmtc-core") {
            from(components["java"])
        }
    }
}
