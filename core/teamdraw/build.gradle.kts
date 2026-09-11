// Modulo Kotlin PURO, senza dipendenze da Android.
// E una scelta precisa: il motore di sorteggio si testa con una JVM in un
// secondo, e lo stesso codice si ricompila dentro un backend senza modifiche.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
