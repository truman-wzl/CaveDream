package com.cavedream.core.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.anim.BoneId;
import com.cavedream.core.anim.Pose;
import com.cavedream.core.anim.Skeleton;
import com.cavedream.core.anim.Transform2;
import com.cavedream.core.anim.rig.DreamerRig;
import com.cavedream.core.anim.rig.SkinBinding;

/**
 * 骨骼蒙皮角色渲染器（软件蒙皮版）：每帧把 21×42 的 {@link PaintedLook} 像素按骨骼世界矩阵
 * （{@code world*invBind} 双骨线性混合）重画进一张较大的画布纹理，再走标准 {@link SpriteBatch} 绘制。
 * 用与方块/武器完全相同的渲染通道 → 无自定义 shader/Mesh 的 GL 状态风险，必定可见、可跟随。
 * 通用件：换 {@link SkinBinding}/像素来源即可为 NPC/怪物蒙皮。
 */
public final class SkeletalAvatar implements Disposable {

    private static final int CW = 52, CH = 76;   // 画布（比人形 32×64 大一圈，容纳肢体摆动）
    private int sprW = DreamerRig.W, sprH = DreamerRig.H;   // 当前皮肤的像素尺寸（随外观设定）

    private final Skeleton skeleton;
    private final Transform2[] invBind = new Transform2[BoneId.COUNT];
    private final Transform2[] skin = new Transform2[BoneId.COUNT];
    private final float[] tmp = new float[2];

    private int[] colors;
    private int[] px, py, pcol;
    private byte[] pba, pbb;
    private float[] pwa;
    private int nPix;

    private final Pixmap canvas;
    private final Texture tex;
    private final TextureRegion region;

    public SkeletalAvatar() {
        this.skeleton = DreamerRig.newSkeleton();
        skeleton.computeBindWorld();
        for (int i = 0; i < BoneId.COUNT; i++) {
            invBind[i] = skeleton.world(BoneId.values()[i]).inverted();
            skin[i] = new Transform2();
        }
        canvas = new Pixmap(CW, CH, Pixmap.Format.RGBA8888);
        canvas.setBlending(Pixmap.Blending.None);
        tex = new Texture(canvas);
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        region = new TextureRegion(tex);
    }

    /** 外观改变时重建像素（颜色取当前上色，绑定依模板 region）。 */
    public void setAppearance(PaintedLook look) {
        this.colors = look.colors;
        SkinBinding binding = DreamerRig.bind(look);
        this.sprW = binding.w;
        this.sprH = binding.h;
        int cap = binding.w * binding.h;
        px = new int[cap];
        py = new int[cap];
        pcol = new int[cap];
        pba = new byte[cap];
        pbb = new byte[cap];
        pwa = new float[cap];
        int n = 0;
        for (int y = 0; y < binding.h; y++) {
            for (int x = 0; x < binding.w; x++) {
                int i = y * binding.w + x;
                if (i >= colors.length || !binding.bound[i] || (colors[i] & 0xFFFFFF) == 0) {
                    continue;
                }
                px[n] = x;
                py[n] = y;
                pcol[n] = colors[i];
                pba[n] = binding.boneA[i];
                pbb[n] = binding.boneB[i];
                pwa[n] = binding.wA[i];
                n++;
            }
        }
        nPix = n;
    }

    /** 由姿态算各骨蒙皮矩阵 {@code world*invBind}。 */
    public void applyPose(Pose pose) {
        skeleton.computeWorld(pose);
        for (int i = 0; i < BoneId.COUNT; i++) {
            skin[i].set(skeleton.world(BoneId.values()[i]));
            skin[i].mul(invBind[i]);
        }
    }

