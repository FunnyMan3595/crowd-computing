package com.funnyman3595.crowd_computing;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.funnyman3595.crowd_computing.WebLink.MiniConfig;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

public class CrowdSourceBlockEntity extends BlockEntity implements MenuProvider, Nameable {
	public static final int MAX_RANGE = 160;
	public static HashMap<String, RegistryObject<BlockEntityType<CrowdSourceBlockEntity>>> block_entities = new HashMap<String, RegistryObject<BlockEntityType<CrowdSourceBlockEntity>>>();
	public static HashMap<Level, HashSet<BlockPos>> known_sources = new HashMap<Level, HashSet<BlockPos>>();
	private static HashMap<Level, HashSet<ChunkPos>> global_dirty_chunks = new HashMap<Level, HashSet<ChunkPos>>();

	public final CrowdSourceBlock block;
	public final int range;

	public int tick = 0;

	public UUID owner = null;

	public HashSet<Player> players_in_gui = new HashSet<Player>();
	public HashSet<WorksiteBlockEntity> requested_workers = new HashSet<WorksiteBlockEntity>();
	public HashMap<WorksiteBlockEntity, CrowdMemberEntity> spawned_workers = new HashMap<WorksiteBlockEntity, CrowdMemberEntity>();
	public HashMap<BlockPos, UUID> spawned_worker_ids = new HashMap<BlockPos, UUID>();

	public HashMap<String, CrowdMemberEntity> spawned_movers = new HashMap<String, CrowdMemberEntity>();
	public HashMap<String, UUID> spawned_mover_ids = new HashMap<String, UUID>();

