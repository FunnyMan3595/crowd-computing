package com.funnyman3595.crowd_computing;

import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.JsonObject;

public record DefaultWorksiteUpgrades(String name, int input_slot_count, int tool_slot_count, int output_slot_count,
		int stack_size, int energy_cap, int fluid_cap) {

	public static DefaultWorksiteUpgrades EVERYTHING = new DefaultWorksiteUpgrades("everything", 6, 2, 9, 64, 100000,
			64000);
	public static DefaultWorksiteUpgrades[] ALL = { EVERYTHING };

	public void writeConfig() throws IOException {
		JsonObject default_worksite_upgrade = new JsonObject();
		default_worksite_upgrade.addProperty("input_slot_count", input_slot_count);
		default_worksite_upgrade.addProperty("tool_slot_count", tool_slot_count);
		default_worksite_upgrade.addProperty("output_slot_count", output_slot_count);
		default_worksite_upgrade.addProperty("stack_size", stack_size);
		default_worksite_upgrade.addProperty("energy_cap", energy_cap);
		default_worksite_upgrade.addProperty("fluid_cap", fluid_cap);

		FileWriter default_worksite_upgrade_writer = new FileWriter(
				CrowdComputing.WORKSITE_UPGRADES_DIR.resolve("default_" + name + ".json").toFile());
		default_worksite_upgrade_writer.write(CrowdComputing.GSON.toJson(default_worksite_upgrade));
		default_worksite_upgrade_writer.close();
	}

}
