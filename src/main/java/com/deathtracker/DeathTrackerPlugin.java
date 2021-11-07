package com.deathtracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Death-Tracker",
	description = "Tracks [cost of] items lost to deaths during session",
	enabledByDefault = false
)
public class DeathTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private DeathTrackerConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Death-Tracker started.");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Death-Tracker stopped.");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "" + config.greeting(), null);
		}
	}

	@Provides
	DeathTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DeathTrackerConfig.class);
	}
}
