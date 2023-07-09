package com.funnyman3595.crowd_computing;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WorksiteBlockMenu extends AbstractContainerMenu {
	public static final RegistryObject<MenuType<WorksiteBlockMenu>> TYPE = RegistryObject
			.create(new ResourceLocation(CrowdComputing.MODID, "worksite"), ForgeRegistries.MENU_TYPES);
	public final WorksiteBlockEntity worksite;
	public final Inventory player_inventory;
	public Container client_cache = new SimpleContainer(1000);
	public final ContainerData slot_counts;

	public WorksiteBlockMenu(int container_id, Inventory inventory) {
		this(container_id, inventory, null, new SimpleContainerData(4));
	}

	public int getBlockSlots() {
		return slot_counts.get(0) + slot_counts.get(1) + slot_counts.get(2) + slot_counts.get(3);
	}

	public WorksiteBlockMenu(int container_id, Inventory player_inventory, WorksiteBlockEntity worksite,
			ContainerData slot_counts) {
		super(WorksiteBlockMenu.TYPE.get(), container_id);
		this.worksite = worksite;
		this.player_inventory = player_inventory;
		this.slot_counts = slot_counts;
		addDataSlots(slot_counts);

		if (worksite != null) {
			worksite.startOpen(player_inventory.player);
			checkContainerSize(worksite, getBlockSlots());

			updateSlots();
		}
	}

	public void updateSlots() {
		this.slots.clear();

		Container container;
		if (worksite == null) {
			container = client_cache;
		} else {
			container = worksite;
		}

		for (int i = 0; i < slot_counts.get(0); i++) {
			addSlot(new UpgradeSlot(container, i, 62 + i * 18, 17));
		}
		int slot_offset = slot_counts.get(0);
		for (int i = 0; i < slot_counts.get(1); i++) {
			addSlot(new DynamicSlot(container, i + slot_offset, 62 + i * 18, 17 + 1 * 18));
		}
		slot_offset += slot_counts.get(1);
		for (int i = 0; i < slot_counts.get(2); i++) {
			addSlot(new ToolSlot(container, i + slot_offset, 62 + i * 18, 17 + 2 * 18));
		}
		slot_offset += slot_counts.get(2);
		for (int i = 0; i < slot_counts.get(3); i++) {
			addSlot(new ResultSlot(container, i + slot_offset, 62 + i * 18, 17 + 3 * 18));
		}

		addInventorySlots();
		broadcastChanges();
	}

	public void addInventorySlots() {
		for (int row = 0; row < 3; ++row) {
			for (int column = 0; column < 9; ++column) {
				this.addSlot(new Slot(player_inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
			}
		}

		for (int hotbar_slot = 0; hotbar_slot < 9; ++hotbar_slot) {
			this.addSlot(new Slot(player_inventory, hotbar_slot, 8 + hotbar_slot * 18, 142));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot_index) {
		if (worksite != null) {
			ItemStack leftovers = ItemStack.EMPTY;
			Slot slot = slots.get(slot_index);
			if (slot != null && slot.hasItem()) {
				ItemStack slot_stack = slot.getItem();
				leftovers = slot_stack.copy();
				if (slot_index < getBlockSlots()) {
					if (!this.moveItemStackTo(slot_stack, getBlockSlots(), getBlockSlots() + 9 * 4, true)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(slot_stack, 0, getBlockSlots(), false)) {
					return ItemStack.EMPTY;
				}

				if (slot_stack.isEmpty()) {
					slot.set(ItemStack.EMPTY);
				} else {
					slot.setChanged();
				}

				if (slot_stack.getCount() == leftovers.getCount()) {
					return ItemStack.EMPTY;
				}

				slot.onTake(player, slot_stack);
			}

			return leftovers;
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		if (worksite != null) {
			return worksite.stillValid(player);
		}
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (worksite != null) {
			worksite.stopOpen(player);
		}
	}

	@Override
	public void setData(int index, int value) {
		super.setData(index, value);

		updateSlots();
	}

	@Override
	public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
		super.initializeContents(stateId, new ArrayList<ItemStack>(), carried);
		for (int i = 0; i < items.size(); i++) {
			client_cache.setItem(i, items.get(i));
		}
	}

	public class DynamicSlot extends Slot {
		public DynamicSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		public int getTextureX() {
			return 0;
		}

		public int getTextureY() {
			return 0;
		}
	}

	public class UpgradeSlot extends DynamicSlot {
		public UpgradeSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public int getTextureX() {
			return 18;
		}

		@Override
		public int getTextureY() {
			return 0;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof WorksiteUpgradeItem;
		}
	}

	public class ToolSlot extends DynamicSlot {
		public ToolSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public int getTextureX() {
			return 18 * 2;
		}

		@Override
		public int getTextureY() {
			return 0;
		}
	}

	public class ResultSlot extends DynamicSlot {
		public ResultSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
