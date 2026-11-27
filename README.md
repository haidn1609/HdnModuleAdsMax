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

`settings.gradle`:
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

`app/build.gradle`:
```groovy
android {
    buildFeatures {
        buildConfig true
        dataBinding true
    }
}

dependencies {
    implementation 'com.github.haidn1609:HdnModuleAdsMax:v1.0.0'
}
```

Thư viện đã kéo sẵn AppLovin SDK và UMP (`api`), app không cần khai báo lại. `google()` cần cho UMP và
AndroidX (project Android thường đã có sẵn).

### Adapter mediation

Thêm adapter của các network đã bật bidding trên dashboard MAX vào `app/build.gradle`:
```groovy
dependencies {
    implementation 'com.applovin.mediation:fyber-adapter:8.5.1.0'         // DT Exchange
    implementation 'com.applovin.mediation:google-adapter:25.5.0.0'       // Google (AdMob)
    implementation 'com.applovin.mediation:bidmachine-adapter:3.8.0.1'
    implementation 'com.applovin.mediation:ironsource-adapter:9.8.0.0.0'
    implementation 'com.applovin.mediation:vungle-adapter:7.8.0.0'        // Liftoff
    implementation 'com.applovin.mediation:facebook-adapter:6.22.1.0'     // Meta
    implementation 'com.applovin.mediation:mintegral-adapter:17.2.21.0'
    implementation 'com.applovin.mediation:bytedance-adapter:8.4.0.6.0'   // Pangle
    implementation 'com.applovin.mediation:unityads-adapter:4.22.0.0'
}
```
BidMachine, Pangle và Mintegral cần thêm repo trong `settings.gradle`:
```groovy
maven { url 'https://artifactory.bidmachine.io/bidmachine' }
maven { url 'https://artifact.bytedance.com/repository/pangle/' }
maven { url 'https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea' }
```
Version trên là bản mới nhất lúc viết (đã build cùng AppLovin SDK 13.7.0); kiểm tra lại trên Maven Central
hoặc dashboard MAX khi nâng cấp. Mở `AdsManager.showMediationDebugger(context)` để xem adapter nào đã nhận.

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
            testDeviceIds = listOf("<GAID máy test>"), // chỉ dùng khi setDebug(true), release tự bỏ qua
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
`testDeviceIds` chỉ có hiệu lực khi `AdsManager.setDebug(true)` (gọi trước `initialize`): app cứ để danh sách
GAID cố định, bản release (`setDebug(BuildConfig.DEBUG)` = false) tự bỏ qua.
ID còn trống thì slot đó chỉ load fail, không crash.

Thư viện kiểm tra trước khi load và fail ngay (log `invalid_sdk_key` / `invalid_ad_unit_id`) khi SDK key
ngắn hơn 86 ký tự hoặc ad unit không phải 16 ký tự hex: MAX chỉ log lỗi chứ không gọi callback trong
trường hợp này. Ad unit nào load quá lâu mà MAX không trả kết quả thì sau `AdsManager.setLoadTimeout(ms)`
(mặc định 15000) cũng coi là fail và thử ID tiếp theo.

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

// Show, kèm dialog native full: 3s sau khi inter hiện thì mở dialog (nằm dưới inter),
// dialog mở rồi mới load native; user đóng inter thì thấy dialog, đóng dialog mới gọi doneCallBack
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
như banner thường, **không expand**. Banner collapsible là tính năng riêng của AdMob (tham số
`collapsible=bottom` gửi kèm request); qua MAX, adapter Google tự tạo request và không nhận tham số này.
Cần hiệu ứng mở to / thu nhỏ ở đáy màn hình thì dùng native collapse (vd slot `nt_clp`) thay thế.

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

Lưu ý khi làm layout:
- Root giữ đúng kích thước khai báo trong XML (layout full màn để `match_parent` vẫn full màn).
- `rating_bar` để `wrap_content` thì thư viện đặt cố định 80x16dp cho dải sao (MAX tự gắn view sao kiểu
  `match_parent`, không giới hạn thì khung sao giãn hết màn và đẩy media/tiêu đề ra ngoài).
- `media_view` tối thiểu 120x120dp nếu muốn nhận video (AdMob native validator sẽ cảnh báo nếu nhỏ hơn).
- Layout dialog native full sau splash có thể ghi đè bằng `res/layout/template_native_full.xml`;
  thiếu id nào cũng không crash.

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
AdsManager.setDebug(true)    // log chi tiết của MAX, App Open không chờ 60s giữa 2 lần show,
                             // testDeviceIds có hiệu lực (gọi trước initialize)
