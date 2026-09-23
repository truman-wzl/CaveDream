package com.cavedream.core.world.gen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 离线地图俯瞰导出器（规划/审阅用）：给定 布局 + 种子 → 生成整层 → 导出带要素标记的全图 PNG。
 * 纯 JDK（{@link MapOverview} 出 int[]，ImageIO 写 PNG），不依赖 libGDX/GL，Gradle 任务直接跑：
 *   gradlew :core:exportMap                              // 默认 l1 布局、种子 20260922
 *   gradlew :core:exportMap --args "tiny 1234"           // 指定布局与种子
 *   gradlew :core:exportMap --args "l1 42 build/maps"    // 指定输出目录
 * 同一种子确定性复现同一张图——因此“存档”只需存 种子+布局，不必存整层格子。
 */
public final class MapExporter {

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        String layoutKey = args.length > 0 ? args[0] : "l1";
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20260922L;
        String outDir = args.length > 2 ? args[2] : "build/maps";

        LayerLayout layout = switch (layoutKey) {
            case "tiny" -> LayerLayout.tiny();
            case "l1" -> LayerLayout.l1();
            default -> throw new IllegalArgumentException("未知布局键（tiny|l1）：" + layoutKey);
        };

        long t0 = System.currentTimeMillis();
        GeneratedWorld gen = WorldGenerator.generate(layout, seed);
        int scale = Math.max(1, (int) Math.ceil(layout.width() / 1200.0));   // 输出宽上限 ~1200px
        MapOverview ov = MapOverview.render(gen.world(), gen, scale);

        BufferedImage img = new BufferedImage(ov.width(), ov.height(), BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, ov.width(), ov.height(), ov.argb(), 0, ov.width());

        File dir = new File(outDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建输出目录：" + dir.getAbsolutePath());
        }
        File out = new File(dir, "map_" + layoutKey + "_" + seed + ".png");
        if (!ImageIO.write(img, "png", out)) {
            throw new IOException("无 PNG 写入门面");
        }
        System.out.println("地图导出：" + out.getAbsolutePath()
                + "  源 " + layout.width() + "x" + layout.height()
                + "  图 " + ov.width() + "x" + ov.height() + "  scale=" + scale
                + "  用时 " + (System.currentTimeMillis() - t0) + "ms");
        System.out.println(MapOverview.legend());
    }

    private MapExporter() {
    }
}
