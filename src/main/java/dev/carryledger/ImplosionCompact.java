package dev.carryledger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collapses spammy "Your Implosion hit N enemies for X damage." lines into one
 * live-updating summary line (rewritten in place within a 1.5s window).
 */
public class ImplosionCompact implements ClientModInitializer {

    static final Pattern HIT = Pattern.compile("^Your Implosion hit (\\d+) enem(?:y|ies) for ([\\d.,]+) damage\\.?$");
    static final String S = "§";
    static final long WINDOW_MS = 1500L;

    public static boolean enabled = true;

    static int hits;
    static int enemies;
    static double total;
    static double max;
    static long last;
    static Component lastMsg;
    static String lastText;
    static boolean deleteBroken;

    @Override
    public void onInitializeClient() {
        load();
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !enabled || message == null) return true;
            String s = message.getString().replaceAll("§.", "").trim();
            Matcher m = HIT.matcher(s);
            if (!m.matches()) return true;
            double dmg = parse(m.group(2));
            hits++;
            enemies += Integer.parseInt(m.group(1));
            total += dmg;
            if (dmg > max) max = dmg;
            last = System.currentTimeMillis();
            return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (hits > 0 && System.currentTimeMillis() - last > WINDOW_MS) flush(mc);
        });
    }

    static void flush(Minecraft mc) {
        String extra = enemies != hits ? "§8 (§7" + enemies + " enemies§8)" : "";
        String text = "§4[§cApocalyp§6se§4] §dImplosion §7x§f" + hits + extra
                + "§8 • §7total §c" + fmt(total)
                + "§8 • §7max §c" + fmt(max)
                + "§8 • §7avg §c" + fmt(total / hits);
        hits = 0;
        enemies = 0;
        total = 0;
        max = 0;
        if (mc == null || mc.player == null) return;
        ChatComponent chat = mc.gui.hud.getChat();
        removeLast(chat);
        Component c = Component.literal(text);
        chat.addClientSystemMessage(c);
        lastMsg = c;
        lastText = c.getString();
    }

    /** Removes our previous summary line from the chat history (via reflection). */
    static void removeLast(ChatComponent chat) {
        if (lastMsg == null || deleteBroken || chat == null) return;
        try {
            boolean removed = false;
            for (Class<?> cls = chat.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (!List.class.isAssignableFrom(f.getType())) continue;
                    try {
                        f.setAccessible(true);
                        if (!(f.get(chat) instanceof List<?> list)) continue;
                        for (Iterator<?> it = list.iterator(); it.hasNext(); ) {
                            if (holds(it.next())) {
                                it.remove();
                                removed = true;
                                break;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (removed) refresh(chat);
        } catch (Throwable t) {
            deleteBroken = true;
        }
        lastMsg = null;
    }

    static boolean holds(Object line) {
        if (line == null) return false;
        if (line == lastMsg) return true;
        for (Class<?> cls = line.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(line);
                    if (v == lastMsg) return true;
                    if (v instanceof Component comp && lastText != null && lastText.equals(comp.getString())) return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    static void refresh(ChatComponent chat) {
        for (String name : new String[]{"refreshTrimmedMessages", "rescaleChat"}) {
            for (Class<?> cls = chat.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                try {
                    Method m = cls.getDeclaredMethod(name);
                    m.setAccessible(true);
                    m.invoke(chat);
                    return;
                } catch (NoSuchMethodException e) {
                    // try superclass
                } catch (Throwable t) {
                    return;
                }
            }
        }
        for (Class<?> cls = chat.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Method m : cls.getDeclaredMethods()) {
                String n = m.getName().toLowerCase(Locale.ROOT);
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
                if (!n.contains("refreshtrimmed") && !n.contains("rescale")) continue;
                try {
                    m.setAccessible(true);
                    m.invoke(chat);
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** "1,234,567" / "1.234,5" / "12,5" -> double. */
    static double parse(String s) {
        String n;
        if (s.indexOf('.') >= 0) {
            n = s.replace(",", "");
        } else {
            int i = s.lastIndexOf(',');
            if (i >= 0 && s.length() - i - 1 != 3) {
                n = s.substring(0, i).replace(",", "") + "." + s.substring(i + 1);
            } else {
                n = s.replace(",", "");
            }
        }
        try {
            return Double.parseDouble(n);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String fmt(double v) {
        if (v >= 1.0E9) return String.format(Locale.ROOT, "%.2fB", v / 1.0E9);
        if (v >= 1_000_000.0) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0);
        if (v >= 1000.0) return String.format(Locale.ROOT, "%.1fk", v / 1000.0);
        return String.format(Locale.ROOT, "%.0f", v);
    }

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("apocalypse-implosion.properties");
    }

    static void load() {
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            Properties pr = new Properties();
            try (InputStream in = Files.newInputStream(p)) {
                pr.load(in);
            }
            enabled = Boolean.parseBoolean(pr.getProperty("compactImplosion", "true"));
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Properties pr = new Properties();
            pr.setProperty("compactImplosion", String.valueOf(enabled));
            try (OutputStream out = Files.newOutputStream(file())) {
                pr.store(out, "Apocalypse: Implosion chat compactor");
            }
        } catch (Exception ignored) {
        }
    }
}
