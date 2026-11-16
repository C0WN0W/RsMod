package com.cownow.rsmod.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * /proc/self/maps 解析
 */
public final class MemoryMaps {

    public static final class Seg {
        public final long start;
        public final long end;
        public final String perm;
        public final long offset;
        public final String path;

        Seg(long start, long end, String perm, long offset, String path) {
            this.start = start;
            this.end = end;
            this.perm = perm;
            this.offset = offset;
            this.path = path;
        }

        public long size() {
            return end - start;
        }

        public boolean readable() {
            return perm != null && perm.contains("r");
        }

        public boolean contains(long addr) {
            return addr >= start && addr < end;
        }

        @Override
        public String toString() {
            return Long.toHexString(start) + "-" + Long.toHexString(end) + " " + perm
                    + " off=0x" + Long.toHexString(offset) + " " + path;
        }
    }

    private MemoryMaps() {
    }

    /** 读取 maps 中包含 pathFilter 的全部段（null = 全部）。 */
    public static List<Seg> segments(String pathFilter) {
        List<Seg> out = new ArrayList<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/self/maps"));
            String line;
            while ((line = br.readLine()) != null) {
                if (pathFilter != null && !line.contains(pathFilter)) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }
                String[] se = parts[0].split("-");
                if (se.length != 2) {
                    continue;
                }
                long s;
                long e;
                long off = 0L;
                try {
                    s = Long.parseLong(se[0], 16);
                    e = Long.parseLong(se[1], 16);
                    if (parts.length > 2) {
                        off = Long.parseLong(parts[2], 16);
                    }
                } catch (Throwable t) {
                    continue;
                }
                String path = parts.length > 5 ? parts[5] : "";
                out.add(new Seg(s, e, parts[1], off, path));
            }
        } catch (Throwable ignored) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    /** 最大 rwx（同时可写可执行）段起点；0 表示没找到。 */
    public static long decBase(String soName) {
        long bestBase = 0L;
        long bestSize = 0L;
        for (Seg s : segments(soName)) {
            boolean x = s.perm.contains("x");
            boolean w = s.perm.contains("w");
            if (x && w && s.size() > bestSize) {
                bestSize = s.size();
                bestBase = s.start;
            }
        }
        return bestBase;
    }

    /** 是否存在可写可执行段（写内存前的前置检查）。 */
    public static boolean hasWritableExec(String soName) {
        return decBase(soName) != 0L;
    }

    public static String describe(String soName) {
        StringBuilder sb = new StringBuilder();
        for (Seg s : segments(soName)) {
            sb.append("\n  ").append(s);
        }
        return sb.length() == 0 ? " (no " + soName + " segment)" : sb.toString();
    }
}
