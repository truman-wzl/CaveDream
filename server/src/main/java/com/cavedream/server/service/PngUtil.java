package com.cavedream.server.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** PNG 辅助：内容哈希（SHA-256 十六进制）与宽高解析（读 IHDR，无需 ImageIO）。 */
public final class PngUtil {

    private PngUtil() {
    }

    /** 计算字节内容的 SHA-256 十六进制小写（64 字符）。 */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }

    /** 校验 PNG 魔数。 */
    public static boolean isPng(byte[] bytes) {
        return bytes != null && bytes.length > 24
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A
                && bytes[6] == 0x1A && bytes[7] == 0x0A;
    }

    /** 从 IHDR 读宽高（大端 4 字节，偏移 16/20）。非 PNG 返回 null。 */
    public static int[] dims(byte[] bytes) {
        if (!isPng(bytes)) {
            return null;
        }
        int w = (bytes[16] & 0xFF) << 24 | (bytes[17] & 0xFF) << 16
                | (bytes[18] & 0xFF) << 8 | (bytes[19] & 0xFF);
        int h = (bytes[20] & 0xFF) << 24 | (bytes[21] & 0xFF) << 16
                | (bytes[22] & 0xFF) << 8 | (bytes[23] & 0xFF);
        return new int[]{w, h};
    }
}
