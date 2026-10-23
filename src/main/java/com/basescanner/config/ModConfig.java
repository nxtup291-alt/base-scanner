package com.basescanner.config;

import com.basescanner.scan.CategoryGroup;
import com.basescanner.scan.ScanCategory;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** All persistent settings. Serialized to config/basescanner.json with Gson. */
public class ModConfig {

    public enum VerticalMode {
        LIMITED("Limited range"),
        HORIZONTAL_ONLY("Full height (horizontal area only)");

        public final String label;

        VerticalMode(String label) {
            this.label = label;
        }
    }

    public enum HudAnchor {
        TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");

        public final String label;

        HudAnchor(String label) {
            this.label = label;
        }
    }

    // ---------- General ----------
    public boolean autoScan = true;
    public boolean interfaceHidden = false;
    public boolean showOutline = true;
    public boolean showHud = true;
    public boolean storageSummaries = true;
    public boolean scoringEnabled = true;
    public boolean mergeOverlapping = true;
    public boolean autoOpenOnSelect = false;
    public boolean autoOpenRequiresSneak = true;

    // ---------- Scanning ----------
    public int areaSize = 20;
    public VerticalMode verticalMode = VerticalMode.LIMITED;
    public int verticalRange = 16;
    public int refreshIntervalTicks = 60;
    public int maxScanDistance = 96;
    public int blocksPerTick = 30000;
    public int maxAreas = 24;
    public int maxMergedSpan = 80;

    // ---------- Visuals ----------
    public int outlineColor = 0x55FFAA;
    public int focusedOutlineColor = 0xFFD54F;
    public int outlineAlpha = 190;
    public int outlineWidth = 2;
    public boolean outlineSeeThrough = true;
    public boolean espEnabled = true;
    public boolean entityHighlight = true;
    public boolean espSeeThrough = true;
    public boolean espAllAreas = false;
    public int espWidth = 2;
    public int espAlpha = 220;
    public int maxEspBoxes = 400;

    // ---------- HUD ----------
    public HudAnchor hudAnchor = HudAnchor.TOP_LEFT;
    public int hudX = 6;
    public int hudY = 6;
    public int hudScale = 100;
    public int hudOpacity = 65;
    public int hudWidth = 210;
    public boolean hudCompact = false;
    public boolean hudShowEvidence = true;
    public int hudMaxEvidence = 4;
    public boolean hudShowCoords = true;
    public boolean hudShowDistance = true;
    public Set<String> collapsedGroups = new LinkedHashSet<>();

    // ---------- Alerts ----------
    public boolean alertsEnabled = true;
    public boolean alertSound = true;
    public int alertVolume = 70;
    public boolean alertToChat = false;
    public boolean alertOnNewArea = true;
    public boolean alertOnHighScore = true;
    public int alertScoreThreshold = 60;
    public boolean alertOnPlayer = true;

    // ---------- Categories & scoring ----------
    public Map<String, CategorySettings> categories = new LinkedHashMap<>();
    public ScoreWeights weights = new ScoreWeights();

    private transient EnumMap<ScanCategory, CategorySettings> resolved;

    /** Fills in anything missing after loading an older or hand-edited file and clamps ranges. */
    public void normalize() {
        if (categories == null) categories = new LinkedHashMap<>();
        if (weights == null) weights = new ScoreWeights();
        if (collapsedGroups == null) collapsedGroups = new LinkedHashSet<>();
        if (verticalMode == null) verticalMode = VerticalMode.LIMITED;
        if (hudAnchor == null) hudAnchor = HudAnchor.TOP_LEFT;
        resolved = new EnumMap<>(ScanCategory.class);
        for (ScanCategory c : ScanCategory.values()) {
            CategorySettings s = categories.get(c.name());
            if (s == null) {
                s = CategorySettings.defaults(c);
                categories.put(c.name(), s);
            }
            resolved.put(c, s);
        }
        areaSize = clamp(areaSize, 4, 64);
        verticalRange = clamp(verticalRange, 1, 160);
        refreshIntervalTicks = clamp(refreshIntervalTicks, 10, 600);
        maxScanDistance = clamp(maxScanDistance, 16, 320);
        blocksPerTick = clamp(blocksPerTick, 2000, 250000);
        maxAreas = clamp(maxAreas, 1, 64);
        maxMergedSpan = clamp(maxMergedSpan, areaSize, 256);
        outlineAlpha = clamp(outlineAlpha, 10, 255);
        outlineWidth = clamp(outlineWidth, 1, 10);
        espWidth = clamp(espWidth, 1, 10);
        espAlpha = clamp(espAlpha, 10, 255);
        maxEspBoxes = clamp(maxEspBoxes, 16, 4000);
        hudScale = clamp(hudScale, 50, 200);
        hudOpacity = clamp(hudOpacity, 0, 100);
        hudWidth = clamp(hudWidth, 140, 400);
        hudMaxEvidence = clamp(hudMaxEvidence, 0, 12);
        alertVolume = clamp(alertVolume, 0, 100);
        alertScoreThreshold = clamp(alertScoreThreshold, 1, 100);
    }

    public CategorySettings get(ScanCategory c) {
        if (resolved == null) normalize();
        return resolved.get(c);
    }

    public boolean isEnabled(ScanCategory c) {
        return get(c).enabled;
    }

    public boolean isCollapsed(CategoryGroup g) {
        return collapsedGroups.contains(g.name());
    }

    public void setCollapsed(CategoryGroup g, boolean collapsed) {
        if (collapsed) collapsedGroups.add(g.name());
        else collapsedGroups.remove(g.name());
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
