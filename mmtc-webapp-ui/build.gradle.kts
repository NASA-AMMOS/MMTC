plugins {
    base
}

val nuxtBuild = tasks.register<Exec>("nuxtBuild") {
    inputs.dir(projectDir.toPath().resolve("mmtc-webapp-ui/app"))
    inputs.dir(projectDir.toPath().resolve("mmtc-webapp-ui/public"))

    inputs.file(projectDir.toPath().resolve("mmtc-webapp-ui/nuxt.config.ts"))
    inputs.file(projectDir.toPath().resolve("mmtc-webapp-ui/pnpm-lock.yaml"))
    inputs.file(projectDir.toPath().resolve("mmtc-webapp-ui/tsconfig.json"))

    workingDir("mmtc-webapp-ui")

    executable("npx")
    args("nuxt", "generate")

    outputs.dir("mmtc-webapp-ui/.output/public/")
}

tasks {
    build {
        dependsOn(nuxtBuild)
    }

    clean {
        dependsOn("cleanNuxtBuild")
    }
}
