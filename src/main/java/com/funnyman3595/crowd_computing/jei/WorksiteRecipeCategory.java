package com.funnyman3595.crowd_computing.jei;

import java.util.List;

import com.funnyman3595.crowd_computing.CrowdComputing;
import com.funnyman3595.crowd_computing.WorksiteBlock;
import com.funnyman3595.crowd_computing.WorksiteBlockMenu;
import com.funnyman3595.crowd_computing.WorksiteBlockMenu.DynamicSlot;
import com.funnyman3595.crowd_computing.WorksiteBlockMenu.ToolSlot;
import com.funnyman3595.crowd_computing.WorksiteBlockScreen;
import com.funnyman3595.crowd_computing.WorksiteRecipe;
import com.funnyman3595.crowd_computing.WorksiteRecipe.BlockRequirement;
import com.funnyman3595.crowd_computing.WorksiteRecipe.CountableIngredient;

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
		return gui_helper.createDrawable(WorksiteBlockScreen.BG, 8 + 18, 17, 18 * 9, 18 * 3);
	}

	@Override
	public IDrawable getIcon() {
		return gui_helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
				new ItemStack(WorksiteBlock.items.values().iterator().next().get()));
	}

	public ObjectArrayList<ItemStack> countable_ingredient_to_stacks(CountableIngredient ingredient) {
		ObjectArrayList<ItemStack> stacks = new ObjectArrayList<ItemStack>();
		for (ItemStack orig_stack : ingredient.ingredient().getItems()) {
			ItemStack stack = orig_stack.copy();
			stack.setCount(ingredient.count());
			stacks.add(stack);
		}
		return stacks;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, WorksiteRecipe recipe, IFocusGroup focuses) {
		WorksiteBlockMenu.makeVariableSlots(0, 2, 3, 1 * 18, 0, recipe.ingredients.length, (info) -> {
			IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, info.x(), info.y());
			slot.setBackground(gui_helper.createDrawable(WorksiteBlockScreen.SLOTS, DynamicSlot.TEXTURE_X,
					DynamicSlot.TEXTURE_Y, 18, 18), -1, -1);
			slot.addItemStacks(countable_ingredient_to_stacks(recipe.ingredients[info.slot()]));
		});

		if (recipe.tools.length >= 1) {
			IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, 3 * 18 + 9, 0);
			slot.setBackground(gui_helper.createDrawable(WorksiteBlockScreen.SLOTS, ToolSlot.TEXTURE_X,
					ToolSlot.TEXTURE_Y, 18, 18), -1, -1);
			slot.addItemStacks(countable_ingredient_to_stacks(recipe.tools[0]));
		}
		if (recipe.tools.length >= 2) {
			IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, 3 * 18 + 9, 0);
			slot.setBackground(gui_helper.createDrawable(WorksiteBlockScreen.SLOTS, ToolSlot.TEXTURE_X,
					ToolSlot.TEXTURE_Y, 18, 18), -1, -1);
			slot.addItemStacks(countable_ingredient_to_stacks(recipe.tools[0]));
		}

		WorksiteRecipe.OutputForJEI[] outputs = recipe.outputs.forJEI();
		WorksiteBlockMenu.makeVariableSlots(0, 3, 3, 5 * 18, 0, outputs.length, (info) -> {
			IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, info.x(), info.y());
			slot.setBackground(gui_helper.createDrawable(WorksiteBlockScreen.SLOTS, DynamicSlot.TEXTURE_X,
					DynamicSlot.TEXTURE_Y, 18, 18), -1, -1);
			slot.addItemStack(outputs[info.slot()].stack());
			slot.addTooltipCallback(new ChanceAdder(outputs[info.slot()].chance()));
		});

		ObjectArrayList<Component> conditions = new ObjectArrayList<Component>();
		if (recipe.check_waterlogged_state) {
			if (recipe.required_waterlogged_state) {
				conditions.add(Component.translatable("crowd_computing.worksite_conditions.waterlogged"));
			} else {
				conditions.add(Component.translatable("crowd_computing.worksite_conditions.not_waterlogged"));
			}
		}
		for (BlockRequirement req : recipe.block_requirements) {
			conditions.add(Component.translatable("crowd_computing.worksite_conditions.block_requirement",
					Component.translatable("crowd_computing.direction." + req.direction().getName()),
					req.block().getName()));
		}
		if (conditions.size() > 0) {
			IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 3 * 18 + 6 + 4,
					1 * 18);
			slot.addItemStack(new ItemStack(CrowdComputing.NOTICE_ITEM.get(), 1)
					.setHoverName(Component.translatable("crowd_computing.extra_requirements")));
			slot.addTooltipCallback(new ConditionsAdder(conditions));
		}
	}

	public class ConditionsAdder implements IRecipeSlotTooltipCallback {
		public final ObjectArrayList<Component> conditions;

		public ConditionsAdder(ObjectArrayList<Component> conditions) {
			this.conditions = conditions;
		}

		@Override
		public void onTooltip(IRecipeSlotView recipeSlotView, List<Component> tooltip) {
			for (Component condition : conditions) {
				tooltip.add(condition);
			}
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
