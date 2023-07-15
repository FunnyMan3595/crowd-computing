package com.funnyman3595.crowd_computing;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

public class WorksiteUpgradeItem extends Item {
	public final WorksiteUpgrade upgrade;

	public WorksiteUpgradeItem(WorksiteUpgrade upgrade) {
		super(new Item.Properties().stacksTo(1).tab(CreativeModeTab.TAB_MISC));
		this.upgrade = upgrade;
	}
}
