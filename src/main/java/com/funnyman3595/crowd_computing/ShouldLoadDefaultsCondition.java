package com.funnyman3595.crowd_computing;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public class ShouldLoadDefaultsCondition implements ICondition {
	public static final ResourceLocation ID = CrowdComputing.resourceLocation("should_load_defaults");
	public static ShouldLoadDefaultsCondition INSTANCE = new ShouldLoadDefaultsCondition();
	public static Serializer SERIALIZER = new Serializer();

	@Override
	public ResourceLocation getID() {
		return ID;
	}

	@Override
	public boolean test(IContext context) {
		return Config.shouldLoadDefaults.get();
	}

	public static class Serializer implements IConditionSerializer<ShouldLoadDefaultsCondition> {
		@Override
		public void write(JsonObject json, ShouldLoadDefaultsCondition value) {
		}

		@Override
		public ShouldLoadDefaultsCondition read(JsonObject json) {
			return INSTANCE;
		}

		@Override
		public ResourceLocation getID() {
			return ID;
		}
	}
}
