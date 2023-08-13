package com.funnyman3595.crowd_computing;

import java.util.HashMap;

import com.funnyman3595.crowd_computing.BlockSelector.Region;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public class CrowdComputingChannelClient {
	public static void set_worksite_message(Component message) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof WorksiteBlockScreen) {
			((WorksiteBlockScreen) minecraft.screen).set_worksite_message(message);
		}
	}

	public static void open_name_region_screen() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.setScreen(new NameRegionScreen());
	}

	public static void on_auth_secret_ack(boolean valid) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof CrowdSourceBlockScreen) {
			((CrowdSourceBlockScreen) minecraft.screen).remake_paste_secret(valid);
		}
	}

	public static void receive_one_region(Region region) {
		CrowdComputing.onMainThread(() -> {
			if (!RegionRenderer.client_region_cache.containsKey(region.dimension)) {
				RegionRenderer.client_region_cache.put(region.dimension, new HashMap<Integer, BlockSelector.Region>());
			}
			RegionRenderer.client_region_cache.get(region.dimension).put(region.id, region);
		});
	}

	public static void delete_one_region(Region region) {
		CrowdComputing.onMainThread(() -> {
			if (!RegionRenderer.client_region_cache.containsKey(region.dimension)) {
				RegionRenderer.client_region_cache.put(region.dimension, new HashMap<Integer, BlockSelector.Region>());
			}
			RegionRenderer.client_region_cache.get(region.dimension).remove(region.id);

			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
					ForgeRegistries.SOUND_EVENTS.getValue((new ResourceLocation("minecraft:entity.chicken.egg"))), 1));
		});
	}

	public static void receive_all_regions(HashMap<String, HashMap<Integer, Region>> regions) {
		CrowdComputing.onMainThread(() -> {
			RegionRenderer.client_region_cache = regions;
		});
	}
}
