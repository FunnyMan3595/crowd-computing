package com.funnyman3595.crowd_computing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeHolder;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

public class WorksiteBlockEntity extends BaseContainerBlockEntity
		implements WorldlyContainer, RecipeHolder, StackedContentsCompatible {
	public static HashMap<String, RegistryObject<BlockEntityType<WorksiteBlockEntity>>> block_entities = new HashMap<String, RegistryObject<BlockEntityType<WorksiteBlockEntity>>>();

	public final WorksiteBlock block;
	public final WorksiteUpgrade builtin;
	public NonNullList<ItemStack> upgrades;
	public NonNullList<ItemStack> input_items;
	public NonNullList<ItemStack> tool_items;
	public NonNullList<ItemStack> output_items;

	public static final int UPGRADES_INDEX = 0;
	public static final int INPUTS_INDEX = 1;
	public static final int TOOLS_INDEX = 2;
	public static final int OUTPUTS_INDEX = 3;
	public ContainerData slot_counts = new SimpleContainerData(4);

	public WorksiteBlockEntity(BlockPos pos, BlockState state) {
		super(block_entities.get(((WorksiteBlock) state.getBlock()).name).get(), pos, state);
		block = (WorksiteBlock) state.getBlock();

		if (block.config.has("builtin")) {
			builtin = WorksiteUpgrade.load(block.name + "/builtin", block.config.getAsJsonObject("builtin"));
		} else {
			builtin = WorksiteUpgrade.load(block.name + "/builtin", new JsonObject());
		}

		int loaded_upgrade_slot_count = 0;
		if (block.config.has("upgrade_slot_count")) {
			try {
				loaded_upgrade_slot_count = block.config.get("upgrade_slot_count").getAsInt();
			} catch (Exception e) {
				CrowdComputing.LOGGER.error("Failed to load upgrade slot count for " + block.name, e);
			}
		}
		slot_counts.set(UPGRADES_INDEX, Math.min(3, loaded_upgrade_slot_count));
		upgrades = NonNullList.withSize(slot_counts.get(UPGRADES_INDEX), ItemStack.EMPTY);
		slot_counts.set(INPUTS_INDEX, getInputSlotCount());
		input_items = NonNullList.withSize(slot_counts.get(INPUTS_INDEX), ItemStack.EMPTY);
		slot_counts.set(TOOLS_INDEX, builtin.tool_slot_count());
		tool_items = NonNullList.withSize(slot_counts.get(TOOLS_INDEX), ItemStack.EMPTY);
		slot_counts.set(OUTPUTS_INDEX, builtin.output_slot_count());
		output_items = NonNullList.withSize(slot_counts.get(OUTPUTS_INDEX), ItemStack.EMPTY);
	}

	public int getInputSlotCount() {
		int max_input_slot_count = builtin.input_slot_count();
		for (ItemStack upgrade_item_stack : upgrades.toArray(new ItemStack[0])) {
			if (!(upgrade_item_stack.getItem() instanceof WorksiteUpgradeItem)) {
				continue;
			}
			WorksiteUpgrade upgrade = ((WorksiteUpgradeItem) upgrade_item_stack.getItem()).upgrade;
			if (upgrade.input_slot_count() > max_input_slot_count) {
				max_input_slot_count = upgrade.input_slot_count();
			}
		}
		return Math.min(6, max_input_slot_count);
	}

	public int getToolSlotCount() {
		int max_tool_slot_count = builtin.tool_slot_count();
		for (ItemStack upgrade_item_stack : upgrades.toArray(new ItemStack[0])) {
			if (!(upgrade_item_stack.getItem() instanceof WorksiteUpgradeItem)) {
				continue;
			}
			WorksiteUpgrade upgrade = ((WorksiteUpgradeItem) upgrade_item_stack.getItem()).upgrade;
			if (upgrade.tool_slot_count() > max_tool_slot_count) {
				max_tool_slot_count = upgrade.tool_slot_count();
			}
		}
		return Math.min(2, max_tool_slot_count);
	}

	public int getOutputSlotCount() {
		int max_output_slot_count = builtin.output_slot_count();
		for (ItemStack upgrade_item_stack : upgrades.toArray(new ItemStack[0])) {
			if (!(upgrade_item_stack.getItem() instanceof WorksiteUpgradeItem)) {
				continue;
			}
			WorksiteUpgrade upgrade = ((WorksiteUpgradeItem) upgrade_item_stack.getItem()).upgrade;
			if (upgrade.output_slot_count() > max_output_slot_count) {
				max_output_slot_count = upgrade.output_slot_count();
			}
		}
		return Math.min(9, max_output_slot_count);
	}

	@Override
	public int getMaxStackSize() {
		int max_stack_size = builtin.stack_size();
		for (ItemStack upgrade_item_stack : upgrades.toArray(new ItemStack[0])) {
			if (!(upgrade_item_stack.getItem() instanceof WorksiteUpgradeItem)) {
				continue;
			}
			WorksiteUpgrade upgrade = ((WorksiteUpgradeItem) upgrade_item_stack.getItem()).upgrade;
			if (upgrade.stack_size() > max_stack_size) {
				max_stack_size = upgrade.stack_size();
			}
		}
		return max_stack_size;
	}

	@Override
	public int getContainerSize() {
		return upgrades.size() + input_items.size() + tool_items.size() + output_items.size();
	}

	@Override
	public boolean isEmpty() {
		for (int slot = 0; slot < getContainerSize(); slot++) {
			if (!getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public void resize(NonNullList<ItemStack> list, int size) {
		if (size > list.size()) {
			for (int i = 0; i < size - list.size(); i++) {
				list.add(ItemStack.EMPTY);
			}
		} else {
			while (list.size() > size) {
				ItemStack removed = list.remove(list.size() - 1);
				if (!level.isClientSide() && !removed.isEmpty()) {
					BlockPos pos = getBlockPos();
					ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, removed);
					entity.setDefaultPickUpDelay();
					level.addFreshEntity(entity);
				}
			}
		}
	}

	public void recalcSlots() {
		if (input_items.size() != getInputSlotCount()) {
			resize(input_items, getInputSlotCount());
		}
		slot_counts.set(INPUTS_INDEX,  getInputSlotCount());
		if (tool_items.size() != getToolSlotCount()) {
			resize(tool_items, getToolSlotCount());
		}
		slot_counts.set(TOOLS_INDEX, getToolSlotCount());
		if (output_items.size() != getOutputSlotCount()) {
			resize(output_items, getOutputSlotCount());
		}
		slot_counts.set(OUTPUTS_INDEX, getOutputSlotCount());
	}

	public Pair<List<ItemStack>, Integer> getSlot(int slot) {
		if (slot < 0 || slot >= getContainerSize()) {
			return null;
		}
		
		if (slot < slot_counts.get(UPGRADES_INDEX)) {
			return Pair.of(upgrades, slot);
		}
		slot -= slot_counts.get(UPGRADES_INDEX);
		
		if (slot < slot_counts.get(INPUTS_INDEX)) {
			return Pair.of(input_items, slot);
		}
		slot -= slot_counts.get(INPUTS_INDEX);
		
		if (slot < slot_counts.get(TOOLS_INDEX)) {
			return Pair.of(tool_items, slot);
		}
		slot -= slot_counts.get(TOOLS_INDEX);
		
		return Pair.of(output_items, slot);
	}

	@Override
	public ItemStack getItem(int slot) {
		Pair<List<ItemStack>, Integer> slot_pair = getSlot(slot);
		return slot_pair.getLeft().get(slot_pair.getRight());
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		Pair<List<ItemStack>, Integer> slot_pair = getSlot(slot);
		ItemStack stack = ContainerHelper.removeItem(slot_pair.getLeft(), slot_pair.getRight(), count);
		if (!stack.isEmpty()) {
			setChanged();

			if (slot_pair.getLeft() == upgrades) {
				recalcSlots();
			}
		}
		return stack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		Pair<List<ItemStack>, Integer> slot_pair = getSlot(slot);
		ItemStack result = ContainerHelper.takeItem(slot_pair.getLeft(), slot_pair.getRight());
		if (slot_pair.getLeft() == upgrades) {
			recalcSlots();
		}
		return result;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		Pair<List<ItemStack>, Integer> slot_pair = getSlot(slot);
		if (slot_pair.getLeft() == upgrades) {
			return stack.getItem() instanceof WorksiteUpgradeItem;
		}
		if (slot_pair.getLeft() == output_items) {
			return false;
		}
		return true;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		Pair<List<ItemStack>, Integer> slot_pair = getSlot(slot);
		slot_pair.getLeft().set(slot_pair.getRight(), stack);
		if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
			stack.setCount(getMaxStackSize());
		}

		if (slot_pair.getLeft() == upgrades) {
			recalcSlots();
		}

		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		if (level.getBlockEntity(worldPosition) != this) {
			return false;
		} else {
			return !(player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
					worldPosition.getZ() + 0.5) > 64);
		}
	}

	@Override
	public void clearContent() {
		upgrades.clear();
		input_items = NonNullList.withSize(builtin.input_slot_count(), ItemStack.EMPTY);
		tool_items = NonNullList.withSize(builtin.tool_slot_count(), ItemStack.EMPTY);
		output_items = NonNullList.withSize(builtin.output_slot_count(), ItemStack.EMPTY);
	}

	@Override
	public void fillStackedContents(StackedContents contents) {
		for (ItemStack stack : upgrades) {
			contents.accountStack(stack);
		}
		for (ItemStack stack : input_items) {
			contents.accountStack(stack);
		}
		for (ItemStack stack : tool_items) {
			contents.accountStack(stack);
		}
		for (ItemStack stack : output_items) {
			contents.accountStack(stack);
		}
	}

	@Override
	public void setRecipeUsed(Recipe<?> p_40134_) {
	}

	@Override
	public Recipe<?> getRecipeUsed() {
		return null;
	}

	@Override
	public int[] getSlotsForFace(Direction p_19238_) {
		int[] nothing = {};
		return nothing;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	@Override
	protected Component getDefaultName() {
		return Component.literal("Worksite");
	}

	@Override
	protected AbstractContainerMenu createMenu(int container_id, Inventory inventory) {
		return new WorksiteBlockMenu(container_id, inventory, this, slot_counts);
	}

	public ItemStack getIdentity() {
		return new ItemStack(WorksiteBlock.items.get(block.name).get(), 1);
	}

	public List<ItemStack> getDrops() {
		List<ItemStack> drops = new ArrayList<ItemStack>();
		drops.add(getIdentity());
		for (ItemStack stack : upgrades) {
			drops.add(stack);
		}
		for (ItemStack stack : input_items) {
			drops.add(stack);
		}
		for (ItemStack stack : tool_items) {
			drops.add(stack);
		}
		for (ItemStack stack : output_items) {
			drops.add(stack);
		}
		return drops;
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);

		ContainerHelper.loadAllItems(tag.getCompound("upgrades"), upgrades);
		recalcSlots();
		ContainerHelper.loadAllItems(tag.getCompound("input_items"), input_items);
		ContainerHelper.loadAllItems(tag.getCompound("tool_items"), tool_items);
		ContainerHelper.loadAllItems(tag.getCompound("output_items"), output_items);
	}

	@Override
	public void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);

		CompoundTag upgrades_tag = new CompoundTag();
		ContainerHelper.saveAllItems(upgrades_tag, upgrades);
		tag.put("upgrades", upgrades_tag);

		CompoundTag input_items_tag = new CompoundTag();
		ContainerHelper.saveAllItems(input_items_tag, input_items);
		tag.put("input_items", input_items_tag);

		CompoundTag tool_items_tag = new CompoundTag();
		ContainerHelper.saveAllItems(tool_items_tag, tool_items);
		tag.put("tool_items", tool_items_tag);

		CompoundTag output_items_tag = new CompoundTag();
		ContainerHelper.saveAllItems(output_items_tag, output_items);
		tag.put("output_items", output_items_tag);
	}
}
