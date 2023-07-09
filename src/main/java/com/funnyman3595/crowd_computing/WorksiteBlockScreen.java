package com.funnyman3595.crowd_computing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class WorksiteBlockScreen extends AbstractContainerScreen<WorksiteBlockMenu> {
	private static final ResourceLocation BG = new ResourceLocation(CrowdComputing.MODID,
			"textures/gui/container/worksite.png");
	private static final ResourceLocation SLOTS = new ResourceLocation(CrowdComputing.MODID,
			"textures/gui/slot_types.png");

	public WorksiteBlockScreen(WorksiteBlockMenu menu, Inventory inventory, Component name) {
		super(menu, inventory, name);
	}

	@Override
	public void render(PoseStack stack, int mouse_x, int mouse_y, float unused) {
		renderBackground(stack);
		super.render(stack, mouse_x, mouse_y, unused);
		renderTooltip(stack, mouse_x, mouse_y);
	}

	@Override
	protected void renderBg(PoseStack stack, float unused1, int mouse_x, int mouse_y) {
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, BG);
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;
		this.blit(stack, i, j, 0, 0, imageWidth, imageHeight);

		RenderSystem.setShaderTexture(0, SLOTS);
		for (int slot_index = 0; slot_index < menu.getBlockSlots(); slot_index++) {
			Slot slot = menu.getSlot(slot_index);
			if (slot instanceof WorksiteBlockMenu.DynamicSlot) {
				WorksiteBlockMenu.DynamicSlot dynamic_slot = (WorksiteBlockMenu.DynamicSlot) slot;
				this.blit(stack, i + slot.x - 1, j + slot.y - 1, dynamic_slot.getTextureX(), dynamic_slot.getTextureY(),
						18, 18);
			}
		}
	}
}
