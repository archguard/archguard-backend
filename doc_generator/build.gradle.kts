plugins {
    id("application")
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    kotlin("plugin.serialization") version "1.6.10"
}

dependencies {
    api(project(":rule_core"))
    api(project(":linter:rule_code"))
    api(project(":linter:rule_sql"))
    api(project(":linter:rule_test_code"))
    api(project(":linter:rule_webapi"))
    api(project(":linter:rule_template"))

    api("org.jetbrains.kotlin:kotlin-compiler:1.6.10")

    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("com.github.ajalt.clikt:clikt:3.4.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.30")
}

application {
    mainClass.set("org.archguard.doc.generator.RunnerKt")
}

tasks {
    shadowJar {
        manifest {
            attributes(Pair("Main-Class", "org.archguard.doc.generator.RunnerKt"))
        }
    }
}
