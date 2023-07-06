package com.funnyman3595.crowd_computing;

import com.mojang.logging.LogUtils;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CrowdComputing.MODID)
public class CrowdComputing
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "crowd_computing";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public CrowdComputing()
    {
    }

	public static ResourceLocation resourceLocation(String path) {
		return new ResourceLocation(MODID, path);
	}

    private void registerCommands(final RegisterCommandsEvent event)
    {
    }
}
