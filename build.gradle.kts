plugins {
    java
}

tasks.jar { enabled = false }

tasks.register<Exec>("buildAllVersions") {
    group = "build"
    description = "Builds LimboNPC for the 26.x, 1.21.x, and 1.20.x compatibility ranges"
    workingDir(rootDir)
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("cmd", "/c", "scripts\\build-all.bat")
    } else {
        commandLine("bash", "scripts/build-all.sh")
    }
}

allprojects {
    group = "dev.limbonpc"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.loohpjames.com/repository")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java")

    val targetJava = providers.gradleProperty("minecraftVersion").orElse("26").map {
        if (it == "26") 21 else 17
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJava)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.11.4")
    }
}
