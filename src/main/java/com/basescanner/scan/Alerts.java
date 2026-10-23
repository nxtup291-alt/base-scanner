package com.basescanner.scan;

import com.basescanner.config.ConfigManager;
import com.basescanner.config.ModConfig;
import com.basescanner.score.ScoreResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Optional sound + chat/action-bar notifications. Rate-limited so they never spam. */
public final class Alerts {
    private static long lastAlertTick = Long.MIN_VALUE / 2;
    private static final int MIN_GAP_TICKS = 40;

    private Alerts() {
    }

    public static void onBlockScanFinished(ScanArea area, boolean firstScan, long tick) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.alertsEnabled) return;
        ScoreResult s = area.score;

        if (firstScan && cfg.alertOnNewArea && !area.announced) {
            area.announced = true;
            MutableText msg = Text.literal("[Scanner] ").formatted(Formatting.AQUA)
                    .append(Text.literal("Storage area #" + area.id + ": " + area.storageCount() + " storage blocks")
                            .formatted(Formatting.WHITE));
            if (s != null && cfg.scoringEnabled) {
                msg.append(Text.literal(" · Base Likelihood " + s.percent() + "% (" + s.rating() + ")")
                        .withColor(s.ratingColor() & 0xFFFFFF));
            }
            notify(msg, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.6f, tick);
        }

        if (s != null && cfg.scoringEnabled && cfg.alertOnHighScore) {
            if (!area.highScoreAlerted && s.percent() >= cfg.alertScoreThreshold) {
                area.highScoreAlerted = true;
                MutableText msg = Text.literal("[Scanner] ").formatted(Formatting.AQUA)
                        .append(Text.literal("Area #" + area.id + " Base Likelihood " + s.percent() + "% — " + s.rating())
                                .withColor(s.ratingColor() & 0xFFFFFF));
                notify(msg, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, tick);
            } else if (area.highScoreAlerted && s.percent() < cfg.alertScoreThreshold - 10) {
                area.highScoreAlerted = false; // re-arm after a clear drop
            }
        }
    }

    public static void onPlayerSeen(ScanArea area, String playerName, long tick) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.alertsEnabled || !cfg.alertOnPlayer) return;
        Long last = area.playerAlertTicks.get(playerName);
        if (last != null && tick - last < 20 * 60) return; // once a minute per player per area
        area.playerAlertTicks.put(playerName, tick);
        MutableText msg = Text.literal("[Scanner] ").formatted(Formatting.AQUA)
                .append(Text.literal(playerName + " is inside area #" + area.id).formatted(Formatting.RED));
        notify(msg, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, tick);
    }

    private static void notify(Text text, SoundEvent sound, float pitch, long tick) {
        MinecraftClient client = MinecraftClient.getInstance();
        ModConfig cfg = ConfigManager.get();
        if (client.player == null) return;
        if (tick - lastAlertTick < MIN_GAP_TICKS) return;
        lastAlertTick = tick;
        client.player.sendMessage(text, !cfg.alertToChat);
        if (cfg.alertSound && cfg.alertVolume > 0) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, cfg.alertVolume / 100f));
        }
    }
}
