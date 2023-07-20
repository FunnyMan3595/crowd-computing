package com.funnyman3595.crowd_computing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

public class CrowdSourceBlockEntity extends BlockEntity implements MenuProvider, Nameable {
	public static final double MAX_RANGE = 160;
	public static HashMap<String, RegistryObject<BlockEntityType<CrowdSourceBlockEntity>>> block_entities = new HashMap<String, RegistryObject<BlockEntityType<CrowdSourceBlockEntity>>>();
	public static HashMap<Level, HashSet<BlockPos>> known_sources = new HashMap<Level, HashSet<BlockPos>>();

	public final CrowdSourceBlock block;
	public final double range;

	public HashSet<Player> players_in_gui = new HashSet<Player>();
	public HashSet<WorksiteBlockEntity> requested_workers = new HashSet<WorksiteBlockEntity>();
	public HashMap<WorksiteBlockEntity, CrowdMemberEntity> spawned_workers = new HashMap<WorksiteBlockEntity, CrowdMemberEntity>();
	public HashMap<BlockPos, UUID> spawned_worker_ids = new HashMap<BlockPos, UUID>();

	public ContainerData crowd_source_data = new ContainerData() {
		@Override
		public int get(int index) {
			switch (index) {
			default:
				return 0;
			}
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
			default:
				break;
			}
		}

		@Override
		public int getCount() {
			return 0;
		}
	};

	public CrowdSourceBlockEntity(BlockPos pos, BlockState state) {
		super(block_entities.get(((CrowdSourceBlock) state.getBlock()).name).get(), pos, state);
		block = (CrowdSourceBlock) state.getBlock();
		range = Math.min(MAX_RANGE, GsonHelper.getAsDouble(block.config, "range"));
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);

		if (!known_sources.containsKey(level)) {
			known_sources.put(level, new HashSet<BlockPos>());
		}
		known_sources.get(level).add(getBlockPos());
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		if (known_sources.containsKey(level)) {
			known_sources.get(level).remove(getBlockPos());
		}
	}

	public void startOpen(Player player) {
		players_in_gui.add(player);
	}

	public void stopOpen(Player player) {
		players_in_gui.remove(player);
	}

	public boolean stillValid(Player player) {
		if (level.getBlockEntity(worldPosition) != this) {
			return false;
		} else {
			return !(player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
					worldPosition.getZ() + 0.5) > 64);
		}
	}

	@Override
	public Component getName() {
		return block.getName();
	}

	@Override
	public Component getDisplayName() {
		return block.getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int container_id, Inventory inventory, Player player) {
		return new CrowdSourceBlockMenu(container_id, player, this, crowd_source_data);
	}

	public ItemStack getIdentity() {
		return new ItemStack(CrowdSourceBlock.items.get(block.name).get(), 1);
	}

	public List<ItemStack> getDrops() {
		List<ItemStack> drops = new ArrayList<ItemStack>();
		drops.add(getIdentity());
		return drops;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CrowdSourceBlockEntity entity) {
		if (entity.requested_workers.size() > 0) {
			WorksiteBlockEntity worksite = entity.requested_workers.iterator().next();
			CrowdMemberEntity worker = new CrowdMemberEntity(CrowdMemberEntity.TYPE, level);
			worker.parent_pos = entity.getBlockPos();
			worker.absMoveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
			worker.task = new CrowdTask.WorkAtWorksite(worksite.getBlockPos());
			worker.setGameProfile("FunnyMan3595");
			level.addFreshEntity(worker);

			entity.requested_workers.remove(worksite);
			entity.spawned_workers.put(worksite, worker);
			entity.spawned_worker_ids.put(worksite.getBlockPos(), worker.getUUID());
			entity.setChanged();
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		ListTag list = tag.getList("spawned_worker_ids", Tag.TAG_COMPOUND);
		for (Tag raw_entry : list) {
			CompoundTag entry = (CompoundTag) raw_entry;
			BlockPos pos = new BlockPos(entry.getInt("block_x"), entry.getInt("block_y"), entry.getInt("block_z"));
			UUID worker_uuid = entry.getUUID("worker_uuid");

			spawned_worker_ids.put(pos, worker_uuid);
		}
	}

	@Override
	public void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		ListTag list = new ListTag();
		for (BlockPos pos : spawned_worker_ids.keySet()) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("block_x", pos.getX());
			entry.putInt("block_y", pos.getY());
			entry.putInt("block_z", pos.getZ());
			entry.putUUID("worker_uuid", spawned_worker_ids.get(pos));
			list.add(entry);
		}
		tag.put("spawned_worker_ids", list);
	}

	public void requestSpawn(WorksiteBlockEntity worksite) {
		if (level.isClientSide) {
			return;
		}

		if (requested_workers.contains(worksite)) {
			return;
		}
		if (spawned_workers.containsKey(worksite)) {
			CrowdMemberEntity worker = spawned_workers.get(worksite);
			if (worker.isDeadOrDying() || worker.isRemoved()) {
				spawned_workers.remove(worksite);
				spawned_worker_ids.remove(worksite.getBlockPos());
				setChanged();
			} else {
				return;
			}
		}
		if (spawned_worker_ids.containsKey(worksite.getBlockPos())) {
			Entity raw_worker = ((ServerLevel) level).getEntity(spawned_worker_ids.get(worksite.getBlockPos()));
			if (raw_worker == null || !(raw_worker instanceof CrowdMemberEntity) || raw_worker.isRemoved()) {
				spawned_worker_ids.remove(worksite.getBlockPos());
				setChanged();
			} else {
				CrowdMemberEntity worker = (CrowdMemberEntity) raw_worker;
				if (worker.isDeadOrDying()) {
					spawned_worker_ids.remove(worksite.getBlockPos());
					setChanged();
				} else {
					spawned_workers.put(worksite, worker);
					return;
				}
			}
		}
		requested_workers.add(worksite);
	}
}
