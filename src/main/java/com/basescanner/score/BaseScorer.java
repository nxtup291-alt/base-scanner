package com.basescanner.score;

import com.basescanner.config.ScoreWeights;
import com.basescanner.scan.AreaScanResult;
import com.basescanner.scan.ScanArea;
import com.basescanner.scan.ScanCategory;
import com.basescanner.scan.ScoreTags;

import java.util.ArrayList;
import java.util.List;

/**
 * Weighted, saturating evidence model for the "Base Likelihood" heuristic.
 *
 * <ol>
 *   <li>Each evidence type contributes {@code weight * (1 - e^(-n / k))} points, where n is the
 *       de-duplicated count and k the type's saturation constant. Contributions flatten out,
 *       so 200 torches are not worth much more than 30.</li>
 *   <li>Every block/entity is counted in exactly one category; double chests, beds and doors
 *       are counted once. An evidence type is evaluated once per area.</li>
 *   <li>An evidence item that reaches at least 25% of its weight is a "strong indicator".
 *       Raw points are multiplied by a synergy factor
 *       {@code min(1, base + perIndicator * indicators)}, so several different kinds of evidence
 *       together score far higher than a lot of one kind.</li>
 *   <li>The result is mapped to 0..100 with {@code 100 * (1 - e^(-points / scale))}.</li>
 *   <li>If the only evidence is a single storage block, the result is capped (default 15%).</li>
 * </ol>
 *
 * The output is a heuristic estimate, not a measured probability.
 */
public final class BaseScorer {
    private BaseScorer() {
    }

    private static final class Builder {
        final List<ScoreResult.Evidence> evidence = new ArrayList<>();
        double raw;
        int indicators;

        void add(String text, int weight, double n, double k) {
            if (weight <= 0 || n <= 0) return;
            double pts = weight * (1.0 - Math.exp(-n / k));
            if (pts < 0.5) return;
            raw += pts;
            if (pts >= weight * 0.25) indicators++;
            evidence.add(new ScoreResult.Evidence(text, pts, weight));
        }
    }

