package com.cownow.rsmod.status;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * 注入状态存储。
 *
 * 背景：LSPosed 是「按作用域注入」的，模块自己的进程默认不会被注入，
 * 所以在模块进程里用 Class.forName("de.robv.android.xposed.XposedBridge") 判断激活永远是 false。
 * 这里改成由被注入的进程（游戏 / 自身）主动上报心跳，UI 读心跳判断真实注入情况。
 */
public final class StatusStore {

    public static final String MODULE_PKG = "com.cownow.rsmod";
    public static final String GAME_PKG = "com.turbochilli.rollingsky";

    public static final String AUTHORITY = "com.cownow.rsmod.status";
    public static final Uri HEARTBEAT_URI = Uri.parse("content://" + AUTHORITY + "/heartbeat");
    public static final String METHOD_HEARTBEAT = "heartbeat";
    public static final String METHOD_CONFIG = "config";
    public static final String ACTION_HEARTBEAT = "com.cownow.rsmod.action.HEARTBEAT";
    public static final String ACTION_DUMP_STATE = "com.cownow.rsmod.action.DUMP_STATE";
    public static final String RECEIVER = MODULE_PKG + ".status.StatusReceiver";

    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_PROCESS = "process";
    public static final String EXTRA_SELF = "self";
    public static final String EXTRA_CFG = "cfg";

    // ---- dump 面板 ----
    public static final String METHOD_DUMP_STATE = "dump_state";
    public static final String EXTRA_DUMP_STATUS = "dump_status";
    public static final String EXTRA_DUMP_RVAS = "dump_rvas";

    private static final String PREFS = "rsmod_status";
    private static final String K_SELF_TIME = "self_time";
    private static final String K_GAME_TIME = "game_time";
    private static final String K_SRC_PKG = "src_pkg";
    private static final String K_SRC_PROCESS = "src_process";
    private static final String K_CFG = "cfg_source";
    private static final String K_DUMP_STATUS = "dump_status";
    private static final String K_DUMP_RVAS = "dump_rvas";
    private static final String K_DUMP_TIME = "dump_time";

    private StatusStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 记录一次心跳；self = 被注入的是模块自身进程，cfg = 该进程实际用的配置来源。 */
    public static void record(Context ctx, String pkg, String process, boolean self) {
        record(ctx, pkg, process, self, "");
    }

    public static void record(Context ctx, String pkg, String process, boolean self, String cfg) {
        if (ctx == null) {
            return;
        }
        String p = pkg == null ? "" : pkg;
        boolean fromModule = self || MODULE_PKG.equals(p);
        // 只认本模块和游戏包名，避免被别的应用随手刷成“已激活”
        if (!fromModule && !GAME_PKG.equals(p)) {
            return;
        }
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (fromModule) {
            e.putLong(K_SELF_TIME, now);
        } else {
            e.putLong(K_GAME_TIME, now)
                    .putString(K_SRC_PKG, p)
                    .putString(K_SRC_PROCESS, process == null ? "" : process)
                    .putString(K_CFG, cfg == null ? "" : cfg);
        }
        e.apply();
    }

    /** 模块自身被注入的最近时间（0 = 从未）。 */
    public static long selfTime(Context ctx) {
        return prefs(ctx).getLong(K_SELF_TIME, 0L);
    }

    /** 游戏进程被注入的最近时间（0 = 从未）。 */
    public static long gameTime(Context ctx) {
        return prefs(ctx).getLong(K_GAME_TIME, 0L);
    }

    public static String gameProcess(Context ctx) {
        return prefs(ctx).getString(K_SRC_PROCESS, "");
    }

    /** 游戏进程上报的配置来源：prefs / provider / defaults（空 = 旧版本没上报）。 */
    public static String gameCfg(Context ctx) {
        return prefs(ctx).getString(K_CFG, "");
    }

    /** dump 进度/结果（游戏进程上报，设置页读取）。rvas 传 null 表示只更新状态文本。 */
    public static void recordDump(Context ctx, String status, String rvas) {
        if (ctx == null) {
            return;
        }
        SharedPreferences.Editor e = prefs(ctx).edit()
                .putString(K_DUMP_STATUS, status == null ? "" : status)
                .putLong(K_DUMP_TIME, System.currentTimeMillis());
        if (rvas != null) {
            e.putString(K_DUMP_RVAS, rvas);
        }
        e.apply();
    }

    public static String dumpStatus(Context ctx) {
        return prefs(ctx).getString(K_DUMP_STATUS, "");
    }

    public static String dumpRvas(Context ctx) {
        return prefs(ctx).getString(K_DUMP_RVAS, "");
    }

    public static long dumpTime(Context ctx) {
        return prefs(ctx).getLong(K_DUMP_TIME, 0L);
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }
}
