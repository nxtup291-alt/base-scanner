package com.basescanner.scan;

/** String tags attached to classifications so the scorer can find specific evidence quickly. */
public final class ScoreTags {
    public static final String PLAYER_ONLY_STORAGE = "player_only_storage"; // shulker boxes, ender chests
    public static final String CRAFTING_TABLE = "crafting_table";
    public static final String SMELTER = "smelter";
    public static final String HOPPER = "hopper";
    public static final String ADVANCED_UTILITY = "advanced_utility";
    public static final String LIT_PORTAL = "lit_portal";
    public static final String DECOR_ENTITY = "decor_entity";
    public static final String NAMED_MOB = "named_mob";

    private ScoreTags() {
    }
}
