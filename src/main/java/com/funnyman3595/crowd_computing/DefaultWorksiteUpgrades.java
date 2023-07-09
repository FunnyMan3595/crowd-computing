package com.funnyman3595.crowd_computing;

import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.JsonObject;

public record DefaultWorksiteUpgrades(String name, int input_slot_count,
		int tool_slot_count, int output_slot_count, int stack_size) {
	public static DefaultWorksiteUpgrades EVERYTHING = new DefaultWorksiteUpgrades("everything", 6, 2, 9, 64);
	public static DefaultWorksiteUpgrades[] ALL = {EVERYTHING}; 

	public void writeConfig() throws IOException {
		JsonObject default_worksite_upgrade = new JsonObject();
		default_worksite_upgrade.addProperty("input_slot_count", input_slot_count);
		default_worksite_upgrade.addProperty("tool_slot_count", tool_slot_count);
		default_worksite_upgrade.addProperty("output_slot_count", output_slot_count);
		default_worksite_upgrade.addProperty("stack_size", stack_size);

		FileWriter default_worksite_upgrade_writer = new FileWriter(
				CrowdComputing.WORKSITE_UPGRADES_DIR.resolve("default_" + name + ".json").toFile());
		default_worksite_upgrade_writer.write(CrowdComputing.GSON.toJson(default_worksite_upgrade));
		default_worksite_upgrade_writer.close();
	}

}
