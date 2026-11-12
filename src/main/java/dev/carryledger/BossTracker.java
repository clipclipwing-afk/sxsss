package dev.carryledger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Binds client bosses by their owner nameplate ("Spawned by: Name" or "Name's SLAYER BOSS"),
 * finds the HP nameplate and the boss entity next to it, and decides kill / lost.
 */
public final class BossTracker {

    public static final class Tracked {
        public final int standId;
        public final String owner;
        public int nameId = -1;
        public int bossId = -1;
        public BossType type;
        public int tier;
        public double hp = -1;
        public double maxHp = -1;
        public String hpText = "";
        public double x, y, z;
        public long firstSeen;
        public int missingTicks;
        public boolean sawAlive;
        public boolean announced;
        public boolean resolved;

        Tracked(int standId, String owner) {
            this.standId = standId;
            this.owner = owner;
        }

        /** 0..1, or -1 when unknown. */
        public float hpFraction() {
            if (hp < 0 || maxHp <= 0) return -1f;
            return (float) Math.max(0.0, Math.min(1.0, hp / maxHp));
        }
    }

    public static final Map<Integer, Tracked> TRACKED = new LinkedHashMap<>();
    private static final Set<Integer> DEAD = new HashSet<>();
    private static final Set<Integer> HANDLED = new HashSet<>();
    private static final Set<Integer> DEBUGGED = new HashSet<>();
    private static final Map<String, Object> TYPE_CACHE = new HashMap<>();
    private static Object lastLevel;