    /**
     * 某骨枢点在世界的坐标与角度（与 {@link #draw} 同一映射），供武器 socket 对接。需先 {@link #applyPose}。
     * 角度为 batch-rot 约定（视觉顺时针、未镜像累计）：poseRot 正角经蒙皮 y-down→世界 y-up 翻转后
     * 恰为视觉顺时针；atan2(b,a) 是像素系 CCW，取负还原 CW。不含 tilt/facing——由调用端统一乘 facing 补偿镜像。
     * @return {worldX, worldY, batchRotDeg}
     */
    public float[] boneWorld(BoneId bone, float footX, float footY, float w, float h, int facing, float tiltDeg) {
        Transform2 m = skeleton.world(bone);
        float s = w / sprW;
        float lx = (m.e - sprW / 2f) * s * facing;
        float ly = -(m.f - sprH / 2f) * s;
        double r = Math.toRadians(tiltDeg);
        float wx = (float) (lx * Math.cos(r) - ly * Math.sin(r));
        float wy = (float) (lx * Math.sin(r) + ly * Math.cos(r));
        float ang = -(float) Math.toDegrees(Math.atan2(m.b, m.a));
        return new float[]{footX + w / 2f + wx, footY + h / 2f + wy, ang};
    }

    /**
     * 软件蒙皮重绘 + 用给定 SpriteBatch（须已设相机投影、处于 begin 状态）绘制。
     * @param footX,footY 角色左下角世界像素（含 bob）；w,h 目标世界尺寸；facing ±1；tiltDeg 倾角
     */
    public void draw(SpriteBatch batch, float footX, float footY, float w, float h, int facing, float tiltDeg) {
        if (nPix == 0) {
            return;
        }
        canvas.setColor(0, 0, 0, 0);
        canvas.fill();
        for (int p = 0; p < nPix; p++) {
            Transform2 ma = skin[pba[p] & 0xFF], mb = skin[pbb[p] & 0xFF];
            float wa = pwa[p], wb = 1f - wa;
            float minx = 1e9f, miny = 1e9f, maxx = -1e9f, maxy = -1e9f;
            for (int k = 0; k < 4; k++) {                 // 变形的四角→包围盒，逐格填充（无空洞、不闪烁）
                float sx = px[p] + ((k & 1) == 0 ? 0f : 1f);
                float sy = py[p] + ((k & 2) == 0 ? 0f : 1f);
                ma.apply(sx, sy, tmp);
                float qx = tmp[0], qy = tmp[1];
                if (wb > 0f) {
                    mb.apply(sx, sy, tmp);
                    qx = qx * wa + tmp[0] * wb;
                    qy = qy * wa + tmp[1] * wb;
                }
                float ux = qx - sprW / 2f + CW / 2f;
                float uy = qy - sprH / 2f + CH / 2f;
                if (ux < minx) minx = ux;
                if (ux > maxx) maxx = ux;
                if (uy < miny) miny = uy;
                if (uy > maxy) maxy = uy;
            }
            int c0 = Math.max(0, (int) Math.floor(minx)), c1 = Math.min(CW - 1, (int) Math.ceil(maxx));
            int r0 = Math.max(0, (int) Math.floor(miny)), r1 = Math.min(CH - 1, (int) Math.ceil(maxy));
            if (c0 > c1 || r0 > r1) {
                continue;
            }
            int c = pcol[p];
            canvas.setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
            for (int yy = r0; yy <= r1; yy++) {
                for (int xx = c0; xx <= c1; xx++) {
                    canvas.drawPixel(xx, yy);
                }
            }
        }
        tex.draw(canvas, 0, 0);

        float s = w / sprW;                      // 世界像素 / 精灵像素
        float cw = CW * s, ch = CH * s;
        float centerX = footX + w / 2f, centerY = footY + h / 2f;
        batch.setColor(1, 1, 1, 1);   // 关键：重置 batch 色，避免被先前绘制的木质面板等 tint 成棕色
        batch.draw(region, centerX - cw / 2f, centerY - ch / 2f, cw / 2f, ch / 2f, cw, ch, facing, 1f, tiltDeg);
    }

    @Override
    public void dispose() {
        canvas.dispose();
        tex.dispose();
    }
}
