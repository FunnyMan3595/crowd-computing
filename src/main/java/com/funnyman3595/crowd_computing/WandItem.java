package com.funnyman3595.crowd_computing;

import java.awt.Color;
import java.time.Clock;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public class WandItem extends Item {
	BlockPos start = null;
	BlockPos end = null;

	public WandItem() {
		super(new Properties().stacksTo(1).tab(CreativeModeTab.TAB_MISC));
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

	public void finishRegion(Player player, String name, boolean overwrite) {
		int color = Color.HSBtoRGB(player.level.random.nextFloat(), 1, 1);
		WebLink.get(player).add_region(player, start, end, name, color, overwrite, (v) -> {
			player.sendSystemMessage(Component.translatable("crowd_computing.region_created", name));
		}, (e) -> {
			player.sendSystemMessage(Component.translatable("crowd_computing.link_failed", e));
		});
		start = null;
		end = null;
	}
	
	@Override
	public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
		return true;
	}
	
	@Override
	public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
		if (!(entity instanceof Player)) {
			return false;
		}
		
		Player player = (Player) entity;
		String dimension = player.level.dimension().location().toString();
		HashMap<Integer, BlockSelector.Region> dimension_regions;
		if (player.level.isClientSide) {	
			if (!RegionRenderer.client_region_cache.containsKey(dimension)) {
				return false;
			}
			dimension_regions = RegionRenderer.client_region_cache.get(dimension);
		} else {
		WebLink link = WebLink.get(player);
		if (!link.has_auth_secret()) {
			return false;
		}
		if (!link.regions.containsKey(dimension)) {
			return false;
		}
		dimension_regions = link.regions.get(dimension);
		}
		
		Vec3 eye = player.getEyePosition();
		Vec3 look = eye.add(player.getLookAngle().multiply(4, 4, 4));
		
		double best_distance_sq = -1;
		BlockSelector.Region best_region = null;
		for (BlockSelector.Region region : dimension_regions.values()) {
			AABB box = new AABB(region.getMinX(), region.getMinY(), region.getMinZ(), region.getMinX() + region.x_size(), region.getMinY() + region.y_size(), region.getMinZ() + region.z_size());
			Optional<Vec3> clip = box.clip(eye, look);
			if (clip.isEmpty()) {
				continue;
			}
			
			double distance_sq = clip.get().subtract(eye).lengthSqr();
			if (best_distance_sq == -1 || distance_sq < best_distance_sq) {
				best_distance_sq = distance_sq;
				best_region = region;
			}
		}
		
		if (best_region != null) {
			if (player.level.isClientSide) {	
				RegionRenderer.breaking_region(best_region);
			} else {
				WebLink link = WebLink.get(player);
				link.breaking_region(player, best_region);
			}
		}
		return false;
	}
}
