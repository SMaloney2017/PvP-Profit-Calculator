/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * Copyright (c) 2018, Sean Maloney <https://github.com/SMaloney2017>
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

import static com.google.common.collect.Iterables.concat;

import static com.google.common.collect.Iterables.concat;
import com.google.common.collect.Lists;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.SwingUtil;
import com.deathtracker.DeathTrackerPlugin;
import com.deathtracker.DeathTrackerConfig;
import com.deathtracker.DeathTrackerItem;
import com.deathtracker.DeathTrackerRecord;
import com.deathtracker.DeathRecordType;

class DeathTrackerPanel extends PluginPanel
{
    private static final String HTML_LABEL_TEMPLATE =
            "<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";

    private final PluginErrorPanel errorPanel = new PluginErrorPanel();

    public final JPanel logsContainer = new JPanel();

    private final JPanel overallPanel = new JPanel();
    private final JLabel overallDeathsLabel = new JLabel();
    private final JLabel overallCostLabel = new JLabel();
    private final JLabel overallIcon = new JLabel();

    private int overallDeaths;
    private int overallCost;

    private final List<DeathTrackerRecord> aggregateRecords = new ArrayList<>();

    private final List<DeathTrackerRecord> sessionRecords = new ArrayList<>();
    private final List<DeathTrackerBox> boxes = new ArrayList<>();

    private final ItemManager itemManager;
    private final DeathTrackerPlugin plugin;
    private final DeathTrackerConfig config;

    private String currentView;
    private DeathRecordType currentType;

    DeathTrackerPanel(final ItemManager itemManager, final DeathTrackerPlugin plugin, final DeathTrackerConfig config)
    {

        this.itemManager = itemManager;
        this.plugin = plugin;
        this.config = config;

        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        final JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        /* Actions Container */

        /* Views & Controls */

        overallPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(5, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        overallPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overallPanel.setLayout(new BorderLayout());
        overallPanel.setVisible(false);

        final JPanel overallInfo = new JPanel();
        overallInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overallInfo.setLayout(new GridLayout(2, 1));
        overallInfo.setBorder(new EmptyBorder(2, 10, 2, 0));
        overallDeathsLabel.setFont(FontManager.getRunescapeSmallFont());
        overallCostLabel.setFont(FontManager.getRunescapeSmallFont());
        overallInfo.add(overallDeathsLabel);
        overallInfo.add(overallCostLabel);
        overallPanel.add(overallIcon, BorderLayout.WEST);
        overallPanel.add(overallInfo, BorderLayout.CENTER);

        final JMenuItem reset = new JMenuItem("Reset All");
        reset.addActionListener(e ->
        {
            final int result = JOptionPane.showOptionDialog(overallPanel, "Reset deaths across all sessions.",
                    "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, new String[]{"Yes", "No"}, "No");

            if (result != JOptionPane.YES_OPTION) {
                return;
            }

            sessionRecords.removeIf(r -> r.matches(currentView, currentType));
            aggregateRecords.removeIf(r -> r.matches(currentView, currentType));
            boxes.removeIf(b -> b.matches(currentView, currentType));
            updateOverall();
            logsContainer.removeAll();
            logsContainer.repaint();
        });

        final JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
        popupMenu.add(reset);
        overallPanel.setComponentPopupMenu(popupMenu);

        logsContainer.setLayout(new BoxLayout(logsContainer, BoxLayout.Y_AXIS));
        /* layoutPanel.add(actionsContainer); */
        layoutPanel.add(overallPanel);
        layoutPanel.add(logsContainer);
    }

    void add(final String eventName, final DeathRecordType type, final int actorLevel, DeathTrackerItem[] items)
    {
        final String subTitle;
        if (type == DeathRecordType.OTHER)
        {
            subTitle = "(Unknown)";
        }
        else
        {
            subTitle = actorLevel > -1 ? "(lvl-" + actorLevel + ")" : "";
        }
        final DeathTrackerRecord record = new DeathTrackerRecord(eventName, subTitle, type, items, 1);
        sessionRecords.add(record);

        /* Hide if null */

        DeathTrackerBox box = buildBox(record);
        if (box != null)
        {
            box.rebuild();
            updateOverall();
        }
    }


    void addRecords(Collection<DeathTrackerRecord> records)
    {
        aggregateRecords.addAll(records);
        rebuild();
    }

    private void rebuild()
    {
        SwingUtil.fastRemoveAll(logsContainer);
        boxes.clear();

        Lists.reverse(sessionRecords).stream()
                .collect(Collectors.toCollection(ArrayDeque::new))
                .descendingIterator()
                .forEachRemaining(this::buildBox);

        boxes.forEach(DeathTrackerBox::rebuild);
        updateOverall();
        logsContainer.revalidate();
        logsContainer.repaint();
    }

    private void updateOverall()
    {
        long overallDeaths = 0;
        long overallCost = 0;

        Iterable<DeathTrackerRecord> records = sessionRecords;

        for (DeathTrackerRecord record : records)
        {
            int present = record.getItems().length;

            for (DeathTrackerItem item : record.getItems())
            {
                overallCost += item.getTotalCost();
            }
            if (present > 0)
            {
                overallDeaths += record.getDeaths();
            }
        }
        overallDeathsLabel.setText(htmlLabel("Total count: ", overallDeaths));
        overallCostLabel.setText(htmlLabel("Total Cost: ", overallCost));
        overallCostLabel.setToolTipText("<html>Total Cost: " + QuantityFormatter.formatNumber(overallCost) + "</html>");
    }

    private static String htmlLabel(String key, long value)
    {
        final String valueStr = QuantityFormatter.quantityToStackSize(value);
        return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
    }
}