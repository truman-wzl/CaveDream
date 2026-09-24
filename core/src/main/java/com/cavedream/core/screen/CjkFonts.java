package com.cavedream.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

/**
 * 中文字体工厂：用 FreeType 加载 Windows 系统字体（微软雅黑/黑体），运行时生成位图字体。
 * 字符集采用构建期生成的 GB2312 符号区+一级汉字全集（{@link CjkChars}），
 * 不依赖运行时 charset 模块，常用中文永不缺字。
 */
public final class CjkFonts {

    private static final String[] CANDIDATES = {
            "C:/Windows/Fonts/msyh.ttc",   // 微软雅黑（Win10/11）
            "C:/Windows/Fonts/msyh.ttf",
            "C:/Windows/Fonts/simhei.ttf", // 黑体
            "C:/Windows/Fonts/simsun.ttc", // 宋体
    };

    private CjkFonts() {
    }

    /** 全字库字体按字号缓存、跨屏复用（避免每次切屏重建几千glyph导致卡顿）。 */
    private static final java.util.HashMap<Integer, BitmapFont> CACHE = new java.util.HashMap<>();

    /** 取（并缓存）含全字库的中文字体；调用方不要 dispose 返回值（共享）。 */
    public static BitmapFont get(int sizePx) {
        return CACHE.computeIfAbsent(sizePx, CjkFonts::create);
    }

    /** 退出时统一释放缓存字体。 */
    public static void disposeAll() {
        for (BitmapFont f : CACHE.values()) {
            f.dispose();
        }
        CACHE.clear();
    }

    /** 生成含 GB2312 一级字库的中文字体（菜单/正文/UI 用）。 */
    public static BitmapFont create(int sizePx) {
        return create(sizePx, null);
    }

    /**
     * 生成字体。extraOnlyChars 非空时＝只用 ASCII+该小集合（大字标题专用，省显存）；
     * 为 null 时＝ASCII+GB2312 符号区与一级汉字全集。
     */
    public static BitmapFont create(int sizePx, String extraOnlyChars) {
        String chars = FreeTypeFontGenerator.DEFAULT_CHARS
                + (extraOnlyChars != null ? extraOnlyChars : CjkChars.GB2312_L1);
        for (String path : CANDIDATES) {
            FileHandle faceFile = Gdx.files.absolute(path);
            if (!faceFile.exists()) {
                continue;
            }
            FreeTypeFontGenerator generator = null;
            try {
                generator = new FreeTypeFontGenerator(faceFile);
                FreeTypeFontParameter param = new FreeTypeFontParameter();
                param.size = sizePx;
                param.characters = chars;
                param.minFilter = TextureFilter.Nearest;
                param.magFilter = TextureFilter.Nearest;
                BitmapFont font = generator.generateFont(param);
                Gdx.app.log("CjkFonts", "中文字体加载: " + path + " @" + sizePx
                        + " glyphs=" + chars.length());
                return font;
            } catch (Exception e) {
                Gdx.app.error("CjkFonts", "字体生成失败 " + path, e);
            } finally {
                if (generator != null) {
                    generator.dispose();
                }
            }
        }
        Gdx.app.log("CjkFonts", "未找到系统中文字体，回退默认英文字体");
        return new BitmapFont();
    }
}
