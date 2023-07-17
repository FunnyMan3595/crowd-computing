package com.funnyman3595.crowd_computing.jei;

import com.funnyman3595.crowd_computing.CrowdComputing;
import com.funnyman3595.crowd_computing.WorksiteBlock;
import com.funnyman3595.crowd_computing.WorksiteBlockScreen;
import com.funnyman3595.crowd_computing.WorksiteRecipe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.registries.RegistryObject;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
	public static final RecipeType<WorksiteRecipe> WORKSITE_RECIPE_TYPE = RecipeType.create(CrowdComputing.MODID,
			"worksite", WorksiteRecipe.class);

	@Override
	public ResourceLocation getPluginUid() {
		return new ResourceLocation(CrowdComputing.MODID, "jei_plugin");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new WorksiteRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addRecipeClickArea(WorksiteBlockScreen.class, 8 + 4 * 18 + 6, 17 + 1 * 18, 24, 16,
				WORKSITE_RECIPE_TYPE);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		Minecraft minecraft = Minecraft.getInstance();
		RecipeManager manager = minecraft.level.getRecipeManager();

		registration.addRecipes(WORKSITE_RECIPE_TYPE, manager.getAllRecipesFor(WorksiteRecipe.TYPE.get()));
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		registration.addRecipeTransferHandler(new WorksiteRecipeTransferHandler(registration.getTransferHelper()),
				WORKSITE_RECIPE_TYPE);
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (RegistryObject<Item> item : WorksiteBlock.items.values())
			registration.addRecipeCatalyst(new ItemStack(item.get()), WORKSITE_RECIPE_TYPE);
	}
}
