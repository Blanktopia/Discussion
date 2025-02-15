@file:Suppress("SpellCheckingInspection")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("io.github.goooler.shadow") version "8.1.7"
}

repositories {
    mavenCentral()

    maven("https://repo.purpurmc.org/snapshots")
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }

    // DiscordSRV
    maven { url = uri("https://m2.dv8tion.net/releases") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://nexus.scarsz.me/content/groups/public/") }

    // PlaceholderAPI
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }

    // bStats
    maven { url = uri("https://repo.codemc.org/repository/maven-public") }

    mavenLocal()
}

dependencies {
    compileOnly(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.charleskorn.kaml:kaml:0.72.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.8.0")

    // Paper
    compileOnly("org.purpurmc.purpur", "purpur-api", "1.21-R0.1-SNAPSHOT")

    // DiscordSRV
    compileOnly("com.discordsrv", "discordsrv", "1.28.0")

    // PlaceholderAPI
    compileOnly("me.clip", "placeholderapi", "2.11.2")

    // bStats
    implementation("org.bstats", "bstats-bukkit", "1.8")
}

bukkit {
    main = "me.weiwen.discussion.Discussion"
    name = "Discussion"
    version = project.version.toString()
    description = "Simple chat formatting and channels plugin"
    apiVersion = "1.18"
    author = "Goh Wei Wen <goweiwen@gmail.com>"
    website = "weiwen.me"

    softDepend = listOf("DiscordSRV", "PlaceholderAPI")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")

    sourceSets.main {
        java.srcDirs("src/main/kotlin")
    }
}

tasks.withType<ShadowJar> {
    fun reloc(pkg: String) = relocate(pkg, "$group.dependency.$pkg")

    reloc("org.bstats")
    reloc("cloud.commandframework")
}

val pluginPath = project.findProperty("plugin_path")

if(pluginPath != null) {
    tasks {
        named<DefaultTask>("build") {
            dependsOn("shadowJar")
            doLast {
                copy {
                    from(findByName("reobfJar") ?: findByName("shadowJar") ?: findByName("jar"))
                    into(pluginPath)
                }
            }
        }
    }
}
