package com.funnyman3595.crowd_computing;

import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public abstract class BlockSelector {
	public static HashMap<String, Function<CompoundTag, BlockSelector>> LOADERS = new HashMap<String, Function<CompoundTag, BlockSelector>>();
	static {
		LOADERS.put(Single.ID, Single::load_nbt);
	}

	public abstract Stream<BlockPos> get_blocks();

	public abstract String get_id();

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
		public String get_id() {
			return ID;
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
}