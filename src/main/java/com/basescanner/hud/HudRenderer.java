package com.basescanner.hud;

import com.basescanner.config.CategorySettings;
import com.basescanner.config.ConfigManager;
import com.basescanner.config.ModConfig;
import com.basescanner.gui.AreaDetailScreen;
import com.basescanner.gui.SettingsScreen;
import com.basescanner.scan.AreaScanResult;
import com.basescanner.scan.CategoryGroup;
import com.basescanner.scan.ContainerMemory;
import com.basescanner.scan.Navigation;
import com.basescanner.scan.ScanArea;
import com.basescanner.scan.ScanCategory;
import com.basescanner.scan.ScanManager;
import com.basescanner.score.ScoreResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Compact on-screen panel for the focused scan area. */
public final class HudRenderer {
    private static final int PAD = 5;
    private static final int LINE_H = 10;

    private record Line(String left, int color, int indent, String right, int rightColor,
                        int barPercent, int barColor, boolean separator) {
        static Line text(String left, int color, int indent) {
            return new Line(left, color, indent, null, 0, -1, 0, false);
        }

        static Line pair(String left, int color, int indent, String right, int rightColor) {
            return new Line(left, color, indent, right, rightColor, -1, 0, false);
        }

        static Line bar(int percent, int color) {
            return new Line("", 0, 0, null, 0, percent, color, false);
        }

        static Line sep() {
            return new Line("", 0, 0, null, 0, -1, 0, true);
        }
    }

    private HudRenderer() {
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ModConfig cfg = ConfigManager.get();
        if (cfg.interfaceHidden || !cfg.showHud || client.player == null || client.world == null) return;
        if (client.options.hudHidden) return;
        if (client.currentScreen instanceof AreaDetailScreen || client.currentScreen instanceof SettingsScreen) return;

        List<Line> lines = buildLines(client, cfg);
        if (lines.isEmpty()) return;

        TextRenderer tr = client.textRenderer;
        int width = cfg.hudWidth;
        int height = PAD * 2;
        for (Line l : lines) height += l.separator ? 5 : l.barPercent >= 0 ? 6 : LINE_H;

        float scale = cfg.hudScale / 100f;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float pw = width * scale, ph = height * scale;
        float x = switch (cfg.hudAnchor) {
            case TOP_LEFT, BOTTOM_LEFT -> cfg.hudX;
            case TOP_RIGHT, BOTTOM_RIGHT -> sw - pw - cfg.hudX;
        };
        float y = switch (cfg.hudAnchor) {
            case TOP_LEFT, TOP_RIGHT -> cfg.hudY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - ph - cfg.hudY;
        };

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);

        int bgAlpha = cfg.hudOpacity * 255 / 100;
        ctx.fill(0, 0, width, height, (bgAlpha << 24) | 0x10141C);
        ScanArea focused = ScanManager.get().focusedArea(client.player.getPos());
        int accent = focused != null && focused.score != null && cfg.scoringEnabled
                ? focused.score.ratingColor() : 0xFF55FFAA;
        ctx.fill(0, 0, 2, height, accent);

