package com.funnyman3595.crowd_computing;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import noobanidus.mods.miniatures.client.renderer.entity.MiniMeRenderer;

public class ClientOnly {
	public static void init() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		modBus.addListener(ClientOnly::registerEntityRenderers);
		MinecraftForge.EVENT_BUS.addListener(ClientOnly::renderRegions);
	}

	private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(CrowdMemberEntity.TYPE, MiniMeRenderer::new);
	}

	private static void renderRegions(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
			Minecraft minecraft = Minecraft.getInstance();
			Player player = minecraft.player;
			if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof WandItem
					|| player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof WandItem) {
				RegionRenderer.renderRegions(event);
			}
		}
	}
}
