package com.funnyman3595.crowd_computing.jei;

import java.util.List;

import com.funnyman3595.crowd_computing.WorksiteBlock;
import com.funnyman3595.crowd_computing.WorksiteBlockMenu.DynamicSlot;
import com.funnyman3595.crowd_computing.WorksiteBlockMenu.ToolSlot;
import com.funnyman3595.crowd_computing.WorksiteBlockScreen;
import com.funnyman3595.crowd_computing.WorksiteRecipe;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class WorksiteRecipeCategory implements IRecipeCategory<WorksiteRecipe> {
	public final IGuiHelper gui_helper;

	public WorksiteRecipeCategory(IGuiHelper helper) {
		gui_helper = helper;
	}

	@Override
	public RecipeType<WorksiteRecipe> getRecipeType() {
		return JEIPlugin.WORKSITE_RECIPE_TYPE;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("crowd_computing.worksite");
	}

	@Override
	public IDrawable getBackground() {
		return gui_helper.createDrawable(WorksiteBlockScreen.BG, 0, 0, 16 + 18 * 6, 16 + 18 * 3);
	}

	@Override
	public IDrawable getIcon() {
		return gui_helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
				new ItemStack(WorksiteBlock.items.values().iterator().next().get()));
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, WorksiteRecipe recipe, IFocusGroup focuses) {
		for (int i = 0; i < recipe.ingredients.length; i++) {
			IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, 8 + 18 * i, 8);
			slot.setBackground(gui_helper.createDrawable(WorksiteBlockScreen.SLOTS, DynamicSlot.TEXTURE_X,
					DynamicSlot.TEXTURE_Y, 18, 18), -1, -1);

			ObjectArrayList<ItemStack> stacks = new ObjectArrayList<ItemStack>();
			for (ItemStack orig_stack : recipe.ingredients[i].ingredient().getItems()) {
				ItemStack stack = orig_stack.copy();
				stack.setCount(recipe.ingredients[i].count());
				stacks.add(stack);
			}
			slot.addItemStacks(stacks);
		}

		for (int i = 0; i < recipe.tools.length; i++) {
			IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.CATALYST, 8 + 18 * i, 8 + 18);
			slot.setBackground(gui_helper.createDrawable(WorksiteBlockScreen.SLOTS, ToolSlot.TEXTURE_X,
					ToolSlot.TEXTURE_Y, 18, 18), -1, -1);

			ObjectArrayList<ItemStack> stacks = new ObjectArrayList<ItemStack>();
			for (ItemStack orig_stack : recipe.tools[i].ingredient().getItems()) {
				ItemStack stack = orig_stack.copy();
				stack.setCount(recipe.tools[i].count());
				stacks.add(stack);
			}
			slot.addItemStacks(stacks);
		}

		WorksiteRecipe.OutputForJEI[] outputs = recipe.outputs.forJEI();
		for (int i = 0; i < outputs.length; i++) {
			IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, 8 + 18 * i, 8 + 18 * 2);
			slot.setBackground(gui_helper.createDrawable(WorksiteBlockScreen.SLOTS, DynamicSlot.TEXTURE_X,
					DynamicSlot.TEXTURE_Y, 18, 18), -1, -1);
			slot.addItemStack(outputs[i].stack());
			slot.addTooltipCallback(new ChanceAdder(outputs[i].chance()));
		}
	}

	public class ChanceAdder implements IRecipeSlotTooltipCallback {
		public final double chance;

		public ChanceAdder(double chance) {
			this.chance = chance;
		}

		@Override
		public void onTooltip(IRecipeSlotView recipeSlotView, List<Component> tooltip) {
			tooltip.add(Component.translatable("crowd_computing.worksite_output_chance", chance * 100));
		}
	}
}
