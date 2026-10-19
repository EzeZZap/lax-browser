import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from keystore.properties (never committed) or, for CI, environment
// variables. Without either, release APKs are built unsigned.
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingValue(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)

/**
 * Bundles CHANGELOG.md (and CHANGELOG.es.md) as `R.raw.changelog`, shown in the app after an
 * update and in Settings > About, so the repository files stay the only copy.
 */
abstract class ChangelogResourcesTask : DefaultTask() {
    @get:InputFile
    abstract val english: RegularFileProperty

    @get:InputFile
    abstract val spanish: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        english.get().asFile.copyTo(File(out, "raw/changelog.md"))
        spanish.get().asFile.copyTo(File(out, "raw-es/changelog.md"))
    }
}

val changelogResources = tasks.register<ChangelogResourcesTask>("changelogResources") {
    english.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
    spanish.set(rootProject.layout.projectDirectory.file("CHANGELOG.es.md"))
    outputDir.set(layout.buildDirectory.dir("generated/changelog/res"))
}

android {
    namespace = "com.javzzap.laxbrowser"
    compileSdk {
        // GeckoView 158 requires compiling against API 37.2.
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.javzzap.laxbrowser"
        minSdk = 26
        targetSdk = 37
        // Release tags on GitHub should match versionName ("v0.1.0"); update checks compare them.
        // Each ABI's APK gets versionCode * 10 + an ABI digit (see androidComponents below).
        versionCode = 6
        versionName = "0.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Where the build is distributed. Both flavors are the same app; they differ in update checks:
    //   github: checks the GitHub releases of `laxUpdateRepo` (gradle.properties or -P) once a day.
    //   fdroid: no update checker, since F-Droid updates apps itself.
    // Tasks: assembleGithubDebug, assembleGithubRelease, assembleFdroidDebug, assembleFdroidRelease.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            val updateRepo = providers.gradleProperty("laxUpdateRepo").orNull.orEmpty().trim()
            buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("String", "UPDATE_REPO", "\"\"")
        }
    }

    signingConfigs {
        create("release") {
            signingValue("storeFile", "LAX_KEYSTORE")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("storePassword", "LAX_KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "LAX_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "LAX_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // R8 shrinks the libraries. Lax's own classes and GeckoView are kept whole
            // (src/main/keepRules/rules.keep); limiting R8 to some packages (packageScope) breaks
            // startup. CI starts every release APK on an emulator.
            optimization {
                enable = true
            }
            val releaseSigning = signingConfigs.getByName("release").takeIf { it.storeFile?.exists() == true }
            // CI's emulator check installs release builds of pull requests, which have no release key.
            val debugSigning = signingConfigs.getByName("debug").takeIf { providers.gradleProperty("laxDebugSignRelease").orNull == "true" }
            signingConfig = releaseSigning ?: debugSigning
        }
    }

    // F-Droid rejects APKs with Google's encrypted dependency metadata block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // GeckoView ships native code for every ABI; one APK per ABI keeps each download small.
    splits {
        abi {
            isEnable = true
            reset()
            // -PlaxAbi=arm64-v8a builds only that APK (F-Droid builds one APK per build block).
            val onlyAbi = providers.gradleProperty("laxAbi").orNull?.takeIf { it.isNotBlank() }
            include(*(onlyAbi?.let { arrayOf(it) } ?: arrayOf("arm64-v8a", "armeabi-v7a", "x86_64")))
            isUniversalApk = false
        }
    }
    packaging {
        // Compress GeckoView's native libraries in the APK (they are extracted at install time).
        jniLibs.useLegacyPackaging = true
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Per-ABI version codes, so a device always gets the APK for its CPU:
// armeabi-v7a = x1, arm64-v8a = x2, x86_64 = x3.
androidComponents {
    val baseVersionCode = android.defaultConfig.versionCode ?: 1
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(changelogResources, ChangelogResourcesTask::outputDir)
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            val digit = when (abi) {
                "armeabi-v7a" -> 1
                "arm64-v8a" -> 2
                "x86_64" -> 3
                else -> 0
            }
            output.versionCode.set(baseVersionCode * 10 + digit)
        }
    }
}

dependencies {
    constraints {
        // Test APK dependencies are aligned to the app's; room-testing's schema reader needs >= 1.8.1.
        implementation(libs.kotlinx.serialization.core)
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.emoji2.emojipicker)
    implementation(libs.androidx.media)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.geckoview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
