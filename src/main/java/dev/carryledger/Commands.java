package dev.carryledger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.CommandBuildContext;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** /carry (alias /cl) client commands. */
final class Commands {

    private static final String[] TOGGLES = {
            "hud", "lenient", "strict", "party", "spawnsound",
            "killsound", "spawntitle", "killtitle", "clipboard", "debug"
    };

    private static final SuggestionProvider<FabricClientCommandSource> CLIENTS = (ctx, b) -> {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (Data.Client c : CarryLedger.data.clients) {
            if (c.name.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(c.name);
        }
        return b.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> PLAYERS = (ctx, b) -> {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (Data.Client c : CarryLedger.data.clients) names.add(c.name);
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                for (PlayerInfo pi : mc.getConnection().getListedOnlinePlayers()) {
                    String n = pi.getProfile().name();
                    if (n != null && n.matches("[A-Za-z0-9_]{1,16}")) names.add(n);
                }
            }
        } catch (Throwable ignored) {
        }
        for (String n : names) {
            if (n.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(n);
        }
        return b.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> BOSSES = (ctx, b) -> {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (BossType t : BossType.values()) {
            if (t.aliases[0].startsWith(rem)) b.suggest(t.aliases[0]);
        }
        return b.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> TOGGLE_NAMES = (ctx, b) -> {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (String t : TOGGLES) {
            if (t.startsWith(rem)) b.suggest(t);
        }
        return b.buildFuture();
    };

    private Commands() {
    }

    static void register(CommandDispatcher<FabricClientCommandSource> d, CommandBuildContext ctx) {
        d.register(build("carry"));
        d.register(build("cl"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> arg(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static String str(CommandContext<FabricClientCommandSource> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    private static int num(CommandContext<FabricClientCommandSource> c, String name) {
        return IntegerArgumentType.getInteger(c, name);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String root) {
        return (LiteralArgumentBuilder<FabricClientCommandSource>) lit(root)
                .executes(c -> help())
                .then(lit("help").executes(c -> help()))
                .then(lit("add")
                        .then(arg("player", StringArgumentType.word()).suggests(PLAYERS)
                                .then(arg("boss", StringArgumentType.word()).suggests(BOSSES)
                                        .then(arg("tier", IntegerArgumentType.integer(1, 5))
                                                .then(arg("amount", IntegerArgumentType.integer(1, 1000))
                                                        .executes(c -> add(str(c, "player"), str(c, "boss"), num(c, "tier"), num(c, "amount"))))))))
                .then(lit("remove")
                        .then(arg("player", StringArgumentType.word()).suggests(CLIENTS)
                                .executes(c -> remove(str(c, "player")))))
                .then(lit("finish")
                        .then(arg("player", StringArgumentType.word()).suggests(CLIENTS)
                                .executes(c -> finish(str(c, "player")))))
                .then(lit("inc")
                        .then(((RequiredArgumentBuilder<FabricClientCommandSource, String>) arg("player", StringArgumentType.word()).suggests(CLIENTS)
                                .executes(c -> inc(str(c, "player"), 1)))
                                .then(arg("n", IntegerArgumentType.integer(1, 1000))
                                        .executes(c -> inc(str(c, "player"), num(c, "n"))))))
                .then(lit("dec")
                        .then(((RequiredArgumentBuilder<FabricClientCommandSource, String>) arg("player", StringArgumentType.word()).suggests(CLIENTS)
                                .executes(c -> inc(str(c, "player"), -1)))
                                .then(arg("n", IntegerArgumentType.integer(1, 1000))
                                        .executes(c -> inc(str(c, "player"), -num(c, "n"))))))
                .then(lit("set")
                        .then(arg("player", StringArgumentType.word()).suggests(CLIENTS)
                                .then(arg("done", IntegerArgumentType.integer(0, 1000))
                                        .executes(c -> set(str(c, "player"), num(c, "done"))))))
                .then(lit("list").executes(c -> list()))
                .then(lit("clear").executes(c -> clear()))
                .then(lit("history").executes(c -> history()))
                .then(lit("stats").executes(c -> stats()))
                .then(((LiteralArgumentBuilder<FabricClientCommandSource>) lit("hud").executes(c -> {
                            CarryLedger.openHudEditor = true;
                            return 1;
                        }))
                        .then(lit("toggle").executes(c -> toggle("hud")))
                        .then(lit("reset").executes(c -> {
                            CarryLedger.data.config.hudX = 6;
                            CarryLedger.data.config.hudY = 40;
                            CarryLedger.data.save();
                            CarryLedger.msg("§cHUD position reset.");
                            return 1;
                        }))
                        .then(lit("opacity")
                                .then(arg("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
                                    CarryLedger.data.config.hudOpacity = num(c, "percent");
                                    CarryLedger.data.save();
                                    CarryLedger.msg("§cBackground opacity: " + num(c, "percent") + "%");
                                    return 1;
                                }))))
                .then(lit("price")
                        .then(arg("boss", StringArgumentType.word()).suggests(BOSSES)
                                .then(arg("tier", IntegerArgumentType.integer(1, 5))
                                        .then(arg("millions", DoubleArgumentType.doubleArg(0, 100000.0))
                                                .executes(c -> price(str(c, "boss"), num(c, "tier"), DoubleArgumentType.getDouble(c, "millions")))))))
                .then(lit("prices").executes(c -> prices()))
                .then(lit("distance")
                        .then(arg("blocks", IntegerArgumentType.integer(5, 200)).executes(c -> {
                            CarryLedger.data.config.maxDistance = num(c, "blocks");
                            CarryLedger.data.save();
                            CarryLedger.msg("§cMax distance: " + num(c, "blocks"));
                            return 1;
                        })))
                .then(((LiteralArgumentBuilder<FabricClientCommandSource>) lit("partymsg").executes(c -> partyMsg(null)))
                        .then(lit("reset").executes(c -> partyMsg(Data.DEFAULT_PARTY_MESSAGE)))
                        .then(arg("text", StringArgumentType.greedyString())
                                .executes(c -> partyMsg(str(c, "text")))))
                .then(lit("partydelay")
                        .then(arg("ms", IntegerArgumentType.integer(0, 10000)).executes(c -> {
                            CarryLedger.data.config.partyDelayMs = num(c, "ms");
                            CarryLedger.data.save();
                            CarryLedger.msg("§cParty message delay: " + num(c, "ms") + " ms");
                            return 1;
                        })))
                .then(lit("toggle")
                        .then(arg("option", StringArgumentType.word()).suggests(TOGGLE_NAMES)
                                .executes(c -> toggle(str(c, "option")))));
    }

    private static int help() {
        String p = "§c/carry";
        CarryLedger.msg("§c§lApocalyp§6§lse §c— commands (§c/cl§c = §c/carry§c, menu: §c/apocalypse§c):");
        CarryLedger.msg(p + "add <name> <boss> <tier> <amount> §c— new client");
        CarryLedger.msg(p + "inc|dec <name> [n] §c— adjust count");
        CarryLedger.msg(p + "set <name> <done> §c, §cremove|finish <name>");
        CarryLedger.msg(p + "list §c, §chistory§c, §cstats§c, §cclear");
        CarryLedger.msg(p + "hud §c— HUD editor (§chud toggle|reset|opacity <%>§c)");
        CarryLedger.msg(p + "price <boss> <tier> <millions> §c, §cprices");
        CarryLedger.msg(p + "partymsg [text|reset] §c— party message on kill, §cpartydelay <ms>");
        CarryLedger.msg(p + "toggle <" + String.join("|", TOGGLES) + ">");
        CarryLedger.msg("§cBosses: rev, tara, sven, void, blaze, vamp");
        return 1;
    }

    private static Data.Client need(String name) {
        Data.Client c = CarryLedger.findClient(name);
        if (c == null) CarryLedger.msg("§cClient §c" + name + "§c not found. §c/carry list");
        return c;
    }

    private static int add(String name, String bossArg, int tier, int amount) {
        BossType t = BossType.byAlias(bossArg);
        if (t == null) {
            CarryLedger.msg("§cUnknown boss §c" + bossArg + "§c. Options: rev, tara, sven, void, blaze, vamp");
            return 0;
        }
        if ((t == BossType.VAMPIRE && tier > 5) || tier < 1) {
            CarryLedger.msg("§cInvalid tier");
            return 0;
        }
        Data.Client existing = CarryLedger.findClient(name);
        if (existing != null && existing.type() == t && existing.tier == tier) {
            existing.total += amount;
            CarryLedger.msg("§cAdded §c+" + amount + "§c for §c" + existing.name + "§c → §c" + existing.done + "/" + existing.total);
        } else {
            if (existing != null) CarryLedger.data.clients.remove(existing);
            Data.Client c = new Data.Client();
            c.name = name;
            c.boss = t.name();
            c.tier = tier;
            c.total = amount;
            c.startedAt = System.currentTimeMillis();
            CarryLedger.data.clients.add(c);
            double p = CarryLedger.data.price(t, tier);
            CarryLedger.msg("§cClient §c" + name + "§c: §c" + amount + "x " + t.display + " " + BossType.roman(tier) + " "
                    + (p > 0 ? "§c(" + CarryLedger.fmtCoins(p * amount) + ")" : ""));
        }
        CarryLedger.data.save();
        return 1;
    }

    private static int remove(String name) {
        Data.Client c = need(name);
        if (c == null) return 0;
        CarryLedger.data.clients.remove(c);
        CarryLedger.data.save();
        CarryLedger.msg("§cClient §c" + c.name + "§c removed (" + c.done + "/" + c.total + ").");
        return 1;
    }

    private static int finish(String name) {
        Data.Client c = need(name);
        if (c == null) return 0;
        c.total = Math.max(1, c.done);
        if (c.done == 0) {
            remove(name);
            return 1;
        }
        CarryLedger.checkFinished(c);
        CarryLedger.data.save();
        return 1;
    }

    private static int inc(String name, int n) {
        Data.Client c = need(name);
        if (c == null) return 0;
        if (n > 0) {
            CarryLedger.addKills(c, n, System.currentTimeMillis());
        } else {
            int k = Math.min(-n, c.done);
            c.done -= k;
            CarryLedger.data.stats.bosses = Math.max(0, CarryLedger.data.stats.bosses - k);
            for (int i = 0; i < k && !c.kills.isEmpty(); i++) c.kills.remove(c.kills.size() - 1);
        }
        CarryLedger.msg("§c" + c.name + " §c" + c.done + "§c/§c" + c.total);
        CarryLedger.checkFinished(c);
        CarryLedger.data.save();
        return 1;
    }

    private static int set(String name, int done) {
        Data.Client c = need(name);
        if (c == null) return 0;
        int d = done - c.done;
        return d == 0 ? 1 : inc(name, d);
    }

    private static int list() {
        if (CarryLedger.data.clients.isEmpty()) {
            CarryLedger.msg("§cNo active clients. §c/carry add <name> <boss> <tier> <amount>");
            return 1;
        }
        CarryLedger.msg("§cActive carries (" + CarryLedger.data.clients.size() + "):");
        for (Data.Client c : CarryLedger.data.clients) {
            BossType t = c.type();
            double sum = CarryLedger.data.price(t, c.tier) * c.total;
            CarryLedger.msg("§4• §c" + c.name + " §c" + (t != null ? t.display : c.boss) + " " + BossType.roman(c.tier)
                    + " §c" + c.done + "/" + c.total + (sum > 0 ? "§c" + CarryLedger.fmtCoins(sum) : ""));
        }
        return 1;
    }

    private static int clear() {
        int n = CarryLedger.data.clients.size();
        CarryLedger.data.clients.clear();
        BossTracker.reset();
        CarryLedger.data.save();
        CarryLedger.msg("§cClients removed: " + n);
        return 1;
    }

    private static int history() {
        var h = CarryLedger.data.history;
        if (h.isEmpty()) {
            CarryLedger.msg("§cHistory is empty.");
            return 1;
        }
        SimpleDateFormat f = new SimpleDateFormat("dd.MM HH:mm");
        CarryLedger.msg("§cRecent carries:");
        for (int i = h.size() - 1; i >= Math.max(0, h.size() - 10); i--) {
            Data.HistoryEntry e = h.get(i);
            BossType t = BossType.byName(e.boss);
            CarryLedger.msg("§4" + f.format(new Date(e.finishedAt)) + " §c" + e.name + " §c" + e.amount + "x "
                    + (t != null ? t.display : e.boss) + " " + BossType.roman(e.tier)
                    + (e.priceM > 0 ? "§c" + CarryLedger.fmtCoins(e.priceM) : ""));
        }
        return 1;
    }

    private static int stats() {
        Data.Stats s = CarryLedger.data.stats;
        CarryLedger.msg("§cStats: §ccarries §c" + s.carries + "§c, bosses §c" + s.bosses + "§c, earned §c" + CarryLedger.fmtCoins(s.coinsM));
        return 1;
    }

    private static int price(String bossArg, int tier, double millions) {
        BossType t = BossType.byAlias(bossArg);
        if (t == null) {
            CarryLedger.msg("§cUnknown boss §c" + bossArg);
            return 0;
        }
        CarryLedger.data.config.prices.put(t.name() + "_" + tier, millions);
        CarryLedger.data.save();
        CarryLedger.msg("§cPrice " + t.display + " " + BossType.roman(tier) + ": §c" + CarryLedger.fmtCoins(millions) + "§c per boss");
        return 1;
    }

    private static int prices() {
        var p = CarryLedger.data.config.prices;
        if (p.isEmpty()) {
            CarryLedger.msg("§cNo prices set. §c/carry price <boss> <tier> <millions>");
            return 1;
        }
        CarryLedger.msg("§cPrices per boss:");
        for (BossType t : BossType.values()) {
            for (int tier = 1; tier <= 5; tier++) {
                Double v = p.get(t.name() + "_" + tier);
                if (v != null) CarryLedger.msg("§4• §c" + t.display + " " + BossType.roman(tier) + " §c" + CarryLedger.fmtCoins(v));
            }
        }
        return 1;
    }

    private static int partyMsg(String text) {
        Data.Config cfg = CarryLedger.data.config;
        if (text != null) {
            if (text.isBlank()) return 0;
            cfg.partyMessage = text.trim();
            CarryLedger.data.save();
        }
        Data.Client sample = new Data.Client();
        sample.name = "Player";
        sample.boss = BossType.values()[0].name();
        sample.tier = 4;
        sample.total = 10;
        sample.done = 3;
        CarryLedger.msg("§cParty message: §f" + cfg.partyMessage.replace("§", ""));
        CarryLedger.msg("§cPreview: §f" + CarryLedger.formatParty(cfg.partyMessage, sample, null, 42500));
        if (text == null) {
            CarryLedger.msg("§cChange: §c/carry partymsg <text> §c(reset: §c/carry partymsg reset§c)");
            CarryLedger.msg("§cPlaceholders: {name} {done} {total} {left} {boss} {tier} {time}");
        }
        if (!cfg.announceParty) CarryLedger.msg("§cParty announcements are off — §c/carry toggle party");
        return 1;
    }

    private static int toggle(String option) {
        Data.Config c = CarryLedger.data.config;
        String o = option.toLowerCase(Locale.ROOT);
        boolean v;
        switch (o) {
            case "hud" -> v = c.hud = !c.hud;
            case "lenient" -> v = c.lenient = !c.lenient;
            case "strict" -> v = c.strictTier = !c.strictTier;
            case "party" -> v = c.announceParty = !c.announceParty;
            case "spawnsound" -> v = c.spawnSound = !c.spawnSound;
            case "killsound" -> v = c.killSound = !c.killSound;
            case "spawntitle" -> v = c.spawnTitle = !c.spawnTitle;
            case "killtitle" -> v = c.killTitle = !c.killTitle;
            case "clipboard" -> v = c.copyOnFinish = !c.copyOnFinish;
            case "debug" -> v = c.debug = !c.debug;
            default -> {
                CarryLedger.msg("§cUnknown option. §c" + String.join(", ", TOGGLES));
                return 0;
            }
        }
        CarryLedger.data.save();
        CarryLedger.msg("§c" + o + ": " + (v ? "§con" : "§coff"));
        return 1;
    }
}
