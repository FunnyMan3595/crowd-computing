package com.funnyman3595.crowd_computing;

import java.util.stream.IntStream;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class WorldlyWrapper implements WorldlyContainer {
	public final Container parent;

	public WorldlyWrapper(Container parent) {
		this.parent = parent;
	}

	@Override
	public int getContainerSize() {
		return parent.getContainerSize();
	}

	@Override
	public boolean isEmpty() {
		return parent.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return parent.getItem(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return parent.removeItem(slot, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return parent.removeItemNoUpdate(slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		parent.setItem(slot, stack);
	}

	@Override
	public void setChanged() {
		parent.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return parent.stillValid(player);
	}

	@Override
	public void clearContent() {
		parent.clearContent();
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return IntStream.range(0, getContainerSize()).toArray();
	}

	@Override
	public boolean canPlaceItemThroughFace(int p_19235_, ItemStack p_19236_, Direction p_19237_) {
		return true;
	}

	@Override
	public boolean canTakeItemThroughFace(int p_19239_, ItemStack p_19240_, Direction p_19241_) {
		return true;
	}

}
