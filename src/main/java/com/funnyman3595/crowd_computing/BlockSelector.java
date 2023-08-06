package com.funnyman3595.crowd_computing;

import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public abstract class BlockSelector {
	public static HashMap<String, Function<CompoundTag, BlockSelector>> LOADERS = new HashMap<String, Function<CompoundTag, BlockSelector>>();
	static {
		LOADERS.put(Single.ID, Single::load_nbt);
		LOADERS.put(Region.ID, Region::load_nbt);
	}

	public abstract Stream<BlockPos> get_blocks();

	protected abstract int get_block_count();

	public abstract String get_id();

	public abstract BlockPos get_center();

	public static BlockPos avg_pos(BlockPos a, BlockPos b) {
		int x = (a.getX() + b.getX()) / 2;
		int y = (a.getY() + b.getY()) / 2;
		int z = (a.getZ() + b.getZ()) / 2;
		return new BlockPos(x, y, z);
	}

	public static BlockSelector load_nbt(CompoundTag tag) {
		Function<CompoundTag, BlockSelector> loader = LOADERS.get(tag.getString("type"));
		return loader.apply(tag);
	}

	public CompoundTag save_to_nbt() {
		CompoundTag tag = new CompoundTag();
		tag.putString("type", get_id());
		return tag;
	}

	public static class Single extends BlockSelector {
		public static final String ID = "single";
		public BlockPos pos;

		public Single(BlockPos pos) {
			this.pos = pos;
		}

		@Override
		public Stream<BlockPos> get_blocks() {
			return Stream.of(pos);
		}

		@Override
		public int get_block_count() {
			return 1;
		}

		@Override
		public String get_id() {
			return ID;
		}

		@Override
		public BlockPos get_center() {
			return pos;
		}

		public static BlockSelector load_nbt(CompoundTag tag) {
			BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
			return new Single(pos);
		}

		@Override
		public CompoundTag save_to_nbt() {
			CompoundTag tag = super.save_to_nbt();
			tag.putInt("x", pos.getX());
			tag.putInt("y", pos.getY());
			tag.putInt("z", pos.getZ());
			return tag;
		}

	}

	public static class Region extends BlockSelector {
		public static final String ID = "region";
		public BlockPos start;
		public BlockPos end;

		public Region(BlockPos start, BlockPos end) {
			this.start = start;
			this.end = end;
		}

		public int x_size() {
			return Math.abs(start.getX() - end.getX()) + 1;
		}

		public int y_size() {
			return Math.abs(start.getY() - end.getY()) + 1;
		}

		public int z_size() {
			return Math.abs(start.getZ() - end.getZ()) + 1;
		}

		public int size() {
			return x_size() * y_size() * z_size();
		}

		public BlockPos at(int x_offset, int y_offset, int z_offset) {
			return new BlockPos(Math.min(start.getX(), end.getX()) + x_offset,
					Math.min(start.getY(), end.getY()) + y_offset, Math.min(start.getZ(), end.getZ()) + z_offset);
		}

		public BlockPos at(int index) {
			int x_offset = index % x_size();
			int y_offset = (index / x_size()) % y_size();
			int z_offset = (index / (x_size() * y_size())) % z_size();
			return at(x_offset, y_offset, z_offset);
		}

		@Override
		public Stream<BlockPos> get_blocks() {
			return IntStream.range(0, size()).mapToObj(this::at);
		}

		@Override
		public int get_block_count() {
			return size();
		}

		@Override
		public String get_id() {
			return ID;
		}

		@Override
		public BlockPos get_center() {
			return avg_pos(start, end);
		}

		public static BlockSelector load_nbt(CompoundTag tag) {
			BlockPos start = new BlockPos(tag.getInt("start_x"), tag.getInt("start_y"), tag.getInt("start_z"));
			BlockPos end = new BlockPos(tag.getInt("end_x"), tag.getInt("end_y"), tag.getInt("end_z"));
			return new Region(start, end);
		}

		@Override
		public CompoundTag save_to_nbt() {
			CompoundTag tag = super.save_to_nbt();
			tag.putInt("start_x", start.getX());
			tag.putInt("start_y", start.getY());
			tag.putInt("start_z", start.getZ());
			tag.putInt("end_x", end.getX());
			tag.putInt("end_y", end.getY());
			tag.putInt("end_z", end.getZ());
			return tag;
		}

	}
}