package com.funnyman3595.crowd_computing;

import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.JsonObject;

public record DefaultCraftingItems(String name) {
	public static DefaultCraftingItems DIRT_BALL = new DefaultCraftingItems("dirt_ball");
	public static DefaultCraftingItems[] ALL = { DIRT_BALL };

	public void writeConfig() throws IOException {
		JsonObject default_crafting_item = new JsonObject();
		FileWriter default_crafting_item_writer = new FileWriter(
				CrowdComputing.CRAFTING_ITEMS_DIR.resolve("default_" + name + ".json").toFile());
		default_crafting_item_writer.write(CrowdComputing.GSON.toJson(default_crafting_item));
		default_crafting_item_writer.close();
	}

}
