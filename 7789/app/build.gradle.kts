import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.dokka.gradle.engine.parameters.KotlinPlatform
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.android)
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
val tmpFilePath = System.getProperty("user.home") + "/work/_temp/keystore/"
val prereleaseStoreFile: File? = File(tmpFilePath).listFiles()?.first()

fun getGitCommitHash(): String {
    return try {
        val headFile = file("${project.rootDir}/.git/HEAD")

        // Read the commit hash from .git/HEAD
        val hash = if (headFile.exists()) {
            val headContent = headFile.readText().trim()
            if (headContent.startsWith("ref:")) {
                val refPath = headContent.substring(5) // e.g., refs/heads/main
                val commitFile = file("${project.rootDir}/.git/$refPath")
                if (commitFile.exists()) commitFile.readText().trim() else ""
            } else {
                headContent // If it's a detached HEAD (commit hash directly)
            }
        } else {
            "" // If .git/HEAD doesn't exist
        }

        hash.take(7) // ✅ Dipindah ke luar blok try
    } catch (_: Throwable) {
        "" // Return empty string if error
    }
}

android {
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    viewBinding {
        enable = true
    }

    signingConfigs {
        if (prereleaseStoreFile != null) {
            create("prerelease") {
                storeFile = file(prereleaseStoreFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    compileSdk = 36

    // 🧩 Otomatis pakai build-tools bawaan GitHub runner (tanpa download)
    val sdkDir = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (sdkDir != null) {
        val tools = File(sdkDir, "build-tools").listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }
        if (!tools.isNullOrEmpty()) {
            val highestBuildTools = tools.last().name
            println("🧰 Using build-tools version: $highestBuildTools")
            buildToolsVersion = highestBuildTools
        }
    }

    defaultConfig {
        applicationId = "com.lagradost.cloudstream3"
        minSdk = 21
        targetSdk = 36
        val dateFormat = SimpleDateFormat("yyMMddHH", Locale.getDefault())
        val timeStamp = dateFormat.format(Date())
        versionCode = timeStamp.toInt()
        versionName = "5.0.4"

        resValue("string", "commit_hash", getGitCommitHash())

        // Reads local.properties
        val localProperties = gradleLocalProperties(rootDir, project.providers)

        buildConfigField(
            "long",
            "BUILD_DATE",
            "${System.currentTimeMillis()}"
        )
        buildConfigField(
            "String",
            "SIMKL_CLIENT_ID",
            "\"" + (System.getenv("SIMKL_CLIENT_ID") ?: localProperties["simkl.id"]) + "\""
        )
        buildConfigField(
            "String",
            "SIMKL_CLIENT_SECRET",
            "\"" + (System.getenv("SIMKL_CLIENT_SECRET") ?: localProperties["simkl.secret"]) + "\""
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("state")
    productFlavors {
        create("stable") {
            dimension = "state"
        }
        create("prerelease") {
            dimension = "state"
            applicationIdSuffix = ".prerelease"
            if (signingConfigs.names.contains("prerelease")) {
                signingConfig = signingConfigs.getByName("prerelease")
            } else {
                logger.warn("No prerelease signing config!")
            }
            versionNameSuffix = "-RPX"
            versionCode = (System.currentTimeMillis() / 60000).toInt()
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    java {
	    // Use Java 17 toolchain even if a higher JDK runs the build.
        // We still use Java 8 for now which higher JDKs have deprecated.
	    toolchain {
		    languageVersion.set(JavaLanguageVersion.of(libs.versions.jdkToolchain.get()))
    	}
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }
    // ✅ Hindari crash resource di Android lama
    packaging {
        // ✅ Aman untuk Android 6–17
        resources.excludes += setOf(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1")
    }
    
    namespace = "com.lagradost.cloudstream3"
}

dependencies {
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.core)
    implementation(libs.junit.ktx)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Android Core & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.navigation)

    // Design & UI
    implementation(libs.preference.ktx)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    // Coil Image Loading
    implementation(libs.bundles.coil)

    // Media 3 (ExoPlayer)
    implementation(libs.bundles.media3)
    implementation(libs.video)

    // FFmpeg Decoding
    implementation(libs.bundles.nextlib)

    // PlayBack
    implementation(libs.colorpicker) // Subtitle Color Picker
    implementation(libs.newpipeextractor) // For Trailers
    implementation(libs.juniversalchardet) // Subtitle Decoding

    // UI Stuff
    implementation(libs.shimmer) // Shimmering Effect (Loading Skeleton)
    implementation(libs.palette.ktx) // Palette for Images -> Colors
    implementation(libs.tvprovider)
    implementation(libs.overlappingpanels) // Gestures
    implementation(libs.biometric) // Fingerprint Authentication
    implementation(libs.previewseekbar.media3) // SeekBar Preview
    implementation(libs.qrcode.kotlin) // QR Code for PIN Auth on TV

    // Extensions & Other Libs
    implementation(libs.jsoup) // HTML Parser
    implementation(libs.rhino) // Run JavaScript
    implementation(libs.quickjs)
    implementation(libs.fuzzywuzzy) // Library/Ext Searching with Levenshtein Distance
    implementation(libs.safefile) // To Prevent the URI File Fu*kery
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio) // NIO Flavor Needed for NewPipeExtractor
    implementation(libs.conscrypt.android) // To Fix SSL Fu*kery on Android 9
    implementation(libs.jackson.module.kotlin) // JSON Parser

    // Torrent Support
    implementation(libs.torrentserver)

    // Downloading & Networking
    implementation(libs.work.runtime.ktx)
    implementation(libs.nicehttp) // HTTP Lib

    implementation(project(":library") {
        // There does not seem to be a good way of getting the android flavor.
        val isDebug = gradle.startParameter.taskRequests.any { task ->
            task.args.any { arg ->
                arg.contains("debug", true)
            }
        }

        this.extra.set("isDebug", isDebug)
    })
}

tasks.register<Jar>("androidSourcesJar") {
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs) // Full Sources
}

tasks.register<Copy>("copyJar") {
    dependsOn("build", ":library:jvmJar")
    from(
        "build/intermediates/compile_app_classes_jar/prereleaseDebug/bundlePrereleaseDebugClassesToCompileJar",
        "../library/build/libs"
    )
    into("build/app-classes")
    include("classes.jar", "library-jvm*.jar")
    // Remove the version
    rename("library-jvm.*.jar", "library-jvm.jar")
}

// Merge the app classes and the library classes into classes.jar
tasks.register<Jar>("makeJar") {
    // Duplicates cause hard to catch errors, better to fail at compile time.
    duplicatesStrategy = DuplicatesStrategy.FAIL
    dependsOn(tasks.getByName("copyJar"))
    from(
        zipTree("build/app-classes/classes.jar"),
        zipTree("build/app-classes/library-jvm.jar")
    )
    destinationDirectory.set(layout.buildDirectory)
    archiveBaseName = "classes"
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        jvmDefault.set(JvmDefaultMode.ENABLE)
        optIn.add("com.lagradost.cloudstream3.Prerelease")
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dokka {
    moduleName = "App"
    dokkaSourceSets {
        main {
            analysisPlatform = KotlinPlatform.JVM
            documentedVisibilities(
                VisibilityModifier.Public,
                VisibilityModifier.Protected
            )

            sourceLink {
                localDirectory = file("..")
                remoteUrl("https://github.com/recloudstream/cloudstream/tree/master")
                remoteLineSuffix = "#L"
            }
        }
    }
}
