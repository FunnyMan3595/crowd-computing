package com.funnyman3595.crowd_computing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.util.LogicalSidedProvider;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.social.PlayerEntry;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CrowdComputing.MODID)
public class CrowdComputing {
	// Define mod id in a common place for everything to reference
	public static final String MODID = "crowd_computing";
	// Directly reference a slf4j logger
	public static final Logger LOGGER = LogUtils.getLogger();
	public static final Gson GSON = new GsonBuilder()
			.registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
			.registerTypeAdapter(WorksiteRecipe.Outputs.class, new WorksiteRecipe.Outputs.Serializer())
			.registerTypeAdapter(Component.class, new Component.Serializer()).create();

	public static final Path CONFIG_SUBDIR = FMLPaths.CONFIGDIR.get().resolve(MODID);
	public static final Path WORKSITES_DIR = CONFIG_SUBDIR.resolve("worksites");
	public static final Path CROWD_SOURCES_DIR = CONFIG_SUBDIR.resolve("crowd_sources");
	public static final Path WORKSITE_UPGRADES_DIR = CONFIG_SUBDIR.resolve("worksite_upgrades");
	public static final Path CRAFTING_ITEMS_DIR = CONFIG_SUBDIR.resolve("crafting_items");

	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
			.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
			MODID);
	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister
			.create(ForgeRegistries.RECIPE_TYPES, MODID);
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
			.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister
			.create(ForgeRegistries.ENTITY_TYPES, MODID);
	public static final RegistryObject<Item> NOTICE_ITEM = ITEMS.register("notice",
			() -> new Item(new Item.Properties()));

	public static final Capability<WebLink> WEB_LINK = CapabilityManager.get(new CapabilityToken<>() {
	});

	public static MinecraftServer SERVER = null;

	public CrowdComputing() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON.getRight());

		try {
			if (!Files.isDirectory(CONFIG_SUBDIR)) {
				Files.createDirectories(WORKSITES_DIR);
				for (DefaultWorksites worksite : DefaultWorksites.ALL) {
					worksite.writeConfig();
				}
				Files.createDirectories(CROWD_SOURCES_DIR);
				for (DefaultCrowdSources worksite : DefaultCrowdSources.ALL) {
					worksite.writeConfig();
				}
				Files.createDirectories(WORKSITE_UPGRADES_DIR);
				for (DefaultWorksiteUpgrades worksite_upgrade : DefaultWorksiteUpgrades.ALL) {
					worksite_upgrade.writeConfig();
				}
				Files.createDirectories(CRAFTING_ITEMS_DIR);
				for (DefaultCraftingItems crafting_item : DefaultCraftingItems.ALL) {
					crafting_item.writeConfig();
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
				WorksiteBlock.items.put(name,
						ITEMS.register(name, () -> new BlockItem(WorksiteBlock.blocks.get(name).get(),
								new Item.Properties().tab(CreativeModeTab.TAB_DECORATIONS))));
			});
		} catch (Exception e) {
			throw new RuntimeException("Unable to read worksites directory.", e);
		}

		try {
			Files.list(CROWD_SOURCES_DIR).forEach(path -> {
				String[] split = path.getFileName().toString().split("\\.", 2);
				if (split.length != 2 || !split[1].equalsIgnoreCase("json")) {
					return;
				}
				String name = "crowd_source/" + split[0].toLowerCase();

				JsonObject tree;
				try {
					tree = GSON.fromJson(new FileReader(path.toFile()), JsonObject.class);
				} catch (IOException e) {
					LOGGER.error("Unable to load crowd source file " + path, e);
					return;
				}
				CrowdSourceBlock.blocks.put(name, BLOCKS.register(name, () -> CrowdSourceBlock.load(name, tree)));
				CrowdSourceBlockEntity.block_entities.put(name,
						BLOCK_ENTITY_TYPES.register(name, () -> BlockEntityType.Builder
								.of(CrowdSourceBlockEntity::new, CrowdSourceBlock.blocks.get(name).get()).build(null)));
				CrowdSourceBlock.items.put(name,
						ITEMS.register(name, () -> new BlockItem(CrowdSourceBlock.blocks.get(name).get(),
								new Item.Properties().tab(CreativeModeTab.TAB_DECORATIONS))));
			});
		} catch (Exception e) {
			throw new RuntimeException("Unable to read crowd sources directory.", e);
		}

		try {
			Files.list(WORKSITE_UPGRADES_DIR).forEach(path -> {
				String[] split = path.getFileName().toString().split("\\.", 2);
				if (split.length != 2 || !split[1].equalsIgnoreCase("json")) {
					return;
				}
				String name = "worksite_upgrade/" + split[0].toLowerCase();

				JsonObject tree;
				try {
					tree = GSON.fromJson(new FileReader(path.toFile()), JsonObject.class);
				} catch (IOException e) {
					LOGGER.error("Unable to load worksite upgrade file " + path, e);
					return;
				}
				WorksiteUpgrade.items.put(name,
						ITEMS.register(name, () -> new WorksiteUpgradeItem(WorksiteUpgrade.load(name, tree))));
			});
		} catch (Exception e) {
			throw new RuntimeException("Unable to read worksite upgrades directory.", e);
		}

		try {
			Files.list(CRAFTING_ITEMS_DIR).forEach(path -> {
				String[] split = path.getFileName().toString().split("\\.", 2);
				if (split.length != 2 || !split[1].equalsIgnoreCase("json")) {
					return;
				}
				String name = "crafting_item/" + split[0].toLowerCase();

				@SuppressWarnings("unused")
				JsonObject tree;
				try {
					tree = GSON.fromJson(new FileReader(path.toFile()), JsonObject.class);
				} catch (IOException e) {
					LOGGER.error("Unable to load worksite upgrade file " + path, e);
					return;
				}
				WorksiteUpgrade.items.put(name,
						ITEMS.register(name, () -> new Item(new Item.Properties().tab(CreativeModeTab.TAB_MISC))));
			});
		} catch (Exception e) {
			throw new RuntimeException("Unable to read crafting items directory.", e);
		}

		MENU_TYPES.register("worksite", () -> new MenuType<WorksiteBlockMenu>(WorksiteBlockMenu::new));
		MENU_TYPES.register("crowd_source", () -> new MenuType<CrowdSourceBlockMenu>(CrowdSourceBlockMenu::new));
		RECIPE_TYPES.register("worksite", () -> {
			RecipeType<WorksiteRecipe> recipe = RecipeType.simple(new ResourceLocation(MODID, "worksite"));
			return recipe;
		});
		RECIPE_SERIALIZERS.register("worksite", () -> WorksiteRecipe.SERIALIZER);
		ITEMS.register("wand", () -> new WandItem());

		ENTITY_TYPES.register("crowd_member", () -> CrowdMemberEntity.TYPE);

		BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
		ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
		BLOCK_ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
		MENU_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
		RECIPE_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
		RECIPE_SERIALIZERS.register(FMLJavaModLoadingContext.get().getModEventBus());
		ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());

		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerConditionSerializers);
		MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
		MinecraftForge.EVENT_BUS.addListener(this::addReloadListeners);
		MinecraftForge.EVENT_BUS.addListener(this::onServerStart);
		MinecraftForge.EVENT_BUS.addListener(this::onPlayerConnected);
		MinecraftForge.EVENT_BUS.addListener(this::onPlayerTick);
		MinecraftForge.EVENT_BUS.addListener(this::onChunkLoad);
		MinecraftForge.EVENT_BUS.addListener(this::onBlockUpdate);
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onBlockDestroy);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerAttributes);

		CrowdComputingChannel.init();

		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientOnly::init);
	}

	public static ResourceLocation resourceLocation(String path) {
		return new ResourceLocation(MODID, path);
	}

	private void registerCommands(final RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("crowd_computing").then(Commands.literal("spawn_config")
				.then(Commands.argument("mini_config_name", StringArgumentType.string()).executes(ctx -> {
					if (!(ctx.getSource().getEntity() instanceof Player)) {
						ctx.getSource()
								.sendSystemMessage(Component.translatable("crowd_computing.player_only_command"));
					}
					Player player = (Player) ctx.getSource().getEntity();
					String[] names = { StringArgumentType.getString(ctx, "mini_config_name") };
					WebLink.get(player).get_specific(player, names, (configs) -> {
						if (configs.configs().length == 0) {
							player.sendSystemMessage(
									Component.translatable("crowd_computing.no_such_config", names[0]));
						}
						onMainThread(() -> {
							CrowdSourceBlockEntity.spawn_at_nearest(player, configs.configs()[0]);
						});
					}, (error) -> {
						ctx.getSource().sendSystemMessage(Component.literal(error.toString()));
					});
					return 0;
				}))));
	}

	private void registerConditionSerializers(final RegisterEvent event) {
		if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
			event.register(ForgeRegistries.Keys.RECIPE_SERIALIZERS,
					helper -> CraftingHelper.register(ShouldLoadDefaultsCondition.SERIALIZER));
		}
	}

	private void addReloadListeners(final AddReloadListenerEvent event) {
		event.addListener(WorksiteRecipe.RECIPES);
	}

	private void clientSetup(final FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			MenuScreens.register(WorksiteBlockMenu.TYPE.get(), WorksiteBlockScreen::new);
			MenuScreens.register(CrowdSourceBlockMenu.TYPE.get(), CrowdSourceBlockScreen::new);
		});
	}

	private void onServerStart(final ServerStartedEvent event) {
		SERVER = event.getServer();
	}

	private void onPlayerConnected(final PlayerLoggedInEvent event) {
		Player player = event.getEntity();
		CrowdComputingChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
				new CrowdComputingChannel.SyncAllRegions(WebLink.get(player).regions));
	}

	private void onPlayerTick(final PlayerTickEvent event) {
		WebLink link = WebLink.get(event.player);
		if (!link.has_auth_secret()) {
			return;
		}
		link.tick = (link.tick + 1) % 20;
		if (link.tick == 0) {
			link.get_updated_regions(event.player, (v) -> {
			}, (e) -> {
			});
		}
	}

	private void registerAttributes(final EntityAttributeCreationEvent event) {
		event.put(CrowdMemberEntity.TYPE, CrowdMemberEntity.attributes().build());
	}

	private void onChunkLoad(final ChunkEvent.Load event) {
		CrowdSourceBlockEntity.delayed_mark_dirty((Level) event.getLevel(), event.getChunk().getPos());
	}

	private void onBlockUpdate(final BlockEvent.NeighborNotifyEvent event) {
		CrowdSourceBlockEntity.mark_dirty((Level) event.getLevel(), event.getPos());
	}

	private void onBlockDestroy(final BlockEvent.BreakEvent event) {
		CrowdSourceBlockEntity.on_block_broken((Level) event.getLevel(), event.getPos());
	}

	public static CompletableFuture<Void> onMainThread(Runnable runnable) {
		BlockableEventLoop<?> executor = LogicalSidedProvider.WORKQUEUE.get(LogicalSide.SERVER);
		if (!executor.isSameThread()) {
			return executor.submitAsync(runnable);
		} else {
			runnable.run();
			return CompletableFuture.completedFuture(null);
		}
	}
}
