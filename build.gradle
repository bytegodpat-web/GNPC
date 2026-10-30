buildscript {
    repositories {
        maven { url = 'https://maven.minecraftforge.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath group: 'net.minecraftforge.gradle', name: 'ForgeGradle', version: '3.+', changing: true
    }
}
apply plugin: 'net.minecraftforge.gradle'

version = '0.3.0'
group = 'com.npcgemini'
archivesBaseName = 'npcgemini-1.12.2'

sourceCompatibility = targetCompatibility = '1.8'
compileJava.sourceCompatibility = compileJava.targetCompatibility = '1.8'

// Obligatorio: el codigo tiene tildes y la CI a veces usa otro encoding
tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
}

minecraft {
    mappings channel: 'stable', version: '39-1.12'
}

dependencies {
    minecraft 'net.minecraftforge:forge:1.12.2-14.23.5.2860'
}

jar.finalizedBy('reobfJar')
