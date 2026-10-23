package com.basescanner.gui;

import com.basescanner.config.ConfigManager;
import com.basescanner.config.ModConfig;
import com.basescanner.scan.AreaScanResult;
import com.basescanner.scan.CategoryGroup;
import com.basescanner.scan.ContainerMemory;
import com.basescanner.scan.Navigation;
import com.basescanner.scan.ScanArea;
import com.basescanner.scan.ScanCategory;
import com.basescanner.scan.ScanManager;
import com.basescanner.score.ScoreResult;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Full report for one scan area: score, evidence and confidence on the left; an
 * expandable list of everything found on the right. Click a coordinate to set it
 * as the navigation target; click a category to expand it; shift-click a category
 * to toggle its ESP outline.
 */
public class AreaDetailScreen extends Screen {
    private static final int ROW_H = 11;
    private static final Set<ScanCategory> EXPANDED = EnumSet.noneOf(ScanCategory.class);
    private static final int MAX_POSITIONS_SHOWN = 12;

    private enum Kind { GROUP, CATEGORY, TYPE, POSITION, INFO }

    private record Row(Kind kind, int indent, String text, int color, String right, int rightColor,
                       Object data, BlockPos pos, int entityId) {
    }

    private final Screen parent;
    private int areaId;
    private final List<Row> rows = new ArrayList<>();
    private int listScroll;
    private int leftScroll;
    private int leftContentH;

    private int listX, listY, listW, listH;
    private int leftX, leftY, leftW, leftH;

    public AreaDetailScreen(Screen parent, int areaId) {
        super(Text.literal("Area Scanner"));
        this.parent = parent;
        this.areaId = areaId;
    }

    private ScanArea area() {
        return areaId < 0 ? null : ScanManager.get().byId(areaId);
    }

