plugins {
    id("fabric-loom") version "1.14.10"
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

base {
    archivesName = properties["archives_base_name"] as String
    group = properties["maven_group"] as String
    version = properties["mod_version"] as String
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

repositories {
    maven {
        url = uri("https://jm.gserv.me/repository/maven-public/")
        content {
            includeGroup("info.journeymap")
        }
    }
    maven {
        url = uri("https://api.modrinth.com/maven/")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven { url = uri("https://www.cursemaven.com") }
    maven { url = uri("https://masa.dy.fi/maven") }
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${properties["fabric_version"] as String}")

    // Meteor Client
    modImplementation("meteordevelopment:meteor-client:${properties["minecraft_version"] as String}-SNAPSHOT")
    modCompileOnly("meteordevelopment:baritone:${properties["minecraft_version"] as String}-SNAPSHOT")
    implementation("org.meteordev:starscript:0.2.5")
    implementation("meteordevelopment:orbit:0.2.4")

    // XaeroPlus
    modImplementation("maven.modrinth:xaeroplus:${properties["xaeroplus_version"] as String}")
    // XaeroWorldMap
    modImplementation("maven.modrinth:xaeros-world-map:${properties["xaeros_worldmap_version"] as String}")
    // XaeroMinimap
    modImplementation("maven.modrinth:xaeros-minimap:${properties["xaeros_minimap_version"] as String}")
    // Sodium
    modImplementation ("maven.modrinth:sodium:${properties["sodium_version"] as String}")

    // Other
    implementation("org.json:json:20231013")
    modImplementation("net.lenni0451:LambdaEvents:2.4.2")
    // fix for dependency failure
    modImplementation("com.github.ben-manes.caffeine:caffeine:3.1.8") {
        include("com.github.ben-manes.caffeine:caffeine:3.1.8")
    }
}

loom {
    accessWidenerPath = file("src/main/resources/sindiaddon.accesswidener")
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to project.property("minecraft_version"),
            "xp_version" to project.property("xaeroplus_version"),
            "xwm_version" to project.property("xaeros_worldmap_version"),
            "xmm_version" to project.property("xaeros_minimap_version")
        )

        inputs.properties(propertyMap)

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    withType<JavaCompile> {
        options.release = 21
        options.encoding = "UTF-8"
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
