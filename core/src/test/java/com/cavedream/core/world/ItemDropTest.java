package com.cavedream.core.world;

import com.cavedream.core.item.Item;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 掉落物物理测试：受重力下落、停在实心面上、不穿地。 */
class ItemDropTest {

    @Test
    void fallsAndRestsOnGround() {
        LayerWorld w = new LayerWorld(20, 20);
        for (int x = 0; x < 20; x++) {
            w.setBlock(x, 5, BlockType.STONE);   // 地面：y=5 实心，其上 y=6 起为空
        }
        ItemDrop d = new ItemDrop(3 * 16 + 4, 12 * 16, Item.ofBlock(BlockType.DIRT), 1);
        for (int i = 0; i < 120; i++) {
            d.update(w, 1 / 60f);
        }
        // 应落在地面之上（y≈6*16），且不再下落
        assertThat(d.y()).isGreaterThanOrEqualTo(6 * 16 - 1f);
        assertThat(d.y()).isLessThan(7 * 16);
        assertThat(d.centerY()).isGreaterThan(3 * 16);   // 没有穿地掉到底下
    }

    @Test
    void overlapsRectDetectsPlayer() {
        ItemDrop d = new ItemDrop(100, 100, Item.ofBlock(BlockType.STONE), 3);
        assertThat(d.overlapsRect(96, 96, 20, 20)).isTrue();
        assertThat(d.overlapsRect(200, 200, 20, 20)).isFalse();
        assertThat(d.count()).isEqualTo(3);
    }
}
