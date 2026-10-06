plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// kanji.json lives once, in Tatsu/. Copy it into a generated assets folder at build time.
val kanjiAssetsDir = layout.buildDirectory.dir("generated/kanjiAssets")
val copyKanjiData = tasks.register<Copy>("copyKanjiData") {
    from(rootProject.file("../Tatsu/kanji.json"))
    into(kanjiAssetsDir)
}

// srcDir(...builtBy) no longer carries task dependencies under AGP 9, so wire the copy explicitly (lint tasks read the folder too)
tasks.matching { (it.name.startsWith("merge") && it.name.endsWith("Assets")) || it.name.contains("lint", ignoreCase = true) }.configureEach { dependsOn(copyKanjiData) }

android {
    namespace = "com.abbabon.kanjioffline"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.abbabon.kanjioffline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    sourceSets.getByName("main").assets.directories.add(kanjiAssetsDir.get().asFile.absolutePath)

    // Upload key: only present on the maintainer's Mac (properties live in ~/.gradle/gradle.properties).
    // Without them `bundleRelease` still builds, just unsigned.
    val uploadStore = providers.gradleProperty("TATSU_UPLOAD_STORE_FILE")
    if (uploadStore.isPresent) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStore.get())
            storePassword = providers.gradleProperty("TATSU_UPLOAD_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("TATSU_UPLOAD_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("TATSU_UPLOAD_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
        }
    }
}

// JVM tests read the real data file from the repo
tasks.withType<Test>().configureEach {
    systemProperty("kanji.json", rootProject.file("../Tatsu/kanji.json").absolutePath)
    inputs.file(rootProject.file("../Tatsu/kanji.json"))
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.adaptive.layout)
    implementation(libs.adaptive.navigation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
