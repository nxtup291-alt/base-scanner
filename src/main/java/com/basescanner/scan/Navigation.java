package com.basescanner.scan;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** A single navigation target chosen from the detail screen; drawn as a tracer and shown in the HUD. */
public final class Navigation {
    private static Vec3d target;
    private static BlockPos targetBlock;
    private static int entityId = -1;
    private static String label;
    private static int color = 0xFFFFD54F;

    private Navigation() {
    }

    public static void setBlock(BlockPos pos, String name, int argb) {
        targetBlock = pos.toImmutable();
        target = Vec3d.ofCenter(pos);
        entityId = -1;
        label = name;
        color = argb;
    }

    public static void setEntity(int id, BlockPos lastKnown, String name, int argb) {
        targetBlock = null;
        target = Vec3d.ofCenter(lastKnown);
        entityId = id;
        label = name;
        color = argb;
    }

    public static void clear() {
        target = null;
        targetBlock = null;
        entityId = -1;
        label = null;
    }

    public static boolean active() {
        return target != null;
    }

    public static Vec3d target() {
        return target;
    }

    public static BlockPos targetBlock() {
        return targetBlock;
    }

    public static int entityId() {
        return entityId;
    }

    public static String label() {
        return label;
    }

    public static int color() {
        return color;
    }

    public static void tick(MinecraftClient client) {
        if (target == null || client.player == null || client.world == null) return;
        if (entityId >= 0) {
            Entity e = client.world.getEntityById(entityId);
            if (e != null) target = e.getPos().add(0, e.getHeight() / 2.0, 0);
        }
        if (client.player.getPos().distanceTo(target) < 2.5) {
            client.player.sendMessage(Text.literal("Reached " + label).formatted(Formatting.GREEN), true);
            clear();
        }
    }

    /** Arrow pointing from the player's view direction towards the target. */
    public static String arrowTo(MinecraftClient client, Vec3d to) {
        if (client.player == null) return "";
        Vec3d p = client.player.getPos();
        double angle = Math.toDegrees(Math.atan2(to.z - p.z, to.x - p.x)) - 90.0;
        double rel = wrap(angle - client.player.getYaw());
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int idx = (int) Math.floorMod(Math.round(rel / 45.0), 8);
        return arrows[idx];
    }

    private static double wrap(double deg) {
        deg %= 360.0;
        if (deg >= 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }
}
