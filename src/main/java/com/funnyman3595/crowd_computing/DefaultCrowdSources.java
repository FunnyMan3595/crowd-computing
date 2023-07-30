package com.funnyman3595.crowd_computing;

import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.JsonObject;

public record DefaultCrowdSources(String name, int range) {
	public static DefaultCrowdSources BASIC = new DefaultCrowdSources("basic", 8);
	public static DefaultCrowdSources[] ALL = { BASIC };

	public void writeConfig() throws IOException {
		JsonObject default_crowd_source = new JsonObject();
		default_crowd_source.addProperty("range", range);

		FileWriter default_crowd_source_writer = new FileWriter(
				CrowdComputing.CROWD_SOURCES_DIR.resolve("default_" + name + ".json").toFile());
		default_crowd_source_writer.write(CrowdComputing.GSON.toJson(default_crowd_source));
		default_crowd_source_writer.close();
	}
}
