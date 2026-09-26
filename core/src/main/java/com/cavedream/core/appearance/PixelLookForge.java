package com.cavedream.core.appearance;

import com.cavedream.core.render.PaintedLook;

/**
 * 确定性像素锻造器：把 {@link LookSpec} 参数按公式渲染成人形的 ARGB 像素 + 部位遮罩（region 1..8），
 * 产出 {@link PaintedLook}（尺寸 {@link Humanoid#W}×{@link Humanoid#H}）→ 直接喂给骨骼蒙皮。
 * 纯函数（同 spec→同输出、无外部随机态），可单测。region 编码：0背景/边 1发 2肤 3眼 4腮 5袍 6臂 7裤 8鞋。
 */
public final class PixelLookForge {

    private static final int W = Humanoid.W, H = Humanoid.H;
    private static final int OUTLINE = 0x1A1426;

    /** 锻造默认长相（供旧档兜底 / 测试基准）。 */
    public static PaintedLook defaultLook() {
        return forge(LookSpec.DEFAULT);
    }

    public static PaintedLook forge(LookSpec s) {
        int[] colors = new int[W * H];
        byte[] regions = new byte[W * H];
        new Forge(s, colors, regions).draw();
        return PaintedLook.forHumanoid(0, W, H, colors, regions);
    }

    private static final class Forge {
        private final LookSpec s;
        private final int[] col;
        private final byte[] reg;
        private final int pal;
        private final int hair, skin, eye, robe, pants, shoe, blush;

        // 竖向分区（像素，y 向下）
        private final int cx, headCy, headRx, headRy, headBottom;
        private final int shoulderY, torsoTop, hemY, torsoBottom;
        private final int legTop, footTop, footBottom;

        Forge(LookSpec s, int[] colors, byte[] regions) {
            this.s = s;
            this.col = colors;
            this.reg = regions;
            this.pal = Math.floorMod(s.paletteIndex, LookDice.paletteCount());
            int[] p = LookDice.paletteAt(pal);
            this.hair = s.hairRgb != 0 ? s.hairRgb : p[0];
            this.skin = s.skinRgb != 0 ? s.skinRgb : p[1];
            this.eye = s.eyeRgb != 0 ? s.eyeRgb : p[5];
            this.robe = s.robeRgb != 0 ? s.robeRgb : p[2];
            this.pants = s.pantsRgb != 0 ? s.pantsRgb : p[3];
            this.shoe = s.shoeRgb != 0 ? s.shoeRgb : p[4];
            this.blush = 0xE89A9A;

            this.cx = W / 2;
            this.headCy = (int) Math.round(0.19 * H);
            this.headRy = (int) Math.round(0.125 * H * s.headScale);
            this.headRx = (int) Math.round(0.235 * W * s.headScale);
            this.headBottom = headCy + headRy;
            this.shoulderY = headBottom + 1;   // 肩线与头底平齐（无颈、肩不长在胸口）
            this.torsoTop = headBottom - 1;
            int torsoH = (int) Math.round(0.28 * H * s.torsoScale);
            this.hemY = Math.min((int) Math.round(0.74 * H), shoulderY + torsoH);
            this.torsoBottom = hemY;
            this.legTop = hemY - 2;   // 腿顶往袍下藏 2px，摆动时不露缝（消除“藕断丝连”）
            this.footTop = (int) Math.round(0.92 * H);
            this.footBottom = (int) Math.round((0.92 + 0.06 * s.legScale) * H);
        }

        void draw() {
            if (s.cape) {
                cape();
            }
            legs();
            feet();
            backArm();
            torso();
            frontArm();
            head();
            hair();
            face();
            if (s.ahoge) {
                ahoge();
            }
            outline();
            shade();
        }

