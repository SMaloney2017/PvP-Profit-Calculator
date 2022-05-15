/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.List;
import javax.inject.Inject;
import javax.swing.border.EmptyBorder;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.plaf.basic.BasicButtonUI;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.SwingUtil;

class PvpProfitCalcPanel extends PluginPanel
{
	@Inject
	private SpriteManager spriteManager;

	private static final BufferedImage visibleImg;
	private static final BufferedImage invisibleImg;
	private static final ImageIcon VISIBLE_ICON;
	private static final ImageIcon INVISIBLE_ICON;

	private static final String HTML_INTERACTION_TEMPLATE =
		"<html>" +
			"<body style='color:%s'>" +
			"%s" +
			"<span style='color:white'>%s" +
			"<span style='color:%s'>%s</span>" +
			"</span>" +
			"</body>" +
			"</html>";
	private static final String HTML_LABEL_TEMPLATE =
		"<html>" +
			"<body style='color:%s'>" +
			"%s" +
			"<span style='color:white'>%s</span>" +
			"</body>" +
			"</html>";
	private static final String HTML_WORLD_TEMPLATE =
		"<html>" +
			"<body style='color:%s'>" +
			"%s" +
			"<span style='color:%s'>%s</span>" +
			"</body>" +
			"</html>";
	private static final String HTML_TOTALS_TEMPLATE =
		"<html>" +
			"<body style='color:%s'>" +
			"<p>Total Kills: <span style='color:%s'>%s</span>" +
			"<p>Total Deaths: <span style='color:%s'>%s</span>" +
			"</body>" +
			"</html>";

	/* Display errorPanel when there are no deaths */
	private final PluginErrorPanel errorPanel = new PluginErrorPanel();

	/* Handle death boxes */
	public final JPanel logsContainer = new JPanel();

	/* Session data */
	private final JPanel overallPanel = new JPanel();
	public final JLabel overallKDLabel = new JLabel();
	public final JLabel overallProfitLabel = new JLabel();
	private final JLabel overallIcon = new JLabel();

	private final JLabel actionsWorldRiskLabel = new JLabel();
	private final JLabel actionsWorldTypeLabel = new JLabel();
	private final JLabel interactionOpponentLabel = new JLabel();
	private final JLabel interactionTimerLabel = new JLabel();
	private final JButton collapseBtn = new JButton();
	private final JLabel prayerStatus = new JLabel();
	public final JLabel skullStatus = new JLabel();

	/* Individual record of each death */
	private final List<PvpProfitCalcRecord> sessionRecords = new ArrayList<>();
	private final List<PvpProfitCalcBox> boxes = new ArrayList<>();

	private final ItemManager itemManager;
	private final PvpProfitCalcPlugin plugin;

	private String currentView;
	private PvpProfitCalcType currentType;
	private boolean collapseAll = false;

	static
	{
		visibleImg = ImageUtil.loadImageResource(LootTrackerPlugin.class, "visible_icon.png");
		invisibleImg = ImageUtil.loadImageResource(LootTrackerPlugin.class, "invisible_icon.png");

		VISIBLE_ICON = new ImageIcon(ImageUtil.alphaOffset(visibleImg, -220));
		INVISIBLE_ICON = new ImageIcon(ImageUtil.alphaOffset(invisibleImg, -200));
	}

	PvpProfitCalcPanel(final PvpProfitCalcPlugin plugin, final ItemManager itemManager)
	{
		this.itemManager = itemManager;
		this.plugin = plugin;

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		add(layoutPanel, BorderLayout.NORTH);

		/* Details and actions */
		JPanel actionsContainer = new JPanel();
		actionsContainer.setLayout(new FlowLayout(FlowLayout.LEFT));
		actionsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actionsContainer.setBorder(new EmptyBorder(0, 5, 0, 10));
		actionsContainer.setVisible(true);


		prayerStatus.setIconTextGap(0);
		prayerStatus.setBorder(new EmptyBorder(0, 0, 0, 5));
		prayerStatus.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actionsContainer.add(prayerStatus);

		skullStatus.setIconTextGap(0);
		skullStatus.setBorder(new EmptyBorder(0, 0, 0, 5));
		skullStatus.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actionsContainer.add(skullStatus);

		JPanel actionsInfo = new JPanel();
		actionsInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actionsInfo.setLayout(new GridLayout(2, 1));
		actionsWorldTypeLabel.setFont(FontManager.getRunescapeSmallFont());
		actionsWorldRiskLabel.setFont(FontManager.getRunescapeSmallFont());
		actionsInfo.add(actionsWorldTypeLabel);
		actionsInfo.add(actionsWorldRiskLabel);
		actionsContainer.add(actionsInfo);

		JPanel interactionDetailsContainer = new JPanel();
		interactionDetailsContainer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(5, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));
		interactionDetailsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		interactionDetailsContainer.setLayout(new BorderLayout());
		interactionDetailsContainer.setVisible(true);

