plugins {
    id("multiloader-loader")
    id("net.fabricmc.fabric-loom")
}

dependencies {
    minecraft("com.mojang:minecraft:${"minecraft_version"()}")
    implementation("net.fabricmc:fabric-loader:${"fabric_loader_version"()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${"fabric_api_version"()}") // Optional
    implementation("net.fabricmc:fabric-language-kotlin:${"fabric_kotlin_version"()}") // Optional
}

loom {
    // optional, splits /src into /src/main and /src/client entrypoints
    //splitEnvironmentSourceSets()
    //mods {
    //    "${mod_id}" {
    //        sourceSet(sourceSets.main)
    //        sourceSet(sourceSets.client)
    //    }
    //}
    val aw = project(":common").file("src/main/resources/${"mod_id"()}.accesswidener")
    if (aw.exists()) accessWidenerPath.set(aw)
}
// Optional datagen, see https://wiki.fabricmc.net/tutorial:datagen_setup
//fabricApi {
//    configureDataGeneration {
//        client = true
//    }
//}

// Implement mcgradleconventions loader attribute
val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements", "javadocElements", "includeInternal", "modCompileClasspath").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "fabric")
        }
    }
}

sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "fabric")
            }
        }
    }
}

operator fun String.invoke(): String {
    return project.properties[this] as? String ?: throw IllegalStateException("Property $this is not defined")
}