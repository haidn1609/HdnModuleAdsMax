package com.hdn.theme.testmoduleads;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.hdn.adsmodule.ads.AdsManager;

import java.util.List;

/**
 * Remote Config: `using_case_test_value` chứa tên key, key đó chứa JSON ID (xem docs/config_ads_max.json),
 * rồi set SDK key + ID cho AdsManager. Fetch lỗi / JSON hỏng vẫn chạy với ID mặc định trong MaxTestIds.
 */
public class RemoteManager {
    public static final String use_case_test = "using_case_test_value";
    private static final Gson gson = new Gson();
    private static MaxConfig config = new MaxConfig();

    public static void initRemoteConfig(Runnable complete) {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setConfigSettingsAsync(new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build());
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);
        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            // Giữ logic demo cũ: using_case_test_value = tên key chứa JSON ID (vd config_ads_max)
            String useCaseTest = remoteConfig.getString(use_case_test).trim();
            config = parse(useCaseTest.isEmpty() ? "" : remoteConfig.getString(useCaseTest));
            initIds();
            if (complete != null) complete.run();
        });
    }

    public static String getSdkKey() {
        String key = config.getSdkKey();
        return key == null || key.trim().isEmpty() ? MaxTestIds.SDK_KEY : key.trim();
    }

    // Rl = ID mặc định của demo, Rm = ID trên Firebase (rỗng thì thư viện dùng Rl)
    private static void initIds() {
        AdsManager.setOpaIdRl(MaxTestIds.APP_OPEN);
        AdsManager.setOpaIdRm(remote(config.getAoa()));

        AdsManager.setItaIdRl(MaxTestIds.INTERSTITIAL);
        AdsManager.setItaIdRm(remote(config.getIntInapp()));

        AdsManager.setItsaIdRl(MaxTestIds.INTER_SPLASH);
        AdsManager.setItsaIdRm(remote(config.getIntSplash()));

        AdsManager.setRwaIdRl(MaxTestIds.REWARDED);
        AdsManager.setRwaIdRm(remote(config.getRvInapp()));

        // Config chưa có banner collapse riêng -> dùng chung bn_inapp
        AdsManager.setBnaIdRl(MaxTestIds.BANNER);
        AdsManager.setBnaIdRm(remote(config.getBnInapp()));
        AdsManager.setBncaIdRl(MaxTestIds.BANNER);
        AdsManager.setBncaIdRm(remote(config.getBnInapp()));

        AdsManager.addNative("native_home", MaxTestIds.NATIVE, remote(config.getNtInapp()));
        AdsManager.addNative("native_collapse", MaxTestIds.NATIVE_COLLAPSE, remote(config.getNtClp()));
        AdsManager.addNative("native_dialog_full", MaxTestIds.NATIVE_FULL, remote(config.getNtOnbFull()));
        AdsManager.addNative("native_lang1", MaxTestIds.NATIVE_LANG1, remote(config.getNtLang1()));
        AdsManager.addNative("native_lang2", MaxTestIds.NATIVE_LANG2, remote(config.getNtLang2()));
    }

    private static String remote(List<String> ids) {
        return gson.toJson(MaxTestIds.maxIds(ids));
    }

    private static MaxConfig parse(String json) {
        try {
            MaxConfig parsed = gson.fromJson(json, MaxConfig.class);
            return parsed != null ? parsed : new MaxConfig();
        } catch (JsonSyntaxException e) {
            return new MaxConfig();
        }
    }
}
