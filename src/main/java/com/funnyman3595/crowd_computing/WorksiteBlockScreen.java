package com.funnyman3595.crowd_computing;

import com.funnyman3595.crowd_computing.CrowdSourceBlockScreen.InvokeCallback;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public class WorksiteBlockScreen extends AbstractContainerScreen<WorksiteBlockMenu> {
	public static final ResourceLocation BG = new ResourceLocation(CrowdComputing.MODID,
			"textures/gui/container/worksite.png");
	public static final ResourceLocation SLOTS = new ResourceLocation(CrowdComputing.MODID,
			"textures/gui/slot_types.png");
	private Component worksite_message = Component.translatable("crowd_computing.worksite_no_message_yet");
	public Button lock = null;
	public FluidStack fluid = FluidStack.EMPTY;
	public float float_fluid_fill = 0;

	public WorksiteBlockScreen(WorksiteBlockMenu menu, Inventory inventory, Component name) {
		super(menu, inventory, name);
		imageWidth = 176 + 18 * 2;
		imageHeight = 166 + 18;
		inventoryLabelX = 8 + 18;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void init() {
		super.init();
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;

		lock = new Button(i + 8, j + 17 + 18 * 2 - 2, 32, 20, Component.literal(""), new InvokeCallback((button) -> {
			menu.toggle_lock();
		}));
		addRenderableWidget(lock);
	}

	@Override
	public void render(PoseStack stack, int mouse_x, int mouse_y, float unused) {
		renderBackground(stack);
		super.render(stack, mouse_x, mouse_y, unused);
		renderTooltip(stack, mouse_x, mouse_y);

		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, BG);
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;

		if (menu.recipe_locked()) {
			this.blit(stack, i + 8 + 18 - 7, j + 17 + 18 * 2 + 3, 236, 0, 10, 12);
		} else {
			this.blit(stack, i + 8 + 18 - 9, j + 17 + 18 * 2 + 2, 236, 12, 13, 13);
		}
	}

	@Override
	protected void renderLabels(PoseStack stack, int unused1, int unused2) {
		font.draw(stack, title, (imageWidth - font.width(title)) / 2.0f, (float) titleLabelY, 4210752);
		font.draw(stack, worksite_message, (imageWidth - font.width(worksite_message)) / 2.0f,
				(float) inventoryLabelY - 16, 4210752);
		font.draw(stack, playerInventoryTitle, (float) inventoryLabelX, (float) inventoryLabelY, 4210752);
	}

	@Override
	protected void renderBg(PoseStack stack, float unused1, int mouse_x, int mouse_y) {
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, BG);
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;
		this.blit(stack, i, j, 0, 0, imageWidth, imageHeight);
		this.blit(stack, i + 8 + 4 * 18 + 6, j + 17 + 1 * 18, imageWidth, 16, menu.getProgress(), 17);

		if (menu.has_energy()) {
			this.blit(stack, i + 8, j + 15, 213, 34, 16, 36);
			int energy_fill = menu.get_energy_fill(30);
			this.blit(stack, i + 8, j + 15 + 3 + 30 - energy_fill, 213 + 16, 34 + 3 + 30 - energy_fill, 16,
					energy_fill);
		}
		if (menu.has_fluid()) {
			this.blit(stack, i + 8 + 18, j + 15, 213, 70, 16, 36);
			int fluid_fill = menu.get_fluid_fill(32);

			if (fluid != FluidStack.EMPTY) {
				IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
				int color = extensions.getTintColor();
				float alpha = (color >> 24 & 0xFF) / 255f;
				float red = (color >> 16 & 0xFF) / 255f;
				float green = (color >> 8 & 0xFF) / 255f;
				float blue = (color & 0xFF) / 255f;
				RenderSystem.setShaderColor(red, green, blue, alpha);
				TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
						.apply(extensions.getStillTexture());
				RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
				int offset = 0;
				Tesselator tessellator = Tesselator.getInstance();
				BufferBuilder buffer = tessellator.getBuilder();
				buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
				if (fluid_fill > 16) {
					buffer.vertex(stack.last().pose(), i + 8 + 18 + 1, j + 15 + 2 + 16, 100)
							.uv(sprite.getU0(), sprite.getV1()).endVertex();
					buffer.vertex(stack.last().pose(), i + 8 + 18 + 1, j + 15 + 2 + 16 + 16, 100)
							.uv(sprite.getU0(), sprite.getV0()).endVertex();
					buffer.vertex(stack.last().pose(), i + 8 + 18 + 13, j + 15 + 2 + 16 + 16, 100)
							.uv(sprite.getU1(), sprite.getV0()).endVertex();
					buffer.vertex(stack.last().pose(), i + 8 + 18 + 13, j + 15 + 2 + 16, 100)
							.uv(sprite.getU1(), sprite.getV1()).endVertex();
					offset = 16;
					fluid_fill -= 16;
				}
				buffer.vertex(stack.last().pose(), i + 8 + 18 + 13, j + 15 + 2 + 16 + 16 - offset, 100)
						.uv(sprite.getU1(), sprite.getV0()).endVertex();
				buffer.vertex(stack.last().pose(), i + 8 + 18 + 13, j + 15 + 2 + 16 + 16 - fluid_fill - offset, 100)
						.uv(sprite.getU1(), sprite.getV0() + fluid_fill * (sprite.getV1() - sprite.getV0()) / 16)
						.endVertex();
				buffer.vertex(stack.last().pose(), i + 8 + 18, j + 15 + 2 + 16 + 16 - fluid_fill - offset, 100)
						.uv(sprite.getU0(), sprite.getV0() + fluid_fill * (sprite.getV1() - sprite.getV0()) / 16)
						.endVertex();
				buffer.vertex(stack.last().pose(), i + 8 + 18, j + 15 + 2 + 16 + 16 - offset, 100)
						.uv(sprite.getU0(), sprite.getV0()).endVertex();
				tessellator.end();
			}

			RenderSystem.setShader(GameRenderer::getPositionTexShader);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			RenderSystem.setShaderTexture(0, BG);
			this.blit(stack, i + 8 + 18, j + 15 + 2 + 32 - fluid_fill, 213 + 16, 70 + 2 + 32 - fluid_fill, 16,
					fluid_fill);
		}

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

	public void set_worksite_message(Component message) {
		worksite_message = message;
	}
}
