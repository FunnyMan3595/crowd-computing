package com.funnyman3595.crowd_computing;

import java.util.HashMap;
import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.RegistryObject;

public class CrowdSourceBlock extends Block implements EntityBlock {
	public static HashMap<String, RegistryObject<Block>> blocks = new HashMap<String, RegistryObject<Block>>();
	public static HashMap<String, RegistryObject<Item>> items = new HashMap<String, RegistryObject<Item>>();
	public final String name;
	public final JsonObject config;

	public CrowdSourceBlock(BlockBehaviour.Properties properties, String name, JsonObject config) {
		super(properties);
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
		if (entity instanceof CrowdSourceBlockEntity) {
			player.openMenu((CrowdSourceBlockEntity) entity);
		}

		return InteractionResult.CONSUME;
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
		return ((CrowdSourceBlockEntity) builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)).getDrops();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrowdSourceBlockEntity(pos, state);
	}

	public static CrowdSourceBlock load(String name, JsonObject config) {
		return new CrowdSourceBlock(Properties.of(Material.STONE).strength(0.3F).noOcclusion()
				.isValidSpawn(CrowdSourceBlock::never).isRedstoneConductor(CrowdSourceBlock::never)
				.isSuffocating(CrowdSourceBlock::never).isViewBlocking(CrowdSourceBlock::never), name, config);
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
		if (!level.isClientSide() && type == CrowdSourceBlockEntity.block_entities.get(name).get()) {
			return createTickerHelper(type, CrowdSourceBlockEntity.block_entities.get(name).get(),
					CrowdSourceBlockEntity::serverTick);
		}
		return null;
	}
}
