/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, TheStonedTurtle <https://github.com/TheStonedTurtle>
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

import com.google.common.annotations.VisibleForTesting;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.vars.AccountType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.loottracker.GameItem;
import com.deathtracker.risk.BrokenOnDeathItem;
import com.deathtracker.risk.DynamicPriceItem;
import com.deathtracker.risk.FixedPriceItem;
import com.deathtracker.risk.Pets;
import com.deathtracker.risk.AlwaysLostItem;
import com.deathtracker.risk.LostIfNotProtected;

@PluginDescriptor(
	name = "Death Tracker",
	description = "Tracks [cost of] items lost to deaths during session",
	enabledByDefault = false
)

@Slf4j
public class DeathTrackerPlugin extends Plugin {

	@AllArgsConstructor
	@Getter
	@VisibleForTesting
	public static class DeathItems {
		private final List<com.deathtracker.risk.ItemStack> keptItems;
		private final List<com.deathtracker.risk.ItemStack> lostItems;
		private final boolean hasAlwaysLost;
	}

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

	private static final int DEEP_WILDY = 20;
	private static final Pattern WILDERNESS_LEVEL_PATTERN = Pattern.compile("^Level: (\\d+).*");

	public static boolean isSkulled = false;
	public static boolean protectingItem = false;
	public static boolean pvpWorld = false;
	public static boolean pvpSafeZone = false;
	public static boolean highRiskWorld = false;
	public static int wildyLevel = -1;

