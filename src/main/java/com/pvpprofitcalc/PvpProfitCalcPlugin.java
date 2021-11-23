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

package com.pvpprofitcalc;

import com.google.common.collect.ImmutableSet;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import lombok.NonNull;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat.HitsplatType;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.SkullIcon;
import net.runelite.api.SpriteID;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.WorldType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "PvP Statistics",
	description = "Logs K/D, profits, losses, and other PvP related information during the session.",
	enabledByDefault = false
)

@Slf4j
public class PvpProfitCalcPlugin extends Plugin
{

	private static final Duration WAIT = Duration.ofSeconds(10);

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	private PvpProfitCalcPanel panel;
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
	public static boolean isInstanced = false;
	public static int wildyLevel = -1;

	private static Widget gravestoneWidget = null;
	private static WorldPoint deathLocation = null;
	private Actor currentPlayerInteraction = null;
	private Actor currentOpponentInteraction = null;
	private Instant lastHitsplatTime;

	public static Item[] currentInventory = null;
	public static Item[] currentEquipment = null;
	public static Item[] afterDeathInventory = null;
	public static Item[] afterDeathEquipment = null;



	static {
		unskulledIcon = ImageUtil.loadImageResource(PvpProfitCalcPlugin.class, "unskulled.png");
		skulledIcon = ImageUtil.loadImageResource(PvpProfitCalcPlugin.class, "skull.png");
		navIcon = ImageUtil.loadImageResource(PvpProfitCalcPlugin.class, "icon.png");
		SKULLED = new ImageIcon(skulledIcon);
		UNSKULLED = new ImageIcon(unskulledIcon);
	}

	private static final Set<Integer> LAST_MAN_STANDING_REGIONS = ImmutableSet.of(13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432);
	private static final Set<Integer> SOUL_WARS_REGIONS = ImmutableSet.of(8493, 8749, 9005);
	private static final Set<Integer> RESPAWN_REGIONS = ImmutableSet.of(
			6457, // Kourend
			12850, // Lumbridge
			11828, // Falador
			12342, // Edgeville
			11062, // Camelot
			13150, 12894 // Prifddinas
	);

	private static Collection<ItemStack> stack(Collection<ItemStack> items)
	{
		final List<ItemStack> list = new ArrayList<>();

		for (final ItemStack item : items)
		{
			int quantity = 0;
			for (final ItemStack i : list)
			{
				if (i.getId() == item.getId())
				{
					quantity = i.getQuantity();
					list.remove(i);
					break;
				}
			}
			if (quantity > 0)
			{
				list.add(new ItemStack(item.getId(), item.getQuantity() + quantity, item.getLocation()));
			}
			else
			{
				list.add(item);
			}
		}

		return list;
	}

