package com.cownow.rsmod;

import android.app.Application;
import android.content.Context;

import com.cownow.rsmod.status.HookReporter;
import com.cownow.rsmod.status.StatusStore;
import com.cownow.rsmod.util.HookUtil;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedInit implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static volatile boolean installed = false;
    private static String modulePath = "";

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath == null ? "" : startupParam.modulePath;
        if (!modulePath.isEmpty()) {
            HookUtil.log("module apk: " + modulePath);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (StatusStore.MODULE_PKG.equals(lpparam.packageName)) {
            HookReporter.selfInjected = true;
            HookUtil.log("module self-injected, process=" + lpparam.processName);
            hookAttach(lpparam, true);
            return;
        }

        if (!Config.GAME_PKG.equals(lpparam.packageName)) {
            return;
        }
        Config.load();
        if (!isTargetProcess(lpparam)) {
            HookUtil.log("skip process " + lpparam.processName);
            return;
        }
        HookUtil.log("handleLoadPackage " + lpparam.packageName + " / " + lpparam.processName
                + " cfg=" + Config.source);
        hookAttach(lpparam, false);
    }

    private void hookAttach(XC_LoadPackage.LoadPackageParam lpparam, final boolean self) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader,
                    "attach", Context.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context ctx = param.args.length > 0 && param.args[0] instanceof Context
                                    ? (Context) param.args[0] : null;
                            if (ctx == null) {
                                return;
                            }
                            if (self) {
                                HookReporter.selfInjected = true;
                                HookUtil.log("self heartbeat sent");
                                HookReporter.report(ctx, ctx.getPackageName(),
                                        HookReporter.processName(ctx), true, "self");
                                return;
                            }
                            if (installed) {
                                return;
                            }
                            installed = true;
                            install(ctx, lpparam.classLoader);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private boolean isTargetProcess(XC_LoadPackage.LoadPackageParam lp) {
        if (Config.allProcesses) {
            return true;
        }
        if (lp.processName == null) {
            return true;
        }
        return Config.GAME_PKG.equals(lp.processName);
    }

    private void install(Context ctx, ClassLoader cl) {
        try {
            Config.load(ctx);
        } catch (Throwable t) {
            HookUtil.log("Config.load error " + t);
        }
        HookUtil.log("config source=" + Config.source
                + " integrity=" + Config.integrity
                + " pay=" + Config.pay + " compliance=" + Config.compliance
                + " adSkip=" + Config.adSkip
                + " pushRewardById=" + Config.pushRewardById
                + " native=" + Config.nativePatch + " mask=" + Config.nativeMask);

        if (Config.SRC_DEFAULTS.equals(Config.source)) {
            HookUtil.log("!! 配置读取失败（prefs 不可读且 Provider 无响应），已回退默认值");
            HookUtil.toast(ctx, "RsMod: 配置读取失败，已使用默认值");
        }

        if (Config.integrity) {
            try {
                IntegrityBypass.install(cl);
            } catch (Throwable t) {
                HookUtil.log("IntegrityBypass error " + t);
            }
        }

        if (Config.pay) {
            try {
                PayBypass.install(cl);
            } catch (Throwable t) {
                HookUtil.log("PayBypass error " + t);
            }
        }

        if (Config.compliance) {
            try {
                ComplianceBypass.install(cl);
            } catch (Throwable t) {
                HookUtil.log("ComplianceBypass error " + t);
            }
        }

        if (Config.adSkip) {
            try {
                AdSkip.install(cl);
            } catch (Throwable t) {
                HookUtil.log("AdSkip error " + t);
            }
        }

        if (Config.nativePatch) {
            try {
                NativePatcher.start(ctx);
            } catch (Throwable t) {
                HookUtil.log("NativePatcher error " + t);
            }
        }

        HookUtil.log("install end");

        try {
            HookReporter.report(ctx, ctx.getPackageName(),
                    HookReporter.processName(ctx), false, Config.source);
        } catch (Throwable t) {
            HookUtil.log("report fail " + t);
        }
    }
}
