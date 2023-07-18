package com.funnyman3595.crowd_computing;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noobanidus.mods.miniatures.entity.MiniMeEntity;

public class CrowdMemberEntity extends MiniMeEntity implements WorksiteBlockEntity.Worker {
	public static final float CLOSE_TO_BLOCK_DISTANCE = 1.625f;

	public BlockPos targetBlock = BlockPos.ZERO;
	public CrowdTask task = CrowdTask.DIE;
	public static final EntityType<CrowdMemberEntity> TYPE = EntityType.Builder
			.of(CrowdMemberEntity::new, MobCategory.CREATURE).sized(0.5f, 0.5f)
			.build(CrowdComputing.MODID + ":crowd_member");

	public CrowdMemberEntity(EntityType<? extends CrowdMemberEntity> type, Level world) {
		super(type, world);
		setMiniScale(0.5f);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LoadTaskGoal(this));
		this.goalSelector.addGoal(2, new DoWorkGoal(this));
		this.goalSelector.addGoal(3, new FloatGoal(this));
		this.goalSelector.addGoal(4, new MoveToTargetBlockGoal(this));
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
	public void load(CompoundTag tag) {
		super.load(tag);

		if (tag.contains("worksite")) {
			CompoundTag worksite_tag = tag.getCompound("worksite");
			BlockPos worksite = new BlockPos(worksite_tag.getInt("x"), worksite_tag.getInt("y"),
					worksite_tag.getInt("z"));
			task = new CrowdTask.WorkAtWorksite(worksite);
		}
	}

	public static class KeepLookingControl extends LookControl {
		public KeepLookingControl(Mob mob) {
			super(mob);
		}

		protected boolean resetXRotOnTick() {
			return false;
		}
	}

	public static class LoadTaskGoal extends Goal {
		public final CrowdMemberEntity mob;

		public LoadTaskGoal(CrowdMemberEntity mob) {
			this.mob = mob;
		}

		@Override
		public boolean canUse() {
			return mob.targetBlock == BlockPos.ZERO;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
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
			return mob.targetBlock != BlockPos.ZERO
					&& mob.targetBlock.closerToCenterThan(mob.position(), CLOSE_TO_BLOCK_DISTANCE);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			mob.task.run(mob);
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
			return mob.targetBlock != BlockPos.ZERO
					&& !mob.targetBlock.closerToCenterThan(mob.position(), CLOSE_TO_BLOCK_DISTANCE);
		}

		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (tick == 0) {
				mob.setNoGravity(false);

				Set<BlockPos> acceptable_positions = new HashSet<BlockPos>();
				for (Direction d : Direction.values()) {
					acceptable_positions.add(mob.targetBlock.relative(d));
				}
				mob.getNavigation().moveTo(mob.getNavigation().createPath(acceptable_positions, 0), 1.0);
			}

			tick = (tick + 1) % 40;
		}
	}
}
