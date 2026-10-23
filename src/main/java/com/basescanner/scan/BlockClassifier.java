package com.basescanner.scan;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps block states to scan categories. Classification relies on stable block
 * classes for containers and on registry ids for everything else, so it does
 * not depend on tag data having been synced from the server. Results are cached
 * per block state (the cache is cleared whenever the world changes).
 */
public final class BlockClassifier {
    private static final Classification NONE = new Classification(null, "", null);
    private static final Map<BlockState, Classification> CACHE = new IdentityHashMap<>();

    private static final Set<String> UTILITY = Set.of(
            "crafting_table", "anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "brewing_stand",
            "beacon", "respawn_anchor", "grindstone", "stonecutter", "loom", "cartography_table",
            "smithing_table", "fletching_table", "lectern", "cauldron", "water_cauldron", "lava_cauldron",
            "powder_snow_cauldron", "bell", "jukebox", "conduit", "lodestone");
    private static final Set<String> ADVANCED = Set.of(
            "anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "brewing_stand", "beacon",
            "respawn_anchor", "grindstone", "smithing_table", "jukebox", "conduit", "lodestone");
    private static final Set<String> VALUABLE = Set.of(
            "diamond_block", "emerald_block", "gold_block", "iron_block", "netherite_block", "lapis_block",
            "coal_block");
    private static final Set<String> DEFENSIVE = Set.of("obsidian", "crying_obsidian", "iron_bars");
    private static final Set<String> PORTAL = Set.of(
            "nether_portal", "end_portal", "end_portal_frame", "end_gateway", "dragon_egg");
    private static final Set<String> FARMING = Set.of(
            "farmland", "wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem",
            "attached_melon_stem", "attached_pumpkin_stem", "torchflower_crop", "pitcher_crop", "nether_wart",
            "composter", "hay_block", "beehive");
    private static final Set<String> REDSTONE = Set.of(
            "repeater", "comparator", "observer", "piston", "sticky_piston", "lever", "daylight_detector",
            "target", "tripwire_hook", "note_block");
    private static final Set<String> LIGHTS = Set.of("glowstone", "shroomlight", "end_rod");
    private static final Set<String> OTHER_STORAGE = Set.of("crafter", "chiseled_bookshelf", "decorated_pot");
    private static final Set<String> IGNORED = Set.of("piston_head", "moving_piston", "air", "cave_air", "void_air");

