package com.funnyman3595.crowd_computing;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.RegistryObject;

public class WorksiteBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static HashMap<String, RegistryObject<Block>> blocks = new HashMap<String, RegistryObject<Block>>();
	public static HashMap<String, RegistryObject<Item>> items = new HashMap<String, RegistryObject<Item>>();
	public final String name;
	public final JsonObject config;

	public WorksiteBlock(BlockBehaviour.Properties properties, String name, JsonObject config) {
		super(properties);
		this.registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		this.name = name;
		this.config = config;
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult result) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		BlockEntity entity = level.getBlockEntity(pos);
		if (entity instanceof WorksiteBlockEntity) {
			player.openMenu((WorksiteBlockEntity) entity);
		}

		return InteractionResult.CONSUME;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
		return ((WorksiteBlockEntity) builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)).getDrops();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WorksiteBlockEntity(pos, state);
	}

	public static WorksiteBlock load(String name, JsonObject config) {
		Material material = Material.STONE;
		if (config.has("material")) {
			try {
				String material_name = config.get("material").getAsString();
				Field field = Material.class.getField(material_name.toUpperCase());
				material = (Material) field.get(null);
			} catch (Exception e) {
				CrowdComputing.LOGGER.error("Unable to load material for worksite " + name, e);
			}
		}
		return new WorksiteBlock(Properties.of(material).strength(0.3F).noOcclusion().isValidSpawn(WorksiteBlock::never)
				.isRedstoneConductor(WorksiteBlock::never).isSuffocating(WorksiteBlock::never)
				.isViewBlocking(WorksiteBlock::never), name, config);
	}

	private static boolean never(BlockState state, BlockGetter getter, BlockPos pos) {
		return false;
	}

	private static boolean never(BlockState state, BlockGetter getter, BlockPos pos, EntityType<?> type) {
		return false;
	}

	@SuppressWarnings("unchecked")
	protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
			BlockEntityType<A> actual_type, BlockEntityType<E> expected_type,
			BlockEntityTicker<? super E> tick_method) {
		return expected_type == actual_type ? (BlockEntityTicker<A>) tick_method : null;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (!level.isClientSide() && type == WorksiteBlockEntity.block_entities.get(name).get()) {
			return createTickerHelper(type, WorksiteBlockEntity.block_entities.get(name).get(),
					WorksiteBlockEntity::serverTick);
		}
		return null;
	}
}