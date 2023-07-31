package com.funnyman3595.crowd_computing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WorksiteBlockMenu extends AbstractContainerMenu {
	public static final RegistryObject<MenuType<WorksiteBlockMenu>> TYPE = RegistryObject
			.create(new ResourceLocation(CrowdComputing.MODID, "worksite"), ForgeRegistries.MENU_TYPES);
	public final WorksiteBlockEntity worksite;
	public final Inventory player_inventory;
	public Container client_cache = new SimpleContainer(1000);
	public final ContainerData worksite_data;
	private boolean lock_cache = false;

	public WorksiteBlockMenu(int container_id, Inventory inventory) {
		this(container_id, inventory, null, new SimpleContainerData(21));
	}

	public int getProgress() {
		if (worksite_data.get(WorksiteBlockEntity.PROCESS_DURATION_INDEX) <= 0) {
			return 0;
		}

		return (int) (24 * Math.min(1, Math.max(0, worksite_data.get(WorksiteBlockEntity.PROCESS_ELAPSED_INDEX)
				/ (double) worksite_data.get(WorksiteBlockEntity.PROCESS_DURATION_INDEX))));
	}

	public int getBlockSlots() {
		return worksite_data.get(WorksiteBlockEntity.UPGRADE_SLOTS_INDEX)
				+ worksite_data.get(WorksiteBlockEntity.INPUT_SLOTS_INDEX)
				+ worksite_data.get(WorksiteBlockEntity.TOOL_SLOTS_INDEX)
				+ worksite_data.get(WorksiteBlockEntity.OUTPUT_SLOTS_INDEX);
	}

	public WorksiteBlockMenu(int container_id, Inventory player_inventory, WorksiteBlockEntity worksite,
			ContainerData slot_counts) {
		super(WorksiteBlockMenu.TYPE.get(), container_id);
		this.worksite = worksite;
		this.player_inventory = player_inventory;
		this.worksite_data = slot_counts;
		addDataSlots(slot_counts);

		if (worksite != null) {
			worksite.startOpen(player_inventory.player);
			checkContainerSize(worksite, getBlockSlots());

			updateSlots();
		}
	}

	public record SlotInfo(int slot, int x, int y) {
	}

	public void updateSlots() {
		this.slots.clear();

		Container container;
		if (worksite == null) {
			container = client_cache;
		} else {
			container = worksite;
		}

		int slot_offset = 0;
		makeVariableSlots(slot_offset, 1, 3, 8 + 10 * 18, 17,
				worksite_data.get(WorksiteBlockEntity.UPGRADE_SLOTS_INDEX),
				(SlotInfo info) -> addSlot(new UpgradeSlot(container, info.slot(), info.x(), info.y())));
		slot_offset += worksite_data.get(WorksiteBlockEntity.UPGRADE_SLOTS_INDEX);

		if (worksite_data.get(WorksiteBlockEntity.INPUT_SLOTS_INDEX) == 1) {
			// Special case because the input slot looks wrong if there's only one and it's
			// on the left.
			addSlot(new InputSlot(container, 0, slot_offset, 8 + 3 * 18, 17 + 1 * 18));
		} else {
			// This is silly, but Java doesn't realize the callback is only used
			// immediately,
			// so it gets upset without making this final.
			final int slot_offset_for_callback = slot_offset;
			makeVariableSlots(slot_offset, 2, 3, 8 + 2 * 18, 17,
					worksite_data.get(WorksiteBlockEntity.INPUT_SLOTS_INDEX),
					(SlotInfo info) -> addSlot(new InputSlot(container, info.slot() - slot_offset_for_callback,
							info.slot(), info.x(), info.y())));
		}
		slot_offset += worksite_data.get(WorksiteBlockEntity.INPUT_SLOTS_INDEX);

		if (worksite_data.get(WorksiteBlockEntity.TOOL_SLOTS_INDEX) >= 1) {
			addSlot(new ToolSlot(container, 0, slot_offset, 8 + 4 * 18 + 9, 17));
		}
		if (worksite_data.get(WorksiteBlockEntity.TOOL_SLOTS_INDEX) >= 2) {
			addSlot(new ToolSlot(container, 1, slot_offset + 1, 8 + 4 * 18 + 9, 17 + 2 * 18));
		}
		slot_offset += worksite_data.get(WorksiteBlockEntity.TOOL_SLOTS_INDEX);

		makeVariableSlots(slot_offset, 3, 3, 8 + 6 * 18, 17, worksite_data.get(WorksiteBlockEntity.OUTPUT_SLOTS_INDEX),
				(SlotInfo info) -> addSlot(new ResultSlot(container, info.slot(), info.x(), info.y())));

		addInventorySlots();
	}

	public static void makeVariableSlots(int slot_offset, int cols, int rows, int x, int y, int slots,
			Consumer<SlotInfo> consumer) {
		int min_col_size = slots / cols;
		int larger_cols = slots % cols;
		for (int i = 0; i < slots; i++) {
			int row = i / cols;
			int col = i % cols;
			int col_size = min_col_size;
			if (col < larger_cols) {
				col_size++;
			}
			consumer.accept(new SlotInfo(slot_offset + i, x + col * 18,
					y + (rows * 18 / 2) - (col_size * 18 / 2) + (row * 18)));
		}
	}

	public void addInventorySlots() {
		for (int row = 0; row < 3; ++row) {
			for (int column = 0; column < 9; ++column) {
				this.addSlot(
						new Slot(player_inventory, column + row * 9 + 9, 8 + column * 18 + 18, 84 + row * 18 + 18));
			}
		}

		for (int hotbar_slot = 0; hotbar_slot < 9; ++hotbar_slot) {
			this.addSlot(new Slot(player_inventory, hotbar_slot, 8 + hotbar_slot * 18 + 18, 142 + 18));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot_index) {
		int old_block_slots = getBlockSlots();
		ItemStack old_stack = slots.get(slot_index).getItem().copy();
		ItemStack result = quickMoveStack_internal(player, slot_index);
		updateSlots();
		if (getBlockSlots() != old_block_slots && slot_index >= old_block_slots) {
			int new_slot_index = slot_index - old_block_slots + getBlockSlots();
			setRemoteSlot(new_slot_index, old_stack);
			slots.get(new_slot_index).setChanged();
		}
		return result;
	}

	public ItemStack quickMoveStack_internal(Player player, int slot_index) {
		if (worksite != null) {
			ItemStack original_stack = ItemStack.EMPTY;
			Slot slot = slots.get(slot_index);
			if (slot != null && slot.hasItem()) {
				ItemStack slot_stack = slot.getItem();
				original_stack = slot_stack.copy();
				int block_slots = getBlockSlots();
				if (slot_index < getBlockSlots()) {
					if (!this.moveItemStackTo(slot_stack, block_slots, block_slots + 9 * 4, true)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(slot_stack, 0, block_slots, false)) {
					return ItemStack.EMPTY;
				}

				if (slot_stack.isEmpty()) {
					slot.set(ItemStack.EMPTY);
				} else {
					slot.setChanged();
				}

				if (slot_stack.getCount() == original_stack.getCount()) {
					return ItemStack.EMPTY;
				}

				slot.onTake(player, slot_stack);
			}
			return original_stack;
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
		int old_value = worksite_data.get(index);
		if (old_value == value) {
			return;
		}
		Container old_cache = client_cache;
		if (!lock_cache && index < 10) {
			client_cache = new SimpleContainer(1000);
		}
		super.setData(index, value);

		if (index >= 10) {
			return;
		}

		updateSlots();

		if (lock_cache) {
			return;
		}

		int offset = 0;
		for (int i = 0; i < index; i++) {
			for (int slot = 0; slot < worksite_data.get(i); slot++) {
				client_cache.setItem(offset + slot, old_cache.getItem(offset + slot));
			}
			offset += worksite_data.get(i);
		}
		for (int slot = 0; slot < Math.min(old_value, value); slot++) {
			client_cache.setItem(offset + slot, old_cache.getItem(offset + slot));
		}
		int old_offset = offset + old_value;
		offset += value;
		for (int i = index + 1; i < 4; i++) {
			for (int slot = 0; slot < worksite_data.get(i); slot++) {
				client_cache.setItem(offset + slot, old_cache.getItem(old_offset + slot));
			}
			old_offset += worksite_data.get(i);
			offset += worksite_data.get(i);
		}
	}

	@Override
	public void setItem(int slot, int state_id, ItemStack value) {
		lock_cache = false;
		super.setItem(slot, state_id, value);
	};

	@Override
	public void broadcastChanges() {
		ContainerSynchronizer synchronizer = ObfuscationReflectionHelper.getPrivateValue(AbstractContainerMenu.class,
				this, "f_150397_");
		if (synchronizer != null) {
			synchronizer.sendDataChange(this, 0, worksite_data.get(0));
			synchronizer.sendDataChange(this, 1, worksite_data.get(1));
			synchronizer.sendDataChange(this, 2, worksite_data.get(2));
			synchronizer.sendDataChange(this, 3, worksite_data.get(3));
		}
		updateSlots();
		super.broadcastChanges();
	}

	@Override
	public void broadcastFullState() {
		ContainerSynchronizer synchronizer = ObfuscationReflectionHelper.getPrivateValue(AbstractContainerMenu.class,
				this, "f_150397_");
		if (synchronizer != null) {
			synchronizer.sendDataChange(this, 0, worksite_data.get(0));
			synchronizer.sendDataChange(this, 1, worksite_data.get(1));
			synchronizer.sendDataChange(this, 2, worksite_data.get(2));
			synchronizer.sendDataChange(this, 3, worksite_data.get(3));
		}
		updateSlots();
		super.broadcastFullState();
	}

	@Override
	public void sendAllDataToRemote() {
		super.sendAllDataToRemote();
	}

	@Override
	public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
		super.initializeContents(stateId, new ArrayList<ItemStack>(), carried);
		for (int i = 0; i < items.size(); i++) {
			client_cache.setItem(i, items.get(i));
		}
		lock_cache = true;
	}

	public class DynamicSlot extends Slot {
		public static int TEXTURE_X = 0;
		public static int TEXTURE_Y = 0;

		public DynamicSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		public int getTextureX() {
			return TEXTURE_X;
		}

		public int getTextureY() {
			return TEXTURE_Y;
		}
	}

	public class InputSlot extends DynamicSlot {
		public final int input_index;

		public InputSlot(Container container, int input_index, int index, int x, int y) {
			super(container, index, x, y);
			this.input_index = input_index;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (worksite == null) {
				return true;
			}
			return worksite.canPlaceItem(index, stack);
		}
	}

	public class UpgradeSlot extends DynamicSlot {
		public static int TEXTURE_X = 18;
		public static int TEXTURE_Y = 0;

		public UpgradeSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public int getTextureX() {
			return TEXTURE_X;
		}

		@Override
		public int getTextureY() {
			return TEXTURE_Y;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof WorksiteUpgradeItem;
		}
	}

	public class ToolSlot extends DynamicSlot {
		public static int TEXTURE_X = 18 * 2;
		public static int TEXTURE_Y = 0;

		public final int tool_index;

		public ToolSlot(Container container, int tool_index, int index, int x, int y) {
			super(container, index, x, y);
			this.tool_index = tool_index;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (worksite == null) {
				return true;
			}
			return worksite.canPlaceItem(index, stack);
		}

		@Override
		public int getTextureX() {
			return TEXTURE_X;
		}

		@Override
		public int getTextureY() {
			return TEXTURE_Y;
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

	public Container getContainer() {
		if (worksite != null) {
			return worksite;
		}
		return client_cache;
	}

	public void toggle_lock() {
		CrowdComputingChannel.INSTANCE.sendToServer(new CrowdComputingChannel.ToggleRecipeLock(new BlockPos(
				worksite_data.get(WorksiteBlockEntity.POS_X_INDEX), worksite_data.get(WorksiteBlockEntity.POS_Y_INDEX),
				worksite_data.get(WorksiteBlockEntity.POS_Z_INDEX))));
	}

	public boolean recipe_locked() {
		return worksite_data.get(WorksiteBlockEntity.RECIPE_LOCK_INDEX) > 0;
	}

	public int get_energy_fill(int bar_size) {
		int storage = worksite_data.get(WorksiteBlockEntity.ENERGY_STORAGE_INDEX);
		int cap = worksite_data.get(WorksiteBlockEntity.ENERGY_CAP_INDEX);
		if (storage == 0) {
			return 0;
		}
		return Math.max(1, (bar_size * storage) / cap);
	}

	public int get_fluid_fill(int bar_size) {
		int storage = worksite_data.get(WorksiteBlockEntity.FLUID_STORAGE_INDEX);
		int cap = worksite_data.get(WorksiteBlockEntity.FLUID_CAP_INDEX);
		if (storage == 0) {
			return 0;
		}
		return Math.max(1, (bar_size * storage) / cap);
	}

	public boolean has_energy() {
		return worksite_data.get(WorksiteBlockEntity.ENERGY_CAP_INDEX) > 0;
	}

	public boolean has_fluid() {
		return worksite_data.get(WorksiteBlockEntity.FLUID_CAP_INDEX) > 0;
	}

	@SuppressWarnings("deprecation")
	public FluidStack get_fluid() {
		int fluid_id = worksite_data.get(WorksiteBlockEntity.FLUID_TYPE);
		if (fluid_id == -1) {
			return FluidStack.EMPTY;
		}
		return new FluidStack(Registry.FLUID.byId(fluid_id),
				worksite_data.get(WorksiteBlockEntity.FLUID_STORAGE_INDEX));
	}
}
