package dev.carryledger;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Slayer bosses we can carry + how to recognise them on nameplates. */
public enum BossType {
    REVENANT("Revenant", "REV", 0xFF5FD068,
            new String[]{"Atoned Horror", "Revenant Horror"}, new int[]{5, 0},
            new String[]{"ZOMBIE"},
            "rev", "revenant", "zombie", "zomb"),
    TARANTULA("Tarantula", "TARA", 0xFFD9534F,
            new String[]{"Conjoined Brood", "Tarantula Broodfather"}, new int[]{5, 0},
            new String[]{"SPIDER", "CAVE_SPIDER"},
            "tara", "tarantula", "spider", "brood"),
    WOLF("Sven", "SVEN", 0xFFE0E0E0,
            new String[]{"Sven Packmaster"}, new int[]{0},
            new String[]{"WOLF"},
            "sven", "wolf"),
    VOIDGLOOM("§5Voidgloom", "VOID", 0xFFB46BFF,
            new String[]{"Voidgloom Seraph"}, new int[]{0},
            new String[]{"ENDERMAN"},
            "void", "voidgloom", "eman", "enderman", "seraph"),
    INFERNO("§6Inferno", "BLAZE", 0xFFFFA733,
            new String[]{"Inferno Demonlord"}, new int[]{0},
            new String[]{"BLAZE"},
            "blaze", "inferno", "demonlord"),
    VAMPIRE("Riftstalker", "VAMP", 0xFFFF4D6D,
            new String[]{"Bloodfiend"}, new int[]{0},
            new String[]{"PLAYER"},
            "vamp", "vampire", "rift", "riftstalker", "bloodfiend");

    /** Result of reading a boss nameplate. hp = -1 when unknown. */
    public record Parsed(BossType type, int tier, double hp, String hpText) {}

    public final String display;
    public final String shortName;
    public final int color;
    /** Nameplate names; impliedTier[i] is used when no roman numeral follows names[i] (0 = unknown). */
    public final String[] names;
    public final int[] impliedTier;
    /** Field names in {@link net.minecraft.world.entity.EntityType}, resolved by reflection. */
    public final String[] entityTypes;
    public final String[] aliases;

    private static final Pattern TIER = Pattern.compile("^\\s+(V|IV|III|II|I)(?![A-Za-z])");
    private static final Pattern HP = Pattern.compile("([\\d.,]+)\\s*([kKmMbB]?)\\s*❤");
    private static final Pattern HITS = Pattern.compile("(\\d+)\\s*Hits?");

    BossType(String display, String shortName, int color, String[] names, int[] impliedTier,
             String[] entityTypes, String... aliases) {
        this.display = display;
        this.shortName = shortName;
        this.color = color;
        this.names = names;
        this.impliedTier = impliedTier;
        this.entityTypes = entityTypes;
        this.aliases = aliases;
    }

    public static BossType byAlias(String s) {
        if (s == null) return null;
        String l = s.toLowerCase(Locale.ROOT);
        for (BossType t : values()) {
            if (t.name().equalsIgnoreCase(l)) return t;
            for (String a : t.aliases) if (a.equals(l)) return t;
        }
        return null;
    }

    public static BossType byName(String s) {
        if (s == null) return null;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return byAlias(s);
        }
    }

    /** Reads a stripped nameplate like "☠ Voidgloom Seraph IV 37.5M❤". */
    public static Parsed parse(String s) {
        if (s == null || s.isEmpty()) return null;
        for (BossType t : values()) {
            for (int i = 0; i < t.names.length; i++) {
                int idx = s.indexOf(t.names[i]);
                if (idx < 0) continue;
                int tier = t.impliedTier[i];
                Matcher tm = TIER.matcher(s.substring(idx + t.names[i].length()));
                if (tm.find()) tier = roman(tm.group(1));

                double hp = -1;
                String hpText = "";
                Matcher hm = HP.matcher(s);
                while (hm.find()) {
                    hp = parseNum(hm.group(1), hm.group(2));
                    hpText = hm.group(1) + hm.group(2) + "❤";
                }
                if (hp < 0) {
                    Matcher hits = HITS.matcher(s);
                    if (hits.find()) {
                        hp = Double.parseDouble(hits.group(1));
                        hpText = hits.group(1) + " Hits";
                    }
                }
                return new Parsed(t, tier, hp, hpText);
            }
        }
        return null;
    }

    static double parseNum(String num, String suffix) {
        double v;
        try {
            v = Double.parseDouble(num.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
        switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k" -> v *= 1000.0;
            case "m" -> v *= 1_000_000.0;
            case "b" -> v *= 1_000_000_000.0;
            default -> { }
        }
        return v;
    }

    public static int roman(String r) {
        return switch (r) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> 0;
        };
    }

    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "?";
        };
    }
}
