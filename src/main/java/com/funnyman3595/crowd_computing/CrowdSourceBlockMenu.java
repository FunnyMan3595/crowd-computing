package com.funnyman3595.crowd_computing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CrowdSourceBlockMenu extends AbstractContainerMenu {
	public static final RegistryObject<MenuType<CrowdSourceBlockMenu>> TYPE = RegistryObject
			.create(new ResourceLocation(CrowdComputing.MODID, "crowd_source"), ForgeRegistries.MENU_TYPES);
	public final CrowdSourceBlockEntity crowd_source;
	public final Player player;
	public final ContainerData crowd_source_data;

	public CrowdSourceBlockMenu(int container_id, Inventory inventory) {
		this(container_id, inventory.player, null, new SimpleContainerData(0));
	}

	public CrowdSourceBlockMenu(int container_id, Player player, CrowdSourceBlockEntity crowd_source,
			ContainerData crowd_source_data) {
		super(CrowdSourceBlockMenu.TYPE.get(), container_id);
		this.player = player;
		this.crowd_source = crowd_source;
		this.crowd_source_data = crowd_source_data;

		if (crowd_source != null) {
			crowd_source.startOpen(player);
		}
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (crowd_source != null) {
			crowd_source.stopOpen(player);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		if (crowd_source != null) {
			return crowd_source.stillValid(player);
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
		return null;
	}

	@Override
	public void sendAllDataToRemote() {
		super.sendAllDataToRemote();
		WebLink.get(player).send_auth_secret_ack((ServerPlayer) player);
	}
}
