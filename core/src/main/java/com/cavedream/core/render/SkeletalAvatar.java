package com.cavedream.core.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.anim.BoneId;
import com.cavedream.core.anim.Pose;
import com.cavedream.core.anim.Skeleton;
import com.cavedream.core.anim.Transform2;
import com.cavedream.core.anim.rig.DreamerRig;
import com.cavedream.core.anim.rig.SkinBinding;

/**
 * 骨骼蒙皮角色渲染器：CPU 端线性混合蒙皮（每不透明像素一个四边形 4 角，按 {@code world*invBind} 变形、双骨权重混合），
 * 产出 {@link Mesh}(position2+color4)，用自带极简 shader 在相机投影下绘制。与 SpriteBatch 独立（须在批处理外调用 {@link #draw}）。
 * 通用件：换 {@link SkinBinding}/像素来源即可为物品/怪物蒙皮（"万物皆可骨骼化"地基）。
 */
public final class SkeletalAvatar implements Disposable {

    private static final String VS =
            "attribute vec3 a_position; attribute vec4 a_color; uniform mat4 u_proj;\n"
                    + "varying vec4 v_color;\n"
                    + "void main(){ v_color=a_color; gl_Position = u_proj * vec4(a_position, 1.0); }";
    private static final String FS =
            "varying vec4 v_color;\n void main(){ gl_FragColor = v_color; }";

    private static final int FLOATS_PER_VERT = 7;   // x,y,z + r,g,b,a

    private final Skeleton skeleton;
    private final Transform2[] invBind = new Transform2[BoneId.COUNT];
    private final Transform2[] skin = new Transform2[BoneId.COUNT];
    private final float[] tmp = new float[2];

    private SkinBinding binding;
    private int[] colors;
    private int[] px, py, pcol;
    private byte[] pba, pbb;
    private float[] pwa;
    private int nPix;

    private Mesh mesh;
    private final ShaderProgram shader;
    private float[] verts;

    // draw 期缓存，供 corner() 读
    private float scaleX, scaleY, cx, cy, cosR, sinR;
    private int facingNow;
    private float AX, AY;

    public SkeletalAvatar() {
        this.skeleton = DreamerRig.newSkeleton();
        skeleton.computeBindWorld();
        for (int i = 0; i < BoneId.COUNT; i++) {
            invBind[i] = skeleton.world(BoneId.values()[i]).inverted();
            skin[i] = new Transform2();
        }
        shader = new ShaderProgram(VS, FS);
        if (!shader.isCompiled()) {
            Gdx.app.error("SkeletalAvatar", shader.getLog());
        }
    }

    /** 外观改变时重建像素与蒙皮（绑定依模板 region；颜色取当前上色）。 */
    public void setAppearance(PaintedLook look) {
        this.colors = look.colors;
        this.binding = DreamerRig.bind(look);
        int cap = DreamerRig.W * DreamerRig.H;
        px = new int[cap];
        py = new int[cap];
        pcol = new int[cap];
        pba = new byte[cap];
        pbb = new byte[cap];
        pwa = new float[cap];
        int n = 0;
        for (int y = 0; y < DreamerRig.H; y++) {
            for (int x = 0; x < DreamerRig.W; x++) {
                int i = y * DreamerRig.W + x;
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
        if (mesh != null) {
            mesh.dispose();
        }
        verts = new float[Math.max(6, nPix) * 6 * FLOATS_PER_VERT];
        mesh = new Mesh(true, nPix * 6, 0,
                VertexAttribute.Position(),
                VertexAttribute.ColorUnpacked());
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
     * 在世界批处理之外绘制蒙皮角色。
     * @param footX,footY 角色左下角世界像素（含 bob）；w,h 目标世界尺寸；facing ±1；tiltDeg 倾角
     */
    public void draw(OrthographicCamera cam, float footX, float footY, float w, float h, int facing, float tiltDeg) {
        if (mesh == null || nPix == 0 || !shader.isCompiled()) {
            return;
        }
        scaleX = w / DreamerRig.W;
        scaleY = h / DreamerRig.H;
        cx = footX + w / 2f;
        cy = footY + h / 2f;
        facingNow = facing;
        double r = Math.toRadians(tiltDeg);
        cosR = (float) Math.cos(r);
        sinR = (float) Math.sin(r);

        int v = 0;
        for (int p = 0; p < nPix; p++) {
            int x = px[p], y = py[p];
            int c = pcol[p];
            float cr = ((c >> 16) & 0xFF) / 255f, cg = ((c >> 8) & 0xFF) / 255f, cb = (c & 0xFF) / 255f;
            float wa = pwa[p], wb = 1f - wa;
            Transform2 ma = skin[pba[p] & 0xFF], mb = skin[pbb[p] & 0xFF];
            corner(x, y, ma, mb, wa, wb);
            float c0x = AX, c0y = AY;
            corner(x + 1f, y, ma, mb, wa, wb);
            float c1x = AX, c1y = AY;
            corner(x + 1f, y + 1f, ma, mb, wa, wb);
            float c2x = AX, c2y = AY;
            corner(x, y + 1f, ma, mb, wa, wb);
            float c3x = AX, c3y = AY;
            v = emit(v, c0x, c0y, cr, cg, cb);
            v = emit(v, c1x, c1y, cr, cg, cb);
            v = emit(v, c2x, c2y, cr, cg, cb);
            v = emit(v, c0x, c0y, cr, cg, cb);
            v = emit(v, c2x, c2y, cr, cg, cb);
            v = emit(v, c3x, c3y, cr, cg, cb);
        }
        mesh.setVertices(verts, 0, v);
        shader.bind();
        shader.setUniformMatrix("u_proj", cam.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        mesh.bind(shader);
        Gdx.gl.glDrawArrays(GL20.GL_TRIANGLES, 0, v / FLOATS_PER_VERT);
    }

    /** 一个像素角：像素(y-down)经双骨蒙皮变形后映射到世界（居中/镜像/旋转/平移）。结果写入 AX,AY。 */
    private void corner(float dx, float dy, Transform2 ma, Transform2 mb, float wa, float wb) {
        ma.apply(dx, dy, tmp);
        float qx = tmp[0], qy = tmp[1];
        if (wb > 0f) {
            mb.apply(dx, dy, tmp);
            qx = qx * wa + tmp[0] * wb;
            qy = qy * wa + tmp[1] * wb;
        }
        float u = (qx - DreamerRig.W / 2f) * scaleX * facingNow;
        float vUp = (DreamerRig.H / 2f - qy) * scaleY;
        AX = cx + (u * cosR - vUp * sinR);
        AY = cy + (u * sinR + vUp * cosR);
    }

    private int emit(int v, float x, float y, float r, float g, float b) {
        verts[v] = x;
        verts[v + 1] = y;
        verts[v + 2] = 0f;
        verts[v + 3] = r;
        verts[v + 4] = g;
        verts[v + 5] = b;
        verts[v + 6] = 1f;
        return v + FLOATS_PER_VERT;
    }

    @Override
    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
        }
        shader.dispose();
    }
}
