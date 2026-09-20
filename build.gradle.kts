plugins {
    id("java")
}

group = "re.imc"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {

}

java {
    // Paper 26.3 et BetterModel 3.5.0 sont publies pour la JVM 25 : rester en 21
    // fait echouer la RESOLUTION, pas la compilation.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.compileJava {
    options.encoding = "UTF-8"
}