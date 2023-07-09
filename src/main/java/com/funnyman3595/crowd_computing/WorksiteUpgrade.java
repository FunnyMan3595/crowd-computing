package com.funnyman3595.crowd_computing;

import java.util.HashMap;

import com.google.gson.JsonObject;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

public record WorksiteUpgrade(int input_slot_count, int tool_slot_count, int output_slot_count, int stack_size) {
	public static HashMap<String, RegistryObject<Item>> items = new HashMap<String, RegistryObject<Item>>();

	public static WorksiteUpgrade load(String name, JsonObject config) {
		return new WorksiteUpgrade(loadInt(name, config, "input_slot_count", 0),
				loadInt(name, config, "tool_slot_count", 0), loadInt(name, config, "output_slot_count", 0),
				loadInt(name, config, "stack_size", 1));
	}

	public static int loadInt(String name, JsonObject config, String key, int default_value) {
		if (config.has(key)) {
			try {
				return config.get(key).getAsInt();
			} catch (Exception e) {
				CrowdComputing.LOGGER.error("Unable to load integer key " + key + " for upgrade " + name);
			}
		}
		return default_value;
	}
}
