package com.funnyman3595.crowd_computing;

import java.awt.Color;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;

import com.funnyman3595.crowd_computing.BlockSelector.Region;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.network.PacketDistributor;

public class RegionRenderer {
	public static HashMap<String, HashMap<Integer, Region>> client_region_cache = new HashMap<String, HashMap<Integer, BlockSelector.Region>>();
	public static BlockSelector.Region break_target = null;
	public static LocalTime last_break_event = null;
	public static int break_progress = 0;

	public static void renderRegions(RenderLevelStageEvent event) {
		PoseStack stack = event.getPoseStack();
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		String dimension = level.dimension().location().toString();

		if (!client_region_cache.containsKey(dimension)) {
			return;
		}
		HashMap<Integer, BlockSelector.Region> regions = client_region_cache.get(dimension);
		if (regions.isEmpty()) {
			return;
		}

		stack.pushPose();
		Vec3 camera = event.getCamera().getPosition();
		stack.translate(-camera.x, -camera.y, -camera.z);

		for (Region region : regions.values()) {
			stack.pushPose();
			stack.translate(region.getMinX(), region.getMinY(), region.getMinZ());
			PoseStack text_stack = new PoseStack();
			text_stack.mulPoseMatrix(stack.last().pose());

			float alpha = 0.5f;
			if (region == break_target && !LocalTime.now().isAfter(last_break_event.plusSeconds(1))) {
				alpha = Math.max(0f, (20 - break_progress) * alpha / 20);
			}

			// Offset outline slightly to appear outside blocks.
			stack.translate(-0.001, -0.001, -0.001);
			stack.scale(region.x_size() + 0.002f, region.y_size() + 0.002f, region.z_size() + 0.002f);

			BlockState state = Blocks.WHITE_WOOL.defaultBlockState();
			Color color = new Color(region.color);
			BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
			VertexConsumer buffer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.translucent());

			int text_width = minecraft.font.width(region.name);
			int text_height = minecraft.font.lineHeight;
			for (Direction d : Direction.values()) {
				List<BakedQuad> quads = model.getQuads(state, d, level.random, ModelData.EMPTY,
						RenderType.translucent());
				BakedQuad quad = quads.get(0);

				buffer.putBulkData(stack.last(), quad, new float[] { 1, 1, 1, 1 }, color.getRed() / 255f,
						color.getGreen() / 255f, color.getBlue() / 255f, alpha,
						new int[] { 0xF000F0, 0xF000F0, 0xF000F0, 0xF000F0 }, OverlayTexture.NO_OVERLAY, false);

				if (d.getNormal().getY() == 0) {
					for (int y_offset = 0; y_offset <= region.y_size(); y_offset += region.y_size()) {
						text_stack.pushPose();
						if (d.getNormal().getX() == 1) {
							text_stack.translate(region.x_size(), y_offset, region.z_size());
						}
						if (d.getNormal().getX() == -1) {
							text_stack.translate(0, y_offset, 0);
						}
						if (d.getNormal().getZ() == -1) {
							text_stack.translate(region.x_size(), y_offset, 0);
						}
						if (d.getNormal().getZ() == 1) {
							text_stack.translate(0, y_offset, region.z_size());
						}
						text_stack.scale(.01f, .01f, .01f);
						text_stack.mulPose(d.getRotation());
						if (y_offset > 0) {
							text_stack.mulPose(Quaternion.fromXYZDegrees(new Vector3f(90, 0, 0)));
						} else {
							text_stack.mulPose(Quaternion.fromXYZDegrees(new Vector3f(180, 0, 0)));
						}
						for (int rotation = 0; rotation < 6; rotation++) {
							text_stack.pushPose();
							if (rotation % 3 == 1) {
								text_stack.mulPose(Quaternion.fromXYZDegrees(new Vector3f(0, 90, 90)));
							} else if (rotation % 3 == 2) {
								text_stack.mulPose(Quaternion.fromXYZDegrees(new Vector3f(0, 90, 180)));
								text_stack.mulPose(Quaternion.fromXYZDegrees(new Vector3f(90, 0, 0)));
							}
							if (rotation >= 3) {
								text_stack.mulPose(Quaternion.fromXYZDegrees(new Vector3f(0, -90, 0)));
							}

							// Move 2 pixels away from the corner, and slightly outside the outline.
							text_stack.translate(2, 2, -.2f);

							// Don't make the bottom right label upside down.
							if (y_offset == 0 && rotation % 3 == 1) {
								text_stack.translate(text_width, text_height, 0);
								text_stack.mulPose(Quaternion.fromXYZDegrees(new Vector3f(0, 0, 180)));
							}
							minecraft.font.draw(text_stack, region.name, 0, 0, 4210752);
							text_stack.popPose();
						}
						text_stack.popPose();
					}
				}
			}

			// Flip inside-out to render the interior faces.
			stack.translate(1, 1, 1);
			stack.scale(-1, -1, -1);
			for (Direction d : Direction.values()) {
				List<BakedQuad> quads = model.getQuads(state, d, level.random, ModelData.EMPTY,
						RenderType.translucent());
				BakedQuad quad = quads.get(0);

				buffer.putBulkData(stack.last(), quad, new float[] { 1, 1, 1, 1 }, 1f, 1f, 1f, 0.5f,
						new int[] { 0xF000F0, 0xF000F0, 0xF000F0, 0xF000F0 }, OverlayTexture.NO_OVERLAY, false);
			}
			stack.popPose();
		}
		stack.popPose();
	}

	public static void breaking_region(Region region) {
		LocalTime now = LocalTime.now();
		if (region != break_target || now.isAfter(last_break_event.plusSeconds(1))) {
			break_target = region;
			last_break_event = now;
			break_progress = 1;
			return;
		}

		last_break_event = now;
		break_progress += 1;
	}

}
