plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Модуль только для запуска ИИ-модели в телефоне (LiteRT-LM). Библиотека собрана более новым Kotlin (метаданные 2.4),
// поэтому проверка версии метаданных здесь отключена. Остальное приложение эту библиотеку не видит.
android {
    namespace = "com.lifeos.llm"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")
}