		JPanel interactionsInfo = new JPanel();
		interactionsInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		interactionsInfo.setLayout(new GridLayout(2, 1));
		interactionOpponentLabel.setFont(FontManager.getRunescapeSmallFont());
		interactionTimerLabel.setFont(FontManager.getRunescapeSmallFont());
		interactionsInfo.add(interactionOpponentLabel);
		interactionsInfo.add(interactionTimerLabel);
		interactionDetailsContainer.add(interactionsInfo);

		SwingUtil.removeButtonDecorations(collapseBtn);
		collapseBtn.setIcon(collapseAll ? INVISIBLE_ICON : VISIBLE_ICON);
		collapseBtn.setToolTipText("Toggle View");
		collapseBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		collapseBtn.setUI(new BasicButtonUI());
		collapseBtn.addMouseListener(new MouseAdapter()
		{
			public void mousePressed(MouseEvent me)
			{
				changeCollapse();
				ImageIcon collapseIcon = (collapseAll ? INVISIBLE_ICON : VISIBLE_ICON);
				collapseBtn.setIcon(collapseIcon);
				collapseAll = !collapseAll;
			}
		});

		/* Panel that will contain overall data */
		overallPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(5, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));
		overallPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		overallPanel.setLayout(new BorderLayout());
		overallPanel.setVisible(true);

		JPanel overallInfo = new JPanel();
		overallInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		overallInfo.setLayout(new GridLayout(2, 1));
		overallInfo.setBorder(new EmptyBorder(2, 10, 2, 0));
		overallKDLabel.setFont(FontManager.getRunescapeSmallFont());
		overallProfitLabel.setFont(FontManager.getRunescapeSmallFont());
		overallInfo.add(overallKDLabel);
		overallInfo.add(overallProfitLabel);
		overallPanel.add(overallIcon, BorderLayout.WEST);
		overallPanel.add(overallInfo, BorderLayout.CENTER);

		final JPanel toggleCollapse = new JPanel();
		toggleCollapse.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		toggleCollapse.add(collapseBtn);
		overallPanel.add(toggleCollapse, BorderLayout.EAST);

		final JMenuItem reset = new JMenuItem("Reset All");
		reset.addActionListener(e -> {
			this.plugin.removeAll();
			resetAll();
		});

		final JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBorder(new EmptyBorder(1, 1, 1, 1));
		popupMenu.add(reset);
		overallPanel.setComponentPopupMenu(popupMenu);

		logsContainer.setLayout(new BoxLayout(logsContainer, BoxLayout.Y_AXIS));
		layoutPanel.add(actionsContainer);
		layoutPanel.add(interactionDetailsContainer);
		layoutPanel.add(overallPanel);
		layoutPanel.add(logsContainer);

		errorPanel.setContent("PvP Profit Calculator", "You have not died in pvp or killed another player yet.");
		add(errorPanel);
		updateOverall();
	}

	private PvpProfitCalcBox buildBox(PvpProfitCalcRecord record)
	{
		if (!record.matches(currentView, currentType))
		{
			return null;
		}

		for (PvpProfitCalcBox box : boxes)
		{
			if (box.matches(record))
			{
				logsContainer.setComponentZOrder(box, 0);
				box.addEntry(record);
				return box;
			}
		}

		remove(errorPanel);

		final PvpProfitCalcBox box = new PvpProfitCalcBox(itemManager, record.getTitle(), record.getType(), record.getSubTitle());
		box.addEntry(record);

		JPopupMenu popupMenu = box.getComponentPopupMenu();
		if (popupMenu == null)
		{
			popupMenu = new JPopupMenu();
			popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
			box.setComponentPopupMenu(popupMenu);
		}

		box.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					if (box.isCollapsed())
					{
						box.expand();
					}
					else
					{
						box.collapse();
					}
					updateCollapseText();
				}
			}
		});

		final JMenuItem reset = new JMenuItem("Reset Selected");
		reset.addActionListener(e -> {
			this.plugin.removeEntry(record);
			resetMatch(box, record);
		});

		popupMenu.add(reset);

		boxes.add(box);
		logsContainer.add(box, 0);

		return box;
	}

	void add(final String eventName, final PvpProfitCalcType type, final int actorLevel, PvpProfitCalcItem[] items)
	{
		final String subTitle;
		subTitle = actorLevel > -1 ? "(lvl-" + actorLevel + ")" : "";
		final PvpProfitCalcRecord record = new PvpProfitCalcRecord(eventName, subTitle, type, items, 1);

		sessionRecords.add(record);
		plugin.pvpProfitCalcSession.addToSession(record);

		PvpProfitCalcBox box = buildBox(record);
		if (box != null)
		{
			box.rebuild();
			updateOverall();
		}
	}

	private void rebuild()
	{
		SwingUtil.fastRemoveAll(logsContainer);
		boxes.clear();

		sessionRecords.forEach(this::buildBox);

		boxes.forEach(PvpProfitCalcBox::rebuild);
		updateOverall();
		logsContainer.revalidate();
		logsContainer.repaint();
	}

	private void updateOverall()
	{
		double overallDeaths = 1;
		double overallKills = 1;
		long overallProfit = 0;

		Iterable<PvpProfitCalcRecord> records = sessionRecords;

		for (PvpProfitCalcRecord record : records)
		{
			if (!record.matches(currentView, currentType))
			{
				continue;
			}

			int present = record.getItems().length;

			for (PvpProfitCalcItem item : record.getItems())
			{
				if (record.getType() == PvpProfitCalcType.DEATH)
				{
					overallProfit -= item.getTotalPrice();
				}
				else if (record.getType() == PvpProfitCalcType.KILL)
				{
					overallProfit += item.getTotalPrice();
				}
			}
			if (present > 0)
			{
				if (record.getType() == PvpProfitCalcType.DEATH)
				{
					overallDeaths += record.getValue();
				}
				else if (record.getType() == PvpProfitCalcType.KILL)
				{
					overallKills += record.getValue();
				}
			}
		}

		updateOverallToolTip(overallKills, overallDeaths, overallProfit);
		updateActionsToolTip();
		updateInteractionsToolTip();
		updateTimersToolTip();
		updateCollapseText();
	}

	void resetAll()
	{
		final int result = JOptionPane.showOptionDialog(overallPanel, "This will permanently delete the current deaths from the client.",
			"Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
			null, new String[]{
				"Yes",
				"No"
			}, "No");

		if (result != JOptionPane.YES_OPTION)
		{
			return;
		}
		resetNewActiveUser();
	}

	void resetNewActiveUser()
	{
		Predicate<PvpProfitCalcRecord> match = r -> r.matches(currentView, currentType);
		sessionRecords.removeIf(match);
		boxes.removeIf(b -> b.matches(currentView, currentType));
		updateOverall();
		logsContainer.removeAll();
		logsContainer.repaint();
		add(errorPanel);
	}

	private void resetMatch(PvpProfitCalcBox box, PvpProfitCalcRecord record)
	{
		final int result = JOptionPane.showOptionDialog(overallPanel, "This will permanently delete the selected record from the client.",
			"Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
			null, new String[]{
				"Yes",
				"No"
			}, "No");

		if (result != JOptionPane.YES_OPTION)
		{
			return;
		}

		Predicate<PvpProfitCalcRecord> match = r -> r.matches(record.getTitle(), record.getType());
		sessionRecords.removeIf(match);
		boxes.remove(box);
		updateOverall();
		logsContainer.remove(box);
		logsContainer.repaint();
		if (sessionRecords.isEmpty())
		{
			plugin.pvpProfitCalcSession.removeAllFromSession();
			add(errorPanel);
		}
	}

	private boolean isAllCollapsed()
	{
		return boxes.stream()
			.filter(PvpProfitCalcBox::isCollapsed)
			.count() == boxes.size();
	}

	private void updateCollapseText()
	{
		collapseBtn.setSelected(isAllCollapsed());
	}

	private void changeCollapse()
	{
		boolean isAllCollapsed = isAllCollapsed();

		for (PvpProfitCalcBox box : boxes)
		{
			if (isAllCollapsed)
			{
				box.expand();
			}
			else if (!box.isCollapsed())
			{
				box.collapse();
			}
		}

		updateCollapseText();
	}

	void loadHeaderSprite(BufferedImage img)
	{
		overallIcon.setIcon(new ImageIcon(img));
	}

	void loadPrayerSprite(BufferedImage img)
	{
		prayerStatus.setIcon(new ImageIcon(img));
	}


	void updateActionsToolTip()
	{
		skullStatus.setToolTipText((PvpProfitCalcPlugin.isSkulled || (PvpProfitCalcPlugin.wildyLevel > 1 && PvpProfitCalcPlugin.highRiskWorld) || (PvpProfitCalcPlugin.highRiskWorld && PvpProfitCalcPlugin.pvpWorld)) ? "Skulled" : "Unskulled");
		prayerStatus.setToolTipText(PvpProfitCalcPlugin.protectingItem ? "Protect Item Enabled" : "Protect Item Disabled");
		actionsWorldTypeLabel.setText(htmlLabelWorld("World Type: ", (PvpProfitCalcPlugin.pvpWorld ? "PvP" : "Normal"), (PvpProfitCalcPlugin.pvpWorld ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.PROGRESS_COMPLETE_COLOR)));
		actionsWorldRiskLabel.setText(htmlLabelWorld("Risk Type: ", (PvpProfitCalcPlugin.highRiskWorld ? "High Risk" : "Regular"), (PvpProfitCalcPlugin.highRiskWorld ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.PROGRESS_COMPLETE_COLOR)));
	}

	void updateInteractionsToolTip()
	{
		if (PvpProfitCalcPlugin.currentOpponentInteraction != null)
		{
			interactionOpponentLabel.setText(htmlLabelInteractions("Opponent: ", PvpProfitCalcPlugin.getCombatLevelColor(), PvpProfitCalcPlugin.currentOpponentInteraction.getName(), " (Lvl-" + PvpProfitCalcPlugin.currentOpponentInteraction.getCombatLevel() + ")"));
			return;
		}
		interactionOpponentLabel.setText(htmlLabelInteractions("Opponent: ", "White", "None", ""));

	}

	void updateTimersToolTip()
	{
		interactionTimerLabel.setText(htmlLabelTimers("Combat Timer: ", PvpProfitCalcPlugin.lastHitsplatTime != null ? 10 - Duration.between(PvpProfitCalcPlugin.lastHitsplatTime, Instant.now()).getSeconds() : Duration.ofSeconds(0).getSeconds()));
	}

	void updateOverallToolTip(double overallKills, double overallDeaths, long overallProfit)
	{
		String KD = String.format("%.2f", overallKills / overallDeaths);
		overallKDLabel.setText(htmlLabelKD("K/D: ", KD));
		overallProfitLabel.setText(htmlLabelProfit("Total Profit: ", overallProfit));
		overallPanel.setToolTipText(htmlLabelTotals((int) (overallKills - 1), (int) (overallDeaths - 1)));
	}

	private static String htmlLabelWorld(String key, String worldType, Color valueColor)
	{
		return String.format(HTML_WORLD_TEMPLATE, ColorUtil.toHexColor(ColorScheme.GRAND_EXCHANGE_LIMIT), key, ColorUtil.toHexColor(valueColor), worldType);
	}

	private static String htmlLabelInteractions(String key, String color, String name, String level)
	{
		return String.format(HTML_INTERACTION_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, name, color, level);
	}

	private static String htmlLabelTimers(String key, Long value)
	{
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, value);
	}

	private static String htmlLabelKD(String key, String value)
	{
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.GRAND_EXCHANGE_LIMIT), key, value);
	}

	private static String htmlLabelProfit(String key, long value)
	{
		final String v = QuantityFormatter.quantityToStackSize(value);
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.GRAND_EXCHANGE_LIMIT), key, v);
	}

	private static String htmlLabelTotals(int value0, int value1)
	{
		return String.format(HTML_TOTALS_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), ColorUtil.toHexColor(ColorScheme.GRAND_EXCHANGE_LIMIT), value0, ColorUtil.toHexColor(ColorScheme.GRAND_EXCHANGE_LIMIT), value1);
	}
}