AdsManager.setEnabled(true)  // bật / tắt toàn bộ ads
AdsManager.setVip(true)      // VIP: không show ads (trừ khi gọi với useWithoutVip = true)
AdsManager.showMediationDebugger(context) // kiểm tra adapter và network khi tích hợp
```

## Chuyển từ HdnModuleAds (AdMob)

1. Đổi dependency `com.github.haidn1609:HdnModuleAds` → `com.github.haidn1609:HdnModuleAdsMax:v1.0.0`.
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

## App demo

Module `app` có màn test mọi loại ads (open, inter, inter splash, force inter, banner, native, native full,
native collapse, reward) và nút mở Mediation Debugger.

1. SDK key và ID MAX (app "Draw Music Android") có sẵn trong
   [`MaxTestIds.kt`](app/src/main/java/com/hdn/theme/testmoduleads/MaxTestIds.kt), chạy được ngay.
   Demo dùng package **`com.ezpro.drawmusic.sketchmusic`** (trùng app Draw Music) vì ad unit MAX đăng ký
   theo package: package khác thì MAX trả lỗi `INV-REQ-MISSING-APP-ID` / "Ad Unit ID ... is invalid or
   disabled". Máy đang cài bản Draw Music từ store (khác chữ ký) thì phải gỡ trước khi cài demo.
   `app/google-services.json` là file Firebase của Draw Music (project `ezt-draw-music`).

   Muốn đổi ID không cần build lại, trên Firebase Remote Config giữ logic demo cũ:

   | Key | Giá trị |
   |---|---|
   | `using_case_test_value` | tên key chứa JSON ID, vd `config_ads_max` (mặc định) |
   | `config_ads_max` (tên ở trên) | nội dung [`docs/config_ads_max.json`](docs/config_ads_max.json) |

   Đổi bộ ID test chỉ cần tạo key JSON khác rồi trỏ `using_case_test_value` sang nó. Đây là Firebase của
   app thật: nên thêm 2 key này theo điều kiện riêng cho bản debug thay vì áp cho mọi người dùng.

   ```json
   {
     "sdk_key": "...",
     "bn_inapp": ["..."], "int_inapp": ["..."], "rv_inapp": ["..."], "nt_inapp": ["..."],
     "int_splash": ["..."], "nt_lang1": ["..."], "nt_lang2": ["..."], "nt_clp": ["..."],
     "nt_onb_full": ["..."], "aoa": ["..."]
   }
   ```
   Mỗi slot là list ID (waterfall). Slot thiếu hoặc ID không đúng định dạng MAX thì dùng ID mặc định.

   | Slot | Dùng cho nút demo |
   |---|---|
   | `aoa` | show opa (App Open) |
   | `int_inapp` | show ita, show ita fix |
   | `int_splash` | show itsa |
   | `rv_inapp` | show rwa |
   | `bn_inapp` | show bna, show bnca (bnca hiển thị như banner thường) |
   | `nt_inapp` | show nta1, nta2 |
   | `nt_clp` | show ntca |
   | `nt_onb_full` | show nta3, nta4, dialog sau itsa |
   | `nt_lang1`, `nt_lang2` | native key `native_lang1` / `native_lang2` (chưa có nút) |

   GAID máy test khai báo trong `MaxTestIds.TEST_DEVICE_IDS` (chỉ áp dụng cho bản debug).
2. Adapter 9 network bidding (DT Exchange, Google, BidMachine, IronSource, Liftoff, Meta, Mintegral,
   Pangle, Unity) đã có trong `app/build.gradle.kts`. Adapter Google cần AdMob App ID trong manifest.
3. Chạy `./gradlew :app:installDebug`, mở bằng
   `adb shell am start -n com.ezpro.drawmusic.sketchmusic/com.hdn.theme.testmoduleads.MainActivity`,
   xem log với tag `Ads_log` và `Ads_revenue`.

Demo bỏ qua bước consent UMP cho gọn; app thật phải xin consent trước `AdsManager.initialize`.
Layout native mẫu: `app/src/main/res/layout/template_native_*.xml`.

## Publish (JitPack)

1. Push lên GitHub (`haidn1609/HdnModuleAdsMax`).
2. Tạo tag theo version, vd `v1.0.0` (nên tăng `version` trong `AdsModule/build.gradle.kts` cho khớp).
3. JitPack build theo `jitpack.yml`; app dùng `com.github.haidn1609:HdnModuleAdsMax:<tag>`
   (vd `com.github.haidn1609:HdnModuleAdsMax:v1.0.0`).

Build và test local:
```bash
./gradlew :AdsModule:assembleRelease :AdsModule:testDebugUnitTest
```
