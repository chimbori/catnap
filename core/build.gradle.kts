import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
  id("com.android.library")
  kotlin("android")
}

android {
  namespace = "com.chimbori.core"
  compileSdk = 34
  defaultConfig {
    minSdk = 28
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
  implementation("androidx.fragment:fragment-ktx:1.6.2")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
}