    private BlockClassifier() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** @return the classification, {@link Classification#SKIP}, or null when the block is not interesting. */
    public static Classification classify(BlockState state) {
        Classification c = CACHE.get(state);
        if (c == null) {
            c = compute(state);
            CACHE.put(state, c);
        }
        return c == NONE ? null : c;
    }

    private static Classification compute(BlockState state) {
        if (state.isAir()) return NONE;
        Block block = state.getBlock();
        String path = Registries.BLOCK.getId(block).getPath();
        if (IGNORED.contains(path)) return NONE;
        String name = block.getName().getString();

        // ---------- Storage ----------
        if (block instanceof ChestBlock) {
            ChestType type = state.getProperties().contains(Properties.CHEST_TYPE)
                    ? state.get(Properties.CHEST_TYPE) : ChestType.SINGLE;
            if (type == ChestType.RIGHT) return Classification.SKIP; // counted with its left half
            return new Classification(ScanCategory.CHESTS,
                    type == ChestType.SINGLE ? name : name + " (double)", null);
        }
        if (block instanceof EnderChestBlock) {
            return new Classification(ScanCategory.CHESTS, name, ScoreTags.PLAYER_ONLY_STORAGE);
        }
        if (block instanceof ShulkerBoxBlock) {
            return new Classification(ScanCategory.SHULKERS, name, ScoreTags.PLAYER_ONLY_STORAGE);
        }
        if (block instanceof BarrelBlock) {
            return new Classification(ScanCategory.BARRELS, name, null);
        }
        if (block instanceof AbstractFurnaceBlock) {
            return new Classification(ScanCategory.OTHER_STORAGE, name, ScoreTags.SMELTER);
        }
        if (block instanceof HopperBlock) {
            return new Classification(ScanCategory.OTHER_STORAGE, name, ScoreTags.HOPPER);
        }
        if (block instanceof DispenserBlock || OTHER_STORAGE.contains(path)) { // DropperBlock extends DispenserBlock
            return new Classification(ScanCategory.OTHER_STORAGE, name, null);
        }

        // ---------- Ores & valuables (before redstone so redstone ore is an ore) ----------
        if (path.endsWith("_ore") || path.equals("ancient_debris") || path.startsWith("raw_") && path.endsWith("_block")) {
            return new Classification(ScanCategory.ORES, name, null);
        }
        if (VALUABLE.contains(path)) return new Classification(ScanCategory.VALUABLES, name, null);

        // ---------- Utility ----------
        if (UTILITY.contains(path)) {
            String tag = path.equals("crafting_table") ? ScoreTags.CRAFTING_TABLE
                    : ADVANCED.contains(path) ? ScoreTags.ADVANCED_UTILITY : null;
            String typeName = path.endsWith("anvil") ? "Anvil" : name;
            return new Classification(ScanCategory.UTILITY, typeName, tag);
        }

        // ---------- Beds (count the head only) ----------
        if (path.endsWith("_bed")) {
            if (state.getProperties().contains(Properties.BED_PART) && state.get(Properties.BED_PART) == BedPart.FOOT) {
                return Classification.SKIP;
            }
            return new Classification(ScanCategory.BEDS, name, null);
        }

        // ---------- Signs ----------
        if (path.endsWith("_sign")) {
            String typeName = path.contains("hanging") ? "Hanging sign" : "Sign";
            return new Classification(ScanCategory.SIGNS, typeName, null);
        }

        // ---------- Portals / End ----------
        if (PORTAL.contains(path)) {
            return new Classification(ScanCategory.PORTALS, name,
                    path.equals("nether_portal") ? ScoreTags.LIT_PORTAL : null);
        }

        // ---------- Defensive ----------
        if (DEFENSIVE.contains(path)) return new Classification(ScanCategory.DEFENSIVE, name, null);

        // ---------- Farming ----------
        if (FARMING.contains(path)) return new Classification(ScanCategory.FARMING, name, null);

        // ---------- Redstone (before lights, so redstone torches/lamps land here) ----------
        if (path.contains("redstone") || REDSTONE.contains(path) || path.endsWith("_button")
                || path.endsWith("_pressure_plate")) {
            String typeName = path.endsWith("_button") ? "Button"
                    : path.endsWith("_pressure_plate") ? "Pressure plate"
                    : path.equals("redstone_wall_torch") ? "Redstone Torch" : name;
            return new Classification(ScanCategory.REDSTONE, typeName, null);
        }

        // ---------- Doors, trapdoors, fences, walls ----------
        if (path.endsWith("_door")) {
            if (state.getProperties().contains(Properties.DOUBLE_BLOCK_HALF)
                    && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                return Classification.SKIP;
            }
            return new Classification(ScanCategory.DOORS_FENCES, path.startsWith("iron") ? "Iron door" : "Door", null);
        }
        if (path.endsWith("_trapdoor")) return new Classification(ScanCategory.DOORS_FENCES, "Trapdoor", null);
        if (path.endsWith("_fence_gate")) return new Classification(ScanCategory.DOORS_FENCES, "Fence gate", null);
        if (path.endsWith("_fence")) return new Classification(ScanCategory.DOORS_FENCES, "Fence", null);
        if (path.endsWith("_wall")) return new Classification(ScanCategory.DOORS_FENCES, "Wall", null);

        // ---------- Light sources ----------
        if (path.endsWith("torch") || path.contains("lantern") || path.endsWith("froglight")
                || path.endsWith("campfire") || path.endsWith("candle") || path.endsWith("candle_cake")
                || LIGHTS.contains(path)) {
            String typeName = path.equals("wall_torch") ? "Torch"
                    : path.equals("soul_wall_torch") ? "Soul Torch"
                    : path.endsWith("candle") || path.endsWith("candle_cake") ? "Candle" : name;
            return new Classification(ScanCategory.LIGHTS, typeName, null);
        }

        // ---------- Rails ----------
        if (path.equals("rail") || path.endsWith("_rail")) return new Classification(ScanCategory.RAILS, name, null);

        // ---------- Decoration ----------
        if (path.endsWith("_banner")) return new Classification(ScanCategory.DECOR, "Banner", null);
        if (path.endsWith("_carpet") && !path.contains("moss")) return new Classification(ScanCategory.DECOR, "Carpet", null);
        if (path.equals("flower_pot") || path.startsWith("potted_")) return new Classification(ScanCategory.DECOR, "Flower pot", null);
        if (path.endsWith("_head") || path.endsWith("_skull")) return new Classification(ScanCategory.DECOR, "Mob head", null);
        if (path.equals("bookshelf")) return new Classification(ScanCategory.DECOR, name, null);

        // ---------- Building materials ----------
        if (path.endsWith("_planks") || path.endsWith("_wool") || path.endsWith("_concrete")
                || path.contains("glass") || path.contains("glazed_terracotta") || path.equals("bricks")
                || path.contains("quartz") || path.equals("smooth_stone")) {
            String typeName;
            if (path.endsWith("_planks")) typeName = "Planks";
            else if (path.endsWith("_wool")) typeName = "Wool";
            else if (path.endsWith("_concrete")) typeName = "Concrete";
            else if (path.contains("glass")) typeName = path.contains("pane") ? "Glass pane" : "Glass";
            else if (path.contains("glazed_terracotta")) typeName = "Glazed terracotta";
            else typeName = name;
            return new Classification(ScanCategory.BUILDING, typeName, null);
        }

        return NONE;
    }
}
