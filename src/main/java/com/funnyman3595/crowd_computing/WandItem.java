package com.funnyman3595.crowd_computing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

public class WandItem extends Item {
	BlockPos crowd_source = null;
	BlockPos start = null;

	public WandItem() {
		super(new Properties().stacksTo(1));
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		BlockPos pos = ctx.getClickedPos();
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		Player player = ctx.getPlayer();

		if (block instanceof CrowdSourceBlock) {
			crowd_source = pos;
			player.sendSystemMessage(Component.translatable("crowd_computing.wand_source"));
			return InteractionResult.SUCCESS;
		}

		BlockEntity block_entity = level.getBlockEntity(pos);
		if (block_entity != null && block_entity instanceof Container) {
			if (start == null) {
				start = pos;
				player.sendSystemMessage(Component.translatable("crowd_computing.wand_start"));
				return InteractionResult.SUCCESS;
			}

			CrowdMemberEntity entity = new CrowdMemberEntity(CrowdMemberEntity.TYPE, level);
			if (crowd_source == null) {
				entity.setPos(player.getPosition(0));
			} else {
				entity.setPos(crowd_source.getX() + 0.5, crowd_source.getY() + 1, crowd_source.getZ() + 0.5);
			}
			entity.parent_pos = crowd_source;
			entity.task = new CrowdTask.MoveStuff(new BlockSelector.Single(start), new BlockSelector.Single(pos));
			level.addFreshEntity(entity);
			player.sendSystemMessage(Component.translatable("crowd_computing.wand_end"));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
		if (hit.getType() == HitResult.Type.BLOCK) {
			return InteractionResultHolder.pass(stack);
		} else {
			if (!level.isClientSide) {
				start = null;
				player.sendSystemMessage(Component.translatable("crowd_computing.wand_clear"));
			}
			return InteractionResultHolder.success(stack);
		}
	}
}
