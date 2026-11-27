plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.hdn.theme.testmoduleads"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ezpro.drawmusic.sketchmusic" // trùng app Draw Music: ad unit MAX đăng ký theo package này
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

    // Adapter mediation MAX: các network bidding đang bật trên dashboard
    implementation("com.applovin.mediation:fyber-adapter:8.5.1.0")         // DT Exchange
    implementation("com.applovin.mediation:google-adapter:25.5.0.0")       // Google (AdMob)
    implementation("com.applovin.mediation:bidmachine-adapter:3.8.0.1")
    implementation("com.applovin.mediation:ironsource-adapter:9.8.0.0.0")
    implementation("com.applovin.mediation:vungle-adapter:7.8.0.0")        // Liftoff
    implementation("com.applovin.mediation:facebook-adapter:6.22.1.0")     // Meta
    implementation("com.applovin.mediation:mintegral-adapter:17.2.21.0")
    implementation("com.applovin.mediation:bytedance-adapter:8.4.0.6.0")   // Pangle
    implementation("com.applovin.mediation:unityads-adapter:4.22.0.0")
}
