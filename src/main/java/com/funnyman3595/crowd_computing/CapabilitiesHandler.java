package com.funnyman3595.crowd_computing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD;

@Mod.EventBusSubscriber(modid = CrowdComputing.MODID)
public class CapabilitiesHandler {
	private static final ResourceLocation WEB_LINK = CrowdComputing.resourceLocation("web_link");

	@Mod.EventBusSubscriber(modid = CrowdComputing.MODID, bus = MOD)
	private static final class Setup {
		@SubscribeEvent
		public static void registerCapabilities(RegisterCapabilitiesEvent event) {
			event.register(WebLink.class);
		}
	}

	@SubscribeEvent
	public static void attachPlayerCapability(AttachCapabilitiesEvent<Entity> event) {
		if (!(event.getObject() instanceof Player))
			return;

		event.addCapability(WEB_LINK, new WebLink());
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		event.getOriginal().reviveCaps();
		WebLink old = WebLink.get(event.getOriginal());
		WebLink clone = WebLink.get(event.getEntity());
		clone.deserializeNBT(old.serializeNBT());
		event.getOriginal().invalidateCaps();
	}
}