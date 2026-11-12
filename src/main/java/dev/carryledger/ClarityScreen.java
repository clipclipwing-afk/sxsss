package dev.carryledger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Main settings window (/apocalypse): Settings / Clients / History tabs. */
public class ClarityScreen extends Screen {

    static final int WIN = -233699319;
    static final int WIN_BORDER = -11659748;
    static final int HEAD_TOP = -9825764;
    static final int HEAD_BOT = -13956336;
    static final int ACCENT = -1750963;
    static final int ACCENT_DK = -7463128;
    static final int ACCENT_GLOW = 1088768077;
    static final int CARD = -14938350;
    static final int CARD_HOVER = -14281704;
    static final int CARD_BORDER = -13166049;
    static final int BTN = -14019303;
    static final int BTN_HOVER = -12903390;
    static final int BTN_BORDER = -11918551;
    static final int BTN_SHADOW = -16120827;
    static final int SEG_BG = -15268087;
    static final int TEXT = -726807;
    static final int TEXT_DIM = -4021080;
    static final int MUTED = -8362390;
    static final int NAME = -13889;
    static final int GREEN = -9709169;
    static final int GOLD = -18355;
    static final int BAR_BG = -15989498;
    static final String S = "§";

    static int tab = 1;

    final List<Btn> btns = new ArrayList<>();
    final int[] scroll = new int[3];
    Data.Client confirm;
    long confirmAt;
    Data.HistoryEntry confirmDel;
    long confirmDelAt;

    static final class Btn {
        final int x, y, w, h;
        final Runnable action;

        Btn(int x, int y, int w, int h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public ClarityScreen() {
        super(Component.literal("Apocalypse"));
    }

    // ---------------------------------------------------------------- primitives

    static boolean hover(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Rounded-corner box: 1px border with clipped corners. */
    static void rbox(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x + 1, y, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    static int boldWidth(Font font, String s) {
        return font.width(s) + s.length();
    }

    static void center(GuiGraphicsExtractor g, Font font, String s, int x, int y, int w, int color) {
        g.text(font, s, x + (w - font.width(s)) / 2, y, color, false);
    }

    /** style: 0 = normal, 1 = accent, 2 = danger. */
    void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, int style,
                int mx, int my, Runnable action) {
        boolean hov = hover(x, y, w, h, mx, my);
        int bg, border;
        if (style == 1) {
            bg = hov ? -5232077 : ACCENT_DK;
            border = ACCENT;
        } else if (style == 2) {
            bg = hov ? -3065280 : -5232077;
            border = -34182;
        } else {
            bg = hov ? BTN_HOVER : BTN;
            border = hov ? ACCENT : BTN_BORDER;
        }
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, BTN_SHADOW);
        rbox(g, x, y, w, h, bg, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, CarryHud.lighten(bg, 0.1f));
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + 1,
                style != 0 || hov ? TEXT : TEXT_DIM, false);
        if (action != null) btns.add(new Btn(x, y, w, h + 1, action));
    }

    static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hov, int stripe) {
        rbox(g, x, y, w, h, hov ? CARD_HOVER : CARD, hov ? -10870231 : CARD_BORDER);
        g.fill(x + 1, y + 2, x + 3, y + h - 2, stripe);
    }

    void toggle(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, boolean on,
                int mx, int my, Runnable action) {
        int h = 20;
        boolean hov = hover(x, y, w, h, mx, my);
        card(g, x, y, w, h, hov, on ? ACCENT : -12966872);
        g.text(font, label, x + 9, y + 6, on ? TEXT : TEXT_DIM, false);
        int sw = 22;
        int sx = x + w - sw - 7;
        int sy = y + 5;
        rbox(g, sx, sy, sw, 10, on ? -4048319 : -13755617, on ? ACCENT : BTN_BORDER);
        int kx = on ? sx + sw - 9 : sx + 1;
        g.fill(kx, sy + 1, kx + 8, sy + 9, on ? -3599 : -7703946);
        btns.add(new Btn(x, y, w, h, action));
    }

