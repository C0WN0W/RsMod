package com.cownow.rsmod;

import java.util.Locale;

public final class AdKeywords {

    public static final String[] KEYWORDS = new String[]{
            "sigmob", "ironsource", "tobid", "pangle", "bytedance", "applovin",
            "mbridge", "mintegral", "unityads", "unity3d.services", "kwad",
            "com.qq.e", "klevin", "anythink", "topon", "chartboost", "vungle",
            "tapjoy", "facebook.ads", "gms.ads", "sdk.controller",
            "adactivity", "portraitad", "interstitial", "rewardvideo", "rewarded"
    };

    private AdKeywords() {
    }

    public static boolean matches(String className) {
        if (className == null) {
            return false;
        }
        String low = className.toLowerCase(Locale.US);
        for (String kw : KEYWORDS) {
            if (low.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
