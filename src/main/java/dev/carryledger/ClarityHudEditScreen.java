package dev.carryledger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Drag / nudge / resize the carry HUD. Opened with /carry hud edit or from the menu. */
public class ClarityHudEditScreen extends Screen {

    private boolean dragging;
    private int offX;
    private int offY;
    private int[] bounds = {0, 0, 0, 0};
    private List<Data.Client> sample;

    public ClarityHudEditScreen() {
        super(Component.literal("Apocalypse HUD"));
    }

    /** Fake clients shown when there is nothing to track. */
    private List<Data.Client> sample() {
        if (sample != null) return sample;
        long now = System.currentTimeMillis();
        String[][] rows = {
                {"Technoblade", "REVENANT", "5", "10", "4"},
                {"Notch", "VOIDGLOOM", "4", "5", "1"},
                {"jeb_", "INFERNO", "3", "20", "13"},
        };
        List<Data.Client> list = new ArrayList<>();
        for (String[] r : rows) {
            Data.Client c = new Data.Client();
            c.name = r[0];
            c.boss = r[1];
            c.tier = Integer.parseInt(r[2]);
            c.total = Integer.parseInt(r[3]);
            c.done = Integer.parseInt(r[4]);
            c.startedAt = now - 60000L * (c.done + 1);
            c.lastKillAt = now - 30000L;
            if (c.kills == null) c.kills = new ArrayList<>();
            for (int i = 0; i < c.done; i++) c.kills.add(now - 60000L * (c.done - i));
            list.add(c);
        }
        return sample = list;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Data.Config cfg = CarryLedger.data.config;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        float f = ClarityHud.factor();

        g.fill(0, 0, sw, sh, 0x66000000);

        if (dragging) {
            cfg.hudX = Math.max(0, Math.min(sw - 10, mouseX - offX));
            cfg.hudY = Math.max(0, Math.min(sh - 10, mouseY - offY));
        }

        boolean preview = CarryLedger.data.clients.isEmpty();
        List<Data.Client> list = preview ? sample() : CarryLedger.data.clients;

        int[] r;
        Object pose = ClarityHud.push(g, cfg.hudX, cfg.hudY, f);
        try {
            r = CarryHud.draw(g, font, cfg.hudX, cfg.hudY, list, preview);
        } finally {
            ClarityHud.pop(pose);
        }

        if (r != null && r.length >= 4) {
            int x = r[0], y = r[1];
            int w = Math.round(r[2] * f);
            int h = Math.round(r[3] * f);
            bounds = new int[]{x, y, w, h};
            int col = dragging ? 0xFFFFC857 : inside(mouseX, mouseY) ? 0xFFFFFFFF : 0x88FFFFFF;
            g.fill(x - 2, y - 2, x + w + 2, y - 1, col);
            g.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, col);
            g.fill(x - 2, y - 2, x - 1, y + h + 2, col);
            g.fill(x + w + 1, y - 2, x + w + 2, y + h + 2, col);
        }

        String title = "§c§lApocalyp§6§lse §6§lHUD Editor" + (preview ? " §c(example)" : "");
        String line1 = "§cLMB§c — pick up / drop panel   §cArrows§c — move (§cShift§c ×10)";
        String line2 = "§cWheel§c — size (§c" + ClarityHud.scale + "%§c)   §cR§c — reset   §cEsc§c — save";
        int ty = sh - 36;
        g.text(font, title, (sw - font.width(title)) / 2, ty, -1, true);
        g.text(font, line1, (sw - font.width(line1)) / 2, ty + 11, -1, true);
        g.text(font, line2, (sw - font.width(line2)) / 2, ty + 22, -1, true);
    }

    private boolean inside(double mx, double my) {
        return mx >= bounds[0] && mx < bounds[0] + bounds[2]
                && my >= bounds[1] && my < bounds[1] + bounds[3];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) {
            Data.Config cfg = CarryLedger.data.config;
            if (dragging) {
                dragging = false;
                CarryLedger.data.save();
                return true;
            }
            if (inside(e.x(), e.y())) {
                dragging = true;
                offX = (int) e.x() - cfg.hudX;
                offY = (int) e.y() - cfg.hudY;
                return true;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy == 0) return true;
        ClarityHud.scale = ClarityHud.clamp(ClarityHud.scale + (sy > 0 ? 10 : -10));
        ClarityHud.save();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        Data.Config cfg = CarryLedger.data.config;
        int step = (e.modifiers() & 1) != 0 ? 10 : 1; // GLFW_MOD_SHIFT
        switch (e.key()) {
            case 262 -> { // right
                cfg.hudX += step;
                return true;
            }
            case 263 -> { // left
                cfg.hudX = Math.max(0, cfg.hudX - step);
                return true;
            }
            case 264 -> { // down
                cfg.hudY += step;
                return true;
            }
            case 265 -> { // up
                cfg.hudY = Math.max(0, cfg.hudY - step);
                return true;
            }
            case 82 -> { // R
                cfg.hudX = 6;
                cfg.hudY = 40;
                ClarityHud.scale = 100;
                ClarityHud.save();
                CarryLedger.data.save();
                return true;
            }
            default -> {
                return super.keyPressed(e);
            }
        }
    }

    @Override
    public void onClose() {
        dragging = false;
        CarryLedger.data.save();
        ClarityHud.save();
        super.onClose();
    }
}
