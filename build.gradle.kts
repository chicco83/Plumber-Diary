// build.gradle.kts (root) — v1.0.0 — 2026-09-20 00:10 UTC
// Dichiara le versioni dei plugin usati dai moduli (qui solo :app) senza applicarli
// alla root, secondo il pattern standard dei progetti Android Gradle multi-modulo.

plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
