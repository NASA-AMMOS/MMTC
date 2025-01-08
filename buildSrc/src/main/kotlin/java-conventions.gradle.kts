plugins {
    `java-library`
    jacoco
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
