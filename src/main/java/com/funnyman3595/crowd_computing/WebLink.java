package com.funnyman3595.crowd_computing;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.PacketDistributor;

public class WebLink implements ICapabilitySerializable<CompoundTag> {
	private String auth_secret = null;

	public static WebLink get(Player player) {
		return (WebLink) player.getCapability(CrowdComputing.WEB_LINK).orElseThrow(WebLinkNotFoundException::new);
	}

	public void set_auth_secret(String auth_secret) {
		this.auth_secret = auth_secret;
	}

	public boolean has_auth_secret() {
		return auth_secret != null && auth_secret.length() == 50;
	}

	public void fetch(String method, Map<String, String> extra_args, Consumer<JsonObject> callback,
			Consumer<Exception> error_callback) {
		if (auth_secret == null) {
			error_callback
					.accept(new Exception("You have not set an auth secret yet.  Place and open a Crowd Source."));
			return;
		}
		try {
			HashMap<String, String> form_data = new HashMap<String, String>(extra_args);
			form_data.put("auth_secret", auth_secret);
			StringBuilder form_builder = new StringBuilder();
			for (Map.Entry<String, String> field : form_data.entrySet()) {
				if (form_builder.length() > 0) {
					form_builder.append("&");
				}
				form_builder.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8));
				form_builder.append("=");
				form_builder.append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
			}

			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://crowd-computing.funnyman3595.com/minecraft/" + method))
					.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
					.POST(HttpRequest.BodyPublishers.ofString(form_builder.toString())).build();
			client.sendAsync(request, BodyHandlers.ofString()).thenAccept(response -> {
				if (response.statusCode() == 200) {
					try {
						callback.accept(CrowdComputing.GSON.fromJson(response.body(), JsonObject.class));
					} catch (Exception e) {
						error_callback.accept(e);
					}
				} else {
					error_callback.accept(
							new Exception("Got response code " + response.statusCode() + ": " + response.body()));
				}
			});
		} catch (Exception e) {
			error_callback.accept(e);
		}
	}

	public void verify(Consumer<Show> callback, Consumer<Exception> error_callback) {
		fetch("verify", new HashMap<String, String>(), json -> {
			try {
				Show.load(json);
			} catch (Exception e) {
				error_callback.accept(e);
			}
		}, error_callback);
	}

	public record Show(String name, String display_name, String host_username) {
		public static Show load(JsonObject object) {
			return new Show(GsonHelper.getAsString(object, "name"), GsonHelper.getAsString(object, "display_name"),
					GsonHelper.getAsString(object, "host_username"));
		}
	}

	public void get_all(Consumer<PagedMiniConfigs> callback, Consumer<Exception> error_callback) {
		get_all(1, callback, error_callback);
	}

	public void get_all(int page, Consumer<PagedMiniConfigs> callback, Consumer<Exception> error_callback) {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("page", "" + page);
		fetch("get_all", args, json -> {
			try {
				callback.accept(PagedMiniConfigs.load(json));
			} catch (Exception e) {
				error_callback.accept(e);
			}
		}, error_callback);
	}

	public record PagedMiniConfigs(int page, int page_count, MiniConfig[] configs) {
		public static PagedMiniConfigs load(JsonObject object) {
			return new PagedMiniConfigs(GsonHelper.getAsInt(object, "page_count", 1),
					GsonHelper.getAsInt(object, "page", 1),
					MiniConfig.load_multiple(GsonHelper.getAsJsonArray(object, "mini_configs")));
		}
	}

	public record MiniConfig(String viewer, String name, BlockSelector source, BlockSelector target, int limit) {
		public static MiniConfig load(JsonObject object) {
			BlockSelector source = null;
			if (object.has("source")) {
				JsonObject source_json = GsonHelper.getAsJsonObject(object, "source");
				source = new BlockSelector.Region(new BlockPos(GsonHelper.getAsInt(source_json, "start_x"),
						GsonHelper.getAsInt(source_json, "start_y"), GsonHelper.getAsInt(source_json, "start_z")),
						new BlockPos(GsonHelper.getAsInt(source_json, "end_x"),
								GsonHelper.getAsInt(source_json, "end_y"), GsonHelper.getAsInt(source_json, "end_z")));
			}
			BlockSelector target = null;
			if (object.has("target")) {
				JsonObject target_json = GsonHelper.getAsJsonObject(object, "target");
				target = new BlockSelector.Region(new BlockPos(GsonHelper.getAsInt(target_json, "start_x"),
						GsonHelper.getAsInt(target_json, "start_y"), GsonHelper.getAsInt(target_json, "start_z")),
						new BlockPos(GsonHelper.getAsInt(target_json, "end_x"),
								GsonHelper.getAsInt(target_json, "end_y"), GsonHelper.getAsInt(target_json, "end_z")));
			}
			return new MiniConfig(GsonHelper.getAsString(object, "viewer"), GsonHelper.getAsString(object, "name"),
					source, target, GsonHelper.getAsInt(object, "limit"));
		}

		public static MiniConfig[] load_multiple(JsonArray array) {
			MiniConfig[] configs = new MiniConfig[array.size()];
			for (int i = 0; i < configs.length; i++) {
				configs[i] = load(GsonHelper.convertToJsonObject(array.get(i), "mini_configs array item"));
			}
			return configs;
		}

		public String full_name() {
			return viewer + "/" + name;
		}
	}

	public void add_region(BlockPos start, BlockPos end, String name, Consumer<Void> callback,
			Consumer<Exception> error_callback) {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("name", name);
		args.put("start_x", "" + start.getX());
		args.put("start_y", "" + start.getY());
		args.put("start_z", "" + start.getZ());
		args.put("end_x", "" + end.getX());
		args.put("end_y", "" + end.getY());
		args.put("end_z", "" + end.getZ());
		fetch("add_region", args, json -> {
			callback.accept(null);
		}, error_callback);
	}

	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		if (auth_secret != null) {
			tag.putString("auth_secret", auth_secret);
		}
		return tag;
	}

	public void deserializeNBT(CompoundTag tag) {
		if (tag.contains("auth_secret")) {
			auth_secret = tag.getString("auth_secret");
		}
	}

	private final LazyOptional<WebLink> capabilityOptional = LazyOptional.of(() -> this);

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
		return capability == CrowdComputing.WEB_LINK ? capabilityOptional.cast() : LazyOptional.empty();
	}

	public static class WebLinkNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public WebLinkNotFoundException() {
			super("No Crowd Computing web link found on player.");
		}
	}

	public void send_auth_secret_ack(ServerPlayer player) {
		CrowdComputingChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
				new CrowdComputingChannel.AuthSecretAck(has_auth_secret()));
	}
}
