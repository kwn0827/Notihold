plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.lumifold.notihold"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumifold.notihold"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // デフォルトのアプリ名
        manifestPlaceholders["appName"] = "NotiHold"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            // 本番用 AdMob ID (本来は本番用IDを入力)
            resValue("string", "admob_banner_id", "ca-app-pub-3940256099942544/6300978111")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            
            // デバッグ用アプリ名
            manifestPlaceholders["appName"] = "NotiHold (Debug)"
            
            // デバッグ用 AdMob ID (テスト用ID)
            resValue("string", "admob_banner_id", "ca-app-pub-3940256099942544/6300978111")
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
        viewBinding = true
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

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Coil for image loading (View based + Compose) - Memory Optimized
    implementation("io.coil-kt:coil:2.6.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")
    
    // Memory optimization libraries
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("androidx.room:room-paging:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("com.google.android.gms:play-services-ads:23.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Billing Library
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
