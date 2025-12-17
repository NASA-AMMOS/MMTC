import java.io.ByteArrayOutputStream
import java.time.Instant

plugins {
    id("mmtc.java-conventions")
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
    implementation(libs.jakarta.xml)
    implementation(libs.jaxb.impl)

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

    // provides javax.xml.bind classes
    implementation(libs.jakarta.xml)
    implementation(libs.jaxb.impl)

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

    // not including Build-Date in manifests for now, to avoid excessive rebuilds
    manifest {
        attributes(
                "Main-Class" to "edu.jhuapl.sd.sig.mmtc.app.MmtcCli",
                "Implementation-Version" to project.version,
                "Implementation-Title" to project.name,
                "Multi-Release" to "true"
        )
    }
}


fun getCurrentCommitShortHash(): String {
    val process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output.trim()
}

fun calculateNewVersionDescriptionFileContents(): String {
    val versionString = "version=" + project.version
    val buildDate = "buildDate=" + Instant.now().toString()

    val commit = "commit=" + getCurrentCommitShortHash()

    return arrayOf(versionString, buildDate, commit).joinToString("\n")
}

val writeVersionDescriptionFile = tasks.register("writeVersionDescriptionFile") {
    val versionDescriptionFilepath = project.layout.projectDirectory.file("src/main/resources/version-description.properties").asFile
    val currentCommitHash = getCurrentCommitShortHash()

    outputs.file(versionDescriptionFilepath)

    // if the current version file's hash has not changed, do not mark this task dirty (to avoid an unnecessary rebuild, as at this point only the build date could be out of date)
    outputs.upToDateWhen {
        versionDescriptionFilepath.exists() && versionDescriptionFilepath.readText().contains(currentCommitHash)
    }

    doLast {
        project.projectDir.resolve("src/main/resources/version-description.properties").writeText(calculateNewVersionDescriptionFileContents())
    }
}

tasks.getByName("sourcesJar") {
    dependsOn(writeVersionDescriptionFile)
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
