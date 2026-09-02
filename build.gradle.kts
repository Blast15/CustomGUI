plugins {
    java
}

group = "dev.customgui"
version = "0.4.0"
val pluginVersion = version.toString()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to pluginVersion) }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("CustomGUI")
}

val sourceZip by tasks.registering(Zip::class) {
    archiveFileName.set("CustomGUI-source.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.projectDirectory) {
        exclude(".bootstrap/**", ".gradle/**", ".smoke/**", "bin/**", "build/**", "*.zip")
    }
}

tasks.build { dependsOn(sourceZip) }
