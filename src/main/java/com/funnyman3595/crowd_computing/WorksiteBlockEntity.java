package com.funnyman3595.crowd_computing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeHolder;
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

	public WorksiteBlockEntity(BlockPos pos, BlockState state) {
		super(block_entities.get(((WorksiteBlock) state.getBlock()).name).get(), pos, state);
		this.block = (WorksiteBlock) state.getBlock();
	}

	@Override
	public int getContainerSize() {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return null;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return null;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return null;
	}

	@Override
	public void setItem(int p_18944_, ItemStack p_18945_) {
	}

	@Override
	public boolean stillValid(Player p_18946_) {
		return true;
	}

	@Override
	public void clearContent() {
	}

	@Override
	public void fillStackedContents(StackedContents contents) {
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
		return null;
	}

	@Override
	protected AbstractContainerMenu createMenu(int p_58627_, Inventory p_58628_) {
		return null;
	}

	public List<ItemStack> getDrops() {
		List<ItemStack> drops = new ArrayList<ItemStack>();
		drops.add(new ItemStack(WorksiteBlock.items.get(block.name).get(), 1));
		return drops;
	}

}
