package com.cavedream.server.boot;

import com.cavedream.server.repo.ItemDao;
import com.cavedream.server.service.PngUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 启动灌库器：把打包进 classpath:assets/items/ 的程序生成 PNG 幂等写入 t_item_form。
 * 文件名约定 &lt;KEY&gt;__&lt;kind&gt;__&lt;variant&gt;.png（例：GRASS__fill__0.png、GRASS__lip__0.png）。
 * 仅在该形态尚无图片时写入，绝不覆盖已通过上传接口写入的真实美术。
 */
@Component
public class ItemSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ItemSeeder.class);

    private final ItemDao items;

    public ItemSeeder(ItemDao items) {
        this.items = items;
    }

    @Override
    public void run(ApplicationArguments args) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:assets/items/*.png");
        } catch (Exception e) {
            log.warn("物品图片种子扫描失败（跳过灌库）：{}", e.toString());
            return;
        }
        int seeded = 0, skipped = 0, bad = 0;
        for (Resource r : resources) {
            String name = r.getFilename();
            if (name == null) {
                continue;
            }
            String base = name.substring(0, name.length() - 4); // 去 .png
            String[] parts = base.split("__");
            if (parts.length != 3) {
                bad++;
                continue;
            }
            try {
                byte[] png = readAll(r);
                Integer itemId = items.itemIdByKey(parts[0]);
                if (itemId == null) {
                    log.warn("种子图 {} 对应物品键不存在，跳过", name);
                    bad++;
                    continue;
                }
                int variant = Integer.parseInt(parts[2]);
                int[] dims = PngUtil.dims(png);
                boolean wrote = items.seedImageIfAbsent(
                        itemId, parts[1], variant, png, PngUtil.sha256Hex(png), dims[0], dims[1]);
                if (wrote) {
                    seeded++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("种子图 {} 处理失败：{}", name, e.toString());
                bad++;
            }
        }
        log.info("物品图片灌库完成：新写入 {}，已存在跳过 {}，无效 {}", seeded, skipped, bad);
    }

    private static byte[] readAll(Resource r) throws Exception {
        try (InputStream in = r.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
