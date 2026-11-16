package com.cownow.rsmod;

import android.content.Context;

import com.cownow.rsmod.util.HookUtil;
import com.cownow.rsmod.util.MemoryMaps;
import com.cownow.rsmod.util.UnsafeMem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public final class NativePatcher implements Runnable {

    public static final String SO_NAME = "libil2cpp.so";

    /** 默认 RVA（游戏更新后的版本）；rva.txt 或模块设置可覆盖。 */
    public static final int[] DEFAULT_RVAS = new int[]{
            0x124c584, 0x15c6a3c, 0x15c6d0c,
            0xcc3bf4, 0xcc3a78, 0x124c2f0,
            0x124bc4c, 0x124bcd4, 0x15c6918
    };

    public static final String[] TARGET_NAMES = new String[]{
            "PropsShield::TakeShield",
            "PropUtil::Set_Shield",
            "PropUtil::Set_Levitate",
            "PropFloating::ResetFloating",
            "PropFloating::OnTriggerExit",
            "PropsShield::resetShield",
            "PropsShield::get_IsUseingShield",
            "PropsShield::get_IsTrigger",
            "PropUtil::get_Get_Shield"
    };

    private static final long[] EXPECT_HEAD = new long[]{
            0xF40F1EF8L,  // TakeShield        str x20,[sp,#-0x10]!
            0xFA0F1BF8L,  // Set_Shield        str x26,[sp,#-0x10]!
            0xFA0F1BF8L,  // Set_Levitate      str x26,[sp,#-0x10]!
            0xF40F1EF8L,  // ResetFloating     str x20,[sp,#-0x10]!
            0xA9BE53F5L,  // OnTriggerExit     stp x21,x20,[sp,#-0x20]!
            0xF40F1EF8L,  // resetShield       str x20,[sp,#-0x10]!
            0L,           // get_IsUseingShield（脚本未给首指令）
            0L,           // get_IsTrigger     （脚本未给首指令）
            0xF81E0FF4L   // get_Get_Shield    （旧版探针首字节推导，可能已过期）
    };

    // arm64 补丁体（小端）
    private static final byte[] RET_BYTES = new byte[]{-64, 3, 95, -42};                     // ret
    private static final byte[] TRUE_BYTES = new byte[]{32, 0, -128, 82, -64, 3, 95, -42};   // mov w0,#1 ; ret
    private static final byte[] BIG_BYTES = new byte[]{-32, -31, -124, 82, -64, 3, 95, -42}; // movz w0,#9999 ; ret
    private static final int SHIELD_FAKE = 9999;

    /** 探针取第 9 个目标：PropUtil::get_Get_Shield。 */
    private static final int PROBE_INDEX = 8;

    private static final long OFF_MIN = 0x1000L;
    private static final long OFF_MAX = 0x2a00000L;

    private static final int WAIT_LOOPS = 120;
    private static final long WAIT_GAP_MS = 1000L;

    /** 补丁结果码 */
    private static final int R_CHANGED = 1;
    private static final int R_ALREADY = 2;
    private static final int R_SKIPPED = 0;
    private static final int R_FAILED = -1;

    /** rva.txt 候选路径：按顺序取第一个存在的（行内格式：9 个十六进制，逗号或空格分隔）。 */
    public static final String[] RVA_FILES = new String[]{
            "/sdcard/Rsmod/rva.txt",
            "/storage/emulated/0/Rsmod/rva.txt",
            "/sdcard/Android/data/" + Config.GAME_PKG + "/files/rva.txt",
            "/data/data/" + Config.GAME_PKG + "/files/rva.txt",
            "/data/user/0/" + Config.GAME_PKG + "/files/rva.txt",
    };

    private final Context ctx;

    private NativePatcher(Context ctx) {
        this.ctx = ctx;
    }

    public static void start(Context ctx) {
        Thread t = new Thread(new NativePatcher(ctx), "RsMod-Native");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
        HookUtil.log("native patcher thread started");
    }

    @Override
    public void run() {
        try {
            if (!UnsafeMem.init()) {
                HookUtil.log("无法获取 Unsafe，补丁退出: " + UnsafeMem.error());
                HookUtil.toast(ctx, "RsMod: Unsafe 不可用");
                return;
            }

            int[] rvas = resolveRvas();
            boolean[] enabled = Config.maskToArray(Config.nativeMask);

            long base = waitForBase(rvas);
            if (base == 0L) {
                HookUtil.log("未定位到已解密的 libil2cpp 段，放弃 err=" + UnsafeMem.error()
                        + " maps:" + MemoryMaps.describe(SO_NAME));
                HookUtil.toast(ctx, "RsMod: 未定位到 il2cpp");
                return;
            }
            HookUtil.log("il2cpp 就绪 base=0x" + Long.toHexString(base));

            int changed = 0;
            int skipped = 0;
            int mismatch = 0;
            for (int i = 0; i < rvas.length; i++) {
                if (!enabled[i]) {
                    HookUtil.log("[关闭] " + TARGET_NAMES[i] + "（见 ENABLED 说明）");
                    continue;
                }
                int r = patchOne(base, rvas, i);
                if (r == R_CHANGED) {
                    changed++;
                } else if (r == R_SKIPPED || r == R_FAILED) {
                    skipped++;
                }
                if (expectMismatch(base, rvas, i)) {
                    mismatch++;
                }
            }

            HookUtil.log("生效 " + changed + " 处，跳过 " + skipped + " 处（0 跳过 = RVA 全部有效）");
            if (mismatch > 0 && !Config.strictVerify) {
                HookUtil.log("[提示] " + mismatch + " 个目标首指令与脚本记录不同（未开“防写坏进程”，已按脚本策略写入）");
            }
            dumpHeads(base, rvas);

            if (skipped > 0) {
                HookUtil.toast(ctx, "RVA 已过期，请重跑 relocate（已跳过 " + skipped + " 处）");
            } else {
                HookUtil.toast(ctx, "RsMod: " + changed + " hooks success");
            }
        } catch (Throwable t) {
            HookUtil.log("native patcher fatal " + t);
        }
    }

    // ---------------- 等待段就绪 ----------------

    /** 探针：最后一个目标（RVA[8]），读到非零指令即认为 rwx 段已解密。 */
    private static int probeRva(int[] rvas) {
        return rvas.length > PROBE_INDEX ? rvas[PROBE_INDEX] : rvas[0];
    }

    private long waitForBase(int[] rvas) {
        long cand = 0L;
        int probe = probeRva(rvas);
        for (int i = 0; i < WAIT_LOOPS; i++) {
            cand = MemoryMaps.decBase(SO_NAME);
            if (cand != 0L) {
                long pv = UnsafeMem.readU32(cand + probe);
                if (pv > 0) {
                    return cand;
                }
            }
            if ((i + 1) % 5 == 0) {
                HookUtil.log("[等待 " + (i + 1) + "s] base=0x" + Long.toHexString(cand)
                        + " err=" + UnsafeMem.error());
            }
            try {
                Thread.sleep(WAIT_GAP_MS);
            } catch (InterruptedException e) {
                return 0L;
            }
        }
        return 0L;
    }

    // ---------------- 单个目标 ----------------

    private int patchOne(long base, int[] rvas, int idx) {
        long addr = base + rvas[idx];
        byte[] patch = patchFor(idx);
        long head = leU32(patch);
        long cur = UnsafeMem.readU32(addr);
        if (cur < 0) {
            HookUtil.log("[读失败] " + TARGET_NAMES[idx] + " " + UnsafeMem.error());
            return R_FAILED;
        }
        if (cur == head) {
            HookUtil.log("[已就绪] " + TARGET_NAMES[idx]);
            return R_ALREADY;
        }
        long offs = rvas[idx];
        if (offs < OFF_MIN || offs > OFF_MAX || cur == 0L) {
            HookUtil.log("[跳过] " + TARGET_NAMES[idx] + " 地址不合理 offs=0x"
                    + Integer.toHexString((int) offs) + " insn=0x" + Long.toHexString(cur)
                    + " → RVA 可能已过期");
            return R_SKIPPED;
        }
        long expect = EXPECT_HEAD[idx];
        if (expect != 0L && cur != expect && Config.strictVerify) {
            HookUtil.log("[跳过] " + TARGET_NAMES[idx] + " 首指令不符 cur=0x" + Long.toHexString(cur)
                    + " expect=0x" + Long.toHexString(expect) + "（strict_verify 开启）");
            return R_SKIPPED;
        }
        if (!UnsafeMem.write(addr, patch)) {
            HookUtil.log("[写失败] " + TARGET_NAMES[idx] + " " + UnsafeMem.error());
            return R_FAILED;
        }
        long after = UnsafeMem.readU32(addr);
        HookUtil.log("[补丁] " + TARGET_NAMES[idx] + " @0x" + Integer.toHexString((int) offs)
                + "  0x" + Long.toHexString(cur) + " -> " + patchDesc(idx)
                + (after == head ? "" : "（回读 0x" + Long.toHexString(after) + " 不一致）"));
        return R_CHANGED;
    }

    private boolean expectMismatch(long base, int[] rvas, int idx) {
        long expect = EXPECT_HEAD[idx];
        if (expect == 0L) {
            return false;
        }
        long cur = UnsafeMem.readU32(base + rvas[idx]);
        return cur >= 0 && cur != expect && cur != leU32(patchFor(idx));
    }

    private void dumpHeads(long base, int[] rvas) {
        StringBuilder sb = new StringBuilder("最终指令:");
        for (int i = 0; i < rvas.length; i++) {
            sb.append("\n   ").append(TARGET_NAMES[i]).append(" = 0x")
                    .append(Long.toHexString(UnsafeMem.readU32(base + rvas[i])));
        }
        HookUtil.log(sb.toString());
    }

    // ---------------- 补丁体选择 ----------------

    private static byte[] patchFor(int idx) {
        String nm = TARGET_NAMES[idx];
        if (nm.contains("get_Get_Shield")) {
            return BIG_BYTES;                 // 数量 -> 9999
        }
        if (nm.contains("get_IsUseingShield") || nm.contains("get_IsTrigger")) {
            return TRUE_BYTES;                // bool -> true
        }
        return RET_BYTES;                     // void -> ret
    }

    private static String patchDesc(int idx) {
        String nm = TARGET_NAMES[idx];
        if (nm.contains("get_Get_Shield")) {
            return "mov w0,#" + SHIELD_FAKE + ";ret";
        }
        if (nm.contains("get_IsUseingShield") || nm.contains("get_IsTrigger")) {
            return "mov w0,#1;ret";
        }
        return "ret";
    }

    // ---------------- RVA 来源 ----------------

    private int[] resolveRvas() {
        int[] fromPrefs = Config.parseRvaList(Config.nativeRva);
        if (fromPrefs != null) {
            HookUtil.log("RVA 来源: 模块设置");
            return fromPrefs;
        }
        for (String path : RVA_FILES) {
            int[] parsed = readRvaFile(path);
            if (parsed != null) {
                HookUtil.log("RVA 来源: " + path);
                return parsed;
            }
        }
        HookUtil.log("RVA 来源: 内置默认值（当前 APK 版本）");
        return DEFAULT_RVAS.clone();
    }

    private int[] readRvaFile(String path) {
        File f = new File(path);
        if (!f.isFile() || !f.canRead()) {
            return null;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            String ln = br.readLine();
            if (ln == null) {
                return null;
            }
            int[] parsed = Config.parseRvaList(ln);
            if (parsed == null) {
                HookUtil.log("rva.txt 项数不足或格式错误: " + path);
            }
            return parsed;
        } catch (Throwable t) {
            HookUtil.log("读 rva.txt 失败 " + path + " " + t);
            return null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------------- 小工具 ----------------

    private static long leU32(byte[] b) {
        return ((b[3] & 0xFFL) << 24) | ((b[2] & 0xFFL) << 16) | ((b[1] & 0xFFL) << 8) | (b[0] & 0xFFL);
    }
}
