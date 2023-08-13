package com.funnyman3595.crowd_computing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.PacketDistributor;

public class WebLink implements ICapabilitySerializable<CompoundTag> {
	private String auth_secret = null;
	public HashMap<String, HashMap<Integer, BlockSelector.Region>> regions = new HashMap<String, HashMap<Integer, BlockSelector.Region>>();
	public String last_region_update = "2023-01-01";
	public int tick = 0;

	public BlockSelector.Region break_target = null;
	public LocalTime last_break_event = null;
	public int break_progress = 0;

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

	public void get_all(Player player, Consumer<PagedMiniConfigs> callback, Consumer<Exception> error_callback) {
		get_all(player, 1, callback, error_callback);
	}

	public void get_all(Player player, int page, Consumer<PagedMiniConfigs> callback,
			Consumer<Exception> error_callback) {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("page", "" + page);
		fetch("get_all", args, json -> {
			try {
				callback.accept(PagedMiniConfigs.load(player, this, json));
			} catch (Exception e) {
				error_callback.accept(e);
			}
		}, error_callback);
	}

	public void get_specific(Player player, String[] names, Consumer<PagedMiniConfigs> callback,
			Consumer<Exception> error_callback) {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("names", String.join("|", names));
		fetch("get_specific", args, json -> {
			try {
				callback.accept(PagedMiniConfigs.load(player, this, json));
			} catch (Exception e) {
				error_callback.accept(e);
			}
		}, error_callback);
	}

	public record PagedMiniConfigs(int page, int page_count, MiniConfig[] configs) {
		public static PagedMiniConfigs load(Player player, WebLink link, JsonObject object) {
			return new PagedMiniConfigs(GsonHelper.getAsInt(object, "page_count", 1),
					GsonHelper.getAsInt(object, "page", 1),
					MiniConfig.load_multiple(player, link, GsonHelper.getAsJsonArray(object, "mini_configs")));
		}
	}

	public record MiniConfig(String viewer, String name, String dimension, int source_id, int target_id, int limit) {
		public static MiniConfig load(Player player, WebLink link, JsonObject object) {
			String dimension = null;
			int source_id = -1;
			if (object.has("source")) {
				JsonObject source_json = GsonHelper.getAsJsonObject(object, "source");
				BlockSelector.Region source = BlockSelector.Region.fromJson(source_json);
				source_id = source.id;
				dimension = source.dimension;
				CrowdComputing.onMainThread(() -> {
					if (!link.regions.containsKey(source.dimension)) {
						link.regions.put(source.dimension, new HashMap<Integer, BlockSelector.Region>());
					}
					HashMap<Integer, BlockSelector.Region> dimension_regions = link.regions.get(source.dimension);
					if (!dimension_regions.containsKey(source.id)) {
						dimension_regions.put(source.id, source);
					} else {
						dimension_regions.get(source.id).update(source);
					}
					CrowdComputingChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
							new CrowdComputingChannel.SyncOneRegion(source));
				});
			}
			int target_id = -1;
			if (object.has("target")) {
				JsonObject target_json = GsonHelper.getAsJsonObject(object, "target");
				BlockSelector.Region target = BlockSelector.Region.fromJson(target_json);
				CrowdComputing.onMainThread(() -> {
					if (!link.regions.containsKey(target.dimension)) {
						link.regions.put(target.dimension, new HashMap<Integer, BlockSelector.Region>());
					}
					HashMap<Integer, BlockSelector.Region> dimension_regions = link.regions.get(target.dimension);
					if (!dimension_regions.containsKey(target.id)) {
						dimension_regions.put(target.id, target);
					} else {
						dimension_regions.get(target.id).update(target);
					}
					CrowdComputingChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
							new CrowdComputingChannel.SyncOneRegion(target));
				});

