package com.funnyman3595.crowd_computing;

import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.JsonObject;

public record DefaultWorksites(String name, int upgrade_slot_count, int input_slot_count, int tool_slot_count,
		int output_slot_count, int stack_size, int energy_cap, int fluid_cap) {

	public static DefaultWorksites WOOD = new DefaultWorksites("wood", 0, 1, 1, 1, 1, 0, 0);
	public static DefaultWorksites STONE = new DefaultWorksites("stone", 0, 2, 1, 1, 4, 0, 1000);
	public static DefaultWorksites IRON = new DefaultWorksites("iron", 1, 3, 1, 2, 16, 1000, 4000);
	public static DefaultWorksites GOLD = new DefaultWorksites("gold", 1, 4, 2, 4, 64, 10000, 16000);
	public static DefaultWorksites DIAMOND = new DefaultWorksites("diamond", 2, 5, 2, 6, 64, 100000, 32000);
	public static DefaultWorksites NETHERITE = new DefaultWorksites("netherite", 3, 6, 2, 9, 64, 1000000, 64000);
	public static DefaultWorksites[] ALL = { WOOD, STONE, IRON, GOLD, DIAMOND, NETHERITE };

	public void writeConfig() throws IOException {
		JsonObject default_worksite = new JsonObject();
		default_worksite.addProperty("upgrade_slot_count", upgrade_slot_count);

		JsonObject default_worksite_builtin = new JsonObject();
		default_worksite_builtin.addProperty("input_slot_count", input_slot_count);
		default_worksite_builtin.addProperty("tool_slot_count", tool_slot_count);
		default_worksite_builtin.addProperty("output_slot_count", output_slot_count);
		default_worksite_builtin.addProperty("stack_size", stack_size);
		default_worksite_builtin.addProperty("energy_cap", energy_cap);
		default_worksite_builtin.addProperty("fluid_cap", fluid_cap);
		default_worksite.add("builtin", default_worksite_builtin);

		FileWriter default_worksite_writer = new FileWriter(
				CrowdComputing.WORKSITES_DIR.resolve("default_" + name + ".json").toFile());
		default_worksite_writer.write(CrowdComputing.GSON.toJson(default_worksite));
		default_worksite_writer.close();
	}
}