    private static final Pattern SPAWNED_BY = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])(?:Spawned\\s+by:\\s*(?:\\[[^\\]]+\\]\\s*)?|(?=[A-Za-z0-9_]{1,16}['’]s?\\s+SLAYER\\s+BOSS\\b))([A-Za-z0-9_]{1,16})\\b");
    private static final Pattern COLOR = Pattern.compile("§.");

    private BossTracker() {}

    public static String strip(String s) {
        return s == null ? "" : COLOR.matcher(s).replaceAll("").trim();
    }

    /** Called from LivingEntityMixin. */
    public static void onDeath(LivingEntity e) {
        if (e instanceof ArmorStand) return;
        if (DEAD.size() > 4096) DEAD.clear();
        DEAD.add(e.getId());
    }

    public static Tracked activeFor(String owner) {
        for (Tracked t : TRACKED.values()) {
            if (!t.resolved && t.owner.equalsIgnoreCase(owner)) return t;
        }
        return null;
    }

    public static void reset() {
        TRACKED.clear();
        DEAD.clear();
        HANDLED.clear();
        DEBUGGED.clear();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            if (lastLevel != null) reset();
            lastLevel = null;
            return;
        }
        if (level != lastLevel) {
            lastLevel = level;
            reset();
        }
        Data data = CarryLedger.data;
        if (data.clients.isEmpty() && !data.config.debug) {
            TRACKED.clear();
            return;
        }

        List<ArmorStand> stands = new ArrayList<>();
        Map<Integer, String> names = new HashMap<>();
        List<LivingEntity> mobs = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof ArmorStand stand) {
                Component cn = stand.getCustomName();
                if (cn == null) continue;
                String s = strip(cn.getString());
                if (s.isEmpty()) continue;
                stands.add(stand);
                names.put(stand.getId(), s);
            } else if (e instanceof LivingEntity living && e != player) {
                mobs.add(living);
            }
        }

        long now = System.currentTimeMillis();

        for (ArmorStand stand : stands) {
            int id = stand.getId();
            if (HANDLED.contains(id)) continue;
            if (TRACKED.containsKey(id)) continue;
            Matcher m = SPAWNED_BY.matcher(names.get(id));
            if (!m.find()) continue;
            String owner = m.group(1);
            Data.Client c = CarryLedger.findClient(owner);
            if (c == null) {
                if (data.config.debug && DEBUGGED.add(id)) {
                    CarryLedger.debug("§cboss of §c" + owner + "§c (not a client), stand #" + id);
                }
                continue;
            }
            Tracked t = new Tracked(id, c.name);
            t.firstSeen = now;
            t.x = stand.getX();
            t.y = stand.getY();
            t.z = stand.getZ();
            TRACKED.put(id, t);
            if (data.config.debug) CarryLedger.debug("§cowner stand #" + id + " for §c" + c.name);
        }

        for (Iterator<Tracked> it = TRACKED.values().iterator(); it.hasNext(); ) {
            Tracked t = it.next();
            if (t.resolved) {
                HANDLED.add(t.standId);
                it.remove();
                continue;
            }
            Entity stand = level.getEntity(t.standId);
            boolean standAlive = stand != null && stand.isAlive();
            if (standAlive) {
                t.x = stand.getX();
                t.y = stand.getY();
                t.z = stand.getZ();
            }

            if ((t.nameId < 0 || !names.containsKey(t.nameId)) && standAlive) {
                t.nameId = findNameStand(t, stands, names);
            }
            if (t.nameId >= 0) {
                BossType.Parsed p = BossType.parse(names.get(t.nameId));
                if (p != null) {
                    t.type = p.type();
                    if (p.tier() > 0) t.tier = p.tier();
                    if (p.hp() >= 0) {
                        t.hp = p.hp();
                        if (p.hp() > t.maxHp) t.maxHp = p.hp();
                        t.hpText = p.hpText();
                    }
                }
            }

            Entity boss = t.bossId >= 0 ? level.getEntity(t.bossId) : null;
            if (boss == null && t.bossId < 0 && standAlive) {
                LivingEntity found = findBoss(t, mobs, player);
                if (found != null) {
                    t.bossId = found.getId();
                    boss = found;
                    if (data.config.debug) {
                        CarryLedger.debug("§cbound boss #" + found.getId() + " to " + t.owner
                                + (t.type != null ? "(" + t.type.display + " " + BossType.roman(t.tier) + ")" : ""));
                    }
                }
            }

            if (!t.announced && (t.type != null || now - t.firstSeen > 1000)) {
                t.announced = true;
                CarryLedger.onSpawn(t);
            }

            boolean dead = t.bossId >= 0 && DEAD.contains(t.bossId);
            if (boss instanceof LivingEntity le) {
                if (le.isAlive() && le.getHealth() > 0f) t.sawAlive = true;
                else if (t.sawAlive) dead = true;
            }
            if (dead) {
                t.resolved = true;
                CarryLedger.onKill(t, "death");
                continue;
            }

            boolean bossGone = t.bossId < 0 || boss == null || !boss.isAlive();
            if (!standAlive && bossGone) {
                if (++t.missingTicks >= 6) {
                    t.resolved = true;
                    decideVanish(t, player, now);
                }
            } else {
                t.missingTicks = 0;
            }
        }
    }

    private static void decideVanish(Tracked t, LocalPlayer player, long now) {
        Data.Config cfg = CarryLedger.data.config;
        double dx = player.getX() - t.x;
        double dy = player.getY() - t.y;
        double dz = player.getZ() - t.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean near = dist <= cfg.maxDistance;
        float frac = t.hpFraction();
        boolean lowHp = (t.hp >= 0 && t.hp <= 0.5) || (frac >= 0 && frac <= 0.12);
        long alive = now - t.firstSeen;
        if (near && lowHp) {
            CarryLedger.onKill(t, "low hp");
        } else if (near && cfg.lenient && alive > 3000) {
            CarryLedger.onKill(t, "lenient");
        } else {
            CarryLedger.onLost(t, near
                    ? "boss vanished without dying (fail/despawn)"
                    : String.format(Locale.ROOT, "you were too far away (%.0f blocks)", dist));
        }
    }

    private static double hdist(Entity e, Tracked t) {
        double dx = e.getX() - t.x;
        double dz = e.getZ() - t.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int findNameStand(Tracked t, List<ArmorStand> stands, Map<Integer, String> names) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (ArmorStand s : stands) {
            if (s.getId() == t.standId) continue;
            String n = names.get(s.getId());
            if (BossType.parse(n) == null) continue;
            if (!n.contains("❤") && !n.contains("Hits")) continue;
            double h = hdist(s, t);
            double dy = s.getY() - t.y;
            if (h > 1.5 || dy < -0.5 || dy > 3.0) continue;
            double score = h * 2.0 + Math.abs(dy) * 0.3 + Math.min(Math.abs(s.getId() - t.standId), 12) * 0.25;
            if (score < bestScore) {
                bestScore = score;
                best = s.getId();
            }
        }
        return best;
    }

    private static LivingEntity findBoss(Tracked t, List<LivingEntity> mobs, LocalPlayer player) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : mobs) {
            if (e == player) continue;
            if (HANDLED.contains(e.getId())) continue;
            if (e instanceof Player && t.type != BossType.VAMPIRE) continue;
            if (!e.isAlive()) continue;
            double h = hdist(e, t);
            double dy = t.y - e.getY();
            if (h > 2.0 || dy < -1.0 || dy > 6.0) continue;
            int idGap = t.standId - e.getId();
            double score = h * 1.5 + (idGap > 0 && idGap <= 8 ? idGap * 0.1 : 2.0);
            if (t.type != null && matchesType(e, t.type)) score -= 2.0;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static boolean matchesType(Entity e, BossType type) {
        EntityType<?> et = e.getType();
        for (String n : type.entityTypes) {
            Object v = TYPE_CACHE.computeIfAbsent(n, k -> {
                try {
                    return EntityType.class.getField(k).get(null);
                } catch (Throwable ex) {
                    return Boolean.FALSE;
                }
            });
            if (v == et) return true;
        }
        return false;
    }
}