	@Override
	protected void startUp()
	{

		panel = new PvpProfitCalcPanel(this, itemManager);
		panel.skullStatus.setIcon(UNSKULLED);

		spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM_DISABLED, 0, panel::loadPrayerSprite);
		spriteManager.getSpriteAsync(SpriteID.EQUIPMENT_ITEMS_LOST_ON_DEATH, 0, panel::loadHeaderSprite);
		navButton = NavigationButton.builder()
				.tooltip("PvP Profit Calculator")
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
		currentPlayerInteraction = null;
		currentOpponentInteraction = null;
		lastHitsplatTime = null;
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if(event.getActor() != client.getLocalPlayer()){
			return;
		}
		if(client.isInInstancedRegion()){
			isInstanced = true;
		}
		deathLocation = (client.getLocalPlayer().getWorldLocation());
	}

	@Subscribe
	public void onPlayerLootReceived(final PlayerLootReceived playerLootReceived)
	{
		if (isPlayerWithinMapRegion(LAST_MAN_STANDING_REGIONS) || isPlayerWithinMapRegion(SOUL_WARS_REGIONS))
		{
			return;
		}

		final Player player = playerLootReceived.getPlayer();
		addEntry(player.getName(), player.getCombatLevel(), PvpProfitCalcType.KILL, playerLootReceived.getItems());

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
		if(deathLocation == null)
		{
			getInventory();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if((event.getGroupId() == WidgetID.GRAVESTONE_GROUP_ID))
		{
			gravestoneWidget = client.getWidget(event.getGroupId());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (lastHitsplatTime != null && Duration.between(lastHitsplatTime, Instant.now()).compareTo(WAIT) > 0)
		{
			nullInteractions();
		}

		if (currentPlayerInteraction != null && deathLocation != null && !client.getLocalPlayer().getWorldLocation().equals(deathLocation))
		{
			if (!RESPAWN_REGIONS.contains(client.getLocalPlayer().getWorldLocation().getRegionID()))
			{
				log.debug("Died, but did not respawn in a known respawn location: {}",
						client.getLocalPlayer().getWorldLocation().getRegionID());

				deathLocation = null;
				currentPlayerInteraction = null;
				currentOpponentInteraction = null;
				lastHitsplatTime = null;
				return;
			}
			getInventory(true);
			processItemsLost();

			deathLocation = null;
			nullInteractions();
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if(currentPlayerInteraction != null
				&& currentOpponentInteraction != null
				&& inPvpCombat()
				|| !(event.getSource() instanceof Player)
				|| !(event.getTarget() instanceof Player))
		{
			return ;
		}
		if (event.getSource().equals(client.getLocalPlayer()))
		{
			currentPlayerInteraction = event.getTarget();
		}
		else if (event.getTarget().equals(client.getLocalPlayer()))
		{
			currentOpponentInteraction = event.getSource();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		HitsplatType hitType = event.getHitsplat().getHitsplatType();
		if (!(event.getActor() == client.getLocalPlayer())
				|| !(hitType == HitsplatType.DAMAGE_ME
					|| hitType == HitsplatType.BLOCK_ME
					|| hitType == HitsplatType.POISON
					|| hitType == HitsplatType.VENOM))
		{
			return;
		}
		lastHitsplatTime = Instant.now();
	}

	private boolean inPvpCombat()
	{
		return currentPlayerInteraction == currentOpponentInteraction;
	}

	private boolean isPlayerWithinMapRegion(Set<Integer> definedMapRegions)
	{
		final int[] mapRegions = client.getMapRegions();

		for (int region : mapRegions)
		{
			if (definedMapRegions.contains(region))
			{
				return true;
			}
		}

		return false;
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

	public void processItemsLost() {
		boolean pvpDeath = gravestoneWidget == null && !isInstanced;

		final Collection<ItemStack> itemsLost = new ArrayList<>();
		ArrayList<Item> itemsLostList = new ArrayList<>(Arrays.asList(currentEquipment));
		itemsLostList.addAll(new ArrayList<>(Arrays.asList(currentInventory)));
		itemsLostList.removeAll(new ArrayList<>(Arrays.asList(afterDeathEquipment)));
		itemsLostList.removeAll(new ArrayList<>(Arrays.asList(afterDeathInventory)));
		for (Item i : itemsLostList) {
			ItemStack newItem = new ItemStack(i.getId(), i.getQuantity(), LocalPoint.fromWorld(client, deathLocation));
			itemsLost.add(newItem);
		}

		if (pvpDeath) {
			if (currentOpponentInteraction != null)
			{
				addEntry(currentOpponentInteraction.getName(), currentOpponentInteraction.getCombatLevel(), PvpProfitCalcType.DEATH, itemsLost);
			}
			else if (currentPlayerInteraction != null) //maybe not necessary?
			{
				addEntry(currentPlayerInteraction.getName(), currentPlayerInteraction.getCombatLevel(), PvpProfitCalcType.DEATH, itemsLost);
			}
		}
	}

	public void syncSettings()
	{
		syncWildernessLevel();
		highRiskWorld = isProtectItemAllowed();
		protectingItem = isProtectingItem();

		try{
			isSkulled = client.getLocalPlayer().getSkullIcon() == SkullIcon.SKULL;
		}
		catch(NullPointerException e){
			isSkulled = false;
		}

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

	void addEntry(@NonNull String name, int combatLevel, PvpProfitCalcType type, Collection<ItemStack> items)
	{
		nullInteractions();
		final PvpProfitCalcItem[] entries = buildEntries(stack(items));
		SwingUtilities.invokeLater(() -> panel.add(name, type, combatLevel, entries));
	}

	private PvpProfitCalcItem buildDeathTrackerItem(int itemId, int quantity)
	{
		final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
		final int gePrice = itemManager.getItemPrice(itemId); /* Replace with cost of item retrieval */
		return new PvpProfitCalcItem(
				itemId,
				itemComposition.getName(),
				quantity,
				gePrice);
	}

	private PvpProfitCalcItem[] buildEntries(final Collection<ItemStack> itemStacks)
	{
		return itemStacks.stream()
				.map(itemStack -> buildDeathTrackerItem(itemStack.getId(), itemStack.getQuantity()))
				.toArray(PvpProfitCalcItem[]::new);
	}

	private void nullInteractions(){
		currentPlayerInteraction = null;
		currentOpponentInteraction = null;
		lastHitsplatTime = null;
	}

}