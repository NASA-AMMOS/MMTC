import java.time.Instant

plugins {
    id("mmtc.java-conventions")
}

val precompiledJniSpiceClasses by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    precompiledJniSpiceClasses(project(mapOf(
        "path" to ":jnispice",
        "configuration" to "precompiledClasses"
    )))

    implementation(project(":mmtc-core"))
    implementation("io.javalin:javalin:6.7.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.jcl)

    testImplementation(project(":mmtc-core"))
    testImplementation(testlibs.junit.jupiter.api)
    testImplementation(testlibs.junit.jupiter.params)
    testImplementation(testlibs.junit.jupiter.engine)
    testRuntimeOnly(testlibs.junit.platform.launcher)
}

description = "mmtc-webapp"

java {
    withJavadocJar()
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

val copyBuiltWebappUiIntoSrc = tasks.register<Copy>("copyBuiltWebappUiIntoSrc") {
    dependsOn(project(":mmtc-webapp-ui").tasks.getByName("nuxtBuild"))
    from(project(":mmtc-webapp-ui").projectDir.toPath().resolve("mmtc-webapp-ui/.output/public/"))
    into(projectDir.toPath().resolve("src/main/resources/static"))
}

val copyBuiltUserDocIntoSrc = tasks.register<Copy>("copyBuiltUserDocIntoSrc") {
    dependsOn(rootProject.tasks.getByName("userGuidePdf"))
    from(rootProject.projectDir.toPath().resolve("build/docs/MMTC_Users_Guide.pdf"))
    into(projectDir.toPath().resolve("src/main/resources/docs"))
}

tasks.processResources {
    dependsOn(copyBuiltWebappUiIntoSrc)
    dependsOn(copyBuiltUserDocIntoSrc)
}

tasks.clean {
    dependsOn("cleanCopyBuiltWebappUiIntoSrc")
}

/*
val runServer = tasks.register<JavaExec>("runServer") {
    dependsOn(copyBuiltWebappUiIntoSrc)
    classpath(sourceSets.main.get().runtimeClasspath)
    mainClass.set("edu.jhuapl.sd.sig.mmtc.webapp")
}
 */

// configure the default jar task to be an uber-jar, as we don't need this build to also produce a thin/unter-jar
tasks.jar {
    dependsOn(":mmtc-core:jar")

    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy=DuplicatesStrategy.EXCLUDE

    // not including Build-Date in manifests for now, to avoid excessive rebuilds
    manifest {
        attributes(
            "Main-Class" to "edu.jhuapl.sd.sig.mmtc.webapp.MmtcWebApp",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Multi-Release" to "true"
        )
    }
}
