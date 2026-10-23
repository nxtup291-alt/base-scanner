package com.basescanner.scan;

/**
 * Every kind of thing the scanner can detect. Each category can be toggled
 * independently for detection, HUD display and ESP outlines.
 */
public enum ScanCategory {
    // ---- Storage (these also trigger scan areas) ----
    CHESTS("Chests", CategoryGroup.STORAGE, false, 0xFFB07A3C, true, true),
    SHULKERS("Shulker boxes", CategoryGroup.STORAGE, false, 0xFFB57EDC, true, true),
    BARRELS("Barrels", CategoryGroup.STORAGE, false, 0xFF8D6E4A, true, true),
    OTHER_STORAGE("Other storage", CategoryGroup.STORAGE, false, 0xFFCFCFCF, true, true),

    // ---- Blocks & base infrastructure ----
    UTILITY("Utility blocks", CategoryGroup.INFRASTRUCTURE, false, 0xFF4FC3F7, false, false),
    BEDS("Beds", CategoryGroup.INFRASTRUCTURE, false, 0xFFE57373, true, false),
    SIGNS("Signs", CategoryGroup.INFRASTRUCTURE, false, 0xFFFFF59D, false, false),
    DOORS_FENCES("Doors, trapdoors & fences", CategoryGroup.INFRASTRUCTURE, false, 0xFFA1887F, false, false),
    LIGHTS("Light sources", CategoryGroup.INFRASTRUCTURE, false, 0xFFFFE082, false, false),
    REDSTONE("Redstone", CategoryGroup.INFRASTRUCTURE, false, 0xFFFF5252, false, false),
    VALUABLES("Valuable blocks", CategoryGroup.INFRASTRUCTURE, false, 0xFF64FFDA, true, false),
    ORES("Ores", CategoryGroup.INFRASTRUCTURE, false, 0xFF80CBC4, false, false),
    DEFENSIVE("Defensive blocks", CategoryGroup.INFRASTRUCTURE, false, 0xFF7E57C2, false, false),
    FARMING("Farming", CategoryGroup.INFRASTRUCTURE, false, 0xFF9CCC65, false, false),
    PORTALS("Portals & End blocks", CategoryGroup.INFRASTRUCTURE, false, 0xFFCE93D8, true, false),
    BUILDING("Building materials", CategoryGroup.INFRASTRUCTURE, false, 0xFFBDBDBD, false, false),
    DECOR("Decoration", CategoryGroup.INFRASTRUCTURE, false, 0xFFF48FB1, false, false),
    RAILS("Rails", CategoryGroup.INFRASTRUCTURE, false, 0xFF90A4AE, false, false),

    // ---- Entities ----
    PLAYERS("Players", CategoryGroup.ENTITIES, true, 0xFFFF1744, true, true),
    VILLAGERS("Villagers", CategoryGroup.ENTITIES, true, 0xFF66BB6A, true, false),
    GOLEMS("Golems", CategoryGroup.ENTITIES, true, 0xFFE0E0E0, true, false),
    PETS("Pets & named mobs", CategoryGroup.ENTITIES, true, 0xFFFFAB40, true, false),
    ANIMALS("Animals", CategoryGroup.ENTITIES, true, 0xFFAED581, false, false),
    HOSTILE("Hostile mobs", CategoryGroup.ENTITIES, true, 0xFFD50000, false, false),
    ARMOR_STANDS("Armor stands", CategoryGroup.ENTITIES, true, 0xFFB0BEC5, false, false),
    ITEM_FRAMES("Item frames", CategoryGroup.ENTITIES, true, 0xFFFFCC80, false, false),

    // ---- Other objects ----
    DROPPED_ITEMS("Dropped items", CategoryGroup.OTHER, true, 0xFFFFFFFF, false, false),
    VEHICLES("Minecarts & boats", CategoryGroup.OTHER, true, 0xFF8C9EFF, false, false),
    OTHER_ENTITIES("Other entities", CategoryGroup.OTHER, true, 0xFF9E9E9E, false, false);

    public final String label;
    public final CategoryGroup group;
    public final boolean entity;
    public final int defaultColor;
    public final boolean defaultEsp;
    /** Storage categories create scan areas when detected. */
    public final boolean triggersArea;

    ScanCategory(String label, CategoryGroup group, boolean entity, int defaultColor,
                 boolean defaultEsp, boolean triggersArea) {
        this.label = label;
        this.group = group;
        this.entity = entity;
        this.defaultColor = defaultColor;
        this.defaultEsp = defaultEsp;
        this.triggersArea = triggersArea;
    }

    public boolean isStorage() {
        return group == CategoryGroup.STORAGE;
    }
}
