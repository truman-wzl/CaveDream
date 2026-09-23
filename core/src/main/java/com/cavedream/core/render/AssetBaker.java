package com.cavedream.core.render;

import com.cavedream.core.world.BlockType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 离线烘焙器：把 {@link BlockTextures} 的程序化绘制结果导出为 PNG，作为 t_item_form 的种子图。
 * 纯 JDK（ARGB int[] + ImageIO），不依赖 libGDX 原生库，可直接用 Gradle 任务运行：
 *   gradlew :core:bakeItems
 * 每种材料输出一张 64×64 连续 sheet（fill 变体 0）；草块另出 lip(16×8)/blob(8×8)。
 * 客户端渲染时仍按世界坐标裁 16×16 子块，画面观感与程序生成一致，但像素源改为 DB。
 */
public final class AssetBaker {

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");   // 无显示环境下 ImageIO 可用
        File dir = new File(args.length > 0 ? args[0] : "server/src/main/resources/assets/items");
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("无法创建输出目录：" + dir.getAbsolutePath());
            System.exit(1);
        }
        int count = 0;
        for (BlockType b : BlockType.values()) {
            if (b == BlockType.AIR) {
                continue;
            }
            write(BlockTextures.bakeSheetArgb(b), BlockTextures.SHEET_SIZE, BlockTextures.SHEET_SIZE,
                    new File(dir, b.name() + "__fill__0.png"));
            count++;
        }
        write(BlockTextures.bakeLipArgb(), BlockTextures.LIP_W, BlockTextures.LIP_H,
                new File(dir, "GRASS__lip__0.png"));
        write(BlockTextures.bakeBlobArgb(), BlockTextures.BLOB_W, BlockTextures.BLOB_H,
                new File(dir, "GRASS__blob__0.png"));
        count += 2;
        System.out.println("已烘焙 " + count + " 张 PNG 到 " + dir.getAbsolutePath());
    }

    /** ARGB（0xAARRGGBB）→ PNG。y 轴方向：数组第 0 行为图片顶行。 */
    private static void write(int[] argb, int w, int h, File out) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        if (!ImageIO.write(img, "png", out)) {
            throw new IOException("无 PNG 写入门面");
        }
    }

    private AssetBaker() {
    }
}
