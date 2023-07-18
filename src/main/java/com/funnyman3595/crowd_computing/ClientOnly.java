package com.funnyman3595.crowd_computing;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import noobanidus.mods.miniatures.client.renderer.entity.MiniMeRenderer;

public class ClientOnly {
	public static void init() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		modBus.addListener(ClientOnly::registerEntityRenderers);
	}

	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(CrowdMemberEntity.TYPE, MiniMeRenderer::new);
	}
}
