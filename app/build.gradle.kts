// app/build.gradle.kts — v1.0.0 — 2026-09-20 00:10 UTC
//
// Modulo Android dell'app Plumber Diary. Scelte principali (vedi context.md
// per il ragionamento completo):
// - Jetpack Compose per la UI (Home, Recap, Dettaglio, Cliente, Squadra,
//   Opzioni, Storico, Dashboard, Rapportino).
// - Firebase (Auth, Firestore, Storage, Messaging) come backend dati/foto/push,
//   piano Spark gratuito — stesso stack già validato in produzione nel
//   progetto gemello chicco83/gwatch-child-tracker.
// - osmdroid al posto di Google Maps SDK: nessuna chiave API, nessuna
//   fatturazione richiesta (Google Maps Platform la richiederebbe ad ogni
//   chiamata, non solo alla creazione della chiave).
// - WorkManager per il sampling periodico di posizione e per i job di upload
//   foto/PDF in background, resilienti a riavvii/kill del processo.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.plumberdiary.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.plumberdiary.app"
        minSdk = 26 // Photo Picker di sistema e Foreground Service Location richiedono API moderne
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // da attivare con proguard-rules.pro dedicate prima del rilascio
        }
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    // --- Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // --- Firebase (BoM: allinea automaticamente le versioni dei singoli SDK) ---
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // --- Posizione e attività ---
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- Mappa (niente Google Maps SDK: vedi commento sopra) ---
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // --- Lavoro in background persistente (sampling posizione, upload foto/PDF) ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- Chiamate al backend Vercel (create-team, create-invite, accept-invite, ecc.) ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- Persistenza della sessione (uid/teamId) tra riavvii dell'app ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Generazione PDF (mandatino ore, rapportino) ---
    implementation("com.itextpdf:itext7-core:8.0.5")

    // --- Google Calendar (promemoria dal recap) ---
    implementation("com.google.api-client:google-api-client-android:2.7.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20240930-2.0.0")

    // --- Test ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
