package com.funnyman3595.crowd_computing;

import java.util.HashMap;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public abstract class CrowdTask {
	public static final CrowdTask DIE = new CrowdTask.Die();

	public static HashMap<String, Function<CompoundTag, CrowdTask>> LOADERS = new HashMap<String, Function<CompoundTag, CrowdTask>>();
	static {
		LOADERS.put(Die.ID, Die::load_nbt);
		LOADERS.put(WorkAtWorksite.ID, WorkAtWorksite::load_nbt);
		LOADERS.put(MoveStuff.ID, MoveStuff::load_nbt);
	}

	public abstract void init(CrowdMemberEntity mob);

	public abstract void run(CrowdMemberEntity mob);

	public abstract String get_id();

	public static CrowdTask load_nbt(CompoundTag tag) {
		Function<CompoundTag, CrowdTask> loader = LOADERS.get(tag.getString("type"));
		return loader.apply(tag);
	}

	public CompoundTag save_to_nbt() {
		CompoundTag tag = new CompoundTag();
		tag.putString("type", get_id());
		return tag;
	}

	public static class Die extends CrowdTask {
		public static String ID = "die";

		@Override
		public void init(CrowdMemberEntity mob) {
			mob.kill();
		}

		@Override
		public void run(CrowdMemberEntity mob) {
			mob.kill();
		}

		@Override
		public String get_id() {
			return ID;
		}

		public static CrowdTask load_nbt(CompoundTag tag) {
			return DIE;
		}

		@Override
		public CompoundTag save_to_nbt() {
			return super.save_to_nbt();
		}
	}

	public static class WorkAtWorksite extends CrowdTask {
		public static String ID = "work_at_worksite";

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

				mob.setPos(mob.targetBlock.getX() + 0.5, mob.targetBlock.getY() + 0.125, mob.targetBlock.getZ() + 0.5);
				mob.getNavigation().stop();
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
				mob.getMoveControl().setWantedPosition(mob.targetBlock.getX() + 0.5, mob.targetBlock.getY() + 0.125,
						mob.targetBlock.getZ() + 0.5, 0);
			}
			tick = (tick + 1) % 20;
		}

		@Override
		public String get_id() {
			return ID;
		}

		public static CrowdTask load_nbt(CompoundTag tag) {
			return new WorkAtWorksite(
					new BlockPos(tag.getInt("worksite_x"), tag.getInt("worksite_y"), tag.getInt("worksite_z")));
		}

		@Override
		public CompoundTag save_to_nbt() {
			CompoundTag tag = super.save_to_nbt();
			tag.putInt("worksite_x", worksite.getX());
			tag.putInt("worksite_y", worksite.getY());
			tag.putInt("worksite_z", worksite.getZ());
			return tag;
		}
	}

	public static class MoveStuff extends CrowdTask {
		public static String ID = "move_stuff";
		public BlockSelector from;
		public BlockSelector to;
		public ItemStack held;

		public MoveStuff(BlockSelector from, BlockSelector to, ItemStack held) {
			this.from = from;
			this.to = to;
			this.held = held;
		}

		@Override
		public void init(CrowdMemberEntity mob) {
			if (held.isEmpty()) {
				mob.targetBlock = from.get_blocks().findAny().get();
			} else {
				mob.targetBlock = to.get_blocks().findAny().get();
			}
		}

		@Override
		public void run(CrowdMemberEntity mob) {
			if (held.isEmpty()) {
				BlockEntity entity = mob.level.getBlockEntity(mob.targetBlock);
				if (entity == null || !(entity instanceof Container)) {
					mob.kill();
					return;
				}
				WorldlyContainer container;
				if (entity instanceof WorldlyContainer) {
					container = (WorldlyContainer) entity;
				} else {
					container = new WorldlyWrapper((Container) entity);
				}
				for (int slot : container.getSlotsForFace(Direction.DOWN)) {
					ItemStack stack = container.getItem(slot);
					if (stack.isEmpty()) {
						continue;
					}
					if (!container.canTakeItemThroughFace(slot, stack, Direction.DOWN)) {
						continue;
					}
					held = container.removeItem(slot, stack.getCount());
					if (!held.isEmpty()) {
						mob.targetBlock = to.get_blocks().findAny().get();
						return;
					}
				}
			} else {
				BlockEntity entity = mob.level.getBlockEntity(mob.targetBlock);
				if (entity == null || !(entity instanceof Container)) {
					mob.kill();
					return;
				}
				WorldlyContainer container;
				if (entity instanceof WorldlyContainer) {
					container = (WorldlyContainer) entity;
				} else {
					container = new WorldlyWrapper((Container) entity);
				}
				for (int slot : container.getSlotsForFace(Direction.UP)) {
					ItemStack target_stack = container.getItem(slot);
					if (!target_stack.isEmpty() && !ItemStack.isSameItemSameTags(target_stack, held)) {
						continue;
					}
					if (target_stack.getCount() >= target_stack.getItem().getMaxStackSize(target_stack)) {
						continue;
					}
					if (!container.canPlaceItemThroughFace(slot, held, Direction.DOWN)) {
						continue;
					}

					int move_cap;
					if (target_stack.isEmpty()) {
						move_cap = held.getItem().getMaxStackSize(held);
					} else {
						move_cap = target_stack.getItem().getMaxStackSize(target_stack) - target_stack.getCount();
					}
					int move_amount = Math.min(held.getCount(), move_cap);
					ItemStack stack = held.split(move_amount);
					stack.setCount(move_amount + target_stack.getCount());
					container.setItem(slot, stack);
					if (held.isEmpty()) {
						held = ItemStack.EMPTY;
						mob.targetBlock = from.get_blocks().findAny().get();
						break;
					}
				}
			}
		}

		@Override
		public String get_id() {
			return ID;
		}

		public static CrowdTask load_nbt(CompoundTag tag) {
			BlockSelector from = BlockSelector.load_nbt(tag.getCompound("from_blocks"));
			BlockSelector to = BlockSelector.load_nbt(tag.getCompound("to_blocks"));
			ItemStack held = ItemStack.EMPTY.copy();
			if (tag.contains("held_item")) {
				held.deserializeNBT(tag.getCompound("held_item"));
			}
			return new MoveStuff(from, to, held);
		}

		@Override
		public CompoundTag save_to_nbt() {
			CompoundTag tag = super.save_to_nbt();
			tag.put("from_blocks", from.save_to_nbt());
			tag.put("to_blocks", to.save_to_nbt());
			tag.put("held_item", held.serializeNBT());
			return tag;
		}
	}
}
