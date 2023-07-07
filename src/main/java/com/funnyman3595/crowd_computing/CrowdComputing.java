package com.funnyman3595.crowd_computing;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CrowdComputing.MODID)
public class CrowdComputing {
	// Define mod id in a common place for everything to reference
	public static final String MODID = "crowd_computing";
	// Directly reference a slf4j logger
	public static final Logger LOGGER = LogUtils.getLogger();

	public CrowdComputing() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON.getRight());

		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerConditionSerializers);
		MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
	}

	public static ResourceLocation resourceLocation(String path) {
		return new ResourceLocation(MODID, path);
	}

	private void registerCommands(final RegisterCommandsEvent event) {
	}

	private void registerConditionSerializers(final RegisterEvent event) {
		if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
			LOGGER.info("Loading serializer.");
			event.register(ForgeRegistries.Keys.RECIPE_SERIALIZERS,
					helper -> CraftingHelper.register(ShouldLoadDefaultsCondition.SERIALIZER));
		}
	}
}
