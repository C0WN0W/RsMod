package com.cownow.rsmod;

import android.app.Activity;
import android.content.Intent;

import com.cownow.rsmod.util.HookUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public final class AdSkip {

    public static final String CLS_NATIVE_UTIL = "com.turbochilli.rollingsky.util.NativeUtil";
    public static final String METHOD_AD_STATUS = "callbackAdShowStatues";

    private static final int MAX_SEEN_LOG = 30;
    private static final List<String> seen = Collections.synchronizedList(new ArrayList<String>());

    private AdSkip() {
    }

    public static void install(ClassLoader cl) {
        hookActivityCreate(cl);
        hookActivityResume(cl);
        hookStartActivity(cl);
        hookAdCallback(cl);
    }

    public static boolean isAdClass(String className) {
        return AdKeywords.matches(className);
    }

    private static void hookActivityCreate(ClassLoader cl) {
        Class<?> activity = XposedHelpers.findClassIfExists("android.app.Activity", cl);
        if (activity == null) {
            HookUtil.log("ad skip: 找不到 android.app.Activity");
            return;
        }
        int n = HookUtil.hookAll(activity, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object self = param.thisObject;
                if (!(self instanceof Activity)) {
                    return;
                }
                Activity act = (Activity) self;
                String cls = act.getClass().getName();
                if (AdKeywords.matches(cls)) {
                    finishQuietly(act, cls);
                } else if (seen.size() < MAX_SEEN_LOG && !seen.contains(cls)) {
                    seen.add(cls);
                    HookUtil.log("  (正常页) " + cls);
                }
            }
        });
        HookUtil.log("ad skip: Activity.onCreate hooks=" + n);
    }

    private static void hookActivityResume(ClassLoader cl) {
        Class<?> activity = XposedHelpers.findClassIfExists("android.app.Activity", cl);
        if (activity == null) {
            return;
        }
        int n = HookUtil.hookAll(activity, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object self = param.thisObject;
                if (!(self instanceof Activity)) {
                    return;
                }
                Activity act = (Activity) self;
                String cls = act.getClass().getName();
                if (AdKeywords.matches(cls) && !act.isFinishing()) {
                    HookUtil.log("[关闭广告页/onResume] " + cls);
                    finishQuietly(act, cls);
                }
            }
        });
        HookUtil.log("ad skip: Activity.onResume hooks=" + n);
    }

    private static void finishQuietly(Activity act, String cls) {
        try {
            if (!act.isFinishing()) {
                act.finish();
                HookUtil.log("[关闭广告页] " + cls);
            }
        } catch (Throwable t) {
            HookUtil.log("finish ad page fail " + t);
        }
    }

    private static void hookStartActivity(ClassLoader cl) {
        Class<?> cw = XposedHelpers.findClassIfExists("android.content.ContextWrapper", cl);
        if (cw == null) {
            HookUtil.log("ad skip: 找不到 android.content.ContextWrapper");
            return;
        }
        int n = HookUtil.hookAll(cw, "startActivity", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof Intent)) {
                    return;
                }
                Intent intent = (Intent) param.args[0];
                String target = "";
                if (intent.getComponent() != null) {
                    target = intent.getComponent().getClassName();
                }
                if (AdKeywords.matches(target)) {
                    HookUtil.log("[拦截] startActivity -> " + target);
                    param.setResult(null);
                }
            }
        });
        HookUtil.log("ad skip: ContextWrapper.startActivity hooks=" + n);
    }

    private static void hookAdCallback(ClassLoader cl) {
        Class<?> nativeUtil = XposedHelpers.findClassIfExists(CLS_NATIVE_UTIL, cl);
        if (nativeUtil == null) {
            return;
        }
        HookUtil.hookAll(nativeUtil, METHOD_AD_STATUS, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                StringBuilder sb = new StringBuilder(METHOD_AD_STATUS).append('(');
                Object[] args = param.args == null ? new Object[0] : param.args;
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(args[i]);
                }
                sb.append(')');
                HookUtil.log(sb.toString());
            }
        });
    }
}
