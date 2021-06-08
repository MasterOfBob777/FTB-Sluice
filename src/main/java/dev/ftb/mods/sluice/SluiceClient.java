package dev.ftb.mods.sluice;

import dev.ftb.mods.sluice.block.SluiceBlockEntities;
import dev.ftb.mods.sluice.renderer.SluiceRenderer;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class SluiceClient {
    public static void init() {
        ClientRegistry.bindTileEntityRenderer(SluiceBlockEntities.OAK_SLUICE.get(), SluiceRenderer::new);
        ClientRegistry.bindTileEntityRenderer(SluiceBlockEntities.IRON_SLUICE.get(), SluiceRenderer::new);
        ClientRegistry.bindTileEntityRenderer(SluiceBlockEntities.DIAMOND_SLUICE.get(), SluiceRenderer::new);
        ClientRegistry.bindTileEntityRenderer(SluiceBlockEntities.NETHERITE_SLUICE.get(), SluiceRenderer::new);
    }
}
