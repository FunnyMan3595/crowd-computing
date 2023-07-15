package com.funnyman3595.crowd_computing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.google.gson.JsonObject;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

public class WorksiteBlockEntity extends BaseContainerBlockEntity
		implements WorldlyContainer, StackedContentsCompatible {
	public static final int MAX_STORED_BLOCKAGE = 10;
	public static HashMap<String, RegistryObject<BlockEntityType<WorksiteBlockEntity>>> block_entities = new HashMap<String, RegistryObject<BlockEntityType<WorksiteBlockEntity>>>();

	public final WorksiteBlock block;
	public final WorksiteUpgrade builtin;
	public NonNullList<ItemStack> upgrades;
	public NonNullList<ItemStack> input_items;
	public NonNullList<ItemStack> tool_items;
	public NonNullList<ItemStack> output_items;
	public int process_elapsed = 0;
	public int process_duration = 0;

	public boolean inventory_dirty = true;
	public WorksiteRecipe current_recipe = null;
	public ObjectArrayList<ItemStack> output_blockage = null;

	public static final int UPGRADE_SLOTS_INDEX = 0;
	public static final int INPUT_SLOTS_INDEX = 1;
	public static final int TOOL_SLOTS_INDEX = 2;
	public static final int OUTPUT_SLOTS_INDEX = 3;
	public static final int PROCESS_ELAPSED_INDEX = 10;
	public static final int PROCESS_DURATION_INDEX = 11;
	public ContainerData worksite_data = new ContainerData() {
		@Override
		public int get(int index) {
			switch (index) {
			case UPGRADE_SLOTS_INDEX:
				return upgrades.size();
			case INPUT_SLOTS_INDEX:
				return input_items.size();
			case TOOL_SLOTS_INDEX:
				return tool_items.size();
			case OUTPUT_SLOTS_INDEX:
				return output_items.size();
			case PROCESS_ELAPSED_INDEX:
				return process_elapsed;
			case PROCESS_DURATION_INDEX:
				return process_duration;
			default:
				return 0;
			}
		}

		@Override
		public void set(int index, int value) {
			if (index < 10) {
				recalcSlots();
			}
			switch (index) {
			case PROCESS_ELAPSED_INDEX:
				process_elapsed = value;
				break;
			case PROCESS_DURATION_INDEX:
				process_duration = value;
				break;
			}
		}

		@Override
		public int getCount() {
			return 12;
		}
	};

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
		upgrades = NonNullList.withSize(Math.min(3, loaded_upgrade_slot_count), ItemStack.EMPTY);
		input_items = NonNullList.withSize(builtin.input_slot_count(), ItemStack.EMPTY);
		tool_items = NonNullList.withSize(builtin.tool_slot_count(), ItemStack.EMPTY);
		output_items = NonNullList.withSize(builtin.output_slot_count(), ItemStack.EMPTY);
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

	public NonNullList<ItemStack> consider_resize(NonNullList<ItemStack> list, int size) {
		if (list.size() == size) {
			return list;
		}
		NonNullList<ItemStack> new_list = NonNullList.withSize(size, ItemStack.EMPTY);
		for (int i = 0; i < list.size(); i++) {
			ItemStack stack = list.get(i);
			if (i < size) {
				new_list.set(i, stack);
			} else {
				if (!level.isClientSide() && !stack.isEmpty()) {
					BlockPos pos = getBlockPos();
					ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
							stack);
					entity.setDefaultPickUpDelay();
					level.addFreshEntity(entity);
				}
			}
		}
		return new_list;
	}

	public void recalcSlots() {
		input_items = consider_resize(input_items, getInputSlotCount());
		tool_items = consider_resize(tool_items, getToolSlotCount());
		output_items = consider_resize(output_items, getOutputSlotCount());
	}

	public Pair<List<ItemStack>, Integer> getSlot(int slot) {
		if (slot < 0 || slot >= getContainerSize()) {
			return null;
		}

		if (slot < worksite_data.get(UPGRADE_SLOTS_INDEX)) {
			return Pair.of(upgrades, slot);
		}
		slot -= worksite_data.get(UPGRADE_SLOTS_INDEX);

		if (slot < worksite_data.get(INPUT_SLOTS_INDEX)) {
			return Pair.of(input_items, slot);
		}
		slot -= worksite_data.get(INPUT_SLOTS_INDEX);

		if (slot < worksite_data.get(TOOL_SLOTS_INDEX)) {
			return Pair.of(tool_items, slot);
		}
		slot -= worksite_data.get(TOOL_SLOTS_INDEX);

		return Pair.of(output_items, slot);
	}

	@Override
	public ItemStack getItem(int slot) {
		Pair<List<ItemStack>, Integer> slot_pair = getSlot(slot);
		if (slot_pair != null) {
			return slot_pair.getLeft().get(slot_pair.getRight());
		}
		return ItemStack.EMPTY;
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
		inventory_dirty = true;
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
		return block.getName();
	}

	@Override
	protected AbstractContainerMenu createMenu(int container_id, Inventory inventory) {
		return new WorksiteBlockMenu(container_id, inventory, this, worksite_data);
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

	public static void serverTick(Level level, BlockPos pos, BlockState state, WorksiteBlockEntity entity) {
		if (entity.current_recipe != null) {
			entity.process_elapsed += 1;
			if (entity.process_elapsed >= entity.process_duration) {
				entity.output_blockage = entity.current_recipe.outputs.roll_output(level.random);
				entity.current_recipe = null;
				entity.inventory_dirty = true;
			} else {
				return;
			}
		}

		if (!entity.inventory_dirty) {
			return;
		}

		if (!entity.tryClearBlockage()) {
			entity.inventory_dirty = false;
			return;
		}

		entity.process_elapsed = 0;

		int best_match_score = -1;
		WorksiteRecipe best_match = null;
		for (WorksiteRecipe recipe : WorksiteRecipe.RECIPES.get(level)) {
			if (recipe.complexity > best_match_score && recipe.matches(entity, level)) {
				best_match_score = recipe.complexity;
				best_match = recipe;
			}
		}

		if (best_match != null) {
			entity.consumeAllInputs(best_match.ingredients);
			entity.damageAllTools(best_match.tools);
			entity.current_recipe = best_match;
			entity.process_duration = 0;
			for (WorksiteRecipe.Stage stage : best_match.stages) {
				entity.process_duration += stage.duration();
			}
			return;
		}

		entity.inventory_dirty = false;
	}

	private boolean tryClearBlockage() {
		if (output_blockage == null) {
			return true;
		}

		int max_stack_size = getMaxStackSize();

		boolean any_left = false;
		for (ItemStack source_stack : output_blockage) {
			if (source_stack.isEmpty()) {
				continue;
			}

			for (int i = 0; i < output_items.size(); i++) {
				ItemStack target_stack = output_items.get(i);
				if (target_stack.isEmpty()) {
					output_items.set(i, source_stack.copy());
					source_stack.setCount(0);
					break;
				}

				if (max_stack_size > 1 && target_stack.getCount() < max_stack_size && source_stack.isStackable()
						&& ItemStack.isSameItemSameTags(source_stack, target_stack)) {
					if (source_stack.getCount() + target_stack.getCount() <= max_stack_size) {
						target_stack.setCount(source_stack.getCount() + target_stack.getCount());
						source_stack.setCount(0);
						break;
					} else {
						source_stack.setCount(source_stack.getCount() + target_stack.getCount() - max_stack_size);
						target_stack.setCount(0);
					}
				}
			}

			if (!source_stack.isEmpty()) {
				any_left = true;
			}
		}

		if (any_left) {
			return false;
		}

		output_blockage = null;
		return true;
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);

		ContainerHelper.loadAllItems(tag.getCompound("upgrades"), upgrades);
		recalcSlots();
		ContainerHelper.loadAllItems(tag.getCompound("input_items"), input_items);
		ContainerHelper.loadAllItems(tag.getCompound("tool_items"), tool_items);
		ContainerHelper.loadAllItems(tag.getCompound("output_items"), output_items);

		if (tag.contains("recipe_info")) {
			CompoundTag recipe_info = tag.getCompound("recipe_info");
			process_elapsed = recipe_info.getInt("elapsed");
			process_duration = recipe_info.getInt("duration");
			inventory_dirty = true;

			if (recipe_info.contains("recipe_id")) {
				try {
					current_recipe = (WorksiteRecipe) level.getRecipeManager()
							.byKey(new ResourceLocation(recipe_info.getString("recipe_id"))).get();
				} catch (Exception e) {
					CrowdComputing.LOGGER.error("Unable to load recipe: ", e);
				}
			} else {
				current_recipe = null;
			}

			if (recipe_info.contains("blockage")) {
				NonNullList<ItemStack> blockage_temp = NonNullList.withSize(MAX_STORED_BLOCKAGE, ItemStack.EMPTY);
				ContainerHelper.loadAllItems(recipe_info.getCompound("blockage"), blockage_temp);
				output_blockage = new ObjectArrayList<ItemStack>();
				for (ItemStack stack : blockage_temp) {
					if (!stack.isEmpty()) {
						output_blockage.add(stack);
					}
				}
				if (output_blockage.size() == 0) {
					output_blockage = null;
				}
			} else {
				output_blockage = null;
			}
		} else {
			process_elapsed = 0;
			process_duration = 0;
			inventory_dirty = true;
			current_recipe = null;
			output_blockage = null;
		}
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

		if (current_recipe != null || output_blockage != null) {
			CompoundTag recipe_info = new CompoundTag();
			recipe_info.putInt("elapsed", process_elapsed);
			recipe_info.putInt("duration", process_duration);

			if (current_recipe != null) {
				recipe_info.putString("recipe_id", current_recipe.id.toString());
			}

			if (output_blockage != null) {
				CompoundTag output_blockage_tag = new CompoundTag();
				NonNullList<ItemStack> blockage_temp = NonNullList.withSize(MAX_STORED_BLOCKAGE, ItemStack.EMPTY);
				for (int i = 0; i < Math.min(MAX_STORED_BLOCKAGE, output_blockage.size()); i++) {
					blockage_temp.set(i, output_blockage.get(i));
				}
				ContainerHelper.saveAllItems(output_blockage_tag, blockage_temp);
				recipe_info.put("recipe_info", output_blockage_tag);
			}
		}
	}

	public boolean hasAllInputs(WorksiteRecipe.CountableIngredient[] ingredients) {
		if (ingredients.length == 0) {
			// Special case: No-ingredient recipes require input slots to be empty.
			for (int i = 0; i < input_items.size(); i++) {
				if (!input_items.get(i).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		int[] needed = new int[ingredients.length];
		for (int j = 0; j < ingredients.length; j++) {
			needed[j] = ingredients[j].count();
		}
		for (int i = 0; i < input_items.size(); i++) {
			ItemStack stack = input_items.get(i);
			int left = stack.getCount();
			for (int j = 0; left > 0 && j < ingredients.length; j++) {
				if (needed[j] <= 0) {
					continue;
				}
				if (ingredients[j].ingredient().test(stack)) {
					if (left > needed[j]) {
						left -= needed[j];
						needed[j] = 0;
					} else {
						needed[j] -= left;
						left = 0;
					}
				}
			}
		}

		for (int j = 0; j < ingredients.length; j++) {
			if (needed[j] > 0) {
				return false;
			}
		}
		return true;
	}

	public void consumeAllInputs(WorksiteRecipe.CountableIngredient[] ingredients) {
		int[] needed = new int[ingredients.length];
		for (int j = 0; j < ingredients.length; j++) {
			needed[j] = ingredients[j].count();
		}
		for (int i = 0; i < input_items.size(); i++) {
			ItemStack stack = input_items.get(i);
			for (int j = 0; stack.getCount() > 0 && j < ingredients.length; j++) {
				if (needed[j] <= 0) {
					continue;
				}
				if (ingredients[j].ingredient().test(stack)) {
					if (stack.getCount() > needed[j]) {
						stack.setCount(stack.getCount() - needed[j]);
						needed[j] = 0;
					} else {
						needed[j] -= stack.getCount();
						stack.setCount(0);
						input_items.set(i, ItemStack.EMPTY);
					}
				}
			}
		}
	}

	public boolean hasAllTools(WorksiteRecipe.CountableIngredient[] tools) {
		int[] needed = new int[tools.length];
		for (int j = 0; j < tools.length; j++) {
			needed[j] = tools[j].count();
		}
		for (int i = 0; i < tool_items.size(); i++) {
			ItemStack stack = tool_items.get(i);
			int left = stack.getCount();
			for (int j = 0; left > 0 && j < tools.length; j++) {
				if (needed[j] <= 0) {
					continue;
				}
				if (tools[j].ingredient().test(stack)) {
					if (left > needed[j]) {
						left -= needed[j];
						needed[j] = 0;
					} else {
						needed[j] -= left;
						left = 0;
					}
				}
			}
		}

		for (int j = 0; j < tools.length; j++) {
			if (needed[j] > 0) {
				return false;
			}
		}
		return true;
	}

	private void damageAndBreak(ItemStack stack, int amount) {
		boolean broke = stack.hurt(amount, level.random, null);
		if (broke) {
			stack.shrink(1);
			stack.setDamageValue(0);
		}
	}

	public boolean damageAllTools(WorksiteRecipe.CountableIngredient[] tools) {
		int[] needed = new int[tools.length];
		for (int j = 0; j < tools.length; j++) {
			needed[j] = tools[j].count();
		}
		for (int i = 0; i < tool_items.size(); i++) {
			ItemStack stack = tool_items.get(i);
			int left = stack.getCount();
			for (int j = 0; left > 0 && j < tools.length; j++) {
				if (needed[j] <= 0) {
					continue;
				}
				if (tools[j].ingredient().test(stack)) {
					if (left > needed[j]) {
						damageAndBreak(stack, needed[j]);
						needed[j] = 0;
					} else {
						damageAndBreak(stack, left);
						left = 0;
					}
				}
			}
		}

		for (int j = 0; j < tools.length; j++) {
			if (needed[j] > 0) {
				return false;
			}
		}
		return true;
	}

	public boolean isWaterlogged() {
		return level.getBlockState(getBlockPos()).getValue(WorksiteBlock.WATERLOGGED);
	}
}
