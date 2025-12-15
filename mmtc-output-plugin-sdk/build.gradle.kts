import java.time.Instant

plugins {
    id("mmtc.java-conventions")
}

dependencies {
    compileOnly(project(":mmtc-core"))

    implementation(libs.commons.cli)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.jcl)

    testImplementation(project(":mmtc-core"))
    testImplementation(testlibs.junit.jupiter.api)
    testImplementation(testlibs.junit.jupiter.params)
    testImplementation(testlibs.junit.jupiter.engine)
    testRuntimeOnly(testlibs.junit.platform.launcher)
    testImplementation(testlibs.mockito.inline)
}

description = "mmtc-output-plugin-example"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
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

val createSdkDist = tasks.register("createSdkDist") {
    inputs.dir("src/main")
    inputs.file("create-sdk-zip.sh")

    dependsOn(":mmtc-core:jar")
    dependsOn(":userGuidePdf")
    dependsOn(":mmtc-core:generatePomFileForMmtc-corePublication")

    doLast {
        exec {
            workingDir(project.projectDir)
            commandLine("bash", "create-sdk-zip.sh", project.version)
        }
    }

    outputs.dir("build/mmtc-sdk-tmp")
}
