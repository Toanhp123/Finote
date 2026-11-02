plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"))
    }
    kotlinGradle {
        target("*.gradle.kts", "../*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        extraWarnings = true
    }
}

android {
    namespace = "io.github.toanhp123.finote"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.toanhp123.finote"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Lists the app languages in the Android 13+ per-app language setting.
        generateLocaleConfig = true
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Version-bump checks would turn the gate red whenever a library ships.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Compose Preview creates runfiles for every variant and requires unit tests on each of them.
androidComponents {
    beforeVariants { variantBuilder ->
        variantBuilder.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
// Konsist reads source text, but the test task tracks compiled classes only. Without this input a source-only
// change (for example a new @Suppress line) leaves testDebugUnitTest up to date and the gate green.
tasks.withType<Test>().configureEach {
    inputs.files(fileTree("src/main")).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register("qualityGate") {
    group = "verification"
    // detektMain skips test sources, so detekt and detektTest cover them (detektTest: unit and androidTest, typed).
    dependsOn("spotlessCheck", "detekt", "detektMain", "detektTest", "lintDebug", "testDebugUnitTest", "assembleDebug")
}
