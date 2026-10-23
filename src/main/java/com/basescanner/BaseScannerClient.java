package com.basescanner;

import com.basescanner.config.ConfigManager;
import com.basescanner.config.ModConfig;
import com.basescanner.gui.AreaDetailScreen;
import com.basescanner.gui.SettingsScreen;
import com.basescanner.hud.HudRenderer;
import com.basescanner.render.WorldOverlayRenderer;
import com.basescanner.scan.ContainerMemory;
import com.basescanner.scan.Navigation;
import com.basescanner.scan.ScanArea;
import com.basescanner.scan.ScanManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseScannerClient implements ClientModInitializer {
    public static final String MOD_ID = "basescanner";
    public static final Logger LOGGER = LoggerFactory.getLogger("Base Scanner");

    private static final String KEY_CATEGORY = "category.basescanner";
    private static KeyBinding openDetails;
    private static KeyBinding openSettings;
    private static KeyBinding toggleInterface;
    private static KeyBinding cycleArea;
    private static KeyBinding clearTarget;
    private static KeyBinding scanNow;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        openDetails = register("key.basescanner.details", GLFW.GLFW_KEY_B);
        openSettings = register("key.basescanner.settings", GLFW.GLFW_KEY_N);
        toggleInterface = register("key.basescanner.toggle_interface", GLFW.GLFW_KEY_H);
        cycleArea = register("key.basescanner.cycle_area", GLFW.GLFW_KEY_J);
        clearTarget = register("key.basescanner.clear_target", GLFW.GLFW_KEY_G);
        scanNow = register("key.basescanner.scan_now", GLFW.GLFW_KEY_V);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) ->
                ScanManager.get().onBlockEntityLoaded(blockEntity));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> ScanManager.get().requestDiscovery());
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> ScanManager.get().requestDiscovery());

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) ContainerMemory.onUseBlock(hitResult.getBlockPos());
            return ActionResult.PASS;
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> HudRenderer.render(drawContext, tickCounter));
        WorldRenderEvents.LAST.register(WorldOverlayRenderer::render);

        LOGGER.info("Base Scanner initialised");
    }

    private static KeyBinding register(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, key, KEY_CATEGORY));
    }

    private void onTick(MinecraftClient client) {
        ScanManager.get().tick(client);
        ContainerMemory.tick(client);
        Navigation.tick(client);
        if (client.player == null) return;
        ModConfig cfg = ConfigManager.get();

        while (openSettings.wasPressed()) {
            client.setScreen(new SettingsScreen(client.currentScreen));
        }
        while (openDetails.wasPressed()) {
            ScanArea focused = ScanManager.get().focusedArea(client.player.getPos());
            client.setScreen(new AreaDetailScreen(client.currentScreen, focused == null ? -1 : focused.id));
        }
        while (toggleInterface.wasPressed()) {
            cfg.interfaceHidden = !cfg.interfaceHidden;
            ConfigManager.save();
            client.player.sendMessage(Text.literal(cfg.interfaceHidden
                    ? "Scanner interface hidden (scanner still running)" : "Scanner interface shown")
                    .formatted(Formatting.AQUA), true);
        }
        while (cycleArea.wasPressed()) {
            ScanManager.get().cycleFocus(client);
        }
        while (clearTarget.wasPressed()) {
            if (Navigation.active()) {
                Navigation.clear();
                client.player.sendMessage(Text.literal("Navigation target cleared").formatted(Formatting.GRAY), true);
            }
        }
        while (scanNow.wasPressed()) {
            ScanManager.get().rescanNow();
            client.player.sendMessage(Text.literal("Rescanning nearby storage…").formatted(Formatting.AQUA), true);
        }
    }
}
