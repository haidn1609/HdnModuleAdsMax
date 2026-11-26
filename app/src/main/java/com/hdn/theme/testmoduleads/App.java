package com.hdn.theme.testmoduleads;

import android.app.Application;

import com.applovin.adview.AppLovinFullscreenActivity;
import com.hdn.adsmodule.ads.open.OpenAds;
import com.hdn.adsmodule.ads.open.OpenAdsHelper;

public class App extends Application {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        new OpenAdsHelper().setup(this);
        // Không hiện App Open đè lên màn quảng cáo fullscreen của MAX
        OpenAds.disableAdsOpenForActivity(AppLovinFullscreenActivity.class);
    }

    public static App getInstance() {
        return instance;
    }
}
