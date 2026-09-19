// settings.gradle.kts — v1.0.0 — 2026-09-20 00:10 UTC
// Radice della configurazione Gradle: dichiara i repository dei plugin/dipendenze
// e i moduli che compongono il progetto (per ora solo l'app Android; il backend
// Vercel Functions in /backend è un progetto Node.js separato, non un modulo Gradle).

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // osmdroid e i suoi tile scaricano da repository pubblici standard,
        // nessun repository aggiuntivo richiesto oltre Maven Central.
    }
}

rootProject.name = "PlumberDiary"
include(":app")
