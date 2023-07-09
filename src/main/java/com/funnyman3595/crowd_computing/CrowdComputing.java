package com.funnyman3595.crowd_computing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CrowdComputing.MODID)
public class CrowdComputing {
	// Define mod id in a common place for everything to reference
	public static final String MODID = "crowd_computing";
	// Directly reference a slf4j logger
	public static final Logger LOGGER = LogUtils.getLogger();
	public static final Gson GSON = new GsonBuilder()
			.registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer()).create();

	public static final Path CONFIG_SUBDIR = FMLPaths.CONFIGDIR.get().resolve(MODID);
	public static final Path WORKSITES_DIR = CONFIG_SUBDIR.resolve("worksites");

	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
			.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

	public CrowdComputing() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON.getRight());

		try {
			if (!Files.isDirectory(WORKSITES_DIR)) {
				Files.createDirectories(WORKSITES_DIR);

				for (DefaultWorksites worksite : DefaultWorksites.ALL) {
					worksite.writeConfig();
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Unable to create config directories.", e);
		}

		try {
			Files.list(WORKSITES_DIR).forEach(path -> {
				String[] split = path.getFileName().toString().split("\\.", 2);
				if (split.length != 2 || !split[1].equalsIgnoreCase("json")) {
					return;
				}
				String name = "worksite/" + split[0].toLowerCase();

				JsonObject tree;
				try {
					tree = GSON.fromJson(new FileReader(path.toFile()), JsonObject.class);
				} catch (IOException e) {
					LOGGER.error("Unable to load worksite file " + path, e);
					return;
				}
				WorksiteBlock.blocks.put(name, BLOCKS.register(name, () -> WorksiteBlock.load(name, tree)));
				WorksiteBlockEntity.block_entities.put(name,
						BLOCK_ENTITY_TYPES.register(name, () -> BlockEntityType.Builder
								.of(WorksiteBlockEntity::new, WorksiteBlock.blocks.get(name).get()).build(null)));
				WorksiteBlock.items.put(name, ITEMS.register(name,
						() -> new BlockItem(WorksiteBlock.blocks.get(name).get(), new Item.Properties())));
			});
		} catch (IOException e) {
			throw new RuntimeException("Unable to read worksite directory.", e);
		}
		
		MENU_TYPES.register("worksite", () -> new MenuType<WorksiteBlockMenu>(WorksiteBlockMenu::new));
		
		BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
		ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
		BLOCK_ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
		MENU_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());

		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerConditionSerializers);
		MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
		MinecraftForge.EVENT_BUS.addListener(this::addReloadListeners);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
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

	private void addReloadListeners(final AddReloadListenerEvent event) {
	}

	private void clientSetup(final FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			MenuScreens.register(WorksiteBlockMenu.TYPE.get(), WorksiteBlockScreen::new);
		});
	}
}
