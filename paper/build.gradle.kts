plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.0"
    id("maven-publish")
}

group = "re.imc"
version = "1.0.9"

publishing {
    publications {
        create<MavenPublication>("shadow") {
            groupId = project.group.toString()
            artifactId = "GeyserModelEngine"
            version = project.version.toString()

            artifact(tasks.shadowJar.get().archiveFile) {
                builtBy(tasks.shadowJar)
            }
        }
    }

    repositories {
        mavenLocal()
    }
}

repositories {
    // Depot Maven statique BTC Studio : commite sous BTCVelocity/repo et televerse tel quel.
    // Nos forks (BetterModel, packetevents) y publient sous les coordonnees AMONT ; le filtre
    // epingle donc explicitement ces modules ici, pour qu ils ne soient jamais resolus depuis
    // Maven Central, ou les memes coordonnees portent le code amont.
    exclusiveContent {
        forRepository {
            maven {
                name = "btcRepo"
                url = uri(
                    providers.gradleProperty("btcRepoDir")
                        .getOrElse(rootProject.file("../BTCVelocity/repo").absolutePath)
                )
            }
        }
        filter {
            includeModule("io.github.toxicity188", "bettermodel-api")
            includeModule("io.github.toxicity188", "bettermodel-bukkit-api")
            // packetevents est EMBARQUE (shadow) dans ce jar : la copie doit etre celle qui
            // connait le NMS 26.3 (2.14.0, fork BTC), sinon SpigotReflectionUtil echoue au
            // chargement sur NMS_ITEM_STACK et le plugin ne demarre pas.
            includeModule("com.github.retrooper", "packetevents-spigot")
            includeModule("com.github.retrooper", "packetevents-api")
            includeModule("com.github.retrooper", "packetevents-netty-common")
        }
    }

    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://central.sonatype.com/repository/maven-snapshots/")

    maven("https://mvn.lumine.io/repository/maven-public/")

    maven("https://repo.opencollab.dev/main/")

    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.26-alpha")

    compileOnly("com.ticxo.modelengine:ModelEngine:R4.1.0")
    compileOnly("io.github.toxicity188:bettermodel-api:3.5.0")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.5.0")
    
    compileOnly(files("libs/geyserutils-spigot-1.0-SNAPSHOT.jar"))
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")

    implementation("com.github.retrooper:packetevents-spigot:2.14.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    implementation("org.reflections:reflections:0.10.2")
}

java {
    // Paper 26.3 et BetterModel 3.5.0 sont publies pour la JVM 25 : rester en 21
    // fait echouer la RESOLUTION, pas la compilation.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.compileJava {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveFileName.set("${rootProject.name}-${version}.jar")

    exclude("plugin.yml") // PacketEvents' plugin.yml does not belong into the shadow jar
    relocate("com.github.retrooper", "re.imc.geysermodelengine.libs.com.github.retrooper.packetevents")
    relocate("io.github.retrooper", "re.imc.geysermodelengine.libs.io.github.retrooper.packetevents")

    relocate("org.bstats", "re.imc.geysermodelengine.libs.bstats")

    relocate("org.reflections", "re.imc.geysermodelengine.libs.reflections")
}

tasks.build {
    dependsOn("shadowJar")
    finalizedBy("publishToMavenLocal")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
