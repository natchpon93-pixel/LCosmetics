plugins {
    java
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25) // Folia 26.1.2 ต้องใช้ Java 25
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")  // packetevents
    maven("https://repo.helpch.at/releases/")                   // PlaceholderAPI
    maven("https://repo.nexomc.com/releases/")                  // Nexo (public mirror)
}

dependencies {
    // ตรงกับ server jar folia-26.1.2-8 ที่รันอยู่
    compileOnly("dev.folia:folia-api:26.1.2.build.8-stable")

    // packetevents 2.12.2 — ตรงกับ packetevents-spigot-2.12.2.jar บนเซิร์ฟ
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.2")

    // Nexo API — paid plugin ไม่มีบน public repo → local jar (version เดียวกับเซิร์ฟ)
    compileOnly(files("libs/nexo-1.28.jar"))

    // PlaceholderAPI — optional expansion %lcosmetics_*%
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()
    options.release = 25
}

tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("LCosmetics")
    archiveClassifier.set("")
}
