package com.funnyman3595.crowd_computing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.funnyman3595.crowd_computing.WorksiteRecipe.Stage;
import com.google.gson.JsonObject;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
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
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;

public class WorksiteBlockEntity extends BaseContainerBlockEntity
		implements WorldlyContainer, StackedContentsCompatible, IFluidTank, IEnergyStorage {
	public static final int MAX_UPGRADE_SLOTS = 3;
	public static final int MAX_INPUT_SLOTS = 6;
	public static final int MAX_TOOL_SLOTS = 2;
	public static final int MAX_OUTPUT_SLOTS = 9;
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
	public int energy_storage = 0;
	public int energy_cap = 0;
	public int fluid_cap = 0;
	public FluidStack fluid = FluidStack.EMPTY;

	public boolean should_recheck_recipe = true;
	public WorksiteRecipe current_recipe = null;
	public WorksiteRecipe locked_recipe = null;
	public WorksiteRecipe last_recipe = null;
	public ObjectArrayList<ItemStack> output_blockage = null;

	public HashSet<Player> players_in_gui = new HashSet<Player>();

	public Worker worker = null;
	public int worker_search_tick = 0;

	public static final int UPGRADE_SLOTS_INDEX = 0;
	public static final int INPUT_SLOTS_INDEX = 1;
	public static final int TOOL_SLOTS_INDEX = 2;
	public static final int OUTPUT_SLOTS_INDEX = 3;
	public static final int PROCESS_ELAPSED_INDEX = 10;
	public static final int PROCESS_DURATION_INDEX = 11;
	public static final int RECIPE_LOCK_INDEX = 12;
	public static final int ENERGY_STORAGE_INDEX = 13;
	public static final int ENERGY_CAP_INDEX = 14;
	public static final int FLUID_STORAGE_INDEX = 15;
	public static final int FLUID_CAP_INDEX = 16;
	public static final int POS_X_INDEX = 17;
	public static final int POS_Y_INDEX = 18;
	public static final int POS_Z_INDEX = 19;
	public static final int FLUID_TYPE = 20;
	public ContainerData worksite_data = new ContainerData() {
		@SuppressWarnings("deprecation")
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
			case RECIPE_LOCK_INDEX:
				return locked_recipe == null ? 0 : 1;
			case ENERGY_STORAGE_INDEX:
				return energy_storage;
			case ENERGY_CAP_INDEX:
				return energy_cap;
			case FLUID_STORAGE_INDEX:
				return fluid == FluidStack.EMPTY ? 0 : fluid.getAmount();
			case FLUID_CAP_INDEX:
				return fluid_cap;
			case POS_X_INDEX:
				return getBlockPos().getX();
			case POS_Y_INDEX:
				return getBlockPos().getY();
			case POS_Z_INDEX:
				return getBlockPos().getZ();
			case FLUID_TYPE:
				if (fluid != FluidStack.EMPTY) {
					return Registry.FLUID.getId(fluid.getFluid());
				}
				return -1;
			default:
				return 0;
			}
		}

		@Override
		public void set(int index, int value) {
			if (index < 10) {
				recalcCapacities();
			}
			switch (index) {
			case PROCESS_ELAPSED_INDEX:
				process_elapsed = value;
				break;
			case PROCESS_DURATION_INDEX:
				process_duration = value;
				break;
			case ENERGY_STORAGE_INDEX:
				energy_storage = value;
				break;
			case ENERGY_CAP_INDEX:
				energy_cap = value;
				break;
			case FLUID_STORAGE_INDEX:
				if (fluid != FluidStack.EMPTY) {
					fluid.setAmount(value);
				}
				break;
			case FLUID_CAP_INDEX:
				fluid_cap = value;
				break;
			}
		}

		@Override
		public int getCount() {
			return 21;
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
		upgrades = NonNullList.withSize(Math.min(MAX_UPGRADE_SLOTS, loaded_upgrade_slot_count), ItemStack.EMPTY);
		input_items = NonNullList.withSize(builtin.input_slot_count(), ItemStack.EMPTY);
		tool_items = NonNullList.withSize(builtin.tool_slot_count(), ItemStack.EMPTY);
		output_items = NonNullList.withSize(builtin.output_slot_count(), ItemStack.EMPTY);
		energy_cap = getEnergyCap();
		fluid_cap = getFluidCap();
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
		return Math.min(MAX_INPUT_SLOTS, max_input_slot_count);
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
		return Math.min(MAX_TOOL_SLOTS, max_tool_slot_count);
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
		return Math.min(MAX_OUTPUT_SLOTS, max_output_slot_count);
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

	public int getEnergyCap() {
		int total_energy_cap = builtin.energy_cap();
		for (ItemStack upgrade_item_stack : upgrades.toArray(new ItemStack[0])) {
			if (!(upgrade_item_stack.getItem() instanceof WorksiteUpgradeItem)) {
				continue;
			}
			WorksiteUpgrade upgrade = ((WorksiteUpgradeItem) upgrade_item_stack.getItem()).upgrade;
			if (upgrade.energy_cap() > 0) {
				total_energy_cap += upgrade.energy_cap();
			}
		}
		return total_energy_cap;
	}

	public int getFluidCap() {
		int total_fluid_cap = builtin.fluid_cap();
		for (ItemStack upgrade_item_stack : upgrades.toArray(new ItemStack[0])) {
			if (!(upgrade_item_stack.getItem() instanceof WorksiteUpgradeItem)) {
				continue;
			}
			WorksiteUpgrade upgrade = ((WorksiteUpgradeItem) upgrade_item_stack.getItem()).upgrade;
			if (upgrade.fluid_cap() > 0) {
				total_fluid_cap += upgrade.fluid_cap();
			}
		}
		return total_fluid_cap;
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

	public void recalcCapacities() {
		input_items = consider_resize(input_items, getInputSlotCount());
		tool_items = consider_resize(tool_items, getToolSlotCount());
		output_items = consider_resize(output_items, getOutputSlotCount());
		energy_cap = getEnergyCap();
		if (energy_storage > energy_cap) {
			energy_storage = energy_cap;
		}
		fluid_cap = getFluidCap();
		if (fluid != FluidStack.EMPTY && fluid.getAmount() > fluid_cap) {
			if (fluid_cap == 0) {
				fluid = FluidStack.EMPTY;
			} else {
				fluid.setAmount(fluid_cap);
			}
		}
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
				recalcCapacities();
			}
		}
		setChanged();
		should_recheck_recipe = true;
		return stack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		Pair<List<ItemStack>, Integer> slot_pair = getSlot(slot);
		ItemStack result = ContainerHelper.takeItem(slot_pair.getLeft(), slot_pair.getRight());
		if (slot_pair.getLeft() == upgrades) {
			recalcCapacities();
		}
		setChanged();
		should_recheck_recipe = true;
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
		if (locked_recipe != null) {
			if (slot_pair.getLeft() == input_items) {
				int index = slot_pair.getRight();
				if (locked_recipe.ingredients.length > index
						&& locked_recipe.ingredients[index].ingredient().test(stack)) {
					return true;
				}
				return false;
			}
			if (slot_pair.getLeft() == tool_items) {
				int index = slot_pair.getRight();
				if (locked_recipe.tools.length > index && locked_recipe.tools[index].ingredient().test(stack)) {
					return true;
				}
				return false;
			}
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
			recalcCapacities();
		}

		setChanged();
		should_recheck_recipe = true;
	}

	@Override
	public void startOpen(Player player) {
		players_in_gui.add(player);
	}

	@Override
	public void stopOpen(Player player) {
		players_in_gui.remove(player);
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
	public int[] getSlotsForFace(Direction direction) {
		if (direction == Direction.UP) {
			return IntStream.range(worksite_data.get(UPGRADE_SLOTS_INDEX),
					worksite_data.get(UPGRADE_SLOTS_INDEX) + worksite_data.get(INPUT_SLOTS_INDEX)).toArray();
		} else if (direction == Direction.DOWN) {
			return IntStream.range(
					worksite_data.get(UPGRADE_SLOTS_INDEX) + worksite_data.get(INPUT_SLOTS_INDEX)
							+ worksite_data.get(TOOL_SLOTS_INDEX),
					worksite_data.get(UPGRADE_SLOTS_INDEX) + worksite_data.get(INPUT_SLOTS_INDEX)
							+ worksite_data.get(TOOL_SLOTS_INDEX) + worksite_data.get(OUTPUT_SLOTS_INDEX))
					.toArray();
		}

		int[] nothing = {};
		return nothing;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return true;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return true;
	}

	@Override
	protected Component getDefaultName() {
		if (worker == null) {
			return Component.translatable("crowd_computing.worksite_name_empty", block.getName());
		}
		return Component.translatable("crowd_computing.worksite_name_worker", block.getName(), worker.getDisplayName());
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
		for (ItemStack stack : output_blockage) {
			drops.add(stack);
		}
		return drops;
	}

	public boolean tryGetWorker() {
		if (worker != null) {
			if (worker.isValid(this)) {
				return true;
			} else {
				worker = null;
			}
		}

		if (worker_search_tick == 0) {
			requestWorker();
		}
		worker_search_tick = (worker_search_tick + 1) % 20;
		return false;
	}

	public void requestWorker() {
		CrowdSourceBlockEntity best_source = CrowdSourceBlockEntity.get_closest_loaded_in_range(level, getBlockPos());

		if (best_source != null) {
			best_source.requestSpawn(this);
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, WorksiteBlockEntity entity) {
		if (!entity.players_in_gui.isEmpty()) {
			Component message = Component.translatable("crowd_computing.worksite_unknown_stage");
			if (entity.worker == null) {
				message = Component.translatable("crowd_computing.worksite_no_worker");
			} else if (entity.current_recipe != null) {
				int duration_left = entity.process_elapsed;
				for (Stage stage : entity.current_recipe.stages) {
					duration_left -= stage.duration();
					if (duration_left < 0) {
						message = stage.message();
						break;
					}
				}
			} else if (entity.output_blockage != null) {
				message = Component.translatable("crowd_computing.worksite_output_blocked");
			} else {
				message = Component.translatable("crowd_computing.worksite_idle");
			}

			ObjectArrayList<Connection> connection_list = new ObjectArrayList<Connection>();
			for (Player player : entity.players_in_gui) {
				connection_list.add(((ServerPlayer) player).connection.connection);
			}
			CrowdComputingChannel.INSTANCE.send(PacketDistributor.NMLIST.with(() -> connection_list),
					new CrowdComputingChannel.WorksiteMessagePacket(message));
		}

		if (!entity.tryGetWorker()) {
			return;
		}

		if (!entity.worker.isValid(entity)) {
			entity.worker = null;
			return;
		}

		if (entity.current_recipe != null) {
			entity.process_elapsed += 1;
			if (entity.process_elapsed >= entity.process_duration) {
				entity.output_blockage = entity.current_recipe.outputs.roll_output(level.random);
				entity.current_recipe = null;
				entity.should_recheck_recipe = true;
			} else {
				return;
			}
		}

		if (!entity.should_recheck_recipe) {
			return;
		}

		if (!entity.tryClearBlockage()) {
			entity.should_recheck_recipe = false;
			return;
		}

		entity.process_elapsed = 0;

		if (entity.locked_recipe != null) {
			if (entity.locked_recipe.matches(entity, level)) {
				entity.consumeAllInputs(entity.locked_recipe.ingredients);
				entity.damageAllTools(entity.locked_recipe.tools);
				entity.current_recipe = entity.locked_recipe;
				entity.last_recipe = entity.locked_recipe;
				entity.process_duration = 0;
				for (WorksiteRecipe.Stage stage : entity.locked_recipe.stages) {
					entity.process_duration += stage.duration();
				}
				entity.setChanged();
			} else {
				entity.should_recheck_recipe = false;
			}

			// Whether it works or not, we stop here, so we don't accept any other recipe.
			return;
		}

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
			entity.last_recipe = best_match;
			entity.process_duration = 0;
			for (WorksiteRecipe.Stage stage : best_match.stages) {
				entity.process_duration += stage.duration();
			}
			entity.setChanged();
			return;
		}

		entity.should_recheck_recipe = false;
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
		recalcCapacities();
		ContainerHelper.loadAllItems(tag.getCompound("input_items"), input_items);
		ContainerHelper.loadAllItems(tag.getCompound("tool_items"), tool_items);
		ContainerHelper.loadAllItems(tag.getCompound("output_items"), output_items);

		if (tag.contains("recipe_info")) {
			CompoundTag recipe_info = tag.getCompound("recipe_info");
			process_elapsed = recipe_info.getInt("elapsed");
			process_duration = recipe_info.getInt("duration");
			should_recheck_recipe = true;

			if (recipe_info.contains("recipe_id")) {
				try {
					current_recipe = (WorksiteRecipe) CrowdComputing.SERVER.getRecipeManager()
							.byKey(new ResourceLocation(recipe_info.getString("recipe_id"))).get();
				} catch (Exception e) {
					CrowdComputing.LOGGER.error("Unable to load current recipe: ", e);
				}
			} else {
				current_recipe = null;
			}

			if (recipe_info.contains("locked_recipe")) {
				try {
					locked_recipe = (WorksiteRecipe) CrowdComputing.SERVER.getRecipeManager()
							.byKey(new ResourceLocation(recipe_info.getString("locked_recipe"))).get();
				} catch (Exception e) {
					CrowdComputing.LOGGER.error("Unable to load locked recipe: ", e);
				}
			} else {
				locked_recipe = null;
			}

			if (recipe_info.contains("last_recipe")) {
				try {
					last_recipe = (WorksiteRecipe) CrowdComputing.SERVER.getRecipeManager()
							.byKey(new ResourceLocation(recipe_info.getString("last_recipe"))).get();
				} catch (Exception e) {
					CrowdComputing.LOGGER.error("Unable to load last recipe: ", e);
				}
			} else {
				last_recipe = null;
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
			should_recheck_recipe = true;
			current_recipe = null;
			locked_recipe = null;
			last_recipe = null;
			output_blockage = null;
		}

		if (tag.contains("energy")) {
			energy_storage = tag.getInt("energy");
		}

		if (tag.contains("fluid")) {
			fluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("fluid"));
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

		if (current_recipe != null || locked_recipe != null || last_recipe != null || output_blockage != null) {
			CompoundTag recipe_info = new CompoundTag();
			recipe_info.putInt("elapsed", process_elapsed);
			recipe_info.putInt("duration", process_duration);

			if (current_recipe != null) {
				recipe_info.putString("recipe_id", current_recipe.id.toString());
			}

			if (locked_recipe != null) {
				recipe_info.putString("locked_recipe", locked_recipe.id.toString());
			}

			if (last_recipe != null) {
				recipe_info.putString("last_recipe", last_recipe.id.toString());
			}

			if (output_blockage != null) {
				CompoundTag output_blockage_tag = new CompoundTag();
				NonNullList<ItemStack> blockage_temp = NonNullList.withSize(MAX_STORED_BLOCKAGE, ItemStack.EMPTY);
				for (int i = 0; i < Math.min(MAX_STORED_BLOCKAGE, output_blockage.size()); i++) {
					blockage_temp.set(i, output_blockage.get(i));
				}
				ContainerHelper.saveAllItems(output_blockage_tag, blockage_temp);
				recipe_info.put("blockage", output_blockage_tag);
			}

			tag.put("recipe_info", recipe_info);
		}

		if (energy_storage > 0) {
			tag.putInt("energy", energy_storage);
		}

		if (fluid != FluidStack.EMPTY) {
			CompoundTag fluid_tag = new CompoundTag();
			fluid.writeToNBT(fluid_tag);
			tag.put("fluid", fluid_tag);
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

	public interface Worker extends Nameable {
		public boolean isValid(WorksiteBlockEntity entity);
	}

	public void toggle_recipe_lock() {
		if (locked_recipe != null) {
			locked_recipe = null;
		} else if (last_recipe != null) {
			locked_recipe = last_recipe;
		}
		should_recheck_recipe = true;
		setChanged();
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		if (energy_cap <= energy_storage) {
			return 0;
		}
		int transfer = Math.min(maxReceive, energy_cap - energy_storage);
		if (!simulate) {
			should_recheck_recipe = true;
			energy_storage += transfer;
		}
		return transfer;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		if (energy_storage == 0) {
			return 0;
		}
		int transfer = Math.min(maxExtract, energy_storage);
		if (!simulate) {
			should_recheck_recipe = true;
			energy_storage -= transfer;
		}
		return transfer;
	}

	@Override
	public int getEnergyStored() {
		return energy_storage;
	}

	@Override
	public int getMaxEnergyStored() {
		return energy_cap;
	}

	@Override
	public boolean canExtract() {
		return true;
	}

	@Override
	public boolean canReceive() {
		return true;
	}

	@Override
	public @NotNull FluidStack getFluid() {
		return fluid;
	}

	@Override
	public int getFluidAmount() {
		return fluid == FluidStack.EMPTY ? 0 : fluid.getAmount();
	}

	@Override
	public int getCapacity() {
		return fluid_cap;
	}

	@Override
	public boolean isFluidValid(FluidStack stack) {
		return true;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if (fluid != FluidStack.EMPTY && !fluid.isFluidEqual(resource)) {
			return 0;
		}
		if (getFluidAmount() >= fluid_cap) {
			return 0;
		}
		int transfer = Math.min(resource.getAmount(), fluid_cap - getFluidAmount());
		if (action == FluidAction.EXECUTE) {
			should_recheck_recipe = true;
			if (fluid == null) {
				fluid = resource.copy();
				fluid.setAmount(transfer);
			} else {
				fluid.setAmount(fluid.getAmount() + transfer);
			}
		}
		return transfer;
	}

	@Override
	public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
		if (fluid == FluidStack.EMPTY) {
			return FluidStack.EMPTY;
		}
		int transfer = Math.min(maxDrain, fluid.getAmount());
		if (transfer == 0) {
			return FluidStack.EMPTY;
		}
		FluidStack extracted = fluid.copy();
		extracted.setAmount(transfer);
		if (action == FluidAction.EXECUTE) {
			should_recheck_recipe = true;
			fluid.setAmount(fluid.getAmount() - transfer);
			if (fluid.isEmpty()) {
				fluid = FluidStack.EMPTY;
			}
		}
		return extracted;
	}

	@Override
	public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
		if (fluid == FluidStack.EMPTY) {
			return FluidStack.EMPTY;
		}
		if (!fluid.isFluidEqual(resource)) {
			return FluidStack.EMPTY;
		}
		return drain(resource.getAmount(), action);
	}
}
