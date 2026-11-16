package com.cownow.rsmod.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * main2.java 里的 Unsafe 读写层（readAt / readU32 / writeAt / initUnsafe）。
 *
 * 原脚本只认 sun.misc.Unsafe.theUnsafe 一个字段；这里补了 THE_ONE / getUnsafe() 两条候选路径，
 * 因为不同 ROM 的 libcore 暴露的名字不一样。全部失败时返回 false，由上层跳过并提示，绝不写坏进程。
 */
public final class UnsafeMem {

    private static Object unsafe;
    private static Method getByteL;    // getByte(long)
    private static Method getByteOL;   // getByte(Object, long)
    private static Method putByteLB;   // putByte(long, byte)
    private static Method putByteOLB;  // putByte(Object, long, byte)
    private static Method copyOOL;     // copyMemory(Object, long, Object, long, long)
    private static Method arrayBase;   // arrayBaseOffset(Class)
    private static long byteArrayBase = 16L;
    private static String error = "not initialized";

    private UnsafeMem() {
    }

    public static synchronized boolean init() {
        if (unsafe != null) {
            return true;
        }
        try {
            Class<?> uc = Class.forName("sun.misc.Unsafe");
            Object u = firstUnsafe(uc);
            if (u == null) {
                error = "no Unsafe instance";
                return false;
            }
            unsafe = u;
            getByteL = optional(uc, "getByte", long.class);
            getByteOL = optional(uc, "getByte", Object.class, long.class);
            putByteLB = optional(uc, "putByte", long.class, byte.class);
            putByteOLB = optional(uc, "putByte", Object.class, long.class, byte.class);
            if (getByteL == null && getByteOL == null) {
                error = "no getByte";
                unsafe = null;
                return false;
            }
            if (putByteLB == null && putByteOLB == null) {
                error = "no putByte";
                unsafe = null;
                return false;
            }
            // 批量读取用：copyMemory(Object,long,Object,long,long) + arrayBaseOffset(byte[])
            copyOOL = optional(uc, "copyMemory",
                    Object.class, long.class, Object.class, long.class, long.class);
            arrayBase = optional(uc, "arrayBaseOffset", Class.class);
            if (arrayBase != null) {
                try {
                    Object v = arrayBase.invoke(unsafe, byte[].class);
                    if (v instanceof Number) {
                        byteArrayBase = ((Number) v).longValue();
                    }
                } catch (Throwable ignored) {
                }
            }
            error = "";
            return true;
        } catch (Throwable t) {
            error = "init:" + t;
            return false;
        }
    }

    private static Object firstUnsafe(Class<?> uc) {
        String[] fields = {"theUnsafe", "THE_ONE"};
        for (String f : fields) {
            try {
                Field ff = uc.getDeclaredField(f);
                ff.setAccessible(true);
                Object o = ff.get(null);
                if (o != null) {
                    return o;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Method m = uc.getDeclaredMethod("getUnsafe");
            m.setAccessible(true);
            return m.invoke(null);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method optional(Class<?> c, String name, Class<?>... types) {
        try {
            Method m = c.getMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String error() {
        return error;
    }

    public static byte[] read(long addr, int n) {
        if (unsafe == null) {
            return null;
        }
        try {
            byte[] buf = new byte[n];
            for (int i = 0; i < n; i++) {
                long a = addr + i;
                Object v = (getByteL != null)
                        ? getByteL.invoke(unsafe, Long.valueOf(a))
                        : getByteOL.invoke(unsafe, null, Long.valueOf(a));
                buf[i] = ((Number) v).byteValue();
            }
            return buf;
        } catch (Throwable t) {
            error = "R:" + t;
            return null;
        }
    }

    public static long readU32(long addr) {
        byte[] q = read(addr, 4);
        if (q == null) {
            return -1L;
        }
        return ((q[3] & 0xFFL) << 24) | ((q[2] & 0xFFL) << 16) | ((q[1] & 0xFFL) << 8) | (q[0] & 0xFFL);
    }

    public static boolean write(long addr, byte[] data) {
        if (unsafe == null) {
            return false;
        }
        try {
            for (int i = 0; i < data.length; i++) {
                long a = addr + i;
                if (putByteLB != null) {
                    putByteLB.invoke(unsafe, Long.valueOf(a), Byte.valueOf(data[i]));
                } else {
                    putByteOLB.invoke(unsafe, null, Long.valueOf(a), Byte.valueOf(data[i]));
                }
            }
            return true;
        } catch (Throwable t) {
            error = "W:" + t;
            return false;
        }
    }

    /** 是否有 copyMemory 批量读取能力（dump 扫描用；没有就退回逐字节读）。 */
    public static boolean hasBulkRead() {
        return unsafe != null && copyOOL != null;
    }

    /**
     * 批量读内存到 byte[]（src 为绝对地址）。返回实际读到的字节数，-1 表示失败。
     * 有 copyMemory 时一次 memcpy；否则退回逐字节 getByte。
     */
    public static int readInto(long addr, byte[] dst, int len) {
        return readInto(addr, dst, 0, len);
    }

    public static int readInto(long addr, byte[] dst, int dstOff, int len) {
        if (unsafe == null || dst == null || len <= 0 || dstOff < 0 || dstOff + len > dst.length) {
            return -1;
        }
        if (copyOOL != null) {
            try {
                copyOOL.invoke(unsafe, null, Long.valueOf(addr), dst,
                        Long.valueOf(byteArrayBase + dstOff), Long.valueOf(len));
                return len;
            } catch (Throwable t) {
                error = "copy:" + t;
            }
        }
        try {
            for (int i = 0; i < len; i++) {
                Object v = (getByteL != null)
                        ? getByteL.invoke(unsafe, Long.valueOf(addr + i))
                        : getByteOL.invoke(unsafe, null, Long.valueOf(addr + i));
                dst[dstOff + i] = ((Number) v).byteValue();
            }
            return len;
        } catch (Throwable t) {
            error = "R:" + t;
            return -1;
        }
    }
}
