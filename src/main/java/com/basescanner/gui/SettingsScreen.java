package com.basescanner.gui;

import com.basescanner.config.CategorySettings;
import com.basescanner.config.ConfigManager;
import com.basescanner.config.ModConfig;
import com.basescanner.config.ScoreWeights;
import com.basescanner.scan.CategoryGroup;
import com.basescanner.scan.ScanCategory;
import com.basescanner.scan.ScanManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Tabbed, scrollable settings screen. Every change is written to config/basescanner.json on close. */
public class SettingsScreen extends Screen {

    private enum Page {
        GENERAL("General"), SCANNING("Scanning"), VISUALS("Outlines"), CATEGORIES("Categories"),
        HUD("HUD"), SCORING("Scoring"), ALERTS("Alerts");

        final String label;

        Page(String label) {
            this.label = label;
        }
    }

    @FunctionalInterface
    private interface RowBuilder {
        void build(int x, int y, int w);
    }

    private record Row(String label, String tooltip, RowBuilder builder) {
    }

    private record Label(String text, int x, int y, int color) {
    }

    private record Swatch(int x, int y, int w, int h, IntSupplier color) {
    }

    private static final int ROW_H = 24;
    private static Page page = Page.GENERAL;

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private int top;
    private int bottom;
    private boolean geometryChanged;
    private String pendingTooltip;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Base Scanner — Settings"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        labels.clear();
        swatches.clear();

        Page[] pages = Page.values();
        int tabW = Math.min(86, (this.width - 20) / pages.length);
        int tx = (this.width - tabW * pages.length) / 2;
        for (int i = 0; i < pages.length; i++) {
            Page p = pages[i];
            ButtonWidget b = ButtonWidget.builder(Text.literal(p.label), btn -> {
                page = p;
                scroll = 0;
                clearAndInit();
            }).dimensions(tx + i * tabW, 22, tabW - 2, 20).build();
            b.active = p != page;
            addDrawableChild(b);
        }