        // —— Loomis 卡通简化侧脸：圆穹颅顶 + 近竖直额平面 + 小鼻凹 + 圆领不収尖 ——
        private static final float[] P_FY = {0f, .10f, .30f, .52f, .66f, .78f, .88f, .95f, 1f};
        private static final float[] P_FRONT = {0.48f, 0.56f, 0.61f, 0.65f, 0.72f, 0.70f, 0.58f, 0.36f, 0.16f};
        private static final float[] P_BACK_FY = {0f, .12f, .35f, .60f, .80f, 1f};
        private static final float[] P_BACK = {0.45f, 0.72f, 0.80f, 0.78f, 0.66f, 0.50f};

        private static float interp(float[] xs, float[] ys, float x) {
            if (x <= xs[0]) {
                return ys[0];
            }
            if (x >= xs[xs.length - 1]) {
                return ys[ys.length - 1];
            }
            for (int i = 1; i < xs.length; i++) {
                if (x <= xs[i]) {
                    float t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                    return ys[i - 1] + (ys[i] - ys[i - 1]) * t;
                }
            }
            return ys[ys.length - 1];
        }

        private int frontX(float fy) {
            return cx + (int) Math.round(headRx * interp(P_FY, P_FRONT, fy));
        }

        private int backX(float fy) {
            return cx - (int) Math.round(headRx * interp(P_BACK_FY, P_BACK, fy));
        }

        private void head() {
            int yTop = headCy - headRy, yBot = headCy + headRy;
            int span = Math.max(1, yBot - yTop);
            for (int y = yTop; y <= yBot; y++) {
                float fy = (y - yTop) / (float) span;
                int xF = frontX(fy), xB = backX(fy);
                fillRect(xB, y, xF - xB + 1, 1, skin, (byte) 2);
            }
        }

        /** 发帽（已由 hair() 内联实现）。 */
        private void hairCap() {
            // 已合并到 hair()
        }

        private void torso() {
            int halfTop = (int) Math.round(0.17 * W * s.shoulderScale);
            int halfBot = (int) Math.round(0.14 * W);
            for (int y = torsoTop; y <= torsoBottom; y++) {
                double t = torsoBottom > torsoTop ? (y - torsoTop) / (double) (torsoBottom - torsoTop) : 0;
                int half = (int) Math.round(halfTop + (halfBot - halfTop) * t);
                fillRect(cx - half, y, 2 * half + 1, 1, robe, (byte) 5);
            }
        }

        private void cape() {
            int top = torsoTop + 1;
            int bot = torsoBottom + (int) Math.round(0.04 * H);
            int halfTop = (int) Math.round(0.15 * W * s.shoulderScale);
            int halfBot = (int) Math.round(0.22 * W);
            for (int y = top; y <= bot; y++) {
                double t = bot > top ? (y - top) / (double) (bot - top) : 0;
                int half = (int) Math.round(halfTop + (halfBot - halfTop) * t);
                fillRect(cx - half, y, 2 * half + 1, 1, shadeCol(robe, 0.7), (byte) 5);
            }
        }

        /** 后臂（右侧、身后）：弯肘握拳，画在躯干之前→被身体遮挡，仅露一小截前臂+拳（x≥16→ARM_FB 链）。 */
        private void backArm() {
            int aw = Math.max(3, (int) Math.round(0.11 * W));
            int x0 = (int) Math.round(0.58 * W);
            int elbowY = shoulderY + (int) Math.round(0.12 * H);
            int foreEnd = (int) Math.round(0.76 * W);
            fillRect(x0, shoulderY, aw, elbowY - shoulderY + 1, shadeCol(robe, 0.70), (byte) 6);          // 上臂竖直
            fillRect(x0, elbowY, foreEnd - x0 + 1, aw, shadeCol(robe, 0.64), (byte) 6);                   // 前臂水平向前
            fillRect(foreEnd - 2, elbowY - 1, 3, aw + 1, shadeCol(skin, 0.72), (byte) 6);                 // 拳头
        }