	public boolean fully_dirty = true;
	public HashSet<ChunkPos> dirty_chunks = new HashSet<ChunkPos>();
	public HashSet<BlockPos> dirty_blocks = new HashSet<BlockPos>();
	public final BufferedImage minimap;
	public boolean minimap_updated = false;

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
		range = Math.min(MAX_RANGE, GsonHelper.getAsInt(block.config, "range"));
		minimap = new BufferedImage(range * 2 + 1, range * 2 + 1, BufferedImage.TYPE_INT_ARGB);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);

		if (level.isClientSide) {
			return;
		}

		tick = level.random.nextInt(20);

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
		if (global_dirty_chunks.containsKey(level)) {
			for (ChunkPos chunk : global_dirty_chunks.get(level)) {
				for (CrowdSourceBlockEntity crowd_source : CrowdSourceBlockEntity.get_loaded_in_range(level, chunk)) {
					crowd_source.mark_dirty(chunk);
				}
			}
			global_dirty_chunks.get(level).clear();
		}

		entity.tick = (entity.tick + 1) % 20;
		if (entity.tick != 0) {
			return;
		}

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

		entity.refresh_minimap();
		if (entity.minimap_updated) {
			entity.try_send_minimap();
		}
	}

	public void try_send_minimap() {
		if (owner == null) {
			return;
		}
		Player player = level.getPlayerByUUID(owner);
		if (player == null) {
			return;
		}

		WebLink link = WebLink.get(player);
		if (link.has_auth_secret()) {
			minimap_updated = false;
			link.upload_minimap(getBlockPos(), range, minimap, (Exception e) -> {
				minimap_updated = true;
				player.sendSystemMessage(Component.translatable("crowd_computing.link_failed", e));
			});
		}
	}

	public void refresh_minimap() {
		if (fully_dirty) {
			for (int x_offset = -range; x_offset <= range; x_offset++) {
				for (int z_offset = -range; z_offset <= range; z_offset++) {
					refresh_minimap_pixel(x_offset, z_offset);
				}
			}

			fully_dirty = false;
			dirty_chunks.clear();
			dirty_blocks.clear();
			return;
		}

		HashSet<BlockPos> refreshed = new HashSet<BlockPos>();
		if (!dirty_chunks.isEmpty()) {
			for (ChunkPos pos : dirty_chunks) {
				int min_x_offset = Math.max(-range, pos.getMinBlockX() - getBlockPos().getX());
				int max_x_offset = Math.min(range, pos.getMaxBlockX() - getBlockPos().getX());
				int min_z_offset = Math.max(-range, pos.getMinBlockZ() - getBlockPos().getZ());
				int max_z_offset = Math.min(range, pos.getMaxBlockZ() - getBlockPos().getZ());
				for (int x_offset = min_x_offset; x_offset <= max_x_offset; x_offset++) {
					for (int z_offset = min_z_offset; z_offset <= max_z_offset; z_offset++) {
						refresh_minimap_pixel(x_offset, z_offset);
						refreshed
								.add(new BlockPos(getBlockPos().getX() + x_offset, 0, getBlockPos().getZ() + z_offset));
					}
				}
			}
			dirty_chunks.clear();
		}

		if (!dirty_blocks.isEmpty()) {
			for (BlockPos pos : dirty_blocks) {
				if (refreshed.contains(pos.atY(0))) {
					continue;
				}

				refresh_minimap_pixel(pos.getX() - getBlockPos().getX(), pos.getZ() - getBlockPos().getZ());
				refreshed.add(pos.atY(0));
			}
			dirty_blocks.clear();
		}
	}

	public void refresh_minimap_pixel(int x_offset, int z_offset) {
		int image_x = x_offset + range;
		int image_z = z_offset + range;
		int abs_x = getBlockPos().getX() + x_offset;
		int abs_z = getBlockPos().getZ() + z_offset;

		boolean found_air = false;
		boolean found_block = false;
		MaterialColor block_color = MaterialColor.NONE;
		int water_depth = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(abs_x, 0, abs_z);
		for (int abs_y = getBlockPos().getY() + range; abs_y >= getBlockPos().getY() - range; abs_y--) {
			pos.setY(abs_y);
			if (!level.isLoaded(pos)) {
				break;
			}

			BlockState blockstate = level.getBlockState(pos);
			MaterialColor color = blockstate.getMapColor(level, pos);
			if (color == MaterialColor.NONE) {
				if (!found_air) {
					found_air = true;
					water_depth = 0;
				}
				continue;
			}
			if (color == MaterialColor.WATER) {
				water_depth += 1;
				continue;
			}

			if (!found_block) {
				found_block = true;
				block_color = color;
			}
			if (found_air) {
				found_block = true;
				block_color = color;
				break;
			}
		}

		MaterialColor.Brightness brightness = MaterialColor.Brightness.NORMAL;
		if (water_depth > 0) {
			block_color = MaterialColor.WATER;
			if (water_depth > 8) {
				brightness = MaterialColor.Brightness.LOWEST;
			} else if (water_depth > 4) {
				brightness = MaterialColor.Brightness.LOW;
			}
		}

		int abgr_color = MaterialColor.getColorFromPackedId(block_color.getPackedId(brightness));
		int ag_mask = -16711936;
		int left_mask = 0xFF0000;
		int right_mask = 0xFF;
		int shift = 16;
		int color = (abgr_color & ag_mask) | ((abgr_color & left_mask) >> shift) | ((abgr_color & right_mask) << shift);

		if (color != minimap.getRGB(image_x, image_z)) {
			minimap.setRGB(image_x, image_z, color);
			minimap_updated = true;
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (tag.contains("owner")) {
			owner = tag.getUUID("owner");
		}
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
		if (owner != null) {
			tag.putUUID("owner", owner);
		}
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

	public void requestSpawn(Player player) {
		if (level.isClientSide) {
			return;
		}

		WebLink.get(player).get_all((configs) -> {
			for (WebLink.MiniConfig config : configs.configs()) {
				if (config.source() == null || config.target() == null) {
					continue;
				}
				String full_name = config.full_name();
				if (spawned_movers.containsKey(full_name)) {
					CrowdMemberEntity mover = spawned_movers.get(full_name);
					if (mover.isDeadOrDying() || mover.isRemoved()) {
						spawned_movers.remove(full_name);
						spawned_mover_ids.remove(full_name);
						setChanged();
					} else {
						continue;
					}
				}
				if (spawned_mover_ids.containsKey(full_name)) {
					Entity raw_mover = ((ServerLevel) level).getEntity(spawned_mover_ids.get(full_name));
					if (raw_mover == null || !(raw_mover instanceof CrowdMemberEntity) || raw_mover.isRemoved()) {
						spawned_mover_ids.remove(full_name);
						setChanged();
					} else {
						CrowdMemberEntity mover = (CrowdMemberEntity) raw_mover;
						if (mover.isDeadOrDying()) {
							spawned_mover_ids.remove(full_name);
							setChanged();
						} else {
							spawned_movers.put(full_name, mover);
							continue;
						}
					}
				}

				requestSpawn(player, config);
			}
		}, error -> {
			player.sendSystemMessage(Component.translatable("crowd_computing.link_failed", error));
		});
	}

	public void requestSpawn(Player player, MiniConfig config) {
		CrowdMemberEntity mover = new CrowdMemberEntity(CrowdMemberEntity.TYPE, level);
		mover.parent_pos = getBlockPos();
		mover.absMoveTo(getBlockPos().getX() + 0.5, getBlockPos().getY() + 1, getBlockPos().getZ() + 0.5);
		mover.task = new CrowdTask.MoveStuff(config.source(), config.target(), ItemStack.EMPTY, config.limit());
		mover.setGameProfile(config.viewer());
		level.addFreshEntity(mover);

		spawned_movers.put(config.full_name(), mover);
		spawned_mover_ids.put(config.full_name(), mover.getUUID());
		setChanged();
	}

	public static int chessboard_distance(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()),
				Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}

	public static double chessboard_distance(BlockPos a, Vec3 b) {
		return Math.max(Math.abs(a.getX() - b.x), Math.max(Math.abs(a.getY() - b.y), Math.abs(a.getZ() - b.z)));
	}

	public static double chessboard_distance(BlockPos a, ChunkPos b) {
		int x_dist;
		if (a.getX() < b.getMinBlockX()) {
			x_dist = b.getMinBlockX() - a.getX();
		} else if (a.getX() <= b.getMaxBlockX()) {
			x_dist = 0;
		} else {
			x_dist = a.getX() - b.getMaxBlockX();
		}
		int z_dist;
		if (a.getZ() < b.getMinBlockZ()) {
			z_dist = b.getMinBlockZ() - a.getZ();
		} else if (a.getZ() <= b.getMaxBlockZ()) {
			z_dist = 0;
		} else {
			z_dist = a.getZ() - b.getMaxBlockZ();
		}
		return Math.max(x_dist, z_dist);
	}

	public static CrowdSourceBlockEntity get_closest_loaded_in_range(Level level, BlockPos blockPos) {
		if (!known_sources.containsKey(level)) {
			return null;
		}

		double best_distance = MAX_RANGE + 1;
		CrowdSourceBlockEntity best_source = null;
		ObjectArrayList<BlockPos> bad_positions = new ObjectArrayList<BlockPos>();
		for (BlockPos pos : known_sources.get(level)) {
			double distance = chessboard_distance(pos, blockPos);
			if (distance < best_distance && level.isLoaded(blockPos)) {
				BlockEntity raw_source = level.getBlockEntity(pos);
				if (raw_source == null || !(raw_source instanceof CrowdSourceBlockEntity)) {
					bad_positions.add(pos);
				} else {
					CrowdSourceBlockEntity source = (CrowdSourceBlockEntity) raw_source;
					if (source.range >= distance) {
						best_distance = distance;
						best_source = source;
					}
				}
			}
		}
		if (bad_positions.size() > 0) {
			for (BlockPos pos : bad_positions) {
				known_sources.get(level).remove(pos);
			}
		}

		return best_source;
	}

	public static ObjectArrayList<CrowdSourceBlockEntity> get_loaded_in_range(Level level, BlockPos blockPos) {
		ObjectArrayList<CrowdSourceBlockEntity> in_range = new ObjectArrayList<CrowdSourceBlockEntity>();
		if (!known_sources.containsKey(level)) {
			return in_range;
		}

		double max_distance = MAX_RANGE;
		ObjectArrayList<BlockPos> bad_positions = new ObjectArrayList<BlockPos>();
		for (BlockPos pos : known_sources.get(level)) {
			double distance = chessboard_distance(pos, blockPos);
			if (distance < max_distance && level.isLoaded(blockPos)) {
				BlockEntity raw_source = level.getBlockEntity(pos);
				if (raw_source == null || !(raw_source instanceof CrowdSourceBlockEntity)) {
					bad_positions.add(pos);
				} else {
					CrowdSourceBlockEntity source = (CrowdSourceBlockEntity) raw_source;
					if (source.range >= distance) {
						in_range.add(source);
					}
				}
			}
		}
		if (bad_positions.size() > 0) {
			for (BlockPos pos : bad_positions) {
				known_sources.get(level).remove(pos);
			}
		}

		return in_range;
	}

	public static ObjectArrayList<CrowdSourceBlockEntity> get_loaded_in_range(Level level, ChunkPos chunkPos) {
		ObjectArrayList<CrowdSourceBlockEntity> in_range = new ObjectArrayList<CrowdSourceBlockEntity>();
		if (!known_sources.containsKey(level)) {
			return in_range;
		}

		double max_distance = MAX_RANGE;
		ObjectArrayList<BlockPos> bad_positions = new ObjectArrayList<BlockPos>();
		for (BlockPos pos : known_sources.get(level)) {
			double distance = chessboard_distance(pos, chunkPos);
			if (distance < max_distance && level.isLoaded(pos)) {
				BlockEntity raw_source = level.getBlockEntity(pos);
				if (raw_source == null || !(raw_source instanceof CrowdSourceBlockEntity)) {
					bad_positions.add(pos);
				} else {
					CrowdSourceBlockEntity source = (CrowdSourceBlockEntity) raw_source;
					if (source.range >= distance) {
						in_range.add(source);
					}
				}
			}
		}
		if (bad_positions.size() > 0) {
			for (BlockPos pos : bad_positions) {
				known_sources.get(level).remove(pos);
			}
		}

		return in_range;
	}

	public void mark_dirty() {
		fully_dirty = true;
	}

	public void mark_dirty(ChunkPos pos) {
		dirty_chunks.add(pos);
	}

	public void mark_dirty(BlockPos pos) {
		dirty_blocks.add(pos);
	}

	public static void delayed_mark_dirty(Level level, ChunkPos pos) {
		if (!global_dirty_chunks.containsKey(level)) {
			global_dirty_chunks.put(level, new HashSet<ChunkPos>());
		}
		global_dirty_chunks.get(level).add(pos);
	}

	public static void mark_dirty(Level level, BlockPos pos) {
		for (CrowdSourceBlockEntity crowd_source : CrowdSourceBlockEntity.get_loaded_in_range(level, pos)) {
			crowd_source.mark_dirty(pos);
		}
	}

	public static void on_block_broken(Level level, BlockPos pos) {
		if (!known_sources.containsKey(level)) {
			return;
		}
		if (!known_sources.get(level).contains(pos)) {
			return;
		}
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof CrowdSourceBlockEntity)) {
			return;
		}
		Player player = level.getPlayerByUUID(((CrowdSourceBlockEntity) entity).owner);
		if (player != null) {
			WebLink link = WebLink.get(player);
			if (link.has_auth_secret()) {
				link.delete_minimap(entity.getBlockPos(), (Exception e) -> {
					player.sendSystemMessage(Component.translatable("crowd_computing.link_failed", e));
				});
			}
		}
	}

	public static void spawn_at_nearest(Player player, MiniConfig miniConfig) {
		Level level = player.level;
		if (!known_sources.containsKey(level)) {
			return;
		}

		BlockPos config_center = BlockSelector.avg_pos(miniConfig.source().get_center(),
				miniConfig.target().get_center());
		CrowdSourceBlockEntity entity = get_closest_loaded_in_range(level, config_center);
		if (entity == null) {
			player.sendSystemMessage(Component.translatable("crowd_computing.no_source_in_range", config_center));
			return;
		}

		entity.requestSpawn(player, miniConfig);
	}
}
