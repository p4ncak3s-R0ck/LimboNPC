plugins { java }

val minecraftVersion = providers.gradleProperty("minecraftVersion").orElse("26")
val limboApiVersions = mapOf(
    "26" to "2026.0.1-ALPHA",
    "1.21" to "0.7.10-ALPHA",
    "1.20" to "0.7.5-ALPHA"
)
val minimessageVersions = mapOf(
    "26" to "4.26.1",
    "1.21" to "4.17.0",
    "1.20" to "4.14.0"
)
val limboApiVersion = minecraftVersion.map { version ->
    limboApiVersions[version] ?: error("Unsupported Minecraft version '$version'. Supported: ${limboApiVersions.keys.joinToString()}")
}
val minimessageVersion = minecraftVersion.map { minimessageVersions.getValue(it) }

dependencies {
    implementation(project(":common"))
    compileOnly("com.loohp:Limbo:${limboApiVersion.get()}")
    implementation("net.kyori:adventure-text-minimessage:${minimessageVersion.get()}")
}

tasks.jar {
    dependsOn(":common:jar")
    archiveFileName.set(minecraftVersion.map { "LimboNPC-Limbo-$it.jar" })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
