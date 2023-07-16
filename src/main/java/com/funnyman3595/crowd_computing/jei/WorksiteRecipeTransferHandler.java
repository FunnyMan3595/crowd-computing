package com.funnyman3595.crowd_computing.jei;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.funnyman3595.crowd_computing.WorksiteBlockEntity;
import com.funnyman3595.crowd_computing.WorksiteBlockMenu;
import com.funnyman3595.crowd_computing.WorksiteRecipe;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public class WorksiteRecipeTransferHandler implements IRecipeTransferHandler<WorksiteBlockMenu, WorksiteRecipe> {
	public final IRecipeTransferHandlerHelper HELPER;

	public WorksiteRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
		HELPER = helper;
	}

	@Override
	public Class<? extends WorksiteBlockMenu> getContainerClass() {
		return WorksiteBlockMenu.class;
	}

	@Override
	public Optional<MenuType<WorksiteBlockMenu>> getMenuType() {
		return Optional.of(WorksiteBlockMenu.TYPE.get());
	}

	@Override
	public RecipeType<WorksiteRecipe> getRecipeType() {
		return JEIPlugin.WORKSITE_RECIPE_TYPE;
	}

	@Override
	public @Nullable IRecipeTransferError transferRecipe(WorksiteBlockMenu container, WorksiteRecipe recipe,
			IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
		int input_offset = container.worksite_data.get(WorksiteBlockEntity.UPGRADE_SLOTS_INDEX);
		int input_size = container.worksite_data.get(WorksiteBlockEntity.INPUT_SLOTS_INDEX);
		int tool_offset = input_offset + input_size;
		int tool_size = container.worksite_data.get(WorksiteBlockEntity.TOOL_SLOTS_INDEX);
		int player_offset = container.getBlockSlots();
		int player_size = 9 * 4;

		IRecipeTransferInfo<WorksiteBlockMenu, WorksiteRecipe> tool_info = HELPER.createBasicRecipeTransferInfo(
				WorksiteBlockMenu.class, WorksiteBlockMenu.TYPE.get(), JEIPlugin.WORKSITE_RECIPE_TYPE, tool_offset,
				tool_size, player_offset, player_size);
		IRecipeTransferError tool_error = HELPER.createUnregisteredRecipeTransferHandler(tool_info)
				.transferRecipe(container, recipe, new ToolSlotsView(recipeSlots), player, false, doTransfer);
		if (tool_error != null) {
			return tool_error;
		}

		IRecipeTransferInfo<WorksiteBlockMenu, WorksiteRecipe> ingredient_info = HELPER.createBasicRecipeTransferInfo(
				WorksiteBlockMenu.class, WorksiteBlockMenu.TYPE.get(), JEIPlugin.WORKSITE_RECIPE_TYPE, input_offset,
				input_size, player_offset, player_size);
		IRecipeTransferError ingredient_error = HELPER.createUnregisteredRecipeTransferHandler(ingredient_info)
				.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
		if (ingredient_error != null) {
			return ingredient_error;
		}
		return null;
	}

	public class ToolSlotsView implements IRecipeSlotsView {
		public final IRecipeSlotsView parent;

		public ToolSlotsView(IRecipeSlotsView recipeSlots) {
			parent = recipeSlots;
		}

		@Override
		public List<IRecipeSlotView> getSlotViews() {
			return parent.getSlotViews();
		}

		@Override
		public List<IRecipeSlotView> getSlotViews(RecipeIngredientRole role) {
			if (role == RecipeIngredientRole.INPUT) {
				return parent.getSlotViews(RecipeIngredientRole.CATALYST);
			}
			return parent.getSlotViews(role);
		}

		@Override
		public Optional<IRecipeSlotView> findSlotByName(String slotName) {
			return parent.findSlotByName(slotName);
		}

	}
}
