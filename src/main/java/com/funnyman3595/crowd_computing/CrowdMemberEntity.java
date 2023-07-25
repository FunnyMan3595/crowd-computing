package com.funnyman3595.crowd_computing;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import noobanidus.mods.miniatures.entity.MiniMeEntity;

public class CrowdMemberEntity extends MiniMeEntity implements WorksiteBlockEntity.Worker {
	public static final float CLOSE_TO_BLOCK_DISTANCE = 2f;

	public BlockPos targetBlock = BlockPos.ZERO;
	public CrowdTask task = CrowdTask.DIE;

	public BlockPos parent_pos = null;
	public CrowdSourceBlockEntity parent = null;

	public static final EntityType<CrowdMemberEntity> TYPE = EntityType.Builder
			.of(CrowdMemberEntity::new, MobCategory.CREATURE).sized(0.5f, 0.5f)
			.build(CrowdComputing.MODID + ":crowd_member");

	public CrowdMemberEntity(EntityType<? extends CrowdMemberEntity> type, Level world) {
		super(type, world);
		setMiniScale(0.5f);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new CheckParentGoal(this));
		this.goalSelector.addGoal(2, new LoadTaskGoal(this));
		this.goalSelector.addGoal(3, new DoWorkGoal(this));
		this.goalSelector.addGoal(4, new FloatGoal(this));
		this.goalSelector.addGoal(5, new MoveToTargetBlockGoal(this));
	}

	@Override
	public boolean isValid(WorksiteBlockEntity entity) {
		if (dead) {
			return false;
		}
		if (!entity.getBlockPos().equals(targetBlock)) {
			return false;
		}
		Vec3 pos = position();
		return targetBlock.getX() == (int) Math.floor(pos.x) && targetBlock.getY() == (int) Math.floor(pos.y)
				&& targetBlock.getZ() == (int) Math.floor(pos.z);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() instanceof Player) {
			Player player = (Player) source.getEntity();
			ItemStack weapon = player.getMainHandItem();
			if (!weapon.isEmpty() && weapon.getItem() instanceof WandItem) {
				kill();
				return true;
			}
		}

		return super.hurt(source, amount);
	}

	protected PathNavigation createNavigation(Level level) {
		return new AmphibiousPathNavigation(this, level);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);

		task = CrowdTask.load_nbt(tag.getCompound("task"));

		if (tag.contains("parent_x")) {
			parent_pos = new BlockPos(tag.getInt("parent_x"), tag.getInt("parent_y"), tag.getInt("parent_z"));
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);

		tag.put("task", task.save_to_nbt());

		if (parent_pos != null) {
			tag.putInt("parent_x", parent_pos.getX());
			tag.putInt("parent_y", parent_pos.getY());
			tag.putInt("parent_z", parent_pos.getZ());
		}
	}

	public static class CheckParentGoal extends Goal {
		public final CrowdMemberEntity mob;

		public CheckParentGoal(CrowdMemberEntity mob) {
			this.mob = mob;
		}

		@Override
		public boolean canUse() {
			return mob.parent_pos != null;
		}

		@Override
		public void tick() {
			if (mob.level.isClientSide) {
				return;
			}

			if (mob.parent != null && !mob.parent.isRemoved()) {
				return;
			}

			if (mob.level.isLoaded(mob.parent_pos)) {
				BlockEntity raw_parent = mob.level.getBlockEntity(mob.parent_pos);
				if (raw_parent == null || !(raw_parent instanceof CrowdSourceBlockEntity)) {
					mob.task = CrowdTask.DIE;
				} else {
					mob.parent = (CrowdSourceBlockEntity) raw_parent;
				}
			}
		}
	}

	public static class LoadTaskGoal extends Goal {
		public final CrowdMemberEntity mob;

		public LoadTaskGoal(CrowdMemberEntity mob) {
			this.mob = mob;
		}

		@Override
		public boolean canUse() {
			return mob.targetBlock == null || mob.targetBlock == BlockPos.ZERO;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (mob.level.isClientSide) {
				return;
			}

			mob.task.init(mob);
		}
	}

	public static class DoWorkGoal extends Goal {
		public final CrowdMemberEntity mob;

		public DoWorkGoal(CrowdMemberEntity mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return mob.targetBlock == null || (mob.targetBlock != BlockPos.ZERO
					&& mob.targetBlock.closerToCenterThan(mob.position(), CLOSE_TO_BLOCK_DISTANCE));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (mob.level.isClientSide) {
				return;
			}

			mob.task.run(mob, this);
		}
	}

	public static class MoveToTargetBlockGoal extends Goal {
		public final CrowdMemberEntity mob;
		public int tick = 0;

		public MoveToTargetBlockGoal(CrowdMemberEntity mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return mob.targetBlock != null && mob.targetBlock != BlockPos.ZERO
					&& !mob.targetBlock.closerToCenterThan(mob.position(), CLOSE_TO_BLOCK_DISTANCE);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (mob.level.isClientSide || mob.targetBlock == null) {
				return;
			}

			if (tick == 0) {
				mob.setNoGravity(false);

				Set<BlockPos> acceptable_positions = new HashSet<BlockPos>();
				for (Direction d : Direction.values()) {
					acceptable_positions.add(mob.targetBlock.relative(d));
				}
				mob.getNavigation().setCanFloat(true);
				mob.getNavigation().moveTo(mob.getNavigation().createPath(acceptable_positions, 0), 1.0);
			}

			tick = (tick + 1) % 40;
		}
	}
}
