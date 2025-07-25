import com.netflix.gradle.plugins.packaging.CopySpecEnhancement.permissionGroup
import com.netflix.gradle.plugins.packaging.CopySpecEnhancement.user
import com.netflix.gradle.plugins.rpm.Rpm
import org.asciidoctor.gradle.jvm.pdf.AsciidoctorPdfTask

plugins {
    distribution
    id("com.netflix.nebula.ospackage") version "11.11.2"
    id("org.asciidoctor.jvm.convert") version "4.0.4"
    id("org.asciidoctor.jvm.pdf") version "4.0.4"
}

allprojects {
    group = "edu.jhuapl.sd.sig"
    version = "1.6.0-SNAPSHOT"

    repositories {
        maven {
            url = uri("https://repo.maven.apache.org/maven2/")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        jvmArgs("-Djava.library.path=${project.rootProject.projectDir}/jnispice/JNISpice/lib/")
        environment("MMTC_HOME", "${project.rootProject.projectDir}/mmtc-core/")
        environment("TK_CONFIG_PATH", "${project.rootProject.projectDir}/mmtc-core/src/test/resources")
    }
}

asciidoctorj {
    fatalWarnings("Errno")
}

val asciidoctor = tasks.register<AsciidoctorPdfTask>("userGuidePdf") {
    inputs.files("docs/MMTC_Users_Guide.adoc")
    inputs.files("docs/themes/basic/basic-theme.yml")

    setBaseDir(file("docs"))
    setSourceDir(file("docs"))
    setOutputDir(file("build/docs"))
    setTheme("basic")

    outputs.dir("build/docs")
}

pdfThemes {
    local("basic") {
        themeDir = file("docs/themes/basic")
        themeName = "basic"
    }
}

val createDistDir = tasks.register("createDistDir") {
    inputs.files("mmtc-core/bin/mmtc")
    inputs.files("mmtc-core/build/libs/mmtc-core-" + project.version + "-app.jar")
    inputs.files("mmtc-plugin-ampcs/build/libs/mmtc-plugin-ampcs-" + project.version + ".jar")

    dependsOn("mmtc-core:uberJar")
    dependsOn("mmtc-plugin-ampcs:jar")
    dependsOn(":userGuidePdf")

    doLast {
        exec {
            commandLine("bash", "create-dist.sh", project.version)
        }
    }

    outputs.dir("build/mmtc-dist-tmp")
}

val mmtcEl8Rpm = tasks.register<Rpm>("mmtcEl8Rpm") {
    dependsOn(tasks.build)

    distribution = "el8"
    release = "1.$distribution"
    archStr = "x86_64"
    os = org.redline_rpm.header.Os.LINUX

    preInstall(file(projectDir.toPath().resolve("rpm-scripts/pre-install.sh")))
    postInstall(file(projectDir.toPath().resolve("rpm-scripts/post-install.sh")))

    user(this, "mmtc")
    permissionGroup(this, "mmtc")

    //make the RPM relocatable using prefix(). however, also set the default location using into();
    //otherwise, if you install without specifying a custom "--prefix", it gets installed in /usr
    //(at least that's what happened in my local testing).
    prefix("/opt/local/mmtc")
    into("/opt/local/mmtc")

    //copy the distribution folder, preserving permissions since they're already correct.
    //NOTE: this doesn't seem to set *directory* permissions, so we use post-install.sh for that.
    from(projectDir.toPath().resolve("build/mmtc-dist-tmp"))

    //NOTE: create-dist.sh updates bin/mmtc to point to the correct (newly installed) jar file,
    //so we no longer create a symlink at lib/mmtc.jar
}

val mmtcEl9Rpm = tasks.register<Rpm>("mmtcEl9Rpm") {
    dependsOn(tasks.build)

    distribution = "el9"
    release = "1.$distribution"
    archStr = "x86_64"
    os = org.redline_rpm.header.Os.LINUX

    preInstall(file(projectDir.toPath().resolve("rpm-scripts/pre-install.sh")))
    postInstall(file(projectDir.toPath().resolve("rpm-scripts/post-install.sh")))

    user(this, "mmtc")
    permissionGroup(this, "mmtc")

    //make the RPM relocatable using prefix(). however, also set the default location using into();
    //otherwise, if you install without specifying a custom "--prefix", it gets installed in /usr
    //(at least that's what happened in my local testing).
    prefix("/opt/local/mmtc")
    into("/opt/local/mmtc")

    //copy the distribution folder, preserving permissions since they're already correct.
    //NOTE: this doesn't seem to set *directory* permissions, so we use post-install.sh for that.
    from(projectDir.toPath().resolve("build/mmtc-dist-tmp"))

    //NOTE: create-dist.sh updates bin/mmtc to point to the correct (newly installed) jar file,
    //so we no longer create a symlink at lib/mmtc.jar
}

// fix for Rpm task setting incorrect digests in RPM metadata
tasks.getByName("mmtcEl8Rpm") {
    dependsOn(tasks.getByName("cleanMmtcEl8Rpm"))
}

tasks.getByName("mmtcEl9Rpm") {
    dependsOn(tasks.getByName("cleanMmtcEl9Rpm"))
}

distributions {
    main {
        distributionBaseName.set(project.name)
        contents {
            from("build/mmtc-dist-tmp")
        }
    }
}

tasks.distZip {
    dependsOn(createDistDir)
}

tasks.distTar {
    dependsOn(createDistDir)
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

tasks.installDist {
    dependsOn(createDistDir)
}

val demoZip = tasks.register("demoZip") {
    dependsOn(tasks.build)
    dependsOn(createDistDir)

    doLast {
        exec {
            commandLine("bash", "create-demo-zip.sh", project.version)
        }
    }
    outputs.dir("build/mmtc-demo-tmp")
}