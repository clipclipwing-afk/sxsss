package dev.carryledger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayDeque;
import java.util.Locale;

public class CarryLedger implements ClientModInitializer {

    public static final String PREFIX = "§4[§cApocalyp§6se§4] §c";

    public static Data data = new Data();
    static boolean openHudEditor;
    private static boolean tickErrorShown;

    /** Party messages waiting to be sent; Hypixel drops chat sent too quickly. */
    private static final ArrayDeque<String> partyQueue = new ArrayDeque<>();
    private static long lastPartySentAt;

    @Override
    public void onInitializeClient() {
        data = Data.load();
        ClientTickEvents.END_CLIENT_TICK.register(CarryLedger::tick);
        ClientCommandRegistrationCallback.EVENT.register(Commands::register);
        log("Apocalypse loaded, " + data.clients.size() + " active clients");
    }

    private static void tick(Minecraft mc) {
        try {
            if (openHudEditor) {
                openHudEditor = false;
                mc.gui.setScreen(new ClarityHudEditScreen());
            }
            BossTracker.tick(mc);
            flushParty(mc);
        } catch (Throwable t) {
            if (!tickErrorShown) {
                tickErrorShown = true;
                log("tick error: " + t);
                t.printStackTrace();
                msg("§cTracker error: " + t + " (see log for details)");
            }
        }
    }

    public static Data.Client findClient(String name) {
        for (Data.Client c : data.clients) {
            if (c.name.equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    static void onSpawn(BossTracker.Tracked t) {
        Data.Client c = findClient(t.owner);
        if (c == null) return;
        BossType type = t.type != null ? t.type : c.type();
        String boss = type != null ? type.display + " " + BossType.roman(t.tier > 0 ? t.tier : c.tier) : "boss";
        msg("§c⚔ §c" + c.name + "§c spawned §c" + boss + " §4(" + (c.done + 1) + "/" + c.total + ")");
        if (data.config.spawnTitle) {
            title("§c§l" + boss.toUpperCase(Locale.ROOT), "§cfor §c" + c.name + " §4• §c" + (c.done + 1) + "/" + c.total, 2, 22, 6);
        }
        if (data.config.spawnSound) sound(SoundEvents.ARROW_HIT_PLAYER, 1f, 0.8f);

        BossType ordered = c.type();
        if (t.type != null && ordered != null && t.type != ordered) {
            msg("§c⚠ §c" + c.name + "§c spawned §c" + t.type.display + "§c, but the order is §c" + ordered.display + "§c. It won't be counted.");
        } else if (t.tier > 0 && t.tier < c.tier && data.config.strictTier) {
            msg("§c⚠ Tier §c" + BossType.roman(t.tier) + "§c is lower than ordered §c" + BossType.roman(c.tier) + "§c. It won't be counted (/carry toggle strict).");
        }
    }

    static void onKill(BossTracker.Tracked t, String reason) {
        Data.Client c = findClient(t.owner);
        if (c == null) return;
        BossType ordered = c.type();
        if (t.type != null && ordered != null && t.type != ordered) {
            msg("§c✘ Killed " + t.type.display + " for §c" + c.name + "§c — wrong boss, not counted.");
            return;
        }
        if (t.tier > 0 && t.tier < c.tier && data.config.strictTier) {
            msg("§c✘ Killed tier " + BossType.roman(t.tier) + " for §c" + c.name + "§c — below the order, not counted.");
            return;
        }
        long now = System.currentTimeMillis();
        long took = now - t.firstSeen;
        addKills(c, 1, now);
        String how = reason.equals("death") ? "" : "§4[" + reason + "]";
        msg("§c✔ §c" + c.name + " §c" + c.done + "§c/§c" + c.total + " §4(" + fmtDur(took) + ")" + how);
        if (data.config.killSound) sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        if (data.config.killTitle) title("§c§l✔ " + c.done + "/" + c.total, "§c" + c.name, 2, 16, 6);
        if (data.config.announceParty) queueParty(formatParty(data.config.partyMessage, c, t, took));
        checkFinished(c);
        data.save();
    }

    /** Fills the party message template. Placeholders: {name} {done} {total} {left} {boss} {tier} {time}. */
    static String formatParty(String template, Data.Client c, BossTracker.Tracked t, long tookMs) {
        if (template == null || template.isBlank()) template = Data.DEFAULT_PARTY_MESSAGE;
        BossType type = t != null && t.type != null ? t.type : c.type();
        int tier = t != null && t.tier > 0 ? t.tier : c.tier;
        String out = template
                .replace("{name}", c.name)
                .replace("{done}", String.valueOf(c.done))
                .replace("{total}", String.valueOf(c.total))
                .replace("{left}", String.valueOf(Math.max(0, c.total - c.done)))
                .replace("{boss}", type != null ? type.display : String.valueOf(c.boss))
                .replace("{tier}", BossType.roman(tier))
                .replace("{time}", fmtDur(tookMs));
        // The server kicks for § and control characters and rejects messages over 256 chars.
        out = out.replaceAll("[§\\p{Cntrl}]", "").trim();
        return out.length() > 250 ? out.substring(0, 250) : out;
    }

    static void queueParty(String message) {
        if (message == null || message.isEmpty()) return;
        if (partyQueue.size() >= 20) partyQueue.pollFirst();
        partyQueue.addLast(message);
    }

    private static void flushParty(Minecraft mc) {
        if (partyQueue.isEmpty()) return;
        if (mc.getConnection() == null) {
            partyQueue.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPartySentAt < data.config.partyDelayMs) return;
        lastPartySentAt = now;
        command("pc " + partyQueue.pollFirst());
    }

    static void onLost(BossTracker.Tracked t, String why) {
        Data.Client c = findClient(t.owner);
        if (c == null) return;
        msg("§c? §cBoss for §c" + c.name + "§c not counted: §c" + why + "§c. If it died — §c/carry inc " + c.name);
    }

    static void addKills(Data.Client c, int n, long when) {
        for (int i = 0; i < n; i++) {
            c.done++;
            c.kills.add(when);
            data.stats.bosses++;
        }
        c.lastKillAt = when;
        while (c.kills.size() > 200) c.kills.remove(0);
    }

    static void checkFinished(Data.Client c) {
        if (c.done < c.total) return;
        BossType type = c.type();
        double price = data.price(type, c.tier) * c.total;

        Data.HistoryEntry h = new Data.HistoryEntry();
        h.name = c.name;
        h.boss = c.boss;
        h.tier = c.tier;
        h.amount = c.total;
        h.priceM = price;
        h.startedAt = c.startedAt;
        h.finishedAt = System.currentTimeMillis();
        data.history.add(h);
        while (data.history.size() > 300) data.history.remove(0);
        data.stats.carries++;
        data.stats.coinsM += price;
        data.clients.remove(c);

        String summary = c.name + ": " + c.total + "x " + (type != null ? type.display : c.boss) + " "
                + BossType.roman(c.tier) + (price > 0 ? "— " + fmtCoins(price) : "");
        msg("§c§l★ Carry finished! §r§c" + summary + " §4(" + fmtDur(h.finishedAt - c.startedAt) + ")");
        title("§c§l★ DONE", "§c" + c.name + " §c— " + c.total + "/" + c.total, 4, 40, 10);
        sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
        if (data.config.copyOnFinish) {
            try {
                ClarityHud.clip(Minecraft.getInstance().keyboardHandler, summary);
                msg("§c(summary copied to clipboard)");
            } catch (Throwable ignored) {
            }
        }
    }

    public static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.hud == null || mc.player == null) {
            log(s);
            return;
        }
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal(PREFIX + s));
    }

