# HdnModuleAdsMax

Bản AppLovin MAX của [HdnModuleAds](https://github.com/haidn1609/HdnModuleAds). API và logic giữ nguyên
(`AdsManager`, `InterAds`, `InterSplashAds`, `RewardAds`, `OpenAds`/`OpenAdsHelper`, `NativeAds`, `BannerAds`,
waterfall theo list ID, cooldown inter, fake loading, cache native, `AdsLog`), chỉ đổi SDK bên dưới sang MAX.
Package vẫn là `com.hdn.adsmodule` để app chuyển từ HdnModuleAds chỉ cần đổi dependency.

## Import

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.haidn1609:HdnModuleAdsMax:<tag>")
    // Adapter mediation MAX khai báo ở app, ví dụ:
    // implementation("com.applovin.mediation:google-adapter:+")
    // implementation("com.applovin.mediation:facebook-adapter:+")
}
```

App cần `buildFeatures { buildConfig = true; dataBinding = true }` như bản gốc.
Nếu dùng adapter Google (AdMob) qua MAX, app vẫn phải khai báo `com.google.android.gms.ads.APPLICATION_ID` trong manifest.

## Khác bản AdMob

| | HdnModuleAds (AdMob) | HdnModuleAdsMax |
|---|---|---|
| Init | `MobileAds.initialize(context)` | `AdsManager.initialize(context, sdkKey, testDeviceIds) { }` (sau consent UMP) |
| ID debug | ID test của Google | Không có: debug dùng ID thật, máy trong `testDeviceIds` nhận ad test |
| `AdValue` | `adValue.valueMicros/currencyCode` (AdMob) | `revenue` (USD), `valueMicros`, `currencyCode = "USD"`, `networkName`, `maxAd` |
| `AdsLog.adError` | `AdError` | `MaxError` |
| Banner collapsible | AdMob collapsible | MAX không có: `BannerType.COLLAPSIBLE` dùng ID riêng, hiển thị như banner thường |
| Layout native | `NativeAdView`, `MediaView`, `RatingBar` | View thường, bind theo id (bên dưới) |

### Layout native

Root là view thường (FrameLayout/ConstraintLayout...). Bind theo id, id nào không có thì bỏ qua:

| id | View | Nội dung |
|---|---|---|
| `primary` | TextView | title |
| `body` | TextView | body |
| `secondary` | TextView | advertiser |
| `icon` | ImageView | icon |
| `cta` | Button | call to action |
| `media_view` | ViewGroup | media |
| `rating_bar` | ViewGroup | star rating |
| `ad_options_view` | ViewGroup | AdChoices |
| `btnClose` | View | đóng native (giữ như bản gốc) |

## Usage

```kotlin
// Application
AdsManager.setDebug(BuildConfig.DEBUG)
AdsManager.adsPair = { v -> Adjust.trackAdRevenue(AdjustAdRevenue("applovin_max_sdk").apply { setRevenue(v.revenue, v.currencyCode) }) }
OpenAdsHelper().setup(application)

// Splash: consent UMP xong rồi
AdsManager.initialize(context, sdkKey = "<SDK_KEY>", testDeviceIds = listOf("<GAID máy test>")) {
    // load / show ads
}

// ID: release + remote (1 ID hoặc JSON array = waterfall), giống bản gốc
AdsManager.setItaIdRl("<inter id>")
AdsManager.setItaIdRm("[\"<id 1>\", \"<id 2>\"]")
AdsManager.addNative("native_home", "<native id>", "")
```

Phần còn lại (`showInterAds`, `showInterSplashAds`, `showRewardAds`, `showBannerAds`, `preloadNative`,
`showNative`, `loadAndShowNative`, `OpenAds.disableAdsOpenForActivity`...) dùng như HdnModuleAds.

## Publish (JitPack)

Push lên GitHub, tạo tag (vd `1.0.0`), JitPack build theo `jitpack.yml`.
