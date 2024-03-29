package com.funnyman3595.crowd_computing;

import com.funnyman3595.crowd_computing.CrowdSourceBlockScreen.InvokeCallback;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class NameRegionScreen extends Screen {
	public static final ResourceLocation BG = new ResourceLocation(CrowdComputing.MODID,
			"textures/gui/name_region.png");
	public static final int imageWidth = 150;
	public static final int imageHeight = 60;
	public EditBox name;
	public Button overwrite_button;
	public boolean saved = false;
	public boolean overwrite = false;

	protected NameRegionScreen() {
		super(Component.translatable("crowd_computing.name_region"));
	}

	@Override
	protected void init() {
		super.init();

		this.minecraft.keyboardHandler.setSendRepeatsToGui(true);
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;
		name = new EditBox(font, i + 5, j + 20, 134, 12, Component.translatable("crowd_computing.name_region"));
		name.setCanLoseFocus(false);
		name.setTextColor(-1);
		name.setTextColorUneditable(-1);
		name.setBordered(false);
		name.setMaxLength(100);
		name.setValue("");
		addWidget(name);
		setInitialFocus(name);

		overwrite_button = new Button(i + 4, j + 20 + 15, 142, 20,
				Component.translatable("crowd_computing.overwrite_off"), new InvokeCallback((button) -> {
					overwrite = !overwrite;
					if (overwrite) {
						overwrite_button.setMessage(Component.translatable("crowd_computing.overwrite_on"));
					} else {
						overwrite_button.setMessage(Component.translatable("crowd_computing.overwrite_off"));
					}
				}));
		addRenderableWidget(overwrite_button);
	}

	@Override
	public void render(PoseStack stack, int mouse_x, int mouse_y, float unused) {
		renderBackground(stack);
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, BG);
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;
		this.blit(stack, i, j, 0, 0, imageWidth, imageHeight);
		super.render(stack, mouse_x, mouse_y, unused);
		renderFg(stack, mouse_x, mouse_y, unused);
	}

	@Override
	public boolean keyPressed(int key, int scan_code, int modifiers) {
		if (key == InputConstants.KEY_ESCAPE) {
			this.minecraft.player.closeContainer();
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			saved = true;
			this.minecraft.player.closeContainer();
		}

		if (!this.name.keyPressed(key, scan_code, modifiers) && !this.name.canConsumeInput()) {
			return super.keyPressed(key, scan_code, modifiers);
		} else {
			return true;
		}
	}

	public void renderFg(PoseStack stack, int mouse_x, int mouse_y, float unused) {
		this.name.render(stack, mouse_x, mouse_y, unused);
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;
		font.draw(stack, title, i + 5, j + 5, 4210752);
	}

	@Override
	public void removed() {
		if (saved) {
			CrowdComputingChannel.INSTANCE
					.sendToServer(new CrowdComputingChannel.RegionNamed(name.getValue(), overwrite));
		}
	}
}
