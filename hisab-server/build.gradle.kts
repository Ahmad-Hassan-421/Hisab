val ktor_version = "2.3.9"
val kmongo_version = "4.11.0"
val logback_version = "1.5.38"
val netty_version = "4.1.140.Final"
val jackson_version = "2.23.0"

plugins {
    kotlin("jvm") version "2.1.21"
    id("io.ktor.plugin") version "2.3.9"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
}

group = "com.hisab"
version = "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.hisab.server.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("io.netty:netty-bom:$netty_version"))
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jackson_version"))

    // Ktor Core & Server
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
    
    // Auth & JWT
    implementation("io.ktor:ktor-server-auth-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktor_version")
    
    // Serialization (JSON)
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    
    // CORS, WebSockets & Static Files
    implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor_version")
    
    // KMongo (MongoDB Async Coroutines)
    implementation("org.litote.kmongo:kmongo-coroutine:$kmongo_version")
    
    // Passwords & Crypto
    implementation("at.favre.lib:bcrypt:0.10.2")
    
    // HTTP Client (for GitHub API & Brevo API)
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")
}
