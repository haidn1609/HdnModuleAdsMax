plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.hdn.theme.testmoduleads"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ezt.theme.testmoduleads"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.core.ktx)

    implementation(project(":AdsModule"))
    implementation(libs.gson)

    // Remote Config lấy ID ads (project Firebase test, file app/google-services.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)

    // Adapter mediation MAX: thêm network đã bật trên dashboard, ví dụ
    // implementation("com.applovin.mediation:google-adapter:<version>")
}
