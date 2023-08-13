package com.funnyman3595.crowd_computing;

import java.util.HashMap;

import com.funnyman3595.crowd_computing.BlockSelector.Region;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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

	public static void receive_one_region(String dimension, Region region) {
		if (!RegionRenderer.client_region_cache.containsKey(dimension)) {
			RegionRenderer.client_region_cache.put(dimension, new HashMap<String, BlockSelector.Region>());
		}
		RegionRenderer.client_region_cache.get(dimension).put(region.name, region);
	}

	public static void receive_all_regions(HashMap<String, HashMap<String, BlockSelector.Region>> regions) {
		RegionRenderer.client_region_cache = regions;
	}
}
