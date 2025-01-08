rootProject.name = "mmtc-plugin-example"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            library("commons-csv", "org.apache.commons:commons-csv:1.6")
            library("commons-lang3", "org.apache.commons:commons-lang3:3.9")
            library("commons-cli", "commons-cli:commons-cli:1.4")

            library("log4j-api", "org.apache.logging.log4j:log4j-api:2.19.0")
            library("log4j-core", "org.apache.logging.log4j:log4j-core:2.19.0")
            library("log4j-jcl", "org.apache.logging.log4j:log4j-jcl:2.19.0")
        }

        create("testlibs") {
            library("junit-jupiter-api", "org.junit.jupiter:junit-jupiter-api:5.6.0")
            library("junit-jupiter-params", "org.junit.jupiter:junit-jupiter-params:5.6.0")
            library("junit-jupiter-engine", "org.junit.jupiter:junit-jupiter-engine:5.6.0")
            library("mockito-inline", "org.mockito:mockito-inline:4.11.0")
        }
    }
}