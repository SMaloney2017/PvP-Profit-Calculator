package com.deathtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("deathtracker")
public interface DeathTrackerConfig extends Config
{
	@ConfigItem(
		keyName = "Example",
		name = "Message",
		description = "Show Development Message."
	)
	default String greeting()
	{
		return "Death-Tracker Plugin Development";
	}
}
