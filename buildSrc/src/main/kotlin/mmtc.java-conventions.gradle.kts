import com.github.spotbugs.snom.Confidence

plugins {
    `java-library`
    jacoco
    id("com.github.spotbugs")
    id("org.owasp.dependencycheck")
}

java.sourceCompatibility = JavaVersion.VERSION_1_8

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

tasks.test {
    extensions.configure(JacocoTaskExtension::class) {
        includes = listOf("edu.jhuapl.*")
    }
}

dependencies {
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
}

// --------------------------------------------
// SpotBugs configuration
// --------------------------------------------

spotbugs {
    ignoreFailures.set(true)             // todo set to 'false' to fail build upon failures found in spotbugs tasks
    reportLevel.set(Confidence.MEDIUM)
}

tasks.spotbugsMain {
    enabled = true
    reports.create("html") {
        required.set(true)
        outputLocation.set(file("$buildDir/reports/spotbugs.html"))
        setStylesheet("fancy-hist.xsl")
    }
}

tasks.spotbugsTest {
    enabled = false    // disable SCA for test files, for now
    reports.create("html") {
        required.set(true)
        outputLocation.set(file("$buildDir/reports/tests/spotbugsTest.html"))
        setStylesheet("fancy-hist.xsl")
    }
}

// --------------------------------------------
// OWASP Dependency Check configuration
// --------------------------------------------

dependencyCheck {
    failBuildOnCVSS = 7.toFloat() // range is 1 to 10, 7 and above is High and Critical
    suppressionFile = file("../buildSrc/src/main/resources/owasp/owasp-dependency-check-suppressions.xml").toString()
}