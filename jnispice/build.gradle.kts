plugins {
    `java-library`
}

java.sourceCompatibility = JavaVersion.VERSION_1_8

description = "jnispice"
version = "N0067"

tasks.jar {
    from(fileTree("JNISpice/classes") {
        include("*.class")
        into("spice/basic")
    })
}

val precompiledClasses by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("precompiledClasses", tasks.jar)
}