    static String trim(Font font, String s, int max) {
        if (max <= 0) return "";
        if (font.width(s) <= max) return s;
        while (s.length() > 1 && font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    static String bossName(String boss, int tier) {
        BossType t = BossType.byName(boss);
        String n = t != null ? t.display : boss;
        if (n == null) n = "?";
        n = n.replaceAll("§.", "");
        if (n.length() > 1 && n.equals(n.toUpperCase(Locale.ROOT))) {
            n = n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
        }
        return n + " T" + tier;
    }

    // ---------------------------------------------------------------- window

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        btns.clear();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        g.fill(0, 0, sw, sh, -1877998586);

        long now = System.currentTimeMillis();
        if (confirm != null && now - confirmAt > 3000L) confirm = null;
        if (confirmDel != null && now - confirmDelAt > 3000L) confirmDel = null;

        Data d = CarryLedger.data;
        int W = Math.max(320, Math.min(sw - 24, 500));
        int x = (sw - W) / 2;
        int y = 10;
        int H = sh - 20;

        rbox(g, x - 2, y - 2, W + 4, H + 4, 0, ACCENT_GLOW);
        rbox(g, x, y, W, H, WIN, WIN_BORDER);

        int head = 30;
        g.fillGradient(x + 1, y + 1, x + W - 1, y + head, HEAD_TOP, HEAD_BOT);
        g.fill(x + 1, y + head, x + W - 1, y + head + 1, ACCENT);

        int lx = x + 9;
        int ly = y + 9;
        rbox(g, lx, ly, 12, 12, ACCENT_DK, ACCENT);
        center(g, font, "§lA", lx, ly + 2, 13, TEXT);
        String title = "§f§lApocalyp§6§lse";
        g.text(font, title, lx + 18, y + 11, -1, true);

        int closeX = x + W - 24;
        button(g, font, closeX, y + 7, 16, 15, "×", 0, mx, my, this::onClose);

        String[] labels = {"Settings", "Clients " + d.clients.size(), "History " + d.history.size()};
        int total = 0;
        int[] widths = new int[3];
        for (int i = 0; i < 3; i++) {
            widths[i] = font.width(labels[i]) + 16;
            total += widths[i];
        }
        int tx = closeX - 8 - total - 4;
        int ty = y + 7;
        rbox(g, tx, ty, total + 4, 16, SEG_BG, -12971240);
        int cx = tx + 2;
        for (int i = 0; i < 3; i++) {
            boolean sel = tab == i;
            boolean hov = hover(cx, ty + 2, widths[i], 12, mx, my);
            if (sel) {
                rbox(g, cx, ty + 2, widths[i], 12, -4180932, -38037);
            } else if (hov) {
                g.fill(cx, ty + 2, cx + widths[i], ty + 14, -13889002);
            }
            center(g, font, labels[i], cx, ty + 4, widths[i], sel ? TEXT : hov ? TEXT : TEXT_DIM);
            final int idx = i;
            btns.add(new Btn(cx, ty + 2, widths[i], 12, () -> tab = idx));
            cx += widths[i];
        }

        int footY = y + H - 15;
        g.fill(x + 1, footY, x + W - 1, footY + 1, CARD_BORDER);
        g.fill(x + 1, footY + 1, x + W - 1, y + H - 1, -15858169);
        g.text(font, "Esc — save & close • /apocalypse • v2.7.1", x + 8, footY + 4, MUTED, false);

        String right;
        if (tab == 2) {
            int n = d.history.size();
            double sum = 0;
            for (int i = Math.max(0, n - 15); i < n; i++) sum += d.history.get(i).priceM;
            right = "§7last 15 earned §6" + CarryLedger.fmtCoins(sum);
        } else {
            double earned = 0, planned = 0;
            for (Data.Client c : d.clients) {
                double p = d.price(c.type(), c.tier);
                earned += p * c.done;
                planned += p * c.total;
            }
            right = "§7earned §a" + CarryLedger.fmtCoins(earned) + "§7 / §6" + CarryLedger.fmtCoins(planned);
        }
        g.text(font, right, x + W - 8 - font.width(right), footY + 4, -1, false);

        int bx = x + 10;
        int by = y + head + 9;
        int bw = W - 20;
        int bh = footY - 6 - by;
        if (tab == 0) settings(g, font, mc, bx, by, bw, mx, my);
        else if (tab == 1) clients(g, font, bx, by, bw, bh, mx, my);
        else history(g, font, bx, by, bw, bh, mx, my);
    }

    void section(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label) {
        g.text(font, label, x, y, ACCENT, false);
        int lx = x + font.width(label) + 6;
        g.fill(lx, y + 4, x + w, y + 5, CARD_BORDER);
    }

    // ---------------------------------------------------------------- settings tab

    void settings(GuiGraphicsExtractor g, Font font, Minecraft mc, int x, int y, int w, int mx, int my) {
        Data.Config cfg = CarryLedger.data.config;
        section(g, font, x, y, w, "General");
        y += 13;
        int colW = (w - 6) / 2;
        Object[][] rows = {
                {"Show HUD", cfg.hud, (Runnable) () -> cfg.hud = !cfg.hud},
                {"Lenient detection", cfg.lenient, (Runnable) () -> cfg.lenient = !cfg.lenient},
                {"Strict tier check", cfg.strictTier, (Runnable) () -> cfg.strictTier = !cfg.strictTier},
                {"Party announcements", cfg.announceParty, (Runnable) () -> cfg.announceParty = !cfg.announceParty},
                {"Spawn sound", cfg.spawnSound, (Runnable) () -> cfg.spawnSound = !cfg.spawnSound},
                {"Kill sound", cfg.killSound, (Runnable) () -> cfg.killSound = !cfg.killSound},
                {"Spawn title", cfg.spawnTitle, (Runnable) () -> cfg.spawnTitle = !cfg.spawnTitle},
                {"Kill title", cfg.killTitle, (Runnable) () -> cfg.killTitle = !cfg.killTitle},
                {"Copy summary on finish", cfg.copyOnFinish, (Runnable) () -> cfg.copyOnFinish = !cfg.copyOnFinish},
                {"Debug messages", cfg.debug, (Runnable) () -> cfg.debug = !cfg.debug},
                {"Compact Implosion chat", ImplosionCompact.enabled, (Runnable) () -> {
                    ImplosionCompact.enabled = !ImplosionCompact.enabled;
                    ImplosionCompact.save();
                }},
        };
        for (int i = 0; i < rows.length; i++) {
            int col = i % 2;
            int row = i / 2;
            toggle(g, font, x + col * (colW + 6), y + row * 23, colW,
                    (String) rows[i][0], (Boolean) rows[i][1], mx, my, (Runnable) rows[i][2]);
        }
        // The odd toggle count leaves the last right-hand slot free.
        stepper(g, font, x + colW + 6, y + rows.length / 2 * 23, colW, "Party msg delay",
                String.format(java.util.Locale.ROOT, "%.2fs", cfg.partyDelayMs / 1000.0), mx, my,
                () -> cfg.partyDelayMs = Math.max(0, cfg.partyDelayMs - 250),
                () -> cfg.partyDelayMs = Math.min(10000, cfg.partyDelayMs + 250));
        y += (rows.length + 1) / 2 * 23 + 5;

        section(g, font, x, y, w, "HUD");
        y += 13;
        stepper(g, font, x, y, colW, "HUD opacity", cfg.hudOpacity + "%", mx, my,
                () -> cfg.hudOpacity = Math.max(0, cfg.hudOpacity - 10),
                () -> cfg.hudOpacity = Math.min(100, cfg.hudOpacity + 10));
        stepper(g, font, x + colW + 6, y, colW, "Max distance", String.valueOf((int) cfg.maxDistance), mx, my,
                () -> cfg.maxDistance = Math.max(5.0, cfg.maxDistance - 5.0),
                () -> cfg.maxDistance = Math.min(256.0, cfg.maxDistance + 5.0));
        y += 24;
        button(g, font, x, y, colW, 18, "Edit HUD position", 1, mx, my,
                () -> mc.gui.setScreen(new ClarityHudEditScreen()));
        button(g, font, x + colW + 6, y, colW, 18, "Reset HUD", 0, mx, my, () -> {
            cfg.hudX = 6;
            cfg.hudY = 40;
            cfg.hudOpacity = 80;
        });
    }

    void stepper(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, String value,
                 int mx, int my, Runnable dec, Runnable inc) {
        card(g, x, y, w, 20, hover(x, y, w, 20, mx, my), GOLD);
        g.text(font, label, x + 9, y + 6, TEXT, false);
        int bx = x + w - 6 - 14 - 30 - 14;
        button(g, font, bx, y + 3, 14, 13, "-", 0, mx, my, dec);
        center(g, font, value, bx + 14, y + 6, 30, GOLD);
        button(g, font, bx + 44, y + 3, 14, 13, "+", 0, mx, my, inc);
    }

    void counter(GuiGraphicsExtractor g, Font font, int x, int y, String label, String value, int color,
                 int mx, int my, Runnable dec, Runnable inc) {
        g.text(font, label, x, y + 3, MUTED, false);
        int bx = x + 30;
        button(g, font, bx, y, 14, 13, "-", 0, mx, my, dec);
        center(g, font, value, bx + 14, y + 3, 24, color);
        button(g, font, bx + 38, y, 14, 13, "+", 0, mx, my, inc);
    }

    // ---------------------------------------------------------------- clients tab

    void clients(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
        Data d = CarryLedger.data;
        List<Data.Client> list = d.clients;
        if (list.isEmpty()) {
            center(g, font, "No active clients", x, y + h / 2 - 10, w, TEXT_DIM);
            center(g, font, "/carry add <name> <boss> <tier> <amount>", x, y + h / 2 + 4, w, MUTED);
            return;
        }
        int rowH = 48;
        int vis = Math.max(1, h / rowH);
        scroll[1] = Math.max(0, Math.min(scroll[1], list.size() - vis));

        for (int i = scroll[1]; i < list.size() && i < scroll[1] + vis; i++) {
            Data.Client c = list.get(i);
            int ry = y + (i - scroll[1]) * rowH;
            int rh = rowH - 5;
            boolean done = c.done >= c.total;
            boolean hov = hover(x, ry, w, rh, mx, my);
            card(g, x, ry, w, rh, hov, done ? GREEN : ACCENT);

            double price = d.price(c.type(), c.tier);
            int finW = 50;
            int ctlW = 84;
            int finX = x + w - 6 - finW;
            int ctlX = finX - 8 - ctlW;
            int tx = x + 10;
            int tw = ctlX - 12 - tx;

            String idx = "#" + (i + 1);
            String money = "§a" + CarryLedger.fmtCoins(price * c.done) + "§8 / §6" + CarryLedger.fmtCoins(price * c.total);
            int mw = font.width(money);
            g.text(font, idx, tx, ry + 6, MUTED, false);
            int nx = tx + font.width(idx) + 5;
            g.text(font, trim(font, c.name, tw - mw - (nx - tx) - 6), nx, ry + 6, NAME, false);
            g.text(font, money, tx + tw - mw, ry + 6, -1, false);
            g.text(font, trim(font, bossName(c.boss, c.tier), tw - 50), tx, ry + 18, TEXT_DIM, false);

            int pct = c.total > 0 ? (int) Math.round(100.0 * c.done / c.total) : 0;
            String prog = "§f" + c.done + "§8/§7" + c.total + " §8" + pct + "%";
            g.text(font, prog, tx + tw - font.width(prog), ry + 18, -1, false);

            int barY = ry + 31;
            int barW = tw;
            int barH = 4;
            g.fill(tx, barY, tx + barW, barY + barH, BAR_BG);
            int fillW = c.total > 0 ? (int) (barW * Math.min(1.0, c.done / (double) c.total)) : 0;
            if (fillW > 0) {
                g.fillGradient(tx, barY, tx + fillW, barY + barH,
                        done ? -7606098 : -38037, done ? -12605338 : -5758164);
            }
            if (c.total > 0 && c.total <= 40) {
                for (int k = 1; k < c.total; k++) {
                    int sx = tx + (int) ((long) barW * k / c.total);
                    g.fill(sx, barY, sx + 1, barY + barH, 0x66000000);
                }
            }

            g.fill(ctlX - 6, ry + 5, ctlX - 5, ry + rh - 5, CARD_BORDER);
            counter(g, font, ctlX, ry + 6, "Done", String.valueOf(c.done), TEXT, mx, my,
                    () -> minusOne(c),
                    () -> {
                        CarryLedger.addKills(c, 1, System.currentTimeMillis());
                        CarryLedger.checkFinished(c);
                    });
            counter(g, font, ctlX, ry + 24, "Need", String.valueOf(c.total), GOLD, mx, my,
                    () -> c.total = Math.max(Math.max(1, c.done + 1), c.total - 1),
                    () -> c.total = c.total + 1);

            if (confirm == c) {
                button(g, font, finX, ry + 6, finW, 30, "Sure?", 2, mx, my, () -> {
                    confirm = null;
                    finish(c);
                });
            } else {
                button(g, font, finX, ry + 6, finW, 30, "Finish", 1, mx, my, () -> {
                    confirm = c;
                    confirmAt = System.currentTimeMillis();
                });
            }
        }

        if (list.size() > vis) {
            String s = (scroll[1] + 1) + "-" + Math.min(list.size(), scroll[1] + vis) + " of " + list.size() + " • scroll";
            g.text(font, s, x + w - font.width(s), y + h - 8, MUTED, false);
        }
    }

    static void minusOne(Data.Client c) {
        if (c.done <= 0) return;
        c.done--;
        Data.Stats st = CarryLedger.data.stats;
        st.bosses = Math.max(0, st.bosses - 1);
        if (c.kills != null && !c.kills.isEmpty()) c.kills.remove(c.kills.size() - 1);
        c.lastKillAt = c.kills == null || c.kills.isEmpty() ? 0L : c.kills.get(c.kills.size() - 1);
    }

    /** Ends a carry early; remembers the originally planned amount in history. */
    static void finish(Data.Client c) {
        int planned = c.total;
        c.total = Math.max(1, c.done);
        if (c.done == 0) {
            CarryLedger.data.clients.remove(c);
            CarryLedger.msg("§7Removed §b" + c.name + "§7 (no bosses killed).");
            return;
        }
        CarryLedger.checkFinished(c);
        List<Data.HistoryEntry> h = CarryLedger.data.history;
        if (!h.isEmpty()) {
            Data.HistoryEntry last = h.get(h.size() - 1);
            if (c.name != null && c.name.equals(last.name)) last.planned = planned;
        }
    }

    // ---------------------------------------------------------------- history tab

    void history(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
        List<Data.HistoryEntry> list = CarryLedger.data.history;
        int n = list.size();
        int delW = 15;
        int inner = w - delW - 6;

        int earnedW = font.width("Earned");
        for (Data.HistoryEntry e : list) earnedW = Math.max(earnedW, font.width(CarryLedger.fmtCoins(e.priceM)));
        earnedW += 8;
        int dateW = font.width("30 Nov 23:59") + 10;
        int tookW = font.width("59m 59s") + 10;
        int earnedX = x + inner - earnedW;
        int finishedX = earnedX - dateW;
        int tookX = finishedX - tookW;
        int clientX = x + 10;
        int carryX = x + Math.max(64, (int) (inner * 0.22));

        g.text(font, "CLIENT", clientX, y, MUTED, false);
        g.text(font, "CARRY", carryX, y, MUTED, false);
        g.text(font, "TOOK", tookX, y, MUTED, false);
        g.text(font, "FINISHED", finishedX, y, MUTED, false);
        g.text(font, "EARNED", earnedX, y, MUTED, false);
        g.fill(x, y + 11, x + w, y + 12, ACCENT_DK);
        y += 15;
        h -= 15;

        if (n == 0) {
            center(g, font, "No finished carries yet", x, y + h / 2 - 4, w, TEXT_DIM);
            return;
        }

        int rowH = 16;
        int vis = Math.max(1, (h - 10) / rowH);
        scroll[2] = Math.max(0, Math.min(scroll[2], n - vis));
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH);

        for (int k = 0; k < vis && scroll[2] + k < n; k++) {
            Data.HistoryEntry e = list.get(n - 1 - scroll[2] - k);
            int ry = y + k * rowH;
            boolean early = e.planned > e.amount;
            boolean hov = hover(x, ry, w, rowH - 2, mx, my);
            card(g, x, ry, w, rowH - 2, hov, early ? GOLD : GREEN);

            g.text(font, trim(font, e.name == null ? "?" : e.name, carryX - clientX - 6), clientX, ry + 3, NAME, false);
            String carry = early
                    ? e.amount + "/" + e.planned + "× " + bossName(e.boss, e.tier) + " §6· ended early"
                    : e.amount + "× " + bossName(e.boss, e.tier);
            g.text(font, trim(font, carry, tookX - carryX - 6), carryX, ry + 3, TEXT, false);
            g.text(font, CarryLedger.fmtDur(Math.max(0L, e.finishedAt - e.startedAt)), tookX, ry + 3, TEXT_DIM, false);
            g.text(font, fmt.format(new Date(e.finishedAt)), finishedX, ry + 3, TEXT_DIM, false);
            g.text(font, e.priceM > 0 ? CarryLedger.fmtCoins(e.priceM) : "-", earnedX, ry + 3, GOLD, false);

            boolean armed = confirmDel == e;
            button(g, font, x + w - delW - 2, ry + 1, delW, rowH - 5, armed ? "?" : "×", armed ? 2 : 0, mx, my, () -> {
                if (confirmDel == e) {
                    confirmDel = null;
                    CarryLedger.data.history.remove(e);
                } else {
                    confirmDel = e;
                    confirmDelAt = System.currentTimeMillis();
                }
            });
        }

        String foot = n > vis
                ? (scroll[2] + 1) + "-" + Math.min(n, scroll[2] + vis) + " of " + n + " • scroll • × twice to delete"
                : n + " total • click × twice to delete";
        g.text(font, foot, x + w - font.width(foot), y + h - 8, MUTED, false);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) {
            for (Btn b : new ArrayList<>(btns)) {
                if (b.in(e.x(), e.y())) {
                    b.action.run();
                    CarryLedger.data.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll[tab] -= (int) Math.signum(sy);
        if (scroll[tab] < 0) scroll[tab] = 0;
        return true;
    }

    @Override
    public void onClose() {
        CarryLedger.data.save();
        super.onClose();
    }
}
