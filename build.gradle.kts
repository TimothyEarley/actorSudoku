plugins {
    kotlin("js") version "1.3.61"
}

group = "de.earley"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.6.12")
}

kotlin.target.browser { }