        int cy = PAD;
        for (Line l : lines) {
            if (l.separator) {
                ctx.fill(PAD, cy + 2, width - PAD, cy + 3, 0x30FFFFFF);
                cy += 5;
                continue;
            }
            if (l.barPercent >= 0) {
                ctx.fill(PAD, cy, width - PAD, cy + 3, 0x40FFFFFF);
                int fillW = (int) ((width - 2 * PAD) * (l.barPercent / 100.0));
                ctx.fill(PAD, cy, PAD + fillW, cy + 3, l.barColor);
                cy += 6;
                continue;
            }
            int lx = PAD + 2 + l.indent * 8;
            int rightW = l.right == null ? 0 : tr.getWidth(l.right) + 4;
            String left = tr.trimToWidth(l.left, width - lx - PAD - rightW);
            ctx.drawText(tr, left, lx, cy, l.color, true);
            if (l.right != null) {
                ctx.drawText(tr, l.right, width - PAD - tr.getWidth(l.right), cy, l.rightColor, true);
            }
            cy += LINE_H;
        }
        ctx.getMatrices().pop();
    }

    private static List<Line> buildLines(MinecraftClient client, ModConfig cfg) {
        List<Line> lines = new ArrayList<>();
        ScanManager mgr = ScanManager.get();
        Vec3d p = client.player.getPos();
        ScanArea area = mgr.focusedArea(p);
        int areaCount = mgr.areas().size();

        if (area == null) {
            lines.add(Line.pair("AREA SCANNER", 0xFF55FFAA, 0,
                    cfg.autoScan ? "no storage nearby" : "auto-scan off", 0xFF9E9E9E));
            addNavigation(lines, client);
            return lines;
        }

        String title = "AREA SCANNER — " + area.sizeX() + "×" + area.sizeZ();
        String right = "#" + area.id + (mgr.isPinned() ? " pinned" : "") + (areaCount > 1 ? "  (" + areaCount + ")" : "");
        lines.add(Line.pair(title, 0xFF55FFAA, 0, right, 0xFFB0BEC5));

        if (cfg.hudShowCoords || cfg.hudShowDistance) {
            StringBuilder sb = new StringBuilder();
            if (cfg.hudShowCoords) {
                sb.append((int) Math.floor(area.centerX())).append(", ").append(area.centerY()).append(", ")
                        .append((int) Math.floor(area.centerZ()));
            }
            String dist = null;
            if (cfg.hudShowDistance) {
                double d = area.horizontalDistance(p);
                dist = d < 0.5 ? "inside" : Math.round(d) + "m " + Navigation.arrowTo(client,
                        new Vec3d(area.centerX(), area.centerY(), area.centerZ()));
            }
            lines.add(Line.pair(sb.toString(), 0xFFB0BEC5, 0, dist, 0xFFE0E0E0));
        }

        if (area.blockResult == null) {
            lines.add(Line.text("Scanning… " + Math.round(area.progress() * 100) + "%", 0xFFFFD54F, 0));
            lines.add(Line.bar((int) Math.round(area.progress() * 100), 0xFFFFD54F));
            addNavigation(lines, client);
            return lines;
        }

        // ---- Base likelihood ----
        ScoreResult s = area.score;
        if (cfg.scoringEnabled && s != null) {
            lines.add(Line.sep());
            lines.add(Line.pair("BASE LIKELIHOOD " + s.percent() + "%", s.ratingColor(), 0, s.rating(), s.ratingColor()));
            lines.add(Line.bar(s.percent(), s.ratingColor()));
            lines.add(Line.pair("Confidence", 0xFFB0BEC5, 0, s.confidence(), s.confidenceColor()));
            if (cfg.hudShowEvidence && !cfg.hudCompact) {
                int n = 0;
                for (ScoreResult.Evidence e : s.evidence()) {
                    if (n++ >= cfg.hudMaxEvidence) break;
                    lines.add(Line.text("• " + e.text(), 0xFFD7CCC8, 0));
                }
            }
        }
        if (area.isScanning()) {
            lines.add(Line.text("Refreshing… " + Math.round(area.progress() * 100) + "%", 0xFF9E9E9E, 0));
        }

        // ---- Categories ----
        int grand = 0;
        for (CategoryGroup g : CategoryGroup.values()) {
            List<ScanCategory> cats = new ArrayList<>();
            int groupTotal = 0;
            for (ScanCategory c : ScanCategory.values()) {
                if (c.group != g) continue;
                CategorySettings cs = cfg.get(c);
                if (!cs.enabled || !cs.showInHud) continue;
                int t = area.total(c);
                if (t <= 0) continue;
                cats.add(c);
                groupTotal += t;
            }
            grand += groupTotal;
            if (cats.isEmpty()) continue;
            boolean collapsed = cfg.isCollapsed(g);
            lines.add(Line.sep());
            lines.add(Line.pair((collapsed ? "▶ " : "▼ ") + g.label, g.color, 0, String.valueOf(groupTotal), g.color));
            if (collapsed) continue;
            for (ScanCategory c : cats) {
                int color = 0xFF000000 | cfg.get(c).color;
                lines.add(Line.pair(c.label, 0xFFE0E0E0, 1, String.valueOf(area.total(c)), color));
                if (!cfg.hudCompact) {
                    String breakdown = breakdown(area, c);
                    if (breakdown != null) lines.add(Line.text(breakdown, 0xFF8D99A6, 2));
                }
            }
        }

        // ---- Known container contents ----
        if (cfg.storageSummaries && !cfg.hudCompact) {
            ContainerMemory.Summary sum = ContainerMemory.summarize(area);
            if (sum.knownContainers() > 0) {
                lines.add(Line.sep());
                lines.add(Line.pair("Known contents", 0xFFFFC857, 0,
                        sum.knownContainers() + " opened · " + String.format("%,d", sum.totalItems()) + " items", 0xFFB0BEC5));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, sum.topItems().size()); i++) {
                    ContainerMemory.ItemCount ic = sum.topItems().get(i);
                    if (i > 0) sb.append(" · ");
                    sb.append(ic.name()).append(" ×").append(String.format("%,d", ic.count()));
                }
                lines.add(Line.text(sb.toString(), 0xFF8D99A6, 1));
            }
        }

        lines.add(Line.sep());
        lines.add(Line.pair("Objects found", 0xFF9E9E9E, 0, String.format("%,d", grand), 0xFFE0E0E0));
        addNavigation(lines, client);
        return lines;
    }

    private static String breakdown(ScanArea area, ScanCategory c) {
        AreaScanResult r = area.resultFor(c);
        if (r == null) return null;
        AreaScanResult.CategoryResult cr = r.get(c);
        if (cr == null || cr.types.isEmpty()) return null;
        List<AreaScanResult.TypeEntry> types = cr.sortedTypes();
        if (types.size() == 1 && c != ScanCategory.DROPPED_ITEMS && types.get(0).name.equals(c.label)) return null;
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (AreaScanResult.TypeEntry te : types) {
            if (shown > 0) sb.append(" · ");
            long n = c == ScanCategory.DROPPED_ITEMS ? te.amount : te.count;
            sb.append(te.name).append(" ×").append(n);
            if (++shown >= 4) break;
        }
        if (types.size() > shown) sb.append(" · +").append(types.size() - shown);
        return sb.toString();
    }

    private static void addNavigation(List<Line> lines, MinecraftClient client) {
        if (!Navigation.active()) return;
        Vec3d t = Navigation.target();
        double d = client.player.getPos().distanceTo(t);
        lines.add(Line.sep());
        lines.add(Line.pair("Target: " + Navigation.label(), 0xFFFFD54F, 0,
                Math.round(d) + "m " + Navigation.arrowTo(client, t), 0xFFFFD54F));
    }
}