				if (dimension == null) {
					dimension = target.dimension;
				} else if (!dimension.equals(target.dimension)) {
					throw new RuntimeException("Bad config: Source and target dimensions do not match.");
				}
				target_id = target.id;
			}
			return new MiniConfig(GsonHelper.getAsString(object, "viewer"), GsonHelper.getAsString(object, "name"),
					dimension, source_id, target_id, GsonHelper.getAsInt(object, "limit"));
		}

		public static MiniConfig[] load_multiple(Player player, WebLink link, JsonArray array) {
			MiniConfig[] configs = new MiniConfig[array.size()];
			for (int i = 0; i < configs.length; i++) {
				configs[i] = load(player, link,
						GsonHelper.convertToJsonObject(array.get(i), "mini_configs array item"));
			}
			return configs;
		}

		public String full_name() {
			return viewer + "/" + name;
		}
	}

	public void add_region(Player player, BlockPos start, BlockPos end, String name, int color, boolean overwrite,
			Consumer<Void> callback, Consumer<Exception> error_callback) {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("name", name);
		String dimension = player.getLevel().dimension().location().toString();
		args.put("dimension", dimension);
		args.put("start_x", "" + start.getX());
		args.put("start_y", "" + start.getY());
		args.put("start_z", "" + start.getZ());
		args.put("end_x", "" + end.getX());
		args.put("end_y", "" + end.getY());
		args.put("end_z", "" + end.getZ());
		args.put("color", HexFormat.of().toHexDigits(color).substring(2));
		if (overwrite) {
			args.put("overwrite", "true");
		}
		fetch("add_region", args, json -> {
			int id = GsonHelper.getAsInt(json, "id");
			if (!regions.containsKey(dimension)) {
				regions.put(dimension, new HashMap<Integer, BlockSelector.Region>());
			}
			BlockSelector.Region region = new BlockSelector.Region(id, dimension, start, end, name, color);
			regions.get(dimension).put(id, region);
			CrowdComputingChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
					new CrowdComputingChannel.SyncOneRegion(region));

			callback.accept(null);
		}, error_callback);
	}

	public void get_updated_regions(Player player, Consumer<Void> callback, Consumer<Exception> error_callback) {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("since", last_region_update);
		fetch("get_updated_regions", args, json -> {
			last_region_update = GsonHelper.getAsString(json, "now");
			ObjectArrayList<BlockSelector.Region> regions_from_json = new ObjectArrayList<BlockSelector.Region>();

			try {
				for (JsonElement elem : GsonHelper.getAsJsonArray(json, "regions")) {
					regions_from_json.add(
							BlockSelector.Region.fromJson(GsonHelper.convertToJsonObject(elem, "regions array item")));
				}
			} catch (Exception e) {
				error_callback.accept(e);
			}

			CrowdComputing.onMainThread(() -> {
				for (BlockSelector.Region region : regions_from_json) {
					if (!regions.containsKey(region.dimension)) {
						regions.put(region.dimension, new HashMap<Integer, BlockSelector.Region>());
					}
					HashMap<Integer, BlockSelector.Region> dimension_regions = regions.get(region.dimension);
					if (!dimension_regions.containsKey(region.id)) {
						dimension_regions.put(region.id, region);
					} else {
						dimension_regions.get(region.id).update(region);
					}
					CrowdComputingChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
							new CrowdComputingChannel.SyncOneRegion(region));
				}
			});
		}, error_callback);
	}

	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		if (auth_secret != null) {
			tag.putString("auth_secret", auth_secret);
		}

		ListTag levels_tag = new ListTag();
		for (String level_key : regions.keySet()) {
			CompoundTag level_tag = new CompoundTag();
			level_tag.putString("dimension", level_key);

			ListTag regions_tag = new ListTag();
			for (BlockSelector.Region region : regions.get(level_key).values()) {
				regions_tag.add(region.save_to_nbt());
			}
			level_tag.put("regions", regions_tag);

			levels_tag.add(level_tag);
		}
		tag.put("levels", levels_tag);

		return tag;
	}

	public void deserializeNBT(CompoundTag tag) {
		if (tag.contains("auth_secret")) {
			auth_secret = tag.getString("auth_secret");
		}

		regions.clear();
		if (tag.contains("levels")) {
			ListTag levels_tag = tag.getList("levels", Tag.TAG_COMPOUND);
			for (Tag raw_level_tag : levels_tag) {
				CompoundTag level_tag = (CompoundTag) raw_level_tag;
				String level_key = level_tag.getString("dimension");
				regions.put(level_key, new HashMap<Integer, BlockSelector.Region>());
				HashMap<Integer, BlockSelector.Region> level_regions = regions.get(level_key);

				ListTag regions_tag = level_tag.getList("regions", Tag.TAG_COMPOUND);
				for (Tag raw_region_tag : regions_tag) {
					BlockSelector.Region region = (BlockSelector.Region) BlockSelector.Region
							.load_nbt((CompoundTag) raw_region_tag);
					level_regions.put(region.id, region);
				}
			}
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

	public void upload_minimap(BlockPos blockPos, int range, BufferedImage minimap,
			Consumer<Exception> error_callback) {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("x", "" + blockPos.getX());
		args.put("y", "" + blockPos.getY());
		args.put("z", "" + blockPos.getZ());
		args.put("range", "" + range);

		ByteArrayOutputStream image = new ByteArrayOutputStream();
		try {
			javax.imageio.ImageIO.write(minimap, "png", image);
		} catch (Exception e) {
			error_callback.accept(e);
			return;
		}
		args.put("image", Base64.getUrlEncoder().encodeToString(image.toByteArray()));

		fetch("upload_minimap", args, json -> {
		}, error_callback);
	}

	public void delete_minimap(BlockPos blockPos, Consumer<Exception> error_callback) {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("x", "" + blockPos.getX());
		args.put("y", "" + blockPos.getY());
		args.put("z", "" + blockPos.getZ());

		fetch("delete_minimap", args, json -> {
		}, error_callback);
	}

	public void breaking_region(Player player, BlockSelector.Region region) {
		LocalTime now = LocalTime.now();
		if (region != break_target || now.isAfter(last_break_event.plusSeconds(1))) {
			break_target = region;
			last_break_event = now;
			break_progress = 1;
			return;
		}

		last_break_event = now;
		break_progress += 1;

		if (break_progress >= 20) {
			regions.get(region.dimension).remove(region.id);
			CrowdComputingChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
					new CrowdComputingChannel.DeleteOneRegion(region));

			HashMap<String, String> args = new HashMap<String, String>();
			args.put("id", "" + region.id);
			fetch("delete_region", args, (json) -> {
			}, (error) -> {
				player.sendSystemMessage(Component.translatable("crowd_computing.link_failed", error));
			});

			break_target = null;
			last_break_event = null;
			break_progress = 0;
		}
	}
}
