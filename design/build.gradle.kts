plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.walsoup.linen"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
  }
}

kotlin {
  jvmToolchain(21)
}

composeCompiler {
  includeComposeMappingFile = false
}

dependencies {
  api(platform("androidx.compose:compose-bom:2026.03.01"))
  api("androidx.compose.ui:ui")
  api("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-core")
  debugImplementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")

  constraints {
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.savedstate:savedstate-compose:1.4.0")
  }
}
