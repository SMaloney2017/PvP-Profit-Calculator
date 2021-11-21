/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2021, Sean Maloney <https://github.com/SMaloney2017>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.deathtracker;

import com.google.common.collect.ImmutableSet;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.SpriteID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.Varbits;
import net.runelite.api.WorldType;
import net.runelite.api.SkullIcon;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Death Tracker",
	description = "Tracks [cost of] items lost to deaths during session",
	enabledByDefault = false
)

@Slf4j
public class DeathTrackerPlugin extends Plugin {

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	private DeathTrackerPanel panel;
	private NavigationButton navButton;

	private static final ImageIcon SKULLED;
	private static final ImageIcon UNSKULLED;
	private static final BufferedImage unskulledIcon;
	private static final BufferedImage skulledIcon;
	private static final BufferedImage navIcon;

	private static final Pattern WILDERNESS_LEVEL_PATTERN = Pattern.compile("^Level: (\\d+).*");

	public static boolean isSkulled = false;
	public static boolean protectingItem = false;
	public static boolean pvpWorld = false;
	public static boolean pvpSafeZone = false;
	public static boolean highRiskWorld = false;
	public static int wildyLevel = -1;

	public static Item[] currentInventory;
	public static Item[] currentEquipment;
	public static Item[] afterDeathInventory;
	public static Item[] afterDeathEquipment;

	private static WorldPoint deathPoint;

	static {
		unskulledIcon = ImageUtil.loadImageResource(DeathTrackerPlugin.class, "unskulled.png");
		skulledIcon = ImageUtil.loadImageResource(DeathTrackerPlugin.class, "skull.png");
		navIcon = ImageUtil.loadImageResource(DeathTrackerPlugin.class, "icon.png");
		SKULLED = new ImageIcon(skulledIcon);
		UNSKULLED = new ImageIcon(unskulledIcon);
	}

	private static final Set<Integer> RESPAWN_REGIONS = ImmutableSet.of(
			6457, // Kourend
			12850, // Lumbridge
			11828, // Falador
			12342, // Edgeville
			11062, // Camelot
			13150, 12894 // Prifddinas
	);

	@Override
	protected void startUp()
	{

		panel = new DeathTrackerPanel(this, itemManager);
		panel.skullStatus.setIcon(UNSKULLED);

		spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM_DISABLED, 0, panel::loadPrayerSprite);
		spriteManager.getSpriteAsync(SpriteID.EQUIPMENT_ITEMS_LOST_ON_DEATH, 0, panel::loadHeaderSprite);
		navButton = NavigationButton.builder()
				.tooltip("Death Tracker")
				.icon(navIcon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		syncSettings();
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			syncSettings();
			getInventory();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if(deathPoint == null)
		{
			getInventory();
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath)
	{
		deathPoint = client.getLocalPlayer().getWorldLocation();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (deathPoint != null && !client.getLocalPlayer().getWorldLocation().equals(deathPoint))
		{
			if (!RESPAWN_REGIONS.contains(client.getLocalPlayer().getWorldLocation().getRegionID()))
			{
				log.debug("Died, but did not respawn in a known respawn location: {}",
						client.getLocalPlayer().getWorldLocation().getRegionID());

				deathPoint = null;
				return;
			}
			getInventory(true);
			processItemsLost();
			deathPoint = null;
		}
	}

	public void getInventory()
	{
		final ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		currentInventory = inventory == null ? new Item[0] : inventory.getItems();
		final ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		currentEquipment = equipment == null ? new Item[0] : equipment.getItems();
	}

	public void getInventory(boolean hasDied)
	{
		if(hasDied){
			final ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
			afterDeathInventory = inventory == null ? new Item[0] : inventory.getItems();
			final ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
			afterDeathEquipment = equipment == null ? new Item[0] : equipment.getItems();
		}
	}

	public void processItemsLost()
	{
		/*
			1) Differentiate PvP or PvM death
			2) Find difference between afterDeath and current
			3) Calculate cost of death
			4) Send results to methods which update overallInfo and create a box containing items lost at monster
		*/
	}

	public void syncSettings()
	{
		syncWildernessLevel();
		highRiskWorld = isProtectItemAllowed();
		protectingItem = isProtectingItem();
		isSkulled = client.getLocalPlayer().getSkullIcon() == SkullIcon.SKULL;

		if(isSkulled || (wildyLevel > 1 && highRiskWorld) || (highRiskWorld && pvpWorld)) {
			panel.skullStatus.setIcon(SKULLED);
		} else {
			panel.skullStatus.setIcon(UNSKULLED);
		}

		if(!protectingItem) {
			spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM_DISABLED, 0, panel::loadPrayerSprite);
		} else {
			spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM, 0, panel::loadPrayerSprite);
		}

		panel.updateActionsToolTip();
	}

	private void syncWildernessLevel()
	{
		if (client.getVar(Varbits.IN_WILDERNESS) != 1)
		{
			pvpWorld = isInPvpWorld();
			pvpSafeZone = isInPvPSafeZone();
			if (pvpWorld && !pvpSafeZone)
			{
				wildyLevel = 1;
				return;
			}
			wildyLevel = -1;
			return;
		}

		final Widget wildernessLevelWidget = client.getWidget(WidgetInfo.PVP_WILDERNESS_LEVEL);
		if (wildernessLevelWidget == null)
		{
			wildyLevel = -1;
			return;
		}

		final String wildernessLevelText = wildernessLevelWidget.getText();
		final Matcher m = WILDERNESS_LEVEL_PATTERN.matcher(wildernessLevelText);
		if (!m.matches())
		{
			wildyLevel = -1;
			return;
		}

		wildyLevel = Integer.parseInt(m.group(1));
	}

	public boolean isInPvpWorld()
	{
		final EnumSet<WorldType> world = client.getWorldType();
		return world.contains(WorldType.PVP);
	}

	private boolean isProtectItemAllowed()
	{
		return client.getWorldType().contains(WorldType.HIGH_RISK);
	}

	private boolean isProtectingItem()
	{
		return client.getVar(Varbits.PRAYER_PROTECT_ITEM) == 1;
	}

	private boolean isInPvPSafeZone()
	{
		final Widget w = client.getWidget(WidgetInfo.PVP_WORLD_SAFE_ZONE);
		return w != null && !w.isHidden();
	}

}
