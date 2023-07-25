package com.funnyman3595.crowd_computing;

import java.util.function.Consumer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CrowdSourceBlockScreen extends AbstractContainerScreen<CrowdSourceBlockMenu> {
	public static final ResourceLocation BG = new ResourceLocation(CrowdComputing.MODID,
			"textures/gui/container/crowd_source.png");
	public Button open_site;
	public Button paste_secret;

	public CrowdSourceBlockScreen(CrowdSourceBlockMenu menu, Inventory inventory, Component name) {
		super(menu, inventory, name);
	}

	@Override
	public void init() {
		super.init();
		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;
		open_site = new Button(i + 5, j + 20, 166, 20,
				Component.translatable("crowd_computing.open_site"),
				new InvokeCallback((button) -> {
					Util.getPlatform().openUri("https://crowd-computing.funnyman3595.com/");
				}));
		addRenderableWidget(open_site);
		paste_secret = make_paste_secret(false);
		addRenderableWidget(paste_secret);
	}

	public Button make_paste_secret(boolean have_secret) {
		Component paste_label = Component.translatable("crowd_computing.set_secret");
		if (have_secret) {
			paste_label = Component.translatable("crowd_computing.change_secret");
		}

		int i = (width - imageWidth) / 2;
		int j = (height - imageHeight) / 2;
		return new Button(i+5, j+45, 166, 20, paste_label, new InvokeCallback((button) -> {
			String clipboard = TextFieldHelper.getClipboardContents(minecraft);
			if (clipboard.length() == 50) {
				CrowdComputingChannel.INSTANCE.sendToServer(new CrowdComputingChannel.SetAuthSecret(clipboard));
			}
		}));
	}

	public void remake_paste_secret(boolean have_secret) {
		removeWidget(paste_secret);
		paste_secret = make_paste_secret(have_secret);
		addRenderableWidget(paste_secret);
	}

	public class InvokeCallback implements Button.OnPress {
		private Consumer<Button> callback;

		public InvokeCallback(Consumer<Button> callback) {
			this.callback = callback;
		}

		@Override
		public void onPress(Button button) {
			callback.accept(button);
		}
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
