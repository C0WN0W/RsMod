package com.cownow.rsmod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

public class NativePatchMathTest {

    private static byte[] bytes(String field) throws Exception {
        Field f = NativePatcher.class.getDeclaredField(field);
        f.setAccessible(true);
        return (byte[]) f.get(null);
    }

    private static long leU32(byte[] b) {
        return ((b[3] & 0xFFL) << 24) | ((b[2] & 0xFFL) << 16) | ((b[1] & 0xFFL) << 8) | (b[0] & 0xFFL);
    }

    @Test
    public void retIsRetInstruction() throws Exception {
        assertEquals(0xD65F03C0L, leU32(bytes("RET_BYTES")));
    }

    @Test
    public void trueBodyIsMovW0OneThenRet() throws Exception {
        byte[] b = bytes("TRUE_BYTES");
        assertEquals(8, b.length);
        assertEquals(0x52800020L, leU32(new byte[]{b[0], b[1], b[2], b[3]})); // mov w0,#1
        assertEquals(0xD65F03C0L, leU32(new byte[]{b[4], b[5], b[6], b[7]})); // ret
    }

    @Test
    public void bigBodyIsMovzW0_9999ThenRet() throws Exception {
        byte[] b = bytes("BIG_BYTES");
        long movz = leU32(new byte[]{b[0], b[1], b[2], b[3]});
        assertEquals(0x5284E1E0L, movz);
        assertEquals(9999L, (movz >> 5) & 0xFFFFL); // imm16 == 9999
        assertEquals(0xD65F03C0L, leU32(new byte[]{b[4], b[5], b[6], b[7]}));
    }

    /** main2.1 的新 RVA 表：9 项、顺序与目标名对应、全部落在脚本的合理区间内。 */
    @Test
    public void newRvaTable() {
        assertEquals(9, NativePatcher.DEFAULT_RVAS.length);
        assertEquals(9, NativePatcher.TARGET_NAMES.length);

        int[] expected = {
                0x124c584, 0x15c6a3c, 0x15c6d0c,
                0xcc3bf4, 0xcc3a78, 0x124c2f0,
                0x124bc4c, 0x124bcd4, 0x15c6918
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals("rva#" + i, expected[i], NativePatcher.DEFAULT_RVAS[i]);
            // 脚本里的合理性区间：0x1000 < rva < 0x2a00000
            assertTrue("rva#" + i + " 越界", NativePatcher.DEFAULT_RVAS[i] > 0x1000
                    && NativePatcher.DEFAULT_RVAS[i] < 0x2a00000);
        }
    }

    @Test
    public void maskMatchesScriptEnabled() {
        boolean[] mask = Config.maskToArray(Config.DEFAULT_MASK);
        assertTrue(mask[0]);
        assertFalse(mask[1]);   // Set_Shield 默认关
        assertFalse(mask[2]);   // Set_Levitate 默认关
        assertTrue(mask[3]);
        assertTrue(mask[8]);
    }

    /** 探针必须用最后一个目标（RVA[8]），rva.txt 更新后自动跟随。 */
    @Test
    public void probeFollowsLastTarget() throws Exception {
        Method m = NativePatcher.class.getDeclaredMethod("probeRva", int[].class);
        m.setAccessible(true);
        int[] custom = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99aabb};
        assertEquals(0x99aabb, m.invoke(null, custom));
        assertEquals(NativePatcher.DEFAULT_RVAS[8], m.invoke(null, NativePatcher.DEFAULT_RVAS));
    }

    @Test
    public void rvaListParsing() {
        int[] parsed = Config.parseRvaList("0x124c584, 0x15c6a3c, 0x15c6d0c, 0xcc3bf4, 0xcc3a78,"
                + " 0x124c2f0, 0x124bc4c, 0x124bcd4, 0x15c6918");
        assertNotNull(parsed);
        assertEquals(0x15c6918, parsed[8]);

        int[] plain = Config.parseRvaList("124c584 15c6a3c 15c6d0c cc3bf4 cc3a78 124c2f0 124bc4c 124bcd4 15c6918");
        assertNotNull(plain);
        assertEquals(0x124c584, plain[0]);

        assertEquals(null, Config.parseRvaList("0x1,0x2"));
        assertEquals(null, Config.parseRvaList("zz,1,2,3,4,5,6,7,8"));
    }
}