    static void debug(String s) {
        msg("§4[debug] " + s);
    }

    static void title(String title, String subtitle, int in, int stay, int out) {
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.hud.setTimes(in, stay, out);
            mc.gui.hud.setSubtitle(Component.literal(subtitle));
            mc.gui.hud.setTitle(Component.literal(title));
        } catch (Throwable ignored) {
        }
    }

    static void sound(SoundEvent ev, float volume, float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.getSoundManager().play(new SimpleSoundInstance(ev, SoundSource.MASTER, volume, pitch,
                    SoundInstance.createUnseededRandom(), mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        } catch (Throwable ignored) {
        }
    }

    static void command(String cmd) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
        } catch (Throwable ignored) {
        }
    }

    public static String fmtDur(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        if (s < 60) return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
        if (s < 3600) return s / 60 + "m " + s % 60 + "s";
        return s / 3600 + "h " + s % 3600 / 60 + "m";
    }

    /** Millions -> "750k" / "12M" / "12.5M" / "1.20B". */
    public static String fmtCoins(double m) {
        if (m >= 1000) return String.format(Locale.ROOT, "%.2fB", m / 1000.0);
        if (m >= 1) return (m == Math.rint(m) ? String.valueOf((long) m) : String.format(Locale.ROOT, "%.1f", m)) + "M";
        return String.format(Locale.ROOT, "%.0fk", m * 1000.0);
    }

    /** Average time per kill over the last 6 kills, or -1. */
    public static long avgKillMs(Data.Client c) {
        int n = c.kills.size();
        if (n >= 2) {
            int from = Math.max(0, n - 6);
            return (c.kills.get(n - 1) - c.kills.get(from)) / Math.max(1, n - 1 - from);
        }
        if (n == 1 && c.startedAt > 0 && c.kills.get(0) > c.startedAt) return c.kills.get(0) - c.startedAt;
        return -1;
    }

    static void log(String s) {
        System.out.println("[Apocalypse] " + s);
    }
}
