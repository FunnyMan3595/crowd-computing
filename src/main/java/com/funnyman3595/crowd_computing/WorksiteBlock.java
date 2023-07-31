package com.funnyman3595.crowd_computing;

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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.RegistryObject;

public class WorksiteBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	public static HashMap<String, RegistryObject<Block>> blocks = new HashMap<String, RegistryObject<Block>>();
	public static HashMap<String, RegistryObject<Item>> items = new HashMap<String, RegistryObject<Item>>();
	public final String name;
	public final JsonObject config;

	public WorksiteBlock(BlockBehaviour.Properties properties, String name, JsonObject config) {
		super(properties);
		this.registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
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
		builder.add(FACING, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = this.defaultBlockState();
		state = state.setValue(FACING, context.getHorizontalDirection());
		FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
		state = state.setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
		return state;
	}

	@SuppressWarnings("deprecation")
	@Override
	public FluidState getFluidState(BlockState state) {
		if (state.getValue(WATERLOGGED)) {
			return Fluids.WATER.getSource(false);
		}
		return super.getFluidState(state);
	}

	@SuppressWarnings("deprecation")
	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState other_state, LevelAccessor accessor,
			BlockPos pos, BlockPos other_pos) {
		if (state.getValue(WATERLOGGED)) {
			accessor.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(accessor));
		}

		((WorksiteBlockEntity) accessor.getBlockEntity(pos)).should_recheck_recipe = true;

		return super.updateShape(state, direction, other_state, accessor, pos, other_pos);
	}

	@Override
	public boolean placeLiquid(LevelAccessor accessor, BlockPos pos, BlockState state, FluidState fluid_state) {
		((WorksiteBlockEntity) accessor.getBlockEntity(pos)).should_recheck_recipe = true;

		return SimpleWaterloggedBlock.super.placeLiquid(accessor, pos, state, fluid_state);
	}

	@Override
	public ItemStack pickupBlock(LevelAccessor accessor, BlockPos pos, BlockState state) {
		((WorksiteBlockEntity) accessor.getBlockEntity(pos)).should_recheck_recipe = true;

		return SimpleWaterloggedBlock.super.pickupBlock(accessor, pos, state);
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
		return new WorksiteBlock(Properties.of(Material.STONE).strength(0.3F).noOcclusion()
				.isValidSpawn(WorksiteBlock::never).isRedstoneConductor(WorksiteBlock::never)
				.isSuffocating(WorksiteBlock::never).isViewBlocking(WorksiteBlock::never), name, config);
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
