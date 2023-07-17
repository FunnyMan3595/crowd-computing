package com.funnyman3595.crowd_computing;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class WorksiteMessageChannelClient {
	public static void set_worksite_message(Component message) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof WorksiteBlockScreen) {
			((WorksiteBlockScreen) minecraft.screen).set_worksite_message(message);
		}
	}
}
