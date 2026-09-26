package com.cavedream.core.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.cavedream.core.appearance.weapon.WeaponDef;
import com.cavedream.core.appearance.weapon.WeaponPart;
import com.cavedream.core.appearance.weapon.WeaponRaster;

import java.util.LinkedHashMap;
import java.util.Map;

/** 把 {@link WeaponDef} 配方渲染成 Pixmap/Texture：整体合成（图标/手持）与逐部件分层（供握持/形变）。 */
public final class WeaponRenderer {

    private WeaponRenderer() {
    }

    /** 所有部件合成一张（图标/当前手持用）。 */
    public static Pixmap composedPixmap(WeaponDef def) {
        return toPixmap(WeaponRaster.render(def), def.canvasW, def.canvasH);
    }

    /** 每部件各一层（key=part.name），供下一步握持挂点与刃/宝石独立动画。 */
    public static Map<String, Pixmap> layerPixmaps(WeaponDef def) {
        Map<String, Pixmap> m = new LinkedHashMap<>();
        for (WeaponPart p : def.parts) {
            m.put(p.name, toPixmap(WeaponRaster.renderPart(def, p), def.canvasW, def.canvasH));
        }
        return m;
    }

    public static Texture composedTexture(WeaponDef def) {
        return toTexture(composedPixmap(def));
    }

    private static Pixmap toPixmap(int[] argb, int w, int h) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        for (int i = 0; i < argb.length; i++) {
            int v = argb[i];
            int a = (v >>> 24) & 0xFF;
            if (a == 0) {
                continue;
            }
            pm.setColor(((v >> 16) & 0xFF) / 255f, ((v >> 8) & 0xFF) / 255f, (v & 0xFF) / 255f, a / 255f);
            pm.drawPixel(i % w, i / w);
        }
        return pm;
    }

    private static Texture toTexture(Pixmap pm) {
        Texture t = new Texture(pm);
        t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        pm.dispose();
        return t;
    }
}
