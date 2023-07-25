package com.funnyman3595.crowd_computing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.PacketDistributor;

public class WandItem extends Item {
	BlockPos start = null;
	BlockPos end = null;

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
		Player player = ctx.getPlayer();
		BlockEntity raw_entity = ctx.getLevel().getBlockEntity(pos);
		if (raw_entity instanceof CrowdSourceBlockEntity) {
			((CrowdSourceBlockEntity) raw_entity).requestSpawn(player);
			return InteractionResult.SUCCESS;
		}

		if (start == null) {
			start = pos;
			player.sendSystemMessage(Component.translatable("crowd_computing.wand_start"));
			return InteractionResult.SUCCESS;
		}

		end = pos;
		CrowdComputingChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
				new CrowdComputingChannel.OpenNameRegionScreen());

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

	public void finishRegion(Player player, String name) {
		WebLink.get(player).add_region(start, end, name, (v) -> {
			player.sendSystemMessage(Component.translatable("crowd_computing.region_created", name));
		}, (e) -> {
			player.sendSystemMessage(Component.translatable("crowd_computing.link_failed", e));
		});
		start = null;
		end = null;
	}
}
