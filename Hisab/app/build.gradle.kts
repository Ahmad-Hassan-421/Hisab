import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

fun apiOrigin(value: String, setting: String, httpsOnly: Boolean): String {
    val uri = runCatching { URI(value) }.getOrNull()
    val schemes = if (httpsOnly) listOf("https") else listOf("http", "https")
    require(uri != null && uri.scheme in schemes && uri.host != null &&
        uri.userInfo == null && uri.path in listOf("", "/") && uri.query == null &&
        uri.fragment == null && (uri.port == -1 || uri.port in 1..65535)
    ) { "$setting must be a valid ${if (httpsOnly) "HTTPS" else "HTTP(S)"} origin" }
    return uri.toASCIIString().trimEnd('/') + "/"
}

val debugOrigin = providers.gradleProperty("HISAB_DEBUG_API_URL")
    .orElse(providers.environmentVariable("HISAB_DEBUG_API_URL"))
    .orElse("https://hisab-onlo.onrender.com/")
    .map { apiOrigin(it, "HISAB_DEBUG_API_URL", httpsOnly = false) }
val releaseOrigin = providers.gradleProperty("HISAB_RELEASE_API_URL")
    .orElse(providers.environmentVariable("HISAB_RELEASE_API_URL"))
    .map { apiOrigin(it, "HISAB_RELEASE_API_URL", httpsOnly = true) }
val qaPackage = providers.gradleProperty("HISAB_QA").map(String::toBooleanStrict).orElse(false).get()
val qaVersion = providers.gradleProperty("HISAB_QA_VERSION_CODE").map(String::toInt).orNull
require(qaVersion == null || (qaPackage && qaVersion > 2)) { "A QA version override requires HISAB_QA and a build above 2" }

android {
    namespace = "com.example.hisab"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.hisab"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["allowCleartext"] = "false"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            val origin = debugOrigin.get()
            buildConfigField("String", "API_BASE_URL", "\"$origin\"")
            manifestPlaceholders["allowCleartext"] = (URI(origin).scheme == "http").toString()
            if (qaPackage) {
                require(URI(origin).host in setOf("127.0.0.1", "localhost", "10.0.2.2")) {
                    "HISAB_QA requires a loopback debug API URL"
                }
                applicationIdSuffix = ".qa"
            }
        }
        release {
            val origin = releaseOrigin.getOrElse("https://hisab.invalid/")
            buildConfigField("String", "API_BASE_URL", "\"$origin\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("proguard-rules.pro")
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        if (qaVersion != null) variant.outputs.forEach { output ->
            output.versionCode.set(qaVersion)
            output.versionName.set("1.1.$qaVersion")
        }
    }
}

val requireReleaseOrigin by tasks.registering {
    doLast {
        require(releaseOrigin.isPresent) { "Set HISAB_RELEASE_API_URL to the HTTPS backend origin for release builds" }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(requireReleaseOrigin)
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // OkHttp & WebSockets
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Image Cropper
    implementation("com.vanniktech:android-image-cropper:4.5.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
