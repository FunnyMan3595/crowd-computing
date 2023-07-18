package com.funnyman3595.crowd_computing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public abstract class CrowdTask {
	public static final CrowdTask DIE = new CrowdTask.Die();

	public abstract void init(CrowdMemberEntity mob);

	public abstract void run(CrowdMemberEntity mob);

	public static class Die extends CrowdTask {
		@Override
		public void init(CrowdMemberEntity mob) {
			mob.kill();
		}

		@Override
		public void run(CrowdMemberEntity mob) {
			mob.kill();
		}
	}

	public static class WorkAtWorksite extends CrowdTask {
		public BlockPos worksite;
		public int tick = 0;

		public WorkAtWorksite(BlockPos worksite) {
			this.worksite = worksite;
		}

		@Override
		public void init(CrowdMemberEntity mob) {
			mob.targetBlock = worksite;
		}

		@Override
		public void run(CrowdMemberEntity mob) {
			if (tick == 0) {
				BlockEntity raw_worksite_entity = mob.level.getBlockEntity(worksite);
				if (raw_worksite_entity == null || !(raw_worksite_entity instanceof WorksiteBlockEntity)) {
					mob.task = DIE;
					return;
				}
				WorksiteBlockEntity worksite_entity = (WorksiteBlockEntity) raw_worksite_entity;
				worksite_entity.worker = mob;

				mob.getNavigation().stop();
				mob.setPos(worksite.getX() + 0.5, worksite.getY() + 0.125, worksite.getZ() + 0.5);
				switch (worksite_entity.getBlockState().getValue(WorksiteBlock.FACING)) {
				case NORTH:
					mob.setYRot(180f);
					break;
				case SOUTH:
					mob.setYRot(00f);
					break;
				case EAST:
					mob.setYRot(270f);
					break;
				case WEST:
					mob.setYRot(90f);
					break;
				default:
					mob.setYRot(45f);
					break;
				}
				mob.setXRot(0f);
				mob.setNoGravity(true);
				mob.setDeltaMovement(Vec3.ZERO);
			}
			tick = (tick + 1) % 20;
		}
	}
}
