package dev.carryledger;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HUD wrapper: applies the user HUD scale (config/apocalypse-hud-scale.txt, 30..300 %).
 * The pose stack is reached by reflection so both the 2D (Matrix3x2f: pushMatrix/translate(x,y))
 * and the legacy 3D (PoseStack: pushPose/translate(x,y,z)) APIs work.
 */
public final class ClarityHud {

    public static int scale = load();

    private static Method mPose, mPush, mPop, mTranslate, mScale;
    private static boolean failed;
    private static boolean is3d;

    private ClarityHud() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("apocalypse-hud-scale.txt");
    }

    private static int load() {
        try {
            return clamp(Integer.parseInt(Files.readString(file()).trim()));
        } catch (Throwable t) {
            return 100;
        }
    }

    public static void save() {
        try {
            Files.writeString(file(), Integer.toString(scale));
        } catch (Throwable ignored) {
        }
    }

    public static int clamp(int v) {
        return Math.max(30, Math.min(300, v));
    }

    public static float factor() {
        return scale / 100.0f;
    }

    static String status(Data.Client c, boolean active) {
        return "";
    }

    public static void render(GuiGraphicsExtractor g) {
        Data.Config cfg = CarryLedger.data.config;
        Object pose = push(g, cfg.hudX, cfg.hudY, factor());
        try {
            CarryHud.render(g);
        } finally {
            pop(pose);
        }
    }

    private static boolean resolve(Object g) {
        if (failed) return false;
        if (mPose != null) return true;
        try {
            Method pose = g.getClass().getMethod("pose");
            Object stack = pose.invoke(g);
            Class<?> c = stack.getClass();
            try {
                mPush = c.getMethod("pushMatrix");
                mPop = c.getMethod("popMatrix");
                mTranslate = c.getMethod("translate", float.class, float.class);
                mScale = c.getMethod("scale", float.class, float.class);
                is3d = false;
            } catch (NoSuchMethodException e) {
                mPush = c.getMethod("pushPose");
                mPop = c.getMethod("popPose");
                mTranslate = c.getMethod("translate", float.class, float.class, float.class);
                mScale = c.getMethod("scale", float.class, float.class, float.class);
                is3d = true;
            }
            mPose = pose;
            return true;
        } catch (Throwable t) {
            failed = true;
            CarryLedger.log("HUD scale unavailable: " + t);
            return false;
        }
    }

    /** Scales around (x, y). Returns the pushed pose stack, or null if nothing was pushed. */
    public static Object push(Object g, int x, int y, float f) {
        if (Math.abs(f - 1f) < 0.001f || !resolve(g)) return null;
        Object stack = null;
        try {
            stack = mPose.invoke(g);
            mPush.invoke(stack);
            try {
                if (is3d) {
                    mTranslate.invoke(stack, (float) x, (float) y, 0f);
                    mScale.invoke(stack, f, f, 1f);
                    mTranslate.invoke(stack, (float) -x, (float) -y, 0f);
                } else {
                    mTranslate.invoke(stack, (float) x, (float) y);
                    mScale.invoke(stack, f, f);
                    mTranslate.invoke(stack, (float) -x, (float) -y);
                }
            } catch (Throwable t) {
                mPop.invoke(stack);
                failed = true;
                return null;
            }
            return stack;
        } catch (Throwable t) {
            failed = true;
            return null;
        }
    }

    public static void pop(Object stack) {
        if (stack == null) return;
        try {
            mPop.invoke(stack);
        } catch (Throwable ignored) {
        }
    }

    /** Copies text without colour codes. */
    public static void clip(KeyboardHandler kb, String s) {
        kb.setClipboard(s == null ? null : s.replaceAll("§.", ""));
    }
}
