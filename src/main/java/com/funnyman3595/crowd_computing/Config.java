package com.funnyman3595.crowd_computing;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
	public static ForgeConfigSpec.BooleanValue shouldLoadDefaults;

	public static final Pair<Config, ForgeConfigSpec> COMMON = new ForgeConfigSpec.Builder().configure(Config::new);

	public Config(ForgeConfigSpec.Builder builder) {
		shouldLoadDefaults = builder.comment("Should Crowd Computing's default configuration be loaded?")
				.define("loadDefaults", true);
	}
}
