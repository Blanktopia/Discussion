@file:Suppress("SpellCheckingInspection")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.mineinabyss.conventions.kotlin")
    kotlin("plugin.serialization")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.2"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

repositories {
    mavenCentral()

    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://papermc.io/repo/repository/maven-public") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }

    // Cloud
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }

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
    implementation(libs.idofront.platform.loader)
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.serialization.kaml)
    compileOnly(libs.kotlinx.serialization.cbor)
    implementation(libs.idofront.core)

    // Paper
    compileOnly("io.papermc.paper", "paper-api", "1.18.2-R0.1-SNAPSHOT")

    // Spigot
    compileOnly("org.spigotmc", "spigot", "1.18.2-R0.1-SNAPSHOT")

    // Cloud
    implementation("cloud.commandframework", "cloud-paper", "1.7.0")
    implementation("cloud.commandframework", "cloud-minecraft-extras", "1.7.0")

    // DiscordSRV
    compileOnly("com.discordsrv", "discordsrv", "1.26.0-SNAPSHOT")

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
    classifier = null

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
