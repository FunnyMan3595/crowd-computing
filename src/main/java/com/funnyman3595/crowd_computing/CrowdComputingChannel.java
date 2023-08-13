package com.funnyman3595.crowd_computing;

import java.util.HashMap;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class CrowdComputingChannel {
	private static final String PROTOCOL_VERSION = "1";
	public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(CrowdComputing.MODID, "main_channel"), () -> PROTOCOL_VERSION,
			PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

	public static void init() {
		int id = 0;
		INSTANCE.registerMessage(id++, WorksiteMessagePacket.class, WorksiteMessagePacket::encode,
				WorksiteMessagePacket::decode, WorksiteMessagePacket::handle);
		INSTANCE.registerMessage(id++, OpenNameRegionScreen.class, OpenNameRegionScreen::encode,
				OpenNameRegionScreen::decode, OpenNameRegionScreen::handle);
		INSTANCE.registerMessage(id++, RegionNamed.class, RegionNamed::encode, RegionNamed::decode,
				RegionNamed::handle);
		INSTANCE.registerMessage(id++, SetAuthSecret.class, SetAuthSecret::encode, SetAuthSecret::decode,
				SetAuthSecret::handle);
		INSTANCE.registerMessage(id++, AuthSecretAck.class, AuthSecretAck::encode, AuthSecretAck::decode,
				AuthSecretAck::handle);
		INSTANCE.registerMessage(id++, ToggleRecipeLock.class, ToggleRecipeLock::encode, ToggleRecipeLock::decode,
				ToggleRecipeLock::handle);
		INSTANCE.registerMessage(id++, SyncOneRegion.class, SyncOneRegion::encode, SyncOneRegion::decode,
				SyncOneRegion::handle);
		INSTANCE.registerMessage(id++, DeleteOneRegion.class, DeleteOneRegion::encode, DeleteOneRegion::decode,
				DeleteOneRegion::handle);
		INSTANCE.registerMessage(id++, SyncAllRegions.class, SyncAllRegions::encode, SyncAllRegions::decode,
				SyncAllRegions::handle);
	}

	public static class WorksiteMessagePacket {
		public final Component message;

		public WorksiteMessagePacket(Component msg) {
			this.message = msg;
		}

		public static void encode(WorksiteMessagePacket packet, FriendlyByteBuf buf) {
			buf.writeComponent(packet.message);
		}

		public static WorksiteMessagePacket decode(FriendlyByteBuf buf) {
			return new WorksiteMessagePacket(buf.readComponent());
		}

		public static void handle(WorksiteMessagePacket packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
						() -> () -> CrowdComputingChannelClient.set_worksite_message(packet.message));
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public static class OpenNameRegionScreen {
		public OpenNameRegionScreen() {
		}

		public static void encode(OpenNameRegionScreen packet, FriendlyByteBuf buf) {
		}

		public static OpenNameRegionScreen decode(FriendlyByteBuf buf) {
			return new OpenNameRegionScreen();
		}

		public static void handle(OpenNameRegionScreen packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
						() -> () -> CrowdComputingChannelClient.open_name_region_screen());
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public static class RegionNamed {
		public final String name;
		public final boolean overwrite;

		public RegionNamed(String msg, boolean overwrite) {
			this.name = msg;
			this.overwrite = overwrite;
		}

		public static void encode(RegionNamed packet, FriendlyByteBuf buf) {
			buf.writeUtf(packet.name);
			buf.writeBoolean(packet.overwrite);
		}

		public static RegionNamed decode(FriendlyByteBuf buf) {
			return new RegionNamed(buf.readUtf(), buf.readBoolean());
		}

		public static void handle(RegionNamed packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				Player player = ctx.get().getSender();
				ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
				if (!stack.isEmpty() && stack.getItem() instanceof WandItem) {
					WandItem wand = (WandItem) stack.getItem();
					wand.finishRegion(player, packet.name, packet.overwrite);
					return;
				}
				stack = player.getItemInHand(InteractionHand.OFF_HAND);
				if (!stack.isEmpty() && stack.getItem() instanceof WandItem) {
					WandItem wand = (WandItem) stack.getItem();
					wand.finishRegion(player, packet.name, packet.overwrite);
					return;
				}

				player.sendSystemMessage(Component.translatable("crowd_computing.wand_missing"));
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public static class SetAuthSecret {
		public final String auth_secret;

		public SetAuthSecret(String auth_secret) {
			this.auth_secret = auth_secret;
		}

		public static void encode(SetAuthSecret packet, FriendlyByteBuf buf) {
			buf.writeUtf(packet.auth_secret);
		}

		public static SetAuthSecret decode(FriendlyByteBuf buf) {
			return new SetAuthSecret(buf.readUtf());
		}

		public static void handle(SetAuthSecret packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				WebLink link = WebLink.get(ctx.get().getSender());
				link.set_auth_secret(packet.auth_secret);
				link.send_auth_secret_ack(ctx.get().getSender());
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public static class AuthSecretAck {
		public final boolean valid_secret;

		public AuthSecretAck(boolean valid_secret) {
			this.valid_secret = valid_secret;
		}

		public static void encode(AuthSecretAck packet, FriendlyByteBuf buf) {
			buf.writeBoolean(packet.valid_secret);
		}

		public static AuthSecretAck decode(FriendlyByteBuf buf) {
			return new AuthSecretAck(buf.readBoolean());
		}

		public static void handle(AuthSecretAck packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
						() -> () -> CrowdComputingChannelClient.on_auth_secret_ack(packet.valid_secret));
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public static class ToggleRecipeLock {
		public final BlockPos pos;

		public ToggleRecipeLock(BlockPos pos) {
			this.pos = pos;
		}

		public static void encode(ToggleRecipeLock packet, FriendlyByteBuf buf) {
			buf.writeBlockPos(packet.pos);
		}

		public static ToggleRecipeLock decode(FriendlyByteBuf buf) {
			return new ToggleRecipeLock(buf.readBlockPos());
		}

		public static void handle(ToggleRecipeLock packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				Player player = ctx.get().getSender();
				if (packet.pos.distToCenterSqr(player.getPosition(0)) > 64.0) {
					return;
				}
				if (!player.level.isLoaded(packet.pos)) {
					return;
				}

				BlockEntity entity = player.level.getBlockEntity(packet.pos);
				if (entity == null || !(entity instanceof WorksiteBlockEntity)) {
					return;
				}

				((WorksiteBlockEntity) entity).toggle_recipe_lock();
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public static class SyncOneRegion {
		public final BlockSelector.Region region;

		public SyncOneRegion(BlockSelector.Region region) {
			this.region = region;
		}

		public static void encode(SyncOneRegion packet, FriendlyByteBuf buf) {
			buf.writeUtf(packet.region.dimension);
			buf.writeInt(packet.region.id);
			buf.writeUtf(packet.region.name);
			buf.writeBlockPos(packet.region.start);
			buf.writeBlockPos(packet.region.end);
			buf.writeInt(packet.region.color);
		}

		public static SyncOneRegion decode(FriendlyByteBuf buf) {
			String dimension = buf.readUtf();
			int id = buf.readInt();
			String name = buf.readUtf();
			BlockPos start = buf.readBlockPos();
			BlockPos end = buf.readBlockPos();
			int color = buf.readInt();
			return new SyncOneRegion(new BlockSelector.Region(id, dimension, start, end, name, color));
		}

		public static void handle(SyncOneRegion packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
						() -> () -> CrowdComputingChannelClient.receive_one_region(packet.region));
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public static class DeleteOneRegion {
		public final BlockSelector.Region region;

		public DeleteOneRegion(BlockSelector.Region region) {
			this.region = region;
		}

		public static void encode(DeleteOneRegion packet, FriendlyByteBuf buf) {
			buf.writeUtf(packet.region.dimension);
			buf.writeInt(packet.region.id);
			buf.writeUtf(packet.region.name);
			buf.writeBlockPos(packet.region.start);
			buf.writeBlockPos(packet.region.end);
			buf.writeInt(packet.region.color);
		}

		public static DeleteOneRegion decode(FriendlyByteBuf buf) {
			String dimension = buf.readUtf();
			int id = buf.readInt();
			String name = buf.readUtf();
			BlockPos start = buf.readBlockPos();
			BlockPos end = buf.readBlockPos();
			int color = buf.readInt();
			return new DeleteOneRegion(new BlockSelector.Region(id, dimension, start, end, name, color));
		}

		public static void handle(DeleteOneRegion packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
						() -> () -> CrowdComputingChannelClient.delete_one_region(packet.region));
			});
			ctx.get().setPacketHandled(true);
		}
	}

	public static class SyncAllRegions {
		public final HashMap<String, HashMap<Integer, BlockSelector.Region>> regions;

		public SyncAllRegions(HashMap<String, HashMap<Integer, BlockSelector.Region>> regions) {
			this.regions = regions;
		}

		public static void encode(SyncAllRegions packet, FriendlyByteBuf buf) {
			buf.writeInt(packet.regions.size());
			for (String dimension : packet.regions.keySet()) {
				buf.writeUtf(dimension);

				HashMap<Integer, BlockSelector.Region> dimension_regions = packet.regions.get(dimension);
				buf.writeInt(dimension_regions.size());
				for (Integer id : dimension_regions.keySet()) {
					buf.writeInt(id);
					BlockSelector.Region region = dimension_regions.get(id);
					buf.writeUtf(region.name);
					buf.writeBlockPos(region.start);
					buf.writeBlockPos(region.end);
					buf.writeInt(region.color);
				}
			}
		}

		public static SyncAllRegions decode(FriendlyByteBuf buf) {
			HashMap<String, HashMap<Integer, BlockSelector.Region>> regions = new HashMap<String, HashMap<Integer, BlockSelector.Region>>();

			int dimensions_count = buf.readInt();
			for (int i = 0; i < dimensions_count; i++) {
				String dimension = buf.readUtf();
				HashMap<Integer, BlockSelector.Region> dimension_regions = new HashMap<Integer, BlockSelector.Region>();

				int dimension_regions_count = buf.readInt();
				for (int j = 0; j < dimension_regions_count; j++) {
					int id = buf.readInt();
					String name = buf.readUtf();
					BlockPos start = buf.readBlockPos();
					BlockPos end = buf.readBlockPos();
					int color = buf.readInt();
					dimension_regions.put(id, new BlockSelector.Region(id, dimension, start, end, name, color));
				}
				regions.put(dimension, dimension_regions);
			}
			return new SyncAllRegions(regions);
		}

		public static void handle(SyncAllRegions packet, Supplier<NetworkEvent.Context> ctx) {
			ctx.get().enqueueWork(() -> {
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
						() -> () -> CrowdComputingChannelClient.receive_all_regions(packet.regions));
			});
			ctx.get().setPacketHandled(true);
		}
	}
}
