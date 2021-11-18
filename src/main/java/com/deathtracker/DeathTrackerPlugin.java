
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.ImageIcon;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.account.AccountSession;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.GameItem;
import net.runelite.http.api.loottracker.LootAggregate;
import net.runelite.http.api.loottracker.LootRecord;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.http.api.loottracker.LootTrackerClient;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import okhttp3.OkHttpClient;
import org.apache.commons.text.WordUtils;

@PluginDescriptor(
	name = "Death-Tracker",
	description = "Tracks [cost of] items lost to deaths during session",
	enabledByDefault = false
)

@Slf4j
public class DeathTrackerPlugin extends Plugin
{
	private static final Set<Integer> LAST_MAN_STANDING_REGIONS = ImmutableSet.of(13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432);
	private static final Set<Integer> SOUL_WARS_REGIONS = ImmutableSet.of(8493, 8749, 9005);

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private DeathTrackerPanel panel;
	private NavigationButton navButton;

	@VisibleForTesting
	String eventType;
	@VisibleForTesting
	DeathRecordType deathRecordType;
	private Object metadata;

	final BufferedImage unskulledIcon = ImageUtil.loadImageResource(getClass(), "unskulled.png");
	final BufferedImage skulledIcon = ImageUtil.loadImageResource(getClass(), "skull.png");
	final ImageIcon SKULL = new ImageIcon(skulledIcon);
	final ImageIcon UNSKULLED = new ImageIcon(unskulledIcon);

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
		panel = new DeathTrackerPanel(this, itemManager);

		spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM_DISABLED, 0, panel::loadPraySprite);
		spriteManager.getSpriteAsync(SpriteID.EQUIPMENT_ITEMS_LOST_ON_DEATH, 0, panel::loadHeaderSprite);
		panel.skullStatus.setIcon(UNSKULLED);

		navButton = NavigationButton.builder()
				.tooltip("Death Tracker")
				.icon(skulledIcon)
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
		setSkull();
		setProtectItem();
	}

	private void setProtectItem(){
		if(client.isPrayerActive(Prayer.PROTECT_ITEM)){
			spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM, 0, panel::loadPraySprite);
			panel.protectionEnabled = true;
		}else{
			spriteManager.getSpriteAsync(SpriteID.PRAYER_PROTECT_ITEM_DISABLED, 0, panel::loadPraySprite);
			panel.protectionEnabled = false;
		}
		panel.updateActionsToolTip();
	}

	private void setSkull(){
		final Player local = client.getLocalPlayer();
		SkullIcon skullSprite = local.getSkullIcon();
		if(skullSprite != null){
			panel.skullStatus.setIcon(SKULL);
			panel.skull = true;
		}else{
			panel.skullStatus.setIcon(UNSKULLED);
			panel.skull = false;
		}
		panel.updateActionsToolTip();
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

	private long getTotalPrice(Collection<ItemStack> items)
	{
		long totalPrice = 0;

		for (final ItemStack itemStack : items)
		{
			totalPrice += (long) itemManager.getItemPrice(itemStack.getId()) * itemStack.getQuantity();
		}

		return totalPrice;
	}
}
