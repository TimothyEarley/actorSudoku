plugins {
    kotlin("js") version "1.3.70-eap-274"
    id("org.ajoberstar.git-publish") version "2.1.3"
}

group = "de.earley"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.6.12")
}

kotlin.target.browser { }

gitPublish {
    repoUri.set("git@github.com:TimothyEarley/actorSudoku.git")
    branch.set("gh-pages")
    contents {
        from("$buildDir/distributions")
    }
}

tasks.findByPath("gitPublishCommit")!!.dependsOn("browserDistribution")