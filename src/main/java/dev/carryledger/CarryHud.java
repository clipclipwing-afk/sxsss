package dev.carryledger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** The carry panel drawn on the HUD (and as a preview in the editor). */
public final class CarryHud {

    static final int PAD = 6;
    static final int HEADER = 17;
    static final int ROW = 30;
    static final int FOOTER = 15;
    static final int MIN_W = 190;

    private static boolean errorShown;

    private CarryHud() {}

    public static void render(GuiGraphicsExtractor g) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui == null || mc.font == null) return;
            Data d = CarryLedger.data;
            if (!d.config.hud || d.clients.isEmpty()) return;
            if (mc.gui.hud.isHidden()) return;
            if (mc.gui.screen() instanceof ClarityHudEditScreen) return;
            draw(g, mc.font, d.config.hudX, d.config.hudY, d.clients, false);
        } catch (Throwable t) {
            if (!errorShown) {
                errorShown = true;
                CarryLedger.log("HUD error: " + t);
                t.printStackTrace();
            }
        }
    }

    private static String tag(Data.Client c) {
        BossType t = c.type();
        return (t != null ? t.shortName : "?") + " " + BossType.roman(c.tier);
    }

    private static int color(Data.Client c) {
        BossType t = c.type();
        return t != null ? t.color : 0xFFC98585;
    }

    private static String status(Data.Client c, boolean preview) {
        BossTracker.Tracked t = preview ? null : BossTracker.activeFor(c.name);
        if (t != null) return "● BOSS " + t.hpText;
        if (preview && c.done % 2 == 1) return "● BOSS 2.4M❤";
        return "○ waiting for spawn";
    }

    private static String rate(Data.Client c) {
        long avg = CarryLedger.avgKillMs(c);
        if (avg > 0) {
            long perHour = 3_600_000L / Math.max(1L, avg);
            int left = Math.max(0, c.total - c.done);
            return CarryLedger.fmtDur(avg) + "/boss · " + perHour + "/h"
                    + (left > 0 ? "· ~" + CarryLedger.fmtDur(avg * left) : "");
        }
        return "◷ " + CarryLedger.fmtDur(System.currentTimeMillis() - c.startedAt);
    }

    /** Draws the panel; returns {x, y, w, h} after clamping to the screen. */
    public static int[] draw(GuiGraphicsExtractor g, Font font, int x, int y, List<Data.Client> clients, boolean preview) {
        Data d = CarryLedger.data;
        int done = 0, total = 0;
        double coins = 0;
        int w = MIN_W;
        for (Data.Client c : clients) {
            done += c.done;
            total += c.total;
            coins += d.price(c.type(), c.tier) * c.total;
            int top = font.width(tag(c)) + PAD + 5 + font.width(c.name) + 14 + font.width(c.done + "/" + c.total + "  100%");
            int bottom = font.width(ClarityHud.status(c, preview)) + 14 + font.width(rate(c));
            w = Math.max(w, Math.max(top, bottom) + 12 + 4);
        }
        boolean footer = coins > 0 || clients.size() > 1;
        int h = 20 + clients.size() * ROW + (footer ? FOOTER : 2);

        int sw = g.guiWidth(), sh = g.guiHeight();
        x = Math.max(0, Math.min(x, sw - w));
        y = Math.max(0, Math.min(y, sh - h));

        int alpha = Math.max(0, Math.min(255, d.config.hudOpacity * 255 / 100));
        int bg = alpha << 24 | 0x190B0C;
        int edge = (alpha / 4) << 24 | 0xFFFFFF;

        // background with rounded corners
        g.fill(x + 1, y, x + w - 1, y + h, bg);
        g.fill(x, y + 1, x + 1, y + h - 1, bg);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
        // subtle edge
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, edge);
        g.fill(x, y + 1, x + 1, y + h - 1, edge);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
        // header
        g.fillGradient(x + 1, y, x + w - 1, y + HEADER - 1, alpha << 24 | 0x401416, alpha << 24 | 0x1C0D0E);
        g.fillGradient(x + 1, y + HEADER - 1, x + w - 1, y + HEADER, 0xFFD9534F, 0xFFA83434);
        g.text(font, "✦ §cApocalyp", x + PAD, y + 5, 0xFFE46B6B, true);
        g.text(font, "§6se", x + PAD + font.width("✦ §cApocalyp"), y + 5, 0xFFF0A3A3, true);

        int n = clients.size();
        String count = n + (n % 10 == 1 && n % 100 != 11 ? " client"
                : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? " clients" : " clients");
        g.text(font, count, x + w - PAD - font.width(count), y + 5, 0xFFC99191, true);

        long now = System.currentTimeMillis();
        int ry = y + HEADER + 4;
        for (Data.Client c : clients) {
            int col = color(c);
            String tag = tag(c);
            int tw = font.width(tag);
            int lx = x + PAD + 2;

            // accent stripe + tag chip
            g.fill(x + 2, ry - 1, x + 4, ry + ROW - 5, col);
            g.fill(lx, ry - 1, lx + tw + 6, ry + 9, 0x45000000 | (col & 0xFFFFFF));
            g.text(font, tag, lx + 3, ry, col, false);
            g.text(font, c.name, lx + tw + 11, ry, 0xFFEE8080, true);

            float frac = c.total > 0 ? Math.min(1f, (float) c.done / c.total) : 0f;
            String cnt = c.done + "/" + c.total;
            String pct = Math.round(frac * 100f) + "%";
            int cx = x + w - PAD - font.width(cnt) - font.width(pct);
            g.text(font, cnt, cx, ry, 0xFFFFFFFF, true);
            g.text(font, pct, cx + font.width(cnt), ry, 0xFFA86666, true);

            // progress bar
            int bx0 = lx, bx1 = x + w - PAD, by = ry + 12;
            g.fill(bx0, by, bx1, by + 4, 0xFF2E1416);
            int fillW = Math.round((bx1 - bx0) * frac);
            if (fillW > 0) {
                g.fillGradient(bx0, by, bx0 + fillW, by + 4, lighten(col, 0.35f), col);
                g.fill(bx0, by, bx0 + fillW, by + 1, lighten(col, 0.6f));
            }
            if (c.total > 1 && c.total <= 20) {
                for (int i = 1; i < c.total; i++) {
                    int tx = bx0 + (bx1 - bx0) * i / c.total;
                    g.fill(tx, by, tx + 1, by + 4, 0x80190B0C);
                }
            }

            String st = ClarityHud.status(c, preview);
            int stCol;
            if (st.startsWith("●")) {
                float pulse = (float) (0.5 + 0.5 * Math.sin(now / 160.0));
                stCol = mix(0xFFFF4D4D, 0xFFFFB0B0, pulse);
            } else {
                stCol = 0xFFA86666;
            }
            g.text(font, st, lx, ry + 19, stCol, true);
            String r = rate(c);
            g.text(font, r, x + w - PAD - font.width(r), ry + 19, 0xFFBF7878, true);
            ry += ROW;
        }

        if (footer) {
            int fy = y + h - FOOTER;
            g.fill(x + PAD, fy, x + w - PAD, fy + 1, 0x30FF7070);
            g.text(font, "Σ " + done + "/" + total + " bosses", x + PAD, fy + 4, 0xFFDD8282, true);
            if (coins > 0) {
                String s = "◆ " + HudSum.fmtCoins(coins);
                g.text(font, s, x + w - PAD - font.width(s), fy + 4, 0xFFE46B6B, true);
            }
        }
        return new int[]{x, y, w, h};
    }

    static int lighten(int c, float f) {
        return mix(c, 0xFFFFFFFF, f);
    }

    static int mix(int a, int b, float f) {
        int aa = a >>> 24;
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * f);
        int gg = Math.round(ag + (bg - ag) * f);
        int bl = Math.round(ab + (bb - ab) * f);
        return aa << 24 | r << 16 | gg << 8 | bl;
    }
}
