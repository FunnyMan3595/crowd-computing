package com.funnyman3595.crowd_computing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CrowdSourceBlockScreen extends AbstractContainerScreen<CrowdSourceBlockMenu> {
	public static final ResourceLocation BG = new ResourceLocation(CrowdComputing.MODID,
			"textures/gui/container/worksite.png");

	public CrowdSourceBlockScreen(CrowdSourceBlockMenu menu, Inventory inventory, Component name) {
		super(menu, inventory, name);
	}

	@Override
	public void render(PoseStack stack, int mouse_x, int mouse_y, float unused) {
		renderBackground(stack);
		super.render(stack, mouse_x, mouse_y, unused);
	}

	@Override
	protected void renderLabels(PoseStack stack, int unused1, int unused2) {
		font.draw(stack, title, (imageWidth - font.width(title)) / 2.0f, (float) titleLabelY, 4210752);
	}

	@Override
	protected void renderBg(PoseStack stack, float unused1, int mouse_x, int mouse_y) {
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, BG);
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;
		this.blit(stack, i, j, 0, 0, imageWidth, imageHeight);
	}
}
