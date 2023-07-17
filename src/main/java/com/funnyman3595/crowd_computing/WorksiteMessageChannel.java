package com.funnyman3595.crowd_computing;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class WorksiteMessageChannel {
	private static final String PROTOCOL_VERSION = "1";
	public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(CrowdComputing.MODID, "worksite_message"), () -> PROTOCOL_VERSION,
			PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

	public static void init() {
		int id = 0;
		INSTANCE.registerMessage(id++, WorksiteMessagePacket.class, WorksiteMessagePacket::encode,
				WorksiteMessagePacket::decode, WorksiteMessagePacket::handle);
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
						() -> () -> WorksiteMessageChannelClient.set_worksite_message(packet.message));
			});
		}
	}
}