        /** 前臂（左侧、主视）：弯肘握拳，前臂伸到接近中线（仍属 ARM_UB 链），画在躯干之后→完整可见。 */
        private void frontArm() {
            int aw = Math.max(3, (int) Math.round(0.11 * W));
            int x0 = (int) Math.round(0.30 * W);
            int elbowY = shoulderY + (int) Math.round(0.12 * H);
            int foreEnd = (int) Math.round(0.54 * W);
            fillRect(x0, shoulderY, aw, elbowY - shoulderY + 1, shadeCol(robe, 1.14), (byte) 6);          // 上臂竖直
            fillRect(x0, elbowY, foreEnd - x0 + 1, aw, shadeCol(robe, 1.06), (byte) 6);                   // 前臂水平向前（加长）
            fillRect(foreEnd - 2, elbowY - 1, 3, aw + 1, skin, (byte) 6);                                 // 拳头
        }

        private void legs() {
            int legW = Math.max(3, (int) Math.round(0.14 * W));
            int backCx = (int) Math.round(0.40 * W);
            int frontCx = (int) Math.round(0.58 * W);
            fillRect(backCx - legW / 2, legTop, legW, footTop - legTop + 1, shadeCol(pants, 0.82), (byte) 7);
            fillRect(frontCx - legW / 2, legTop, legW, footTop - legTop + 1, pants, (byte) 7);
        }

        private void feet() {
            int fb = Math.min(H - 1, footBottom);
            fillRect((int) Math.round(0.32 * W), footTop, (int) Math.round(0.16 * W), fb - footTop + 1, shoe, (byte) 8);
            fillRect((int) Math.round(0.50 * W), footTop, (int) Math.round(0.22 * W), fb - footTop + 1, shoe, (byte) 8);
        }

        /** 16 种发型：0光头 1寸头 2短发 3波波 4长发 5双马尾 6马尾 7丸子头 8道髻 9刺发 10卷发 11莫西干 12侧分 13蓬松 14波浪 15凌乱。 */
        private void hair() {
            int st = s.hairstyle;
            if (st < 0 || st >= 16) {
                st = 0;
            }
            if (st == 0) {
                return;                                   // 光头
            }
            int yTop = headCy - headRy, yBot = headCy + headRy, span = Math.max(1, yBot - yTop);
            int hc = hair;
            int eyeY = yTop + (int) Math.round(0.42 * span);   // 与 face() 一致
            float capFy = st == 1 ? 0.20f : Math.max(0.18f, (eyeY - 3 - yTop) / (float) span);   // 发际止于眉上 1 行，再上为发型层
            for (int y = yTop - 1; y <= yBot; y++) {       // 发帽（颅顶上方多 1 行→发型可超出额头）
                float fy = (y - yTop) / (float) span;
                if (fy > capFy) {
                    break;
                }
                int xB = backX(fy), xF = frontX(fy);
                if (st == 11) {                            // 莫西干：仅中央条
                    int w = Math.max(1, (int) Math.round(0.16 * headRx));
                    fillRect(cx - w, y, 2 * w + 1, 1, hc, (byte) 1);
                } else {
                    fillRect(xB, y, xF - xB + 1, 1, hc, (byte) 1);
                }
            }
            int backTo = backBottom(st, yBot);             // 后发幕
            for (int y = yTop; y <= Math.min(backTo, H - 1); y++) {
                float fy = Math.min(1f, (y - yTop) / (float) span);
                int xB = backX(fy);
                int w = (int) Math.round((st == 13 ? 0.9 : 0.5) * headRx);
                fillRect(xB, y, w, 1, hc, (byte) 1);
                if (st == 3 || st == 10) {                 // 波波/卷：前缘也垂下框脸
                    int sw = Math.max(2, (int) Math.round(0.30 * headRx));
                    fillRect(frontX(fy) - sw + 1, y, sw, 1, hc, (byte) 1);
                }
            }
            switch (st) {                                  // 造型点缀（只在头外/两侧）
                case 5: twintails(); break;
                case 6: ponytail(); break;
                case 7: bun(); break;
                case 8: knot(); break;
                case 9: spikes(yTop); break;
                case 12: sidePart(); break;
                case 14: wavy(yTop, span); break;
                case 15: messy(yTop); break;
                default: break;
            }
        }

