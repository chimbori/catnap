import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
  id("com.android.application")
  id("kotlin-android")
  kotlin("plugin.serialization")
}

android {
  namespace = "com.chimbori.catnap"
  compileSdk = libs.versions.compileSdk.get().toInt()
  defaultConfig {
    applicationId = "com.chimbori.catnap"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      isShrinkResources = true
      isCrunchPngs = false
    }
  }
  buildFeatures {
    viewBinding = true
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
  compileOptions {
    sourceCompatibility(VERSION_1_8)
    targetCompatibility(VERSION_1_8)
  }
}

dependencies {
  implementation(project(":core"))
  implementation(project(":jtransforms"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.navigation.fragment)
  implementation(libs.androidx.navigation.ui)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.material)
  implementation(libs.viewgenesis)
}
