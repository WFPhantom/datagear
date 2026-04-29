pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		exclusiveContent {
			forRepository {
				maven {
					name = "Fabric"
					url = uri("https://maven.fabricmc.net/")
				}
			}
			filter {
				includeGroupAndSubgroups("net.fabricmc")
			}
		}
	}
	plugins{
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
		id("net.neoforged.moddev") version providers.gradleProperty("mdg_version")
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("common")
include("fabric")
include("neoforge")