        private int backBottom(int st, int yBot) {
            switch (st) {
                case 2: return yBot - Math.max(1, headRy / 3);
                case 3: return (int) Math.round(headCy + headRy * 1.5);
                case 4: case 14: return torsoBottom + 2;
                case 10: return (int) Math.round(headCy + headRy * 1.8);
                case 13: return (int) Math.round(headCy + headRy * 1.6);
                default: return yBot;
            }
        }

        private void twintails() {
            int tw = Math.max(2, (int) Math.round(0.14 * headRx));
            int y = headCy - headRy / 2, len = (int) Math.round(headRy * 2.0);
            fillRect(backX(0.25f) - tw, y, tw, len, hair, (byte) 1);
            fillRect(frontX(0.15f) + 1, y - headRy / 3, tw, len, hair, (byte) 1);
        }

        private void ponytail() {
            int tw = Math.max(2, (int) Math.round(0.18 * headRx));
            fillRect(backX(0.22f) - tw, headCy - headRy / 2, tw, (int) Math.round(headRy * 2.4), hair, (byte) 1);
        }

        private void bun() {
            int r = Math.max(2, (int) Math.round(0.30 * headRx));
            ellipse(cx, headCy - headRy - r + 2, r, r, hair, (byte) 1);
        }

        private void knot() {
            int r = Math.max(1, (int) Math.round(0.16 * headRx));
            ellipse(cx, headCy - headRy - r + 1, r, r, hair, (byte) 1);
        }

        private void spikes(int yTop) {
            int h = Math.max(2, headRy / 4), xL = backX(0.2f), xR = frontX(0.2f), n = 5;
            for (int k = 0; k < n; k++) {
                int x = xL + (int) Math.round((xR - xL) * (k + 0.5) / n);
                fillRect(x, yTop - h, 1, h, hair, (byte) 1);
            }
        }

        private void sidePart() {
            int fringe = Math.max(2, (int) Math.round(0.5 * headRy));
            fillRect(cx - headRx, headCy - headRy / 2, headRx, fringe, hair, (byte) 1);
        }

        private void wavy(int yTop, int span) {
            for (int y = yTop; y <= torsoBottom + 1; y += 3) {
                float fy = Math.min(1f, (y - yTop) / (float) span);
                fillRect(backX(fy) - 1, y, 2, 2, hair, (byte) 1);
            }
        }

        private void messy(int yTop) {
            int h = Math.max(1, headRy / 5), xL = backX(0.2f), xR = frontX(0.2f);
            for (int k = 0; k < 4; k++) {
                int x = xL + (int) Math.round((xR - xL) * k / 3.0);
                fillRect(x, yTop - h, 1, h, hair, (byte) 1);
            }
        }

        private void face() {
            int yTop = headCy - headRy;
            int span = Math.max(1, (headCy + headRy) - yTop);
            int eyeH = s.eyeStyle == 1 ? 1 : 2;   // 眼睛固定 ~4 格（2×2），细眼 2×1
            int eyeW = 2;
            float eyeFy = 0.42f;
            int eyeY = yTop + (int) Math.round(eyeFy * span);
            int ex = cx + 1;   // 眼睛紧贴中线右侧（侧脸：眼中位、鼻在前沿）
            drawEye(ex, eyeY, eyeW, eyeH);
            int browY = eyeY - Math.max(1, eyeH / 2) - 1;
            fillRect(ex, browY, eyeW, 1, shadeCol(hair, 1.15), (byte) 1);
            // 嘴：靠前的短横线
            float mouthFy = 0.80f;
            int mouthY = yTop + (int) Math.round(mouthFy * span);
            int mw = Math.max(3, eyeW + 1);
            fillRect(frontX(mouthFy) - mw - 1, mouthY, mw, 1, shadeCol(skin, 0.45), (byte) 2);
            // 鼻：嘴上方 2 行、靠前缘点缀 2 格
            int noseY = mouthY - 2;
            float noseFy = (noseY - yTop) / (float) span;
            fillRect(frontX(noseFy) - 2, noseY, 2, 1, shadeCol(skin, 0.6), (byte) 2);
        }

        /** 一只眼：近白眼白 + 偏外(朝右)深色瞳孔 → 高对比、看得清。 */
        private void drawEye(int x, int y, int w, int h) {
            fillRect(x, y, w, h, 0xF2F2F6, (byte) 3);
            int pw = Math.max(1, w - 1), ph = Math.max(1, h - 1);
            fillRect(x + (w - pw), y + (h - ph) / 2, pw, ph, eye, (byte) 3);
        }

        private void ahoge() {
            int top = headCy - headRy;
            int x = cx + (int) Math.round(0.05 * headRx);
            fillRect(x, top - Math.max(2, headRy / 5), Math.max(1, headRx / 12), Math.max(2, headRy / 5) + 1,
                    hair, (byte) 1);
        }

        private void outline() {
            int[] src = col.clone();
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int i = y * W + x;
                    if ((src[i] & 0xFFFFFF) != 0) {
                        continue;
                    }
                    if (neighborOpaque(src, x, y)) {
                        col[i] = 0xFF000000 | (OUTLINE & 0xFFFFFF);
                        reg[i] = 0;
                    }
                }
            }
        }

        private boolean neighborOpaque(int[] g, int x, int y) {
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] k : d) {
                int nx = x + k[0], ny = y + k[1];
                if (nx >= 0 && ny >= 0 && nx < W && ny < H && (g[ny * W + nx] & 0xFFFFFF) != 0) {
                    return true;
                }
            }
            return false;
        }

        private void shade() {
            for (int i = 0; i < col.length; i++) {
                if ((col[i] & 0xFFFFFF) == 0) {
                    continue;
                }
                int y = i / W;
                double f = 0.97 + 0.06 * (1 - y / (double) H);   // 极轻渐变，保颜色鲜亮
                col[i] = 0xFF000000 | (shadeCol(rgbOf(col[i]), f) & 0xFFFFFF);
            }
        }

        // ---- 工具 ----
        private void ellipse(int ccx, int ccy, int rx, int ry, int rgb, byte region) {
            if (rx <= 0 || ry <= 0) {
                return;
            }
            for (int y = ccy - ry; y <= ccy + ry; y++) {
                if (y < 0 || y >= H) {
                    continue;
                }
                for (int x = ccx - rx; x <= ccx + rx; x++) {
                    if (x < 0 || x >= W) {
                        continue;
                    }
                    double dx = (x - ccx) / (double) rx, dy = (y - ccy) / (double) ry;
                    if (dx * dx + dy * dy <= 1.0) {
                        set(x, y, rgb, region);
                    }
                }
            }
        }

        private void fillRect(int x0, int y0, int w, int h, int rgb, byte region) {
            for (int y = y0; y < y0 + h; y++) {
                if (y < 0 || y >= H) {
                    continue;
                }
                for (int x = x0; x < x0 + w; x++) {
                    if (x < 0 || x >= W) {
                        continue;
                    }
                    set(x, y, rgb, region);
                }
            }
        }

        private void set(int x, int y, int rgb, byte region) {
            col[y * W + x] = 0xFF000000 | (rgb & 0xFFFFFF);   // 直接用策展色（程序生成不走调色板吸附，保肤色多样）
            reg[y * W + x] = region;
        }

        private int shadeCol(int rgb, double f) {
            int r = (int) Math.min(255, Math.max(0, ((rgb >> 16) & 0xFF) * f));
            int g = (int) Math.min(255, Math.max(0, ((rgb >> 8) & 0xFF) * f));
            int b = (int) Math.min(255, Math.max(0, (rgb & 0xFF) * f));
            return r << 16 | g << 8 | b;
        }

        private static int rgbOf(int argb) {
            return argb & 0xFFFFFF;
        }
    }

    private PixelLookForge() {
    }
}