        top = 50;
        bottom = this.height - 32;
        List<Row> rows = rows(page);
        maxScroll = Math.max(0, rows.size() * ROW_H - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        boolean wide = page == Page.CATEGORIES;
        int labelX = this.width / 2 - (wide ? 200 : 180);
        int widgetX = this.width / 2 + (wide ? -20 : 5);
        int widgetW = wide ? 220 : 175;

        if (wide) {
            int y = top - 11;
            labels.add(new Label("Scan", widgetX + 6, y, 0xFFB0BEC5));
            labels.add(new Label("HUD", widgetX + 50, y, 0xFFB0BEC5));
            labels.add(new Label("ESP", widgetX + 94, y, 0xFFB0BEC5));
            labels.add(new Label("Color", widgetX + 136, y, 0xFFB0BEC5));
        }

        for (int i = 0; i < rows.size(); i++) {
            int y = top + i * ROW_H - scroll;
            if (y < top || y + 20 > bottom) continue;
            Row row = rows.get(i);
            if (row.label != null && !row.label.isEmpty()) {
                int color = row.builder == null ? 0xFF55FFAA : 0xFFFFFFFF;
                labels.add(new Label(row.label, labelX, y + 6, color));
            }
            if (row.builder != null) {
                pendingTooltip = row.tooltip;
                row.builder.build(widgetX, y, widgetW);
                pendingTooltip = null;
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(this.width / 2 - 100, this.height - 26, 200, 20).build());
    }

    private <T extends ClickableWidget> T add(T widget) {
        if (pendingTooltip != null) widget.setTooltip(Tooltip.of(Text.literal(pendingTooltip)));
        return addDrawableChild(widget);
    }

    // ------------------------------------------------------------------ widget helpers

    private interface BoolSetter {
        void set(boolean v);
    }

    private void toggle(int x, int y, int w, boolean value, BoolSetter setter) {
        add(CyclingButtonWidget.onOffBuilder(value).omitKeyText()
                .build(x, y, w, 20, Text.empty(), (button, v) -> setter.set(v)));
    }

    private void slider(int x, int y, int w, int min, int max, int step, int value, IntFunction<String> fmt,
                        IntConsumer setter) {
        add(new IntSlider(x, y, w, 20, "", min, max, step, value, fmt, setter));
    }

    private void color(int x, int y, int w, int value, IntConsumer setter, IntSupplier current) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x + 1, y + 1, w - 26, 18, Text.literal("Hex color"));
        field.setMaxLength(6);
        field.setText(String.format(Locale.ROOT, "%06X", value & 0xFFFFFF));
        field.setChangedListener(s -> {
            if (s.matches("[0-9a-fA-F]{6}")) setter.accept(Integer.parseInt(s, 16));
        });
        add(field);
        swatches.add(new Swatch(x + w - 22, y + 1, 20, 18, current));
    }

    private void button(int x, int y, int w, String text, Runnable action) {
        add(ButtonWidget.builder(Text.literal(text), b -> action.run()).dimensions(x, y, w, 20).build());
    }

    private static String pct255(int v) {
        return Math.round(v * 100 / 255.0) + "%";
    }

    // ------------------------------------------------------------------ pages

    private List<Row> rows(Page p) {
        ModConfig c = ConfigManager.get();
        List<Row> rows = new ArrayList<>();
        switch (p) {
            case GENERAL -> {
                rows.add(new Row("Automatic scanning", "Detect storage and refresh areas automatically as you explore.",
                        (x, y, w) -> toggle(x, y, w, c.autoScan, v -> c.autoScan = v)));
                rows.add(new Row("Hide entire interface", "Hides HUD and outlines. The scanner keeps running.",
                        (x, y, w) -> toggle(x, y, w, c.interfaceHidden, v -> c.interfaceHidden = v)));
                rows.add(new Row("Show area outline", null,
                        (x, y, w) -> toggle(x, y, w, c.showOutline, v -> c.showOutline = v)));
                rows.add(new Row("Show information HUD", null,
                        (x, y, w) -> toggle(x, y, w, c.showHud, v -> c.showHud = v)));
                rows.add(new Row("Base-likelihood scoring", null,
                        (x, y, w) -> toggle(x, y, w, c.scoringEnabled, v -> c.scoringEnabled = v)));
                rows.add(new Row("Storage content summaries",
                        "Summarise items in containers you have opened (the server sends contents only when opened).",
                        (x, y, w) -> toggle(x, y, w, c.storageSummaries, v -> c.storageSummaries = v)));
                rows.add(new Row("Merge overlapping areas", "Combine nearby storage into one area instead of many overlays.",
                        (x, y, w) -> toggle(x, y, w, c.mergeOverlapping, v -> {
                            c.mergeOverlapping = v;
                            geometryChanged = true;
                        })));
                rows.add(new Row("Open details on storage look", "Open the area detail screen when you look at a storage block.",
                        (x, y, w) -> toggle(x, y, w, c.autoOpenOnSelect, v -> c.autoOpenOnSelect = v)));
                rows.add(new Row("  …only while sneaking", null,
                        (x, y, w) -> toggle(x, y, w, c.autoOpenRequiresSneak, v -> c.autoOpenRequiresSneak = v)));
                rows.add(new Row("Reset all settings", "Restore every setting to its default value.",
                        (x, y, w) -> button(x, y, w, "Reset to defaults…", this::confirmReset)));
            }
            case SCANNING -> {
                rows.add(new Row("Scan area size", "Width and length of the area around each storage block.",
                        (x, y, w) -> slider(x, y, w, 4, 64, 2, c.areaSize, v -> v + "×" + v, v -> {
                            c.areaSize = v;
                            geometryChanged = true;
                        })));
                rows.add(new Row("Vertical scan mode", "Limited: storage Y ± range. Full height: whole column.",
                        (x, y, w) -> add(CyclingButtonWidget.<ModConfig.VerticalMode>builder(m -> Text.literal(m.label))
                                .values(ModConfig.VerticalMode.values()).initially(c.verticalMode).omitKeyText()
                                .build(x, y, w, 20, Text.empty(), (b, v) -> {
                                    c.verticalMode = v;
                                    geometryChanged = true;
                                }))));
                rows.add(new Row("Vertical range (±)", "Only used in Limited mode.",
                        (x, y, w) -> slider(x, y, w, 1, 160, 1, c.verticalRange, v -> "±" + v + " blocks", v -> {
                            c.verticalRange = v;
                            geometryChanged = true;
                        })));
                rows.add(new Row("Refresh interval", "How often areas are rediscovered and rescanned.",
                        (x, y, w) -> slider(x, y, w, 10, 600, 10, c.refreshIntervalTicks,
                                v -> String.format(Locale.ROOT, "%.1f s", v / 20.0), v -> c.refreshIntervalTicks = v)));
                rows.add(new Row("Max scan distance", "Storage farther than this (horizontally) does not create areas.",
                        (x, y, w) -> slider(x, y, w, 16, 320, 8, c.maxScanDistance, v -> v + " blocks", v -> {
                            c.maxScanDistance = v;
                            geometryChanged = true;
                        })));
                rows.add(new Row("Max merged area span", "Merged areas never grow larger than this.",
                        (x, y, w) -> slider(x, y, w, 20, 256, 4, c.maxMergedSpan, v -> v + " blocks", v -> {
                            c.maxMergedSpan = v;
                            geometryChanged = true;
                        })));
                rows.add(new Row("Max tracked areas", null,
                        (x, y, w) -> slider(x, y, w, 1, 64, 1, c.maxAreas, String::valueOf, v -> {
                            c.maxAreas = v;
                            geometryChanged = true;
                        })));
                rows.add(new Row("Blocks per tick", "Scan budget. Lower it if you notice FPS drops.",
                        (x, y, w) -> slider(x, y, w, 2000, 250000, 2000, c.blocksPerTick,
                                v -> String.format(Locale.ROOT, "%,d", v), v -> c.blocksPerTick = v)));
                rows.add(new Row("Rescan everything now", null,
                        (x, y, w) -> button(x, y, w, "Rescan now", () -> ScanManager.get().rescanNow())));
            }
            case VISUALS -> {
                rows.add(new Row("Area outline color", "Hex RGB, e.g. 55FFAA",
                        (x, y, w) -> color(x, y, w, c.outlineColor, v -> c.outlineColor = v, () -> c.outlineColor)));
                rows.add(new Row("Focused area color", null,
                        (x, y, w) -> color(x, y, w, c.focusedOutlineColor, v -> c.focusedOutlineColor = v,
                                () -> c.focusedOutlineColor)));
                rows.add(new Row("Outline opacity", null,
                        (x, y, w) -> slider(x, y, w, 10, 255, 5, c.outlineAlpha, SettingsScreen::pct255,
                                v -> c.outlineAlpha = v)));
                rows.add(new Row("Outline thickness", null,
                        (x, y, w) -> slider(x, y, w, 1, 10, 1, c.outlineWidth, v -> v + " px", v -> c.outlineWidth = v)));
                rows.add(new Row("Outline visible through walls", null,
                        (x, y, w) -> toggle(x, y, w, c.outlineSeeThrough, v -> c.outlineSeeThrough = v)));
                rows.add(new Row("Block ESP outlines", "Outline detected blocks in categories with ESP on.",
                        (x, y, w) -> toggle(x, y, w, c.espEnabled, v -> c.espEnabled = v)));
                rows.add(new Row("Entity highlighting", "Outline detected entities in categories with ESP on.",
                        (x, y, w) -> toggle(x, y, w, c.entityHighlight, v -> c.entityHighlight = v)));
                rows.add(new Row("ESP in every area", "Off: only the focused area is outlined.",
                        (x, y, w) -> toggle(x, y, w, c.espAllAreas, v -> c.espAllAreas = v)));
                rows.add(new Row("ESP visible through walls", null,
                        (x, y, w) -> toggle(x, y, w, c.espSeeThrough, v -> c.espSeeThrough = v)));
                rows.add(new Row("ESP thickness", null,
                        (x, y, w) -> slider(x, y, w, 1, 10, 1, c.espWidth, v -> v + " px", v -> c.espWidth = v)));
                rows.add(new Row("ESP opacity", null,
                        (x, y, w) -> slider(x, y, w, 10, 255, 5, c.espAlpha, SettingsScreen::pct255, v -> c.espAlpha = v)));
                rows.add(new Row("Max ESP boxes", "Caps outlines per frame to protect FPS.",
                        (x, y, w) -> slider(x, y, w, 16, 4000, 16, c.maxEspBoxes, String::valueOf, v -> c.maxEspBoxes = v)));
            }
            case CATEGORIES -> {
                CategoryGroup current = null;
                for (ScanCategory cat : ScanCategory.values()) {
                    if (cat.group != current) {
                        current = cat.group;
                        rows.add(new Row(current.label, null, null));
                    }
                    CategorySettings s = c.get(cat);
                    rows.add(new Row(cat.label, null, (x, y, w) -> {
                        toggle(x, y, 42, s.enabled, v -> {
                            s.enabled = v;
                            geometryChanged = true;
                        });
                        toggle(x + 44, y, 42, s.showInHud, v -> s.showInHud = v);
                        toggle(x + 88, y, 42, s.esp, v -> s.esp = v);
                        color(x + 132, y, w - 132, s.color, v -> s.color = v, () -> s.color);
                    }));
                }
                rows.add(new Row("", null, null));
                rows.add(new Row("Category defaults", null,
                        (x, y, w) -> button(x, y, w, "Restore category defaults", () -> {
                            for (ScanCategory cat : ScanCategory.values()) {
                                c.categories.put(cat.name(), CategorySettings.defaults(cat));
                            }
                            c.normalize();
                            geometryChanged = true;
                            clearAndInit();
                        })));
            }
            case HUD -> {
                rows.add(new Row("HUD position", null,
                        (x, y, w) -> add(CyclingButtonWidget.<ModConfig.HudAnchor>builder(a -> Text.literal(a.label))
                                .values(ModConfig.HudAnchor.values()).initially(c.hudAnchor).omitKeyText()
                                .build(x, y, w, 20, Text.empty(), (b, v) -> c.hudAnchor = v))));
                rows.add(new Row("Horizontal offset", null,
                        (x, y, w) -> slider(x, y, w, 0, 400, 1, c.hudX, v -> v + " px", v -> c.hudX = v)));
                rows.add(new Row("Vertical offset", null,
                        (x, y, w) -> slider(x, y, w, 0, 300, 1, c.hudY, v -> v + " px", v -> c.hudY = v)));
                rows.add(new Row("HUD scale", null,
                        (x, y, w) -> slider(x, y, w, 50, 200, 5, c.hudScale, v -> v + "%", v -> c.hudScale = v)));
                rows.add(new Row("Background opacity", null,
                        (x, y, w) -> slider(x, y, w, 0, 100, 5, c.hudOpacity, v -> v + "%", v -> c.hudOpacity = v)));
                rows.add(new Row("Panel width", null,
                        (x, y, w) -> slider(x, y, w, 140, 400, 5, c.hudWidth, v -> v + " px", v -> c.hudWidth = v)));
                rows.add(new Row("Compact mode", "Only category totals; no breakdowns or evidence.",
                        (x, y, w) -> toggle(x, y, w, c.hudCompact, v -> c.hudCompact = v)));
                rows.add(new Row("Show evidence", null,
                        (x, y, w) -> toggle(x, y, w, c.hudShowEvidence, v -> c.hudShowEvidence = v)));
                rows.add(new Row("Evidence lines", null,
                        (x, y, w) -> slider(x, y, w, 0, 12, 1, c.hudMaxEvidence, String::valueOf, v -> c.hudMaxEvidence = v)));
                rows.add(new Row("Show coordinates", null,
                        (x, y, w) -> toggle(x, y, w, c.hudShowCoords, v -> c.hudShowCoords = v)));
                rows.add(new Row("Show distance & direction", null,
                        (x, y, w) -> toggle(x, y, w, c.hudShowDistance, v -> c.hudShowDistance = v)));
                rows.add(new Row("Expanded sections", null, null));
                for (CategoryGroup g : CategoryGroup.values()) {
                    rows.add(new Row("  " + g.label, "Collapsed sections show only their total.",
                            (x, y, w) -> toggle(x, y, w, !c.isCollapsed(g), v -> c.setCollapsed(g, !v))));
                }
                rows.add(new Row("Which categories appear", null,
                        (x, y, w) -> button(x, y, w, "Edit in Categories tab (HUD column)", () -> {
                            page = Page.CATEGORIES;
                            scroll = 0;
                            clearAndInit();
                        })));
            }
            case SCORING -> {
                ScoreWeights wt = c.weights;
                rows.add(new Row("Weights = max points per evidence type", null, null));
                weight(rows, "Storage clustered together", () -> wt.storageCluster, v -> wt.storageCluster = v);
                weight(rows, "Different storage types", () -> wt.storageVariety, v -> wt.storageVariety = v);
                weight(rows, "Shulker boxes / ender chests", () -> wt.playerOnlyStorage, v -> wt.playerOnlyStorage = v);
                weight(rows, "Beds", () -> wt.beds, v -> wt.beds = v);
                weight(rows, "Crafting tables", () -> wt.crafting, v -> wt.crafting = v);
                weight(rows, "Furnaces / smelting", () -> wt.smelting, v -> wt.smelting = v);
                weight(rows, "Advanced utility blocks", () -> wt.advancedUtility, v -> wt.advancedUtility = v);
                weight(rows, "Hoppers & redstone", () -> wt.automation, v -> wt.automation = v);
                weight(rows, "Signs", () -> wt.signs, v -> wt.signs = v);
                weight(rows, "Signs with writing", () -> wt.signText, v -> wt.signText = v);
                weight(rows, "Frames, stands & decoration", () -> wt.organization, v -> wt.organization = v);
                weight(rows, "Farming", () -> wt.farming, v -> wt.farming = v);
                weight(rows, "Villagers & golems", () -> wt.villagers, v -> wt.villagers = v);
                weight(rows, "Pets & named mobs", () -> wt.pets, v -> wt.pets = v);
                weight(rows, "Players present", () -> wt.players, v -> wt.players = v);
                weight(rows, "Defensive blocks", () -> wt.defensive, v -> wt.defensive = v);
                weight(rows, "Valuable blocks", () -> wt.valuables, v -> wt.valuables = v);
                weight(rows, "Light sources", () -> wt.lighting, v -> wt.lighting = v);
                weight(rows, "Building materials", () -> wt.building, v -> wt.building = v);
                weight(rows, "Lit nether portal", () -> wt.portal, v -> wt.portal = v);
                rows.add(new Row("Model", null, null));
                rows.add(new Row("Score scale", "Points needed for ~63%. Higher = stricter.",
                        (x, y, w) -> slider(x, y, w, 10, 150, 1, wt.scale, String::valueOf, v -> wt.scale = v)));
                rows.add(new Row("Synergy base", "Multiplier with no strong indicators.",
                        (x, y, w) -> slider(x, y, w, 0, 100, 1, wt.synergyBase, v -> v + "%", v -> wt.synergyBase = v)));
                rows.add(new Row("Synergy per indicator", "Added per independent strong indicator.",
                        (x, y, w) -> slider(x, y, w, 0, 50, 1, wt.synergyPerIndicator, v -> v + "%",
                                v -> wt.synergyPerIndicator = v)));
                rows.add(new Row("Single-storage cap", "Max score when the only evidence is one storage block.",
                        (x, y, w) -> slider(x, y, w, 0, 100, 1, wt.singleStorageCap, v -> v + "%",
                                v -> wt.singleStorageCap = v)));
                rows.add(new Row("Default weights", null,
                        (x, y, w) -> button(x, y, w, "Restore default weights", () -> {
                            c.weights = new ScoreWeights();
                            clearAndInit();
                        })));
            }
            case ALERTS -> {
                rows.add(new Row("Alerts", null,
                        (x, y, w) -> toggle(x, y, w, c.alertsEnabled, v -> c.alertsEnabled = v)));
                rows.add(new Row("Alert sound", null,
                        (x, y, w) -> toggle(x, y, w, c.alertSound, v -> c.alertSound = v)));
                rows.add(new Row("Sound volume", null,
                        (x, y, w) -> slider(x, y, w, 0, 100, 5, c.alertVolume, v -> v + "%", v -> c.alertVolume = v)));
                rows.add(new Row("Send to chat", "Off: show alerts in the action bar above the hotbar.",
                        (x, y, w) -> toggle(x, y, w, c.alertToChat, v -> c.alertToChat = v)));
                rows.add(new Row("New storage area found", null,
                        (x, y, w) -> toggle(x, y, w, c.alertOnNewArea, v -> c.alertOnNewArea = v)));
                rows.add(new Row("High Base Likelihood", null,
                        (x, y, w) -> toggle(x, y, w, c.alertOnHighScore, v -> c.alertOnHighScore = v)));
                rows.add(new Row("High score threshold", null,
                        (x, y, w) -> slider(x, y, w, 1, 100, 1, c.alertScoreThreshold, v -> v + "%",
                                v -> c.alertScoreThreshold = v)));
                rows.add(new Row("Player inside an area", null,
                        (x, y, w) -> toggle(x, y, w, c.alertOnPlayer, v -> c.alertOnPlayer = v)));
            }
        }
        return rows;
    }

    private void weight(List<Row> rows, String label, IntSupplier get, IntConsumer set) {
        rows.add(new Row(label, null,
                (x, y, w) -> slider(x, y, w, 0, 50, 1, get.getAsInt(), v -> v + " pts", set)));
    }

    private void confirmReset() {
        this.client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ConfigManager.resetToDefaults();
                ScanManager.get().onSettingsChanged();
            }
            this.client.setScreen(this);
        }, Text.literal("Reset all Base Scanner settings?"), Text.literal("Every option returns to its default.")));
    }

    // ------------------------------------------------------------------ render & input

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        for (Label l : labels) ctx.drawText(this.textRenderer, l.text, l.x, l.y, l.color, true);
        for (Swatch s : swatches) {
            ctx.fill(s.x - 1, s.y - 1, s.x + s.w + 1, s.y + s.h + 1, 0xFF000000);
            ctx.fill(s.x, s.y, s.x + s.w, s.y + s.h, 0xFF000000 | (s.color.getAsInt() & 0xFFFFFF));
        }
        if (maxScroll > 0) {
            int trackH = bottom - top;
            int thumbH = Math.max(12, trackH * trackH / (trackH + maxScroll));
            int thumbY = top + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            int sx = this.width - 8;
            ctx.fill(sx, top, sx + 3, bottom, 0x30FFFFFF);
            ctx.fill(sx, thumbY, sx + 3, thumbY + thumbH, 0xA0FFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0 && verticalAmount != 0) {
            int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * ROW_H)));
            if (next != scroll) {
                scroll = next;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        ConfigManager.save();
        if (geometryChanged) ScanManager.get().onSettingsChanged();
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
