package dev.carryledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything that is saved to config/carryledger-v2.json. */
public class Data {

    public static class Client {
        public String name;
        /** BossType name (kept as a string so unknown values don't break loading). */
        public String boss;
        public int tier;
        public int total;
        public int done;
        public long startedAt;
        public long lastKillAt;
        /** Timestamps of recent kills (max 200), used for the average kill time. */
        public List<Long> kills = new ArrayList<>();

        public BossType type() {
            return BossType.byName(boss);
        }
    }

    public static class HistoryEntry {
        public int planned;
        public String name;
        public String boss;
        public int tier;
        public int amount;
        /** Total price in millions. */
        public double priceM;
        public long startedAt;
        public long finishedAt;
    }

    public static final String DEFAULT_PARTY_MESSAGE = "{name}: {done}/{total}";

    public static class Config {
        public boolean hud = true;
        public int hudX = 6;
        public int hudY = 40;
        public int hudOpacity = 80;
        public boolean lenient = false;
        public boolean strictTier = true;
        public boolean announceParty = false;
        /** Party chat template, see CarryLedger.formatParty for the placeholders. */
        public String partyMessage = DEFAULT_PARTY_MESSAGE;
        /** Minimum gap between two party messages. */
        public int partyDelayMs = 1000;
        public boolean spawnSound = true;
        public boolean killSound = true;
        public boolean spawnTitle = true;
        public boolean killTitle = false;
        public boolean copyOnFinish = true;
        public boolean debug = false;
        public double maxDistance = 40.0;
        /** Price per boss in millions, key = BOSS_TIER, e.g. VOIDGLOOM_4. */
        public Map<String, Double> prices = new HashMap<>();
    }

    public static class Stats {
        public int carries;
        public int bosses;
        public double coinsM;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public Config config = new Config();
    public List<Client> clients = new ArrayList<>();
    public List<HistoryEntry> history = new ArrayList<>();
    public Stats stats = new Stats();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("carryledger-v2.json");
    }

    public static Data load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Data.class);
                if (d != null) {
                    if (d.config == null) d.config = new Config();
                    if (d.config.prices == null) d.config.prices = new HashMap<>();
                    if (d.config.partyMessage == null || d.config.partyMessage.isBlank()) d.config.partyMessage = DEFAULT_PARTY_MESSAGE;
                    d.config.partyDelayMs = Math.max(0, Math.min(10000, d.config.partyDelayMs));
                    if (d.clients == null) d.clients = new ArrayList<>();
                    if (d.history == null) d.history = new ArrayList<>();
                    if (d.stats == null) d.stats = new Stats();
                    for (Client c : d.clients) if (c.kills == null) c.kills = new ArrayList<>();
                    return d;
                }
            }
        } catch (Throwable t) {
            CarryLedger.log("Failed to read " + p + ": " + t);
            try {
                Files.copy(p, p.resolveSibling("carryledger-v2.broken.json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {
            }
        }
        return new Data();
    }

    public void save() {
        Path p = path();
        try {
            Path tmp = p.resolveSibling("carryledger-v2.json.tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            CarryLedger.log("Failed to save " + p + ": " + t);
        }
    }

    public double price(BossType type, int tier) {
        if (type == null) return 0;
        Double v = config.prices.get(type.name() + "_" + tier);
        return v == null ? 0 : v;
    }
}
