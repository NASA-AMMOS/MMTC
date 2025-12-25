import java.time.Instant

plugins {
    id("mmtc.java-conventions")
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

    compileOnly(project(":mmtc-core"))

    implementation("com.google.guava:guava:33.4.8-jre")

    // provides javax.xml.bind classes
    implementation(libs.jakarta.xml)
    implementation(libs.jaxb.impl)

    implementation(libs.commons.csv)
    implementation(libs.commons.lang3)
    implementation(libs.commons.cli)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.jcl)

    // provides javax.xml.bind classes
    implementation(libs.jakarta.xml)
    implementation(libs.jaxb.impl)

    testImplementation(project(":mmtc-core"))
    testImplementation(testlibs.junit.jupiter.api)
    testImplementation(testlibs.junit.jupiter.params)
    testImplementation(testlibs.junit.jupiter.engine)
    testRuntimeOnly(testlibs.junit.platform.launcher)
    testImplementation(testlibs.mockito.inline)
}

description = "mmtc-plugin-ampcs"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
}

configurations.getByName("testRuntimeClasspath") {
    extendsFrom(precompiledJniSpiceClasses)
}

// configure the default jar task to be an uber-jar, as we don't need this build to also produce a thin/unter-jar
tasks.jar {
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy=DuplicatesStrategy.EXCLUDE

    // not including Build-Date in manifests for now, to avoid excessive rebuilds
    manifest {
        attributes(

                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Multi-Release" to "true"
        )
    }
}

val setupTestPluginDir = tasks.register("setupTestPluginDir") {
    dependsOn(tasks.assemble)

    doLast {
        exec {
            commandLine("bash", "-c", "rm -r build/test-plugin-dir && mkdir build/test-plugin-dir && cp build/libs/mmtc-plugin-ampcs-${project.version}.jar build/test-plugin-dir/")
        }
    }

    outputs.dir("build/test-plugin-dir")
}

tasks.test {
    dependsOn(setupTestPluginDir)
    workingDir("../mmtc-core/")
    environment("CHILL_GDS", "${project.projectDir}/src/test/resources")
}
