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
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import net.runelite.api.Item;
import net.runelite.api.ItemID;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;

class DeathTrackerPanel extends PluginPanel
{
    @Inject
    private SpriteManager spriteManager;

    private static final BufferedImage visibleImg;
    private static final BufferedImage invisibleImg;
    private static final ImageIcon VISIBLE_ICON;
    private static final ImageIcon INVISIBLE_ICON;

    private static final String HTML_LABEL_TEMPLATE =
            "<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";
    private static final String HTML_WORLD_TEMPLATE =
            "<html><body style='color:%s'>%s<span style='color:%s'>%s</span></body></html>";

    /* Display errorPanel when there are no deaths */
    private final PluginErrorPanel errorPanel = new PluginErrorPanel();

    /* Handle death boxes */
    public final JPanel logsContainer = new JPanel();

    /* Session data */
    private final JPanel overallPanel = new JPanel();
    private final JLabel overallDeathsLabel = new JLabel();
    public final JLabel overallCostLabel = new JLabel();
    public final JLabel overallIcon = new JLabel();

    /* Details and actions */
    private final JPanel actionsContainer = new JPanel();
    private final JLabel actionsWorldRiskLabel = new JLabel();
    private final JLabel actionsWorldLabel = new JLabel();
    private final JButton collapseBtn = new JButton();
    private final JLabel prayerStatus = new JLabel();
    public final JLabel skullStatus = new JLabel();

    /* Individual record of each death */
    private final List<DeathTrackerRecord> sessionRecords = new ArrayList<>();
    private final List<DeathTrackerBox> boxes = new ArrayList<>();

    private final ItemManager itemManager;

    private String currentView;
    private DeathRecordType currentType;
    private boolean collapseAll = false;

    static {
        visibleImg = ImageUtil.loadImageResource(LootTrackerPlugin.class, "visible_icon.png");
        invisibleImg = ImageUtil.loadImageResource(LootTrackerPlugin.class, "invisible_icon.png");

        VISIBLE_ICON = new ImageIcon(ImageUtil.alphaOffset(visibleImg, -220));
        INVISIBLE_ICON = new ImageIcon(ImageUtil.alphaOffset(invisibleImg, -200));
    }

