import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import groovy.json.JsonSlurper
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.gradle.play.publisher)
}

val loanTrackerApplicationId = "com.nojus.loantracker"
val knownDebugOnlyFirebaseSha1s = setOf("12995445dac9cbdec7eb5229cc5162409eaf56c1")
val releaseSigningStoreFile = providers.environmentVariable("ANDROID_UPLOAD_KEYSTORE_FILE").orNull
val releaseSigningStorePassword = providers.environmentVariable("ANDROID_UPLOAD_KEYSTORE_PASSWORD").orNull
val releaseSigningKeyAlias = providers.environmentVariable("ANDROID_UPLOAD_KEY_ALIAS").orNull
val releaseSigningKeyPassword = providers.environmentVariable("ANDROID_UPLOAD_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.nojus.loantracker"
    compileSdk = 36

    defaultConfig {
        applicationId = loanTrackerApplicationId
        minSdk = 30
        targetSdk = 36
        versionCode = 9
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigningStoreFile!!)
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

play {
    defaultToAppBundles.set(true)
    track.set(providers.environmentVariable("PLAY_TRACK").orElse("alpha"))
    releaseStatus.set(ReleaseStatus.COMPLETED)
}

fun String.normalizedSha1(): String = replace(":", "").lowercase(Locale.US)

fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

fun keystoreCertificateSha1(
    keystoreFile: File,
    storePassword: CharArray,
    alias: String
): String? = runCatching {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keystoreFile.inputStream().use { keyStore.load(it, storePassword) }
    val certificate = keyStore.getCertificate(alias) ?: return@runCatching null
    MessageDigest.getInstance("SHA-1").digest(certificate.encoded).toHex()
}.getOrNull()

val verifyGoogleSignInReleaseConfig by tasks.registering {
    group = "verification"
    description = "Fails release builds when Firebase Google sign-in OAuth is configured only for the local debug key."

    val googleServicesJson = layout.projectDirectory.file("google-services.json")
    inputs.file(googleServicesJson)

    doLast {
        val configFile = googleServicesJson.asFile
        if (!configFile.isFile) {
            throw GradleException("Missing ${configFile.absolutePath}. Google sign-in cannot be verified.")
        }

        val config = JsonSlurper().parse(configFile) as Map<*, *>
        val firebaseClients = config["client"] as? List<*> ?: emptyList<Any>()
        val appClient = firebaseClients
            .mapNotNull { it as? Map<*, *> }
            .firstOrNull { client ->
                val clientInfo = client["client_info"] as? Map<*, *>
                val androidInfo = clientInfo?.get("android_client_info") as? Map<*, *>
                androidInfo?.get("package_name") == loanTrackerApplicationId
            }
            ?: throw GradleException(
                "google-services.json has no Android client for $loanTrackerApplicationId."
            )

        val oauthClients = (appClient["oauth_client"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?: emptyList()

        val hasWebClient = oauthClients.any { (it["client_type"] as? Number)?.toInt() == 3 }
        if (!hasWebClient) {
            throw GradleException(
                "google-services.json has no web OAuth client. Firebase Auth Google sign-in needs default_web_client_id."
            )
        }

        val androidSha1s = oauthClients
            .filter { (it["client_type"] as? Number)?.toInt() == 1 }
            .mapNotNull { oauthClient ->
                val androidInfo = oauthClient["android_info"] as? Map<*, *>
                (androidInfo?.get("certificate_hash") as? String)?.normalizedSha1()
            }
            .toSet()

        if (androidSha1s.isEmpty()) {
            throw GradleException(
                "google-services.json has no Android OAuth SHA-1 fingerprints for $loanTrackerApplicationId."
            )
        }

        val debugSha1 = keystoreCertificateSha1(
            keystoreFile = File(System.getProperty("user.home"), ".android/debug.keystore"),
            storePassword = "android".toCharArray(),
            alias = "androiddebugkey"
        )

        val isLocalDebugOnly = debugSha1 != null && androidSha1s.size == 1 && androidSha1s.contains(debugSha1)
        val isKnownCheckedInDebugOnly = androidSha1s.size == 1 && androidSha1s.any {
            knownDebugOnlyFirebaseSha1s.contains(it)
        }

        if (isLocalDebugOnly || isKnownCheckedInDebugOnly) {
            throw GradleException(
                """
                Firebase Google sign-in is configured only for the local debug SHA-1.

                Play Store installs are signed with the Play app signing certificate, so Credential Manager
                can cancel Google sign-in before the app receives an ID token.

                Fix:
                1. In Play Console, open Test and release > Setup > App signing.
                2. Copy the App signing key certificate SHA-1 and SHA-256.
                3. Add both fingerprints to Firebase Project settings > Your apps > Android app.
                4. Download the updated google-services.json and replace app/google-services.json.
                5. Rerun :app:verifyGoogleSignInReleaseConfig and :app:assembleRelease.
                """.trimIndent()
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyGoogleSignInReleaseConfig)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.play.app.update)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
