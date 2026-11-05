plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.caizhixing.trail.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.caizhixing.trail.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "config"
    productFlavors {
        create("internal") {
            dimension = "config"
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
        }
        create("production") {
            dimension = "config"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":trail-api"))
    "internalImplementation"(project(":trail-debug"))
    "productionImplementation"(project(":trail-noop"))
}