    DeathTrackerPanel(final DeathTrackerPlugin plugin, final ItemManager itemManager)
    {
        this.itemManager = itemManager;

        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        final JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        add(layoutPanel, BorderLayout.NORTH);

        actionsContainer.setLayout(new FlowLayout(FlowLayout.LEFT));
        actionsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actionsContainer.setBorder(new EmptyBorder(0, 5, 0, 10));
        actionsContainer.setVisible(true);

        prayerStatus.setIconTextGap(0);
        prayerStatus.setBorder(new EmptyBorder(0,0,0,5));
        prayerStatus.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actionsContainer.add(prayerStatus);

        skullStatus.setIconTextGap(0);
        skullStatus.setBorder(new EmptyBorder(0,0,0,5));
        skullStatus.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actionsContainer.add(skullStatus);

        JPanel actionsInfo = new JPanel();
        actionsInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actionsInfo.setLayout(new GridLayout(2, 1));
        actionsWorldLabel.setFont(FontManager.getRunescapeSmallFont());
        actionsWorldRiskLabel.setFont(FontManager.getRunescapeSmallFont());
        actionsInfo.add(actionsWorldLabel);
        actionsInfo.add(actionsWorldRiskLabel);
        actionsContainer.add(actionsInfo);

        SwingUtil.removeButtonDecorations(collapseBtn);
        collapseBtn.setIcon(collapseAll ? INVISIBLE_ICON : VISIBLE_ICON);
        collapseBtn.setToolTipText("Toggle View");
        collapseBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        collapseBtn.setUI(new BasicButtonUI());
        collapseBtn.addMouseListener(new MouseAdapter()
        {
            public void mousePressed(MouseEvent me) {
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
        overallDeathsLabel.setFont(FontManager.getRunescapeSmallFont());
        overallCostLabel.setFont(FontManager.getRunescapeSmallFont());
        overallInfo.add(overallDeathsLabel);
        overallInfo.add(overallCostLabel);
        overallPanel.add(overallIcon, BorderLayout.WEST);
        overallPanel.add(overallInfo, BorderLayout.CENTER);

        final JPanel toggleCollapse = new JPanel();
        toggleCollapse.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        toggleCollapse.add(collapseBtn);
        overallPanel.add(toggleCollapse, BorderLayout.EAST);

        final JMenuItem reset = new JMenuItem("Reset All");
        reset.addActionListener(e -> resetAll());

        final JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(new EmptyBorder(1, 1, 1, 1));
        popupMenu.add(reset);
        overallPanel.setComponentPopupMenu(popupMenu);

        logsContainer.setLayout(new BoxLayout(logsContainer, BoxLayout.Y_AXIS));
        layoutPanel.add(actionsContainer);
        layoutPanel.add(overallPanel);
        layoutPanel.add(logsContainer);

        errorPanel.setContent("Death Tracker", "You have not died yet.");
        add(errorPanel);
        updateOverall();
    }

    void updateCollapseText()
    {
        collapseBtn.setSelected(isAllCollapsed());
    }

    private boolean isAllCollapsed()
    {
        return boxes.stream()
                .filter(DeathTrackerBox::isCollapsed)
                .count() == boxes.size();
    }

    private void changeCollapse()
    {
        boolean isAllCollapsed = isAllCollapsed();

        for (DeathTrackerBox box : boxes)
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

    public void updateActionsToolTip()
    {
        skullStatus.setToolTipText((DeathTrackerPlugin.isSkulled || (DeathTrackerPlugin.wildyLevel > 1 && DeathTrackerPlugin.highRiskWorld) || (DeathTrackerPlugin.highRiskWorld && DeathTrackerPlugin.pvpWorld)) ? "Skulled" : "Unskulled");
        prayerStatus.setToolTipText(DeathTrackerPlugin.protectingItem ? "Protect Item Enabled" : "Protect Item Disabled");
        actionsWorldLabel.setText(htmlLabel("World Type: ", (DeathTrackerPlugin.pvpWorld ? "PvP":"Normal"), (DeathTrackerPlugin.pvpWorld ? ColorScheme.PROGRESS_ERROR_COLOR:ColorScheme.PROGRESS_COMPLETE_COLOR)));
        actionsWorldRiskLabel.setText(htmlLabel("Risk Type: ", (DeathTrackerPlugin.highRiskWorld ? "High Risk":"Regular"), (DeathTrackerPlugin.highRiskWorld ? ColorScheme.PROGRESS_ERROR_COLOR:ColorScheme.PROGRESS_COMPLETE_COLOR)));
    }

    private static String htmlLabel(String key, long value)
    {
        final String valueStr = QuantityFormatter.quantityToStackSize(value);
        return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.GRAND_EXCHANGE_LIMIT), key, valueStr);
    }

    private static String htmlLabel(String key, String text, Color valueColor)
    {
        return String.format(HTML_WORLD_TEMPLATE, ColorUtil.toHexColor(ColorScheme.BRAND_ORANGE) ,key, ColorUtil.toHexColor(valueColor), text);
    }

    void add(final String eventName, final DeathRecordType type, final int actorLevel, DeathTrackerItem[] items)
    {
        final String subTitle;
        if(type == DeathRecordType.OTHER)
        {
            subTitle = "(Unidentified)";
        }
        else
        {
            subTitle = actorLevel > -1 ? "(lvl-" + actorLevel + ")" : "";
        }
        final DeathTrackerRecord record = new DeathTrackerRecord(eventName, subTitle, type, items, 1);
        sessionRecords.add(record);

        DeathTrackerBox box = buildBox(record);
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

        boxes.forEach(DeathTrackerBox::rebuild);
        updateOverall();
        logsContainer.revalidate();
        logsContainer.repaint();
    }

    private DeathTrackerBox buildBox(DeathTrackerRecord record)
    {
        if (!record.matches(currentView, currentType))
        {
            return null;
        }

        for (DeathTrackerBox box : boxes)
        {
            if (box.matches(record))
            {
                logsContainer.setComponentZOrder(box, 0);
                box.addDeath(record);
                return box;
            }
        }

        remove(errorPanel);
        actionsContainer.setVisible(true);
        overallPanel.setVisible(true);

        final DeathTrackerBox box = new DeathTrackerBox(itemManager, record.getTitle(), record.getType(), record.getSubTitle());
        box.addDeath(record);

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
        reset.addActionListener(e -> resetMatch(box, record));

        popupMenu.add(reset);

        boxes.add(box);
        logsContainer.add(box, 0);

        return box;
    }

    private void updateOverall()
    {
        long overallDeaths = 0;
        long overallCost = 0;

        Iterable<DeathTrackerRecord> records = sessionRecords;

        for (DeathTrackerRecord record : records)
        {
            if (!record.matches(currentView, currentType))
            {
                continue;
            }

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
        overallDeathsLabel.setText(htmlLabel("Total Deaths: ", overallDeaths));
        overallCostLabel.setText(htmlLabel("Total Cost: ", overallCost));
        updateActionsToolTip();
        updateCollapseText();
    }

    private void resetAll()
    {
        final int result = JOptionPane.showOptionDialog(overallPanel, "This will permanently delete the current deaths from the client.",
                "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new String[]{"Yes", "No"}, "No");

        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        Predicate<DeathTrackerRecord> match = r -> r.matches(currentView, currentType);
        sessionRecords.removeIf(match);
        boxes.removeIf(b -> b.matches(currentView, currentType));
        updateOverall();
        logsContainer.removeAll();
        logsContainer.repaint();
        add(errorPanel);
    }

    private void resetMatch(DeathTrackerBox box, DeathTrackerRecord record)
    {
        final int result = JOptionPane.showOptionDialog(overallPanel, "This will permanently delete the selected record from the client.",
                "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new String[]{"Yes", "No"}, "No");

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        Predicate<DeathTrackerRecord> match = r -> r.matches(record.getTitle(), record.getType());
        sessionRecords.removeIf(match);
        boxes.remove(box);
        updateOverall();
        logsContainer.remove(box);
        logsContainer.repaint();
        if(sessionRecords.isEmpty()){
            add(errorPanel);
        }
    }
}