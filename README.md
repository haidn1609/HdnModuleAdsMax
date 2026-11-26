# HdnModuleAdsMax

Load và show quảng cáo bằng **AppLovin MAX** (SDK 13.7.0), cùng API và logic với
[HdnModuleAds](https://github.com/haidn1609/HdnModuleAds) (bản AdMob):

- Waterfall theo danh sách ID (release + remote), thử lần lượt tới khi có ad.
- Inter có cooldown, fake loading, force show; reward có fallback sang inter.
- App Open khi quay lại app qua màn `Overlay`, tắt được theo từng activity.
- Native có cache theo key, chờ show, load-and-show; banner cache theo bề rộng, có margin.
- VIP / tắt ads, callback doanh thu (`adsPair`) và log (`adsLog`).

Package vẫn là `com.hdn.adsmodule`: app đang dùng HdnModuleAds chuyển sang chỉ cần đổi dependency
và sửa vài chỗ trong mục [Chuyển từ HdnModuleAds](#chuyển-từ-hdnmoduleads-admob).

## Import

`settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Chỉ cần khi dùng adapter tương ứng
        maven { url = uri("https://artifact.bytedance.com/repository/pangle/") }
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
    }
}
```

`app/build.gradle.kts`:
```kotlin
android {
    buildFeatures {
        buildConfig = true
        dataBinding = true
    }
}

dependencies {
    implementation("com.github.haidn1609:HdnModuleAdsMax:1.0.0")

    // Adapter mediation: chỉ thêm network đã bật trên dashboard MAX
    implementation("com.applovin.mediation:google-adapter:+")     // AdMob
    implementation("com.applovin.mediation:facebook-adapter:+")   // Meta Audience Network
    implementation("com.applovin.mediation:mintegral-adapter:+")
    implementation("com.applovin.mediation:bytedance-adapter:+")  // Pangle
}
```
Nên ghim version adapter cụ thể thay cho `+` khi release (xem version trên dashboard MAX hoặc Maven Central).

Thư viện đã kéo sẵn AppLovin SDK và UMP (`api`), app không cần khai báo lại.

### Manifest

Dùng `google-adapter` (AdMob qua MAX) thì **bắt buộc** khai báo AdMob App ID, thiếu là crash lúc mở app:
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-xxxxxxxx~yyyyyyyy" />
```
Không dùng adapter AdMob thì bỏ qua.

## Init

### Application
```kotlin
AdsManager.setDebug(BuildConfig.DEBUG)
AdsManager.setEnabled(false)           // bật lại sau khi init xong
AdsManager.setVip(isPremium)
AdsManager.setInterAdsTime(45_000L)    // cooldown giữa 2 lần show inter (ms)
AdsManager.adsPair = { adValue -> /* doanh thu, xem mục Doanh thu */ }
AdsManager.adsLog = { log -> /* log ads */ }

OpenAdsHelper().setup(application)
// Màn không muốn hiện App Open khi quay lại app
OpenAds.disableAdsOpenForActivity(SplashActivity::class.java)
```

### Splash
Thứ tự: set ID, xin consent, init MAX, bật ads.
```kotlin
// 1. Set ID (xem từng loại bên dưới)
AdsManager.setItsaIdRl(...)

// 2. Consent (UMP). MAX tự đọc chuỗi TCF mà UMP lưu.
val consent = UserMessagingPlatform.getConsentInformation(activity)
consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
        // 3. Init MAX
        AdsManager.initialize(
            context = activity,
            sdkKey = "<APPLOVIN_SDK_KEY>",
            testDeviceIds = listOf("<GAID máy test>"), // chỉ cho debug, release để trống
        ) {
            // 4. Bật ads rồi load / show
            AdsManager.setEnabled(consent.canRequestAds())
        }
    }
}, { /* lỗi consent */ })
```
Có thể dùng `GDPRRequestable` có sẵn trong thư viện thay cho đoạn UMP trên.

Callback của `initialize` luôn chạy trên main thread. SDK key lấy ở dashboard AppLovin → Account → Keys.

## ID quảng cáo

Mỗi slot có 2 ID:
- **Rl** (release): ID mặc định cài trong app.
- **Rm** (remote): thường lấy từ Remote Config. Là 1 ID hoặc JSON array ID (`["id1","id2"]`), thử lần lượt
  theo thứ tự (waterfall). Rỗng hoặc không hợp lệ thì dùng ID release.

MAX **không có ID test công khai** như AdMob: bản debug dùng ID thật. Để nhận quảng cáo test,
thêm GAID máy test vào `testDeviceIds` khi init (hoặc khai báo trên dashboard MAX → Test Mode).
ID còn trống thì slot đó chỉ load fail, không crash.

## Usage

### Open
Set id
```kotlin
AdsManager.setOpaIdRl(id) // id release
AdsManager.setOpaIdRm(id) // id remote
```
Load ads (App Open khi quay lại app do `OpenAdsHelper` tự lo)
```kotlin
OpenAds.initOpenAds(activity) {
    // load xong (thành công hay lỗi)
}
```
Custom màn Overlay: tạo `res/layout/activity_overlay.xml` trong app để ghi đè layout của thư viện.
Id tùy chọn: `imgAvatar` (ImageView, gán icon app), `btnContinue` (click để đóng).

### Interstitial
Set id
```kotlin
AdsManager.setItaIdRl(id) // id release
AdsManager.setItaIdRm(id) // id remote
```
Load ads
```kotlin
InterAds.initInterAds(
    context = activity,
    onLoadError = { /* load lỗi */ },
    onLoadSuccess = { /* load xong */ }
)
```
Show ads
```kotlin
// Có ad và hết cooldown thì show, không thì load sẵn cho lần sau và trả false ngay
AdsManager.showInterAds(activity) { shown ->
    // shown = true: đã show và đóng
}

// Load-and-show: chưa có ad thì hiện loading và load luôn, bỏ qua cooldown
AdsManager.forceShowInterAds(activity) { shown -> }

// fakeLoadingTime (ms): hiện loading tối thiểu chừng đó rồi mới show
AdsManager.showInterAds(activity, fakeLoadingTime = 1500L) { shown -> }
```

### Interstitial Splash
Set id
```kotlin
AdsManager.setItsaIdRl(id) // id release
AdsManager.setItsaIdRm(id) // id remote
```
Load ads
```kotlin
InterSplashAds.initInterAds(activity) {
    // load xong (thành công hay lỗi)
}
```
Show ads
```kotlin
// Show, không có dialog sau khi đóng
AdsManager.showInterSplashAds(
    activity = activity,
    startCallback = { /* bắt đầu show */ },
    doneCallBack = { /* đóng / lỗi -> đi tiếp */ }
)

// Show, kèm dialog native full sau khi đóng
AdsManager.showInterSplashAds(
    activity = activity,
    dialogRes = R.layout.my_native_full, // 0 = layout mặc định của thư viện
    nativeKey = "native_dialog_full",
    startCallback = { },
    doneCallBack = { }
)
```

### Banner Ads
Set id
```kotlin
AdsManager.setBnaIdRl(id)  // banner thường, release
AdsManager.setBnaIdRm(id)  // banner thường, remote
AdsManager.setBncaIdRl(id) // banner "collapsible", release
AdsManager.setBncaIdRm(id) // banner "collapsible", remote
```
Show ads
```kotlin
AdsManager.showBannerAds(activity, BannerType.NORMAL, viewContainer)
// Có margin (dp); bề rộng banner co theo start + end
AdsManager.showBannerAds(activity, BannerType.NORMAL, viewContainer, BannerMargin.symmetric(horizontal = 16))
```
Banner adaptive theo bề rộng màn hình, tự refresh. Banner thường được dùng lại giữa các màn cùng bề rộng.

MAX **không có banner collapsible** như AdMob: `BannerType.COLLAPSIBLE` dùng ID riêng nhưng hiển thị
như banner thường.

### Reward Ads
Set id
```kotlin
AdsManager.setRwaIdRl(id) // id release
AdsManager.setRwaIdRm(id) // id remote
```
Show
```kotlin
AdsManager.showRewardAds(activity, callback = object : RewardAds.RewardCallback {
    override fun onAdShowed() { /* bắt đầu show */ }
    override fun onAdClosed() { /* ad đóng */ }
    override fun onRewardEarned() { /* trao thưởng */ }
    override fun onAdFailed() { /* không show được */ }
    override fun onPremium() { /* VIP / tắt ads: trao thưởng luôn */ }
})

// Reward lỗi thì show inter thay thế
AdsManager.showRewardAds(activity, useInterFallback = true, callback = callback)
```

### Native Ads
Tạo native theo key
```kotlin
AdsManager.addNative("native_home", releaseId, remoteId)
```
Dùng
```kotlin
// Load sẵn vào cache
AdsManager.preloadNative(activity, key)
// Show từ cache; chưa có thì chờ, loadIfMissing = true để load luôn
AdsManager.showNative(activity, key, R.layout.my_native, viewContainer)
// Load and show
AdsManager.loadAndShowNative(activity, key, R.layout.my_native, viewContainer)
```

#### Layout native
Root là view thường (`FrameLayout`, `ConstraintLayout`...), **không** dùng `NativeAdView` / `MediaView`
/ `RatingBar` của AdMob. Thư viện bind theo id, layout thiếu id nào thì bỏ qua cái đó:

| id | Loại view | Nội dung |
|---|---|---|
| `primary` | TextView | tiêu đề |
| `body` | TextView | mô tả |
| `secondary` | TextView | nhà quảng cáo |
| `icon` | ImageView | icon |
| `cta` | Button | nút hành động |
| `media_view` | ViewGroup (vd `FrameLayout`) | ảnh / video |
| `rating_bar` | ViewGroup | số sao |
| `ad_options_view` | ViewGroup | biểu tượng AdChoices |
| `btnClose` | View | đóng native (do thư viện xử lý) |

Field nào ad không có (body, advertiser, rating...) thì view tương ứng tự ẩn.

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <FrameLayout
            android:id="@+id/media_view"
            android:layout_width="match_parent"
            android:layout_height="180dp" />

        <ImageView
            android:id="@+id/icon"
            android:layout_width="40dp"
            android:layout_height="40dp" />

        <TextView
            android:id="@+id/primary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/body"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <Button
            android:id="@+id/cta"
            android:layout_width="match_parent"
            android:layout_height="48dp" />
    </LinearLayout>

    <FrameLayout
        android:id="@+id/ad_options_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|end" />
</FrameLayout>
```

### Doanh thu
`adsPair` nhận mỗi impression có doanh thu (`onAdRevenuePaid` của MAX, luôn là USD):
```kotlin
AdsManager.adsPair = { adValue ->
    // Adjust
    Adjust.trackAdRevenue(AdjustAdRevenue("applovin_max_sdk").apply {
        setRevenue(adValue.revenue, adValue.currencyCode)
        setAdRevenueNetwork(adValue.networkName)
        setAdRevenueUnit(adValue.adUnitId)
    })
}
```
| Thuộc tính | Ý nghĩa |
|---|---|
| `revenue` | doanh thu USD |
| `valueMicros` | `revenue * 1_000_000`, giống AdMob |
| `currencyCode` | luôn `"USD"` |
| `networkName` | network thắng (AdMob, Meta...) |
| `adUnitId` | ad unit MAX |
| `maxAd` | `MaxAd` gốc, đọc thêm placement, precision... |

### Log
```kotlin
AdsManager.adsLog = { log ->
    // log.adType: AdsLog.Type.* (ita, itsa, rwa, bna, bnca, opa) hoặc key native
    // log.action: AdsLog.Action.*, log.mess: AdsLog.Mess.*
    // log.adId, log.adError (MaxError?)
}
```

### Other
```kotlin
AdsManager.setDebug(true)    // log chi tiết của MAX, App Open không chờ 60s giữa 2 lần show
AdsManager.setEnabled(true)  // bật / tắt toàn bộ ads
AdsManager.setVip(true)      // VIP: không show ads (trừ khi gọi với useWithoutVip = true)
AdsManager.showMediationDebugger(context) // kiểm tra adapter và network khi tích hợp
```

## Chuyển từ HdnModuleAds (AdMob)

1. Đổi dependency `com.github.haidn1609:HdnModuleAds` → `com.github.haidn1609:HdnModuleAdsMax`.
   Bỏ `play-services-ads` và adapter `com.google.ads.mediation:*`, thêm adapter `com.applovin.mediation:*`.
2. `MobileAds.initialize(...)` → `AdsManager.initialize(context, sdkKey) { }`.
3. Đổi ID AdMob sang ID ad unit MAX (Rl và Rm).
4. `adsPair`: `adValue.adValue.valueMicros` / `currencyCode` → `adValue.revenue` / `adValue.currencyCode`;
   source Adjust đổi từ `admob_sdk` sang `applovin_max_sdk`.
5. Layout native: bỏ `NativeAdView` / `MediaView` / `RatingBar`, đổi `media_view` và `rating_bar` sang
   ViewGroup, thêm `ad_options_view` nếu muốn (xem [Layout native](#layout-native)).
6. Code đọc `AdsLog.adError` kiểu `AdError` → `MaxError`.
7. `BannerAds.getAdSize(...)` đã bỏ → `BannerAds.getAdWidthDp(...)`.

Các hàm show / load / preload và callback giữ nguyên.

## Publish (JitPack)

1. Push lên GitHub (`haidn1609/HdnModuleAdsMax`).
2. Tăng `version` trong `AdsModule/build.gradle.kts` nếu cần, tạo tag trùng version (vd `1.0.0`).
3. JitPack build theo `jitpack.yml`; app dùng `com.github.haidn1609:HdnModuleAdsMax:<tag>`.

Build và test local:
```bash
./gradlew :AdsModule:assembleRelease :AdsModule:testDebugUnitTest
```