	static {
		unskulledIcon = ImageUtil.loadImageResource(DeathTrackerPlugin.class, "unskulled.png");
		skulledIcon = ImageUtil.loadImageResource(DeathTrackerPlugin.class, "skull.png");
		navIcon = ImageUtil.loadImageResource(DeathTrackerPlugin.class, "icon.png");
		SKULLED = new ImageIcon(skulledIcon);
		UNSKULLED = new ImageIcon(unskulledIcon);
	}

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
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		processRisk(inventory, equipment);
	}

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath)
	{
		/* Process Death Here */
	}

	private void processRisk(ItemContainer inventoryContainer, ItemContainer gearContainer)
	{
		Item inventoryItems[] = inventoryContainer.getItems();
	}

	public void syncSettings()
	{
		final SkullIcon s = client.getLocalPlayer().getSkullIcon();
		isSkulled = s == SkullIcon.SKULL || isUltimateIronman();
		if(!isSkulled) {
			panel.skullStatus.setIcon(UNSKULLED);
		} else {
			panel.skullStatus.setIcon(SKULLED);
		}
		protectingItem = isProtectingItem();
		if(!protectingItem) {
			spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM_DISABLED, 0, panel::loadPrayerSprite);
		} else {
			spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM, 0, panel::loadPrayerSprite);
		}
		syncWildernessLevel();
		highRiskWorld = isProtectItemAllowed();
		pvpSafeZone = isInPvPSafeZone();
		panel.updateActionsToolTip();
	}

	private void syncWildernessLevel()
	{
		if (client.getVar(Varbits.IN_WILDERNESS) != 1)
		{
			pvpWorld = isInPvpWorld();
			if (pvpWorld && !isInPvPSafeZone())
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
		return client.getWorldType().contains(WorldType.HIGH_RISK)
				|| isUltimateIronman();
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

	private boolean isUltimateIronman()
	{
		return client.getAccountType() == AccountType.ULTIMATE_IRONMAN;
	}

	private int getDefaultItemsKept()
	{
		final int count = isSkulled ? 0 : 3;
		return count + (protectingItem ? 1 : 0);
	}

	DeathItems calculateKeptLostItems(final Item[] inv, final Item[] equip)
	{
		final List<Item> items = new ArrayList<>();
		Collections.addAll(items, inv);
		Collections.addAll(items, equip);

		items.sort(Comparator.comparing(this::getDeathPrice).reversed());

		boolean hasClueBox = false;
		boolean hasAlwaysLost = false;
		int keepCount = getDefaultItemsKept();

		final List<com.deathtracker.risk.ItemStack> keptItems = new ArrayList<>();
		final List<com.deathtracker.risk.ItemStack> lostItems = new ArrayList<>();

		for (final Item i : items)
		{
			final int id = i.getId();
			int qty = i.getQuantity();
			if (id == -1)
			{
				continue;
			}

			if (id == ItemID.OLD_SCHOOL_BOND || id == ItemID.OLD_SCHOOL_BOND_UNTRADEABLE)
			{
				keptItems.add(new com.deathtracker.risk.ItemStack(id, qty));
				continue;
			}

			final AlwaysLostItem alwaysLostItem = AlwaysLostItem.getByItemID(id);
			if (alwaysLostItem != null && (!alwaysLostItem.isKeptOutsideOfWilderness() || wildyLevel > 0))
			{
				hasAlwaysLost = true;
				hasClueBox = hasClueBox || id == ItemID.CLUE_BOX;
				lostItems.add(new com.deathtracker.risk.ItemStack(id, qty));
				continue;
			}

			if (keepCount > 0)
			{
				if (i.getQuantity() > keepCount)
				{
					keptItems.add(new com.deathtracker.risk.ItemStack(id, keepCount));
					qty -= keepCount;
					keepCount = 0;
				}
				else
				{
					keptItems.add(new com.deathtracker.risk.ItemStack(id, qty));
					keepCount -= qty;
					continue;
				}
			}

			if (!Pets.isPet(id)
					&& !LostIfNotProtected.isLostIfNotProtected(id)
					&& !isTradeable(itemManager.getItemComposition(id)) && wildyLevel <= DEEP_WILDY
					&& (wildyLevel <= 0 || BrokenOnDeathItem.isBrokenOnDeath(i.getId())))
			{
				keptItems.add(new com.deathtracker.risk.ItemStack(id, qty));
			}
			else
			{
				lostItems.add(new com.deathtracker.risk.ItemStack(id, qty));
			}
		}

		if (hasClueBox)
		{
			boolean alreadyProtectingClue = false;
			for (final com.deathtracker.risk.ItemStack item : keptItems)
			{
				if (isClueBoxable(item.getId()))
				{
					alreadyProtectingClue = true;
					break;
				}
			}

			if (!alreadyProtectingClue)
			{
				int clueId = -1;
				for (final Item i : inv)
				{
					final int id = i.getId();
					if (id != -1 && isClueBoxable(id))
					{
						clueId = id;
					}
				}

				if (clueId != -1)
				{
					for (final com.deathtracker.risk.ItemStack boxableItem : lostItems)
					{
						if (boxableItem.getId() == clueId)
						{
							if (boxableItem.getQty() > 1)
							{
								boxableItem.setQty(boxableItem.getQty() - 1);
								keptItems.add(new com.deathtracker.risk.ItemStack(clueId, 1));
							}
							else
							{
								lostItems.remove(boxableItem);
								keptItems.add(boxableItem);
							}
							break;
						}
					}
				}
			}
		}

		return new DeathItems(keptItems, lostItems, hasAlwaysLost);
	}

	boolean isClueBoxable(final int itemID)
	{
		final String name = itemManager.getItemComposition(itemID).getName();
		return name.contains("Clue scroll (") || name.contains("Reward casket (");
	}

	int getDeathPrice(Item item)
	{

		int itemId = item.getId();

		int canonicalizedItemId = itemManager.canonicalize(itemId);
		int exchangePrice = 0;

		final DynamicPriceItem dynamicPrice = DynamicPriceItem.find(canonicalizedItemId);
		if (dynamicPrice != null)
		{
			final int basePrice = itemManager.getItemPrice(dynamicPrice.getChargedId());
			return dynamicPrice.calculateDeathPrice(basePrice);
		}

		final FixedPriceItem fixedPrice = FixedPriceItem.find(canonicalizedItemId);
		if (fixedPrice != null && fixedPrice.getBaseId() != -1)
		{
			exchangePrice = itemManager.getItemPrice(fixedPrice.getBaseId());
		}
		else
		{
			for (final ItemMapping mappedID : ItemMapping.map(canonicalizedItemId))
			{
				exchangePrice += itemManager.getItemPrice(mappedID.getTradeableItem());
			}
		}

		if (exchangePrice == 0)
		{
			final ItemComposition c1 = itemManager.getItemComposition(canonicalizedItemId);
			exchangePrice = c1.getPrice();
		}

		exchangePrice += fixedPrice == null ? 0 : fixedPrice.getOffset();

		return exchangePrice;
	}

	private static boolean isTradeable(final ItemComposition c)
	{
		if (c.getNote() != -1
				|| c.getLinkedNoteId() != -1
				|| c.isTradeable())
		{
			return true;
		}

		final int id = c.getId();
		switch (id)
		{
			case ItemID.COINS_995:
			case ItemID.PLATINUM_TOKEN:
				return true;
			default:
				return false;
		}
	}

	private DeathTrackerItem buildLootTrackerItem(int itemId, int quantity)
	{
		final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
		final int gePrice = itemManager.getItemPrice(itemId);
		final int haPrice = itemComposition.getHaPrice();

		return new DeathTrackerItem(
				itemId,
				itemComposition.getName(),
				quantity,
				gePrice);
	}

	private DeathTrackerItem[] buildEntries(final Collection<ItemStack> itemStacks)
	{
		return itemStacks.stream()
				.map(itemStack -> buildLootTrackerItem(itemStack.getId(), itemStack.getQuantity()))
				.toArray(DeathTrackerItem[]::new);
	}

	private static Collection<GameItem> toGameItems(Collection<ItemStack> items)
	{
		return items.stream()
				.map(item -> new GameItem(item.getId(), item.getQuantity()))
				.collect(Collectors.toList());
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
}
