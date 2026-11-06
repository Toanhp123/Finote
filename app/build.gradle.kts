plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.androidx.room3)
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

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        extraWarnings = true
        // Room 3 generates Kotlin (build/generated/ksp) that trips these extraWarnings checkers.
        // Kotlin has no per-file switch, so they are off project-wide.
        freeCompilerArgs.addAll(
            "-Xwarning-level=REDUNDANT_VISIBILITY_MODIFIER:disabled",
            "-Xwarning-level=CAN_BE_VAL:disabled",
        )
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
        screenshotTests.create("screenshotTest") {
            engineVersion = libs.versions.screenshot.get()
            targetVariants.add("debug")
            dependencies {
                implementation(libs.androidx.compose.ui.tooling)
                implementation(libs.screenshot.validation.api)
            }
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
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.composeunstyled.bottom.sheet)
    implementation(libs.composeunstyled.dialog)
    implementation(libs.composeunstyled.modal.bottom.sheet)
    implementation(libs.composeunstyled.toggle.switch)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.room3.runtime)
    implementation(libs.sqlite.framework)
    implementation(libs.sqlcipher.android)
    ksp(libs.room3.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
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
    // Robolectric on SDK 37 reaches FileDescriptor internals through SharedSecrets, which Java 21 does not export.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

tasks.register("qualityGate") {
    group = "verification"
    // detektMain skips test sources, so detekt and detektTest cover them (detektTest: unit and androidTest, typed).
    dependsOn(
        "spotlessCheck",
        "detekt",
        "detektMain",
        "detektTest",
        "lintDebug",
        "testDebugUnitTest",
        "testDebugScreenshotTestDefaultTestSuite",
        "assembleDebug",
    )
}