    public static ScoreResult compute(ScanArea area, ScoreWeights w) {
        AreaScanResult blocks = area.blockResult;
        if (blocks == null) return null;
        Builder b = new Builder();

        int chests = area.total(ScanCategory.CHESTS);
        int shulkers = area.total(ScanCategory.SHULKERS);
        int barrels = area.total(ScanCategory.BARRELS);
        int otherStorage = area.total(ScanCategory.OTHER_STORAGE);
        // Furnaces and hoppers are scored as smelting / automation evidence, so they are
        // excluded here to avoid counting the same block twice.
        int smelters = blocks.tag(ScoreTags.SMELTER);
        int hoppers = blocks.tag(ScoreTags.HOPPER);
        int storage = Math.max(0, chests + shulkers + barrels + otherStorage - smelters - hoppers);

        // 1. Storage clustering (a single chest gives nothing)
        if (storage >= 2) {
            b.add(storage >= 6 ? "Multiple storage blocks clustered together (" + storage + ")"
                    : storage + " storage blocks close together", w.storageCluster, storage - 1, 4);
        }
        // 2. Variety of storage types
        int storageTypes = 0;
        for (ScanCategory c : new ScanCategory[]{ScanCategory.CHESTS, ScanCategory.SHULKERS,
                ScanCategory.BARRELS, ScanCategory.OTHER_STORAGE}) {
            AreaScanResult.CategoryResult cr = blocks.get(c);
            if (cr != null) storageTypes += cr.types.size();
        }
        storageTypes -= blocks.tagTypes(ScoreTags.SMELTER).size() + blocks.tagTypes(ScoreTags.HOPPER).size();
        if (storageTypes >= 2) {
            b.add(storageTypes + " different storage types", w.storageVariety, storageTypes - 1, 3);
        }
        // 3. Storage that only players place
        int playerOnly = blocks.tag(ScoreTags.PLAYER_ONLY_STORAGE);
        if (playerOnly > 0) {
            b.add("Shulker boxes / ender chests placed (" + playerOnly + ")", w.playerOnlyStorage, playerOnly, 1.5);
        }
        // 4. Beds
        int beds = area.total(ScanCategory.BEDS);
        b.add(beds == 1 ? "A bed" : beds + " beds detected", w.beds, beds, 1.5);
        // 5/6. Crafting and smelting
        int crafting = blocks.tag(ScoreTags.CRAFTING_TABLE);
        if (crafting > 0 && smelters > 0) {
            b.add("Crafting and smelting infrastructure", w.crafting + w.smelting, crafting + smelters, 2);
        } else {
            b.add("Crafting table" + (crafting > 1 ? "s (" + crafting + ")" : ""), w.crafting, crafting, 1);
            b.add("Furnaces / smokers / blast furnaces (" + smelters + ")", w.smelting, smelters, 2);
        }
        // 7. Advanced utility (distinct types)
        int advanced = blocks.tagTypes(ScoreTags.ADVANCED_UTILITY).size();
        if (advanced > 0) {
            b.add("Advanced utility: " + String.join(", ", blocks.tagTypes(ScoreTags.ADVANCED_UTILITY)),
                    w.advancedUtility, advanced, 2);
        }
        // 8. Automation: hoppers + redstone
        int redstone = area.total(ScanCategory.REDSTONE);
        if (hoppers + redstone > 0) {
            b.add("Hoppers and redstone machinery (" + (hoppers + redstone) + ")", w.automation, hoppers + redstone, 8);
        }
        // 9. Signs / written signs
        int signs = area.total(ScanCategory.SIGNS);
        int textSigns = blocks.textSigns();
        b.add("Signs (" + signs + ")", w.signs, signs, 3);
        b.add("Signs with writing (" + textSigns + ")", w.signText, textSigns, 2);
        // 10. Organization & decoration
        int organization = area.total(ScanCategory.ITEM_FRAMES) + area.total(ScanCategory.ARMOR_STANDS)
                + area.total(ScanCategory.DECOR)
                + (area.entityResult == null ? 0 : area.entityResult.tag(ScoreTags.DECOR_ENTITY));
        b.add("Item frames, armor stands and decoration (" + organization + ")", w.organization, organization, 5);
        // 11. Farming
        int farming = area.total(ScanCategory.FARMING);
        b.add("Farming infrastructure (" + farming + " blocks)", w.farming, farming, 16);
        // 12. Villagers & golems
        int villagers = area.total(ScanCategory.VILLAGERS) + area.total(ScanCategory.GOLEMS);
        b.add("Villagers / golems (" + villagers + ")", w.villagers, villagers, 3);
        // 13. Pets & named mobs
        int pets = area.total(ScanCategory.PETS);
        b.add("Tamed or name-tagged animals (" + pets + ")", w.pets, pets, 1.5);
        // 14. Players currently visible
        int players = area.total(ScanCategory.PLAYERS);
        b.add("Player" + (players == 1 ? "" : "s") + " present (" + players + ")", w.players, players, 1);
        // 15. Defensive blocks
        int defensive = area.total(ScanCategory.DEFENSIVE);
        b.add("Obsidian / defensive blocks (" + defensive + ")", w.defensive, defensive, 12);
        // 16. Valuable blocks
        int valuables = area.total(ScanCategory.VALUABLES);
        b.add("Valuable mineral blocks (" + valuables + ")", w.valuables, valuables, 3);
        // 17. Lighting
        int lights = area.total(ScanCategory.LIGHTS);
        b.add("Placed light sources (" + lights + ")", w.lighting, lights, 10);
        // 18. Building materials
        int building = area.total(ScanCategory.BUILDING);
        b.add("Crafted building materials (" + building + ")", w.building, building, 48);
        // 19. Lit nether portal
        int portal = blocks.tag(ScoreTags.LIT_PORTAL);
        if (portal > 0) b.add("Lit nether portal", w.portal, 1, 0.5);

        b.evidence.sort((x, y) -> Double.compare(y.points(), x.points()));

        double synergy = Math.min(1.0, (w.synergyBase + w.synergyPerIndicator * (double) b.indicators) / 100.0);
        double points = b.raw * synergy;
        int percent = (int) Math.round(100.0 * (1.0 - Math.exp(-points / Math.max(1, w.scale))));
        if (storage <= 1 && b.indicators <= 1) percent = Math.min(percent, w.singleStorageCap);
        percent = Math.max(0, Math.min(100, percent));

        // Confidence from how much of the requested area was really examined
        double coverage = blocks.coverage();
        String confidence;
        int confColor;
        String confText;
        int pct = (int) Math.round(coverage * 100);
        if (coverage >= 0.95) {
            confidence = "High";
            confColor = 0xFF66BB6A;
            confText = pct >= 100 ? "the whole scan area was loaded and examined"
                    : "most of the scan area (" + pct + "%) was loaded and examined";
        } else if (coverage >= 0.6) {
            confidence = "Medium";
            confColor = 0xFFFFCA28;
            confText = pct + "% of the scan area is loaded; the rest could not be examined";
        } else {
            confidence = "Low";
            confColor = 0xFFEF5350;
            confText = "only " + pct + "% of the scan area is loaded";
        }

        return new ScoreResult(percent, rating(percent), ratingColor(percent), b.evidence, b.raw,
                b.indicators, confidence, confText, confColor, coverage);
    }

    public static String rating(int percent) {
        if (percent >= 80) return "Very likely";
        if (percent >= 60) return "Likely";
        if (percent >= 40) return "Possible";
        if (percent >= 20) return "Unlikely";
        return "Very unlikely";
    }

    public static int ratingColor(int percent) {
        if (percent >= 80) return 0xFFFF5252;
        if (percent >= 60) return 0xFFFF9100;
        if (percent >= 40) return 0xFFFFD740;
        if (percent >= 20) return 0xFF9CCC65;
        return 0xFF90A4AE;
    }
}
