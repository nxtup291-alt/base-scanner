package com.basescanner.scan;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.item.ItemStack;

/** Maps client-side entities to scan categories. Only uses data the client already has. */
public final class EntityClassifier {
    private EntityClassifier() {
    }

    /** @return the classification, or null when the entity should be ignored. */
    public static Classification classify(Entity entity) {
        if (entity == null || entity.isRemoved()) return null;
        String typeName = entity.getType().getName().getString();

        if (entity instanceof PlayerEntity player) {
            return new Classification(ScanCategory.PLAYERS, player.getName().getString(), null);
        }
        if (entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity) {
            return new Classification(ScanCategory.VILLAGERS, typeName, null);
        }
        if (entity instanceof IronGolemEntity || entity instanceof SnowGolemEntity) {
            return new Classification(ScanCategory.GOLEMS, typeName, null);
        }
        if (entity instanceof TameableEntity tameable && tameable.isTamed()) {
            return new Classification(ScanCategory.PETS, typeName + " (tamed)", null);
        }
        if (entity instanceof AbstractHorseEntity horse && horse.isTame()) {
            return new Classification(ScanCategory.PETS, typeName + " (tamed)", null);
        }
        if (entity instanceof MobEntity && entity.hasCustomName()) {
            return new Classification(ScanCategory.PETS, typeName + " (named)", ScoreTags.NAMED_MOB);
        }
        if (entity instanceof Monster) {
            return new Classification(ScanCategory.HOSTILE, typeName, null);
        }
        if (entity instanceof AnimalEntity || entity instanceof WaterCreatureEntity) {
            return new Classification(ScanCategory.ANIMALS, typeName, null);
        }
        if (entity instanceof ArmorStandEntity) {
            return new Classification(ScanCategory.ARMOR_STANDS, typeName, null);
        }
        if (entity instanceof ItemFrameEntity frame) {
            ItemStack held = frame.getHeldItemStack();
            String label = held.isEmpty() ? "Empty " + typeName.toLowerCase() : held.getName().getString();
            return new Classification(ScanCategory.ITEM_FRAMES, label, null);
        }
        if (entity instanceof ItemEntity item) {
            return new Classification(ScanCategory.DROPPED_ITEMS, item.getStack().getName().getString(), null);
        }
        if (entity instanceof AbstractMinecartEntity || entity instanceof VehicleEntity) {
            return new Classification(ScanCategory.VEHICLES, typeName, null);
        }
        if (entity instanceof PaintingEntity) {
            return new Classification(ScanCategory.OTHER_ENTITIES, typeName, ScoreTags.DECOR_ENTITY);
        }
        if (entity instanceof ProjectileEntity || entity instanceof ExperienceOrbEntity) {
            return null;
        }
        return new Classification(ScanCategory.OTHER_ENTITIES, typeName, null);
    }
}
