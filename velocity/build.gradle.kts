plugins { java }

val minecraftVersion = providers.gradleProperty("minecraftVersion").orElse("26")

dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("org.yaml:snakeyaml:2.4")
}

tasks.jar {
    dependsOn(":common:jar")
    archiveFileName.set(minecraftVersion.map { "LimboNPC-Velocity-$it.jar" })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
