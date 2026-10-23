package com.basescanner.mixin;

import com.basescanner.scan.ScanManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notifies the scanner when the server updates a block, so affected areas are
 * rescanned quickly instead of waiting for the periodic refresh. Marked
 * {@code require = 0}: if the target ever changes, the mod still works and falls
 * back to periodic refreshes.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "handleBlockUpdate", at = @At("TAIL"), require = 0)
    private void basescanner$onBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        ScanManager.get().onBlockChanged(pos, state);
    }
}