    @Override
    protected void init() {
        leftX = 10;
        leftY = 28;
        leftW = Math.max(170, Math.min(240, (int) (this.width * 0.38)));
        leftH = this.height - 28 - 34;
        listX = leftX + leftW + 12;
        listY = 28;
        listW = this.width - listX - 10;
        listH = this.height - 28 - 34;

        int bw = Math.min(96, (this.width - 20) / 6 - 4);
        int total = bw * 6 + 4 * 5;
        int bx = (this.width - total) / 2;
        int by = this.height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("◀ Prev area"), b -> step(-1))
                .dimensions(bx, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next area ▶"), b -> step(1))
                .dimensions(bx + (bw + 4), by, bw, 20).build());
        ScanManager mgr = ScanManager.get();
        addDrawableChild(ButtonWidget.builder(Text.literal(mgr.isPinned() ? "Unpin HUD" : "Pin to HUD"), b -> {
            if (mgr.isPinned()) mgr.pin(-1);
            else if (area() != null) mgr.pin(areaId);
            clearAndInit();
        }).dimensions(bx + 2 * (bw + 4), by, bw, 20).build());
        ButtonWidget clear = ButtonWidget.builder(Text.literal("Clear target"), b -> {
            Navigation.clear();
            clearAndInit();
        }).dimensions(bx + 3 * (bw + 4), by, bw, 20).build();
        clear.active = Navigation.active();
        addDrawableChild(clear);
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), b -> this.client.setScreen(new SettingsScreen(this)))
                .dimensions(bx + 4 * (bw + 4), by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(bx + 5 * (bw + 4), by, bw, 20).build());
    }

    private void step(int dir) {
        if (this.client.player == null) return;
        List<ScanArea> sorted = ScanManager.get().sortedByDistance(this.client.player.getPos());
        if (sorted.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < sorted.size(); i++) if (sorted.get(i).id == areaId) idx = i;
        idx = Math.floorMod(idx + dir, sorted.size());
        areaId = sorted.get(idx).id;
        listScroll = 0;
        leftScroll = 0;
    }

    // ------------------------------------------------------------------ list model

    private void rebuildRows(ScanArea area, ModConfig cfg) {
        rows.clear();
        Vec3d p = this.client.player.getPos();
        for (CategoryGroup g : CategoryGroup.values()) {
            int groupTotal = 0;
            List<ScanCategory> cats = new ArrayList<>();
            for (ScanCategory c : ScanCategory.values()) {
                if (c.group != g || !cfg.get(c).enabled) continue;
                int t = area.total(c);
                if (t > 0) {
                    cats.add(c);
                    groupTotal += t;
                }
            }
            boolean collapsed = cfg.isCollapsed(g);
            rows.add(new Row(Kind.GROUP, 0, (collapsed ? "▶ " : "▼ ") + g.label.toUpperCase(), g.color,
                    String.valueOf(groupTotal), g.color, g, null, -1));
            if (collapsed) continue;
            if (cats.isEmpty()) {
                rows.add(new Row(Kind.INFO, 1, "nothing detected", 0xFF757575, null, 0, null, null, -1));
                continue;
            }
            for (ScanCategory c : cats) {
                boolean expanded = EXPANDED.contains(c);
                int catColor = 0xFF000000 | cfg.get(c).color;
                String esp = cfg.get(c).esp ? "  [ESP]" : "";
                rows.add(new Row(Kind.CATEGORY, 1, (expanded ? "− " : "+ ") + c.label + esp, 0xFFFFFFFF,
                        String.valueOf(area.total(c)), catColor, c, null, -1));
                AreaScanResult r = area.resultFor(c);
                if (r == null || r.get(c) == null) continue;
                for (AreaScanResult.TypeEntry te : r.get(c).sortedTypes()) {
                    String count = c == ScanCategory.DROPPED_ITEMS
                            ? te.amount + " items (" + te.count + ")" : "×" + te.count;
                    rows.add(new Row(Kind.TYPE, 2, te.name, 0xFFCFD8DC, count, 0xFFB0BEC5, null, null, -1));
                    if (!expanded) continue;
                    addPositions(te, c, catColor, p, cfg);
                }
                if (expanded && c == ScanCategory.SIGNS && area.blockResult != null) {
                    for (AreaScanResult.SignInfo si : area.blockResult.signs) {
                        String text = "“" + String.join(" / ", si.lines()) + "”";
                        rows.add(new Row(Kind.POSITION, 3, text, 0xFFFFF59D, fmt(si.pos()), 0xFF9E9E9E,
                                "Sign", si.pos(), -1));
                    }
                }
            }
        }
    }

    private void addPositions(AreaScanResult.TypeEntry te, ScanCategory c, int color, Vec3d p, ModConfig cfg) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < te.positions.size(); i++) order.add(i);
        order.sort((a, b) -> Double.compare(dist(te.positions.get(a), p), dist(te.positions.get(b), p)));
        int shown = 0;
        for (int i : order) {
            if (shown++ >= MAX_POSITIONS_SHOWN) {
                rows.add(new Row(Kind.INFO, 3, "+" + (te.count - MAX_POSITIONS_SHOWN) + " more", 0xFF757575,
                        null, 0, null, null, -1));
                break;
            }
            BlockPos pos = te.positions.get(i);
            int entityId = c.entity && i < te.entityIds.size() ? te.entityIds.get(i) : -1;
            rows.add(new Row(Kind.POSITION, 3, "→ " + fmt(pos), color,
                    Math.round(dist(pos, p)) + "m", 0xFF9E9E9E, te.name, pos, entityId));
            if (cfg.storageSummaries && c.isStorage()) {
                ContainerMemory.Snapshot snap = ContainerMemory.get(pos);
                if (snap != null) {
                    StringBuilder sb = new StringBuilder("seen: ");
                    if (snap.items().isEmpty()) sb.append("empty");
                    for (int k = 0; k < Math.min(4, snap.items().size()); k++) {
                        if (k > 0) sb.append(", ");
                        sb.append(snap.items().get(k).name()).append(" ×").append(snap.items().get(k).count());
                    }
                    rows.add(new Row(Kind.INFO, 4, sb.toString(), 0xFFFFC857,
                            snap.usedSlots() + "/" + snap.totalSlots(), 0xFF9E9E9E, null, null, -1));
                }
            }
        }
    }

    private static double dist(BlockPos pos, Vec3d p) {
        double dx = pos.getX() + 0.5 - p.x, dy = pos.getY() + 0.5 - p.y, dz = pos.getZ() + 0.5 - p.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String fmt(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ModConfig cfg = ConfigManager.get();
        ScanArea area = area();
        if (area == null && this.client.player != null) {
            ScanArea f = ScanManager.get().focusedArea(this.client.player.getPos());
            if (f != null) {
                areaId = f.id;
                area = f;
            }
        }

        String header = area == null ? "AREA SCANNER" : "AREA SCANNER — " + area.sizeX() + "×" + area.sizeZ()
                + "   ·   Area #" + area.id + " of " + ScanManager.get().areas().size();
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(header), this.width / 2, 10, 0xFF55FFAA);

        if (area == null || this.client.player == null) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("No scan areas yet. Explore near storage blocks (chests, barrels, shulkers…)."),
                    this.width / 2, this.height / 2 - 10, 0xFFB0BEC5);
            return;
        }

        drawLeft(ctx, area, cfg);
        rebuildRows(area, cfg);
        drawList(ctx, mouseX, mouseY);
    }

    private void drawLeft(DrawContext ctx, ScanArea area, ModConfig cfg) {
        ctx.fill(leftX - 4, leftY - 4, leftX + leftW + 4, leftY + leftH + 4, 0x90101418);
        ctx.enableScissor(leftX - 4, leftY - 4, leftX + leftW + 4, leftY + leftH + 4);
        int y = leftY - leftScroll;
        int x = leftX;
        Vec3d p = this.client.player.getPos();

        y = text(ctx, "Center " + (int) Math.floor(area.centerX()) + ", " + area.centerY() + ", "
                + (int) Math.floor(area.centerZ()), x, y, 0xFFE0E0E0);
        double d = area.horizontalDistance(p);
        y = text(ctx, (d < 0.5 ? "You are inside this area" : Math.round(d) + " m away "
                + Navigation.arrowTo(this.client, new Vec3d(area.centerX(), area.centerY(), area.centerZ()))), x, y, 0xFFB0BEC5);
        y = text(ctx, "Bounds X " + area.minX + ".." + area.maxX + "  Z " + area.minZ + ".." + area.maxZ, x, y, 0xFF9E9E9E);
        y = text(ctx, "Y " + area.minY + ".." + area.maxY + "  ·  " + area.anchors.size() + " storage anchor"
                + (area.anchors.size() == 1 ? "" : "s"), x, y, 0xFF9E9E9E);
        y += 6;

        if (area.blockResult == null) {
            y = text(ctx, "Scanning… " + Math.round(area.progress() * 100) + "%", x, y, 0xFFFFD54F);
        } else if (cfg.scoringEnabled && area.score != null) {
            ScoreResult s = area.score;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            ctx.getMatrices().scale(1.6f, 1.6f, 1f);
            ctx.drawText(this.textRenderer, "BASE LIKELIHOOD: " + s.percent() + "%", 0, 0, s.ratingColor(), true);
            ctx.getMatrices().pop();
            y += 16;
            y = text(ctx, "Rating: " + s.rating(), x, y, s.ratingColor());
            ctx.fill(x, y, x + leftW, y + 4, 0x40FFFFFF);
            ctx.fill(x, y, x + (int) (leftW * s.percent() / 100.0), y + 4, s.ratingColor());
            y += 9;
            y = wrapped(ctx, "Confidence: " + s.confidence() + " — " + s.confidenceText() + ".", x, y, s.confidenceColor());
            y += 4;
            y = text(ctx, "Evidence", x, y, 0xFF55FFAA);
            if (s.evidence().isEmpty()) y = text(ctx, "  no base indicators found", x, y, 0xFF9E9E9E);
            for (ScoreResult.Evidence e : s.evidence()) {
                y = wrapped(ctx, "• " + e.text() + String.format(" (+%.1f)", e.points()), x, y, 0xFFD7CCC8);
            }
            y += 3;
            y = wrapped(ctx, "Heuristic estimate from visible evidence — not a verified probability. "
                    + "Raw " + String.format("%.1f", s.rawPoints()) + " pts, " + s.indicators() + " strong indicators.",
                    x, y, 0xFF757575);
        } else if (!cfg.scoringEnabled) {
            y = text(ctx, "Base-likelihood scoring is turned off.", x, y, 0xFF9E9E9E);
        }

        if (area.blockResult != null) {
            y += 6;
            AreaScanResult r = area.blockResult;
            y = text(ctx, "Examined " + r.loadedColumns + "/" + r.totalColumns + " columns, "
                    + String.format("%,d", r.blocksExamined) + " blocks", x, y, 0xFF9E9E9E);
            if (area.isScanning()) y = text(ctx, "Refreshing… " + Math.round(area.progress() * 100) + "%", x, y, 0xFF9E9E9E);
        }

        if (cfg.storageSummaries) {
            ContainerMemory.Summary sum = ContainerMemory.summarize(area);
            y += 6;
            y = text(ctx, "Known contents", x, y, 0xFFFFC857);
            if (sum.knownContainers() == 0) {
                y = wrapped(ctx, "Open containers here to record what they hold.", x, y, 0xFF757575);
            } else {
                y = text(ctx, sum.knownContainers() + " opened · " + String.format("%,d", sum.totalItems()) + " items",
                        x, y, 0xFFB0BEC5);
                for (ContainerMemory.ItemCount ic : sum.topItems()) {
                    y = text(ctx, "  " + ic.name() + " ×" + String.format("%,d", ic.count()), x, y, 0xFFCFD8DC);
                }
            }
        }

        if (Navigation.active()) {
            y += 6;
            Vec3d t = Navigation.target();
            y = text(ctx, "Target: " + Navigation.label() + " — " + Math.round(p.distanceTo(t)) + " m "
                    + Navigation.arrowTo(this.client, t), x, y, 0xFFFFD54F);
        }
        leftContentH = y + leftScroll - leftY;
        ctx.disableScissor();
    }

    private int text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(s, leftW), x, y, color, true);
        return y + ROW_H;
    }

    private int wrapped(DrawContext ctx, String s, int x, int y, int color) {
        for (OrderedText line : this.textRenderer.wrapLines(Text.literal(s), leftW)) {
            ctx.drawText(this.textRenderer, line, x, y, color, true);
            y += ROW_H - 1;
        }
        return y;
    }

    private void drawList(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(listX - 4, listY - 4, listX + listW + 4, listY + listH + 4, 0x90101418);
        int contentH = rows.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - listH);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        ctx.enableScissor(listX - 4, listY - 4, listX + listW + 4, listY + listH + 4);
        int y = listY - listScroll;
        for (Row row : rows) {
            if (y + ROW_H >= listY - ROW_H && y <= listY + listH) {
                boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW_H
                        && mouseY >= listY && mouseY < listY + listH;
                boolean clickable = row.kind == Kind.GROUP || row.kind == Kind.CATEGORY || row.kind == Kind.POSITION;
                if (hover && clickable) ctx.fill(listX - 2, y - 1, listX + listW + 2, y + ROW_H - 1, 0x30FFFFFF);
                if (row.kind == Kind.GROUP) ctx.fill(listX - 2, y + ROW_H - 2, listX + listW + 2, y + ROW_H - 1, 0x40FFFFFF);
                int tx = listX + row.indent * 9;
                int rightW = row.right == null ? 0 : this.textRenderer.getWidth(row.right) + 6;
                String text = this.textRenderer.trimToWidth(row.text, listW - (tx - listX) - rightW);
                ctx.drawText(this.textRenderer, text, tx, y, row.color, true);
                if (row.right != null) {
                    ctx.drawText(this.textRenderer, row.right, listX + listW - this.textRenderer.getWidth(row.right), y,
                            row.rightColor, true);
                }
            }
            y += ROW_H;
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int thumbH = Math.max(12, listH * listH / (listH + maxScroll));
            int thumbY = listY + (int) ((listH - thumbH) * (listScroll / (double) maxScroll));
            ctx.fill(listX + listW + 5, listY, listX + listW + 7, listY + listH, 0x30FFFFFF);
            ctx.fill(listX + listW + 5, thumbY, listX + listW + 7, thumbY + thumbH, 0xA0FFFFFF);
        }
        ctx.drawText(this.textRenderer, "Click: expand / navigate · Shift-click category: toggle ESP",
                listX, listY + listH + 6 - 2, 0xFF616161, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY || mouseY >= listY + listH) return false;
        int idx = (int) ((mouseY - listY + listScroll) / ROW_H);
        if (idx < 0 || idx >= rows.size()) return false;
        Row row = rows.get(idx);
        ModConfig cfg = ConfigManager.get();
        switch (row.kind) {
            case GROUP -> {
                CategoryGroup g = (CategoryGroup) row.data;
                cfg.setCollapsed(g, !cfg.isCollapsed(g));
                ConfigManager.save();
                return true;
            }
            case CATEGORY -> {
                ScanCategory c = (ScanCategory) row.data;
                if (hasShiftDown()) {
                    cfg.get(c).esp = !cfg.get(c).esp;
                    ConfigManager.save();
                } else if (!EXPANDED.remove(c)) {
                    EXPANDED.add(c);
                }
                return true;
            }
            case POSITION -> {
                String name = row.data instanceof String s ? s : "object";
                int color = 0xFF000000 | (row.color & 0xFFFFFF);
                if (row.entityId >= 0) Navigation.setEntity(row.entityId, row.pos, name, color);
                else Navigation.setBlock(row.pos, name, color);
                if (this.client.player != null) {
                    this.client.player.sendMessage(Text.literal("Navigating to " + name + " at " + fmt(row.pos)), true);
                }
                close();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int delta = (int) Math.round(verticalAmount * ROW_H * 3);
        if (mouseX >= listX) {
            listScroll = Math.max(0, listScroll - delta);
        } else {
            int max = Math.max(0, leftContentH - leftH);
            leftScroll = Math.max(0, Math.min(max, leftScroll - delta));
        }
        return true;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
