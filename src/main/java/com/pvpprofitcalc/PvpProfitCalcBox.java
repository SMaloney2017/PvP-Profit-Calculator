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

package com.pvpprofitcalc;

import com.google.common.base.Strings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.ToLongFunction;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.border.EmptyBorder;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

class PvpProfitCalcBox extends JPanel
{
    private static final int ITEMS_PER_ROW = 5;
    private static final int TITLE_PADDING = 5;

    private final JPanel itemContainer = new JPanel();
    private final JLabel priceLabel = new JLabel();
    private final JLabel subTitleLabel = new JLabel();
    private final JPanel logTitle = new JPanel();
    private final ItemManager itemManager;
    @Getter(AccessLevel.PACKAGE)
    private final String id;
    private final PvpProfitCalcType pvpStatsType;

    private int value;
    @Getter
    private final List<PvpProfitCalcItem> items = new ArrayList<>();

    private long totalPrice;

    PvpProfitCalcBox(
            final ItemManager itemManager,
            final String id,
            final PvpProfitCalcType pvpStatsType,
            @Nullable final String subtitle
    )
    {
        this.id = id;
        this.pvpStatsType = pvpStatsType;
        this.itemManager = itemManager;

        setLayout(new BorderLayout(0, 1));
        setBorder(new EmptyBorder(5, 0, 0, 0));

        logTitle.setLayout(new BoxLayout(logTitle, BoxLayout.X_AXIS));
        logTitle.setBorder(new EmptyBorder(7, 7, 7, 7));
        logTitle.setBackground(pvpStatsType == PvpProfitCalcType.KILL ? new Color(25, 25, 55) : new Color(55, 20, 20));

        JLabel titleLabel = new JLabel();
        titleLabel.setText(Text.removeTags(id));
        titleLabel.setFont(FontManager.getRunescapeSmallFont());
        titleLabel.setForeground(Color.WHITE);

        titleLabel.setMinimumSize(new Dimension(1, titleLabel.getPreferredSize().height));
        logTitle.add(titleLabel);

        subTitleLabel.setFont(FontManager.getRunescapeSmallFont());
        subTitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        if (!Strings.isNullOrEmpty(subtitle))
        {
            subTitleLabel.setText(subtitle);
        }

        logTitle.add(Box.createRigidArea(new Dimension(TITLE_PADDING, 0)));
        logTitle.add(subTitleLabel);
        logTitle.add(Box.createHorizontalGlue());
        logTitle.add(Box.createRigidArea(new Dimension(TITLE_PADDING, 0)));

        priceLabel.setFont(FontManager.getRunescapeSmallFont());
        priceLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        logTitle.add(priceLabel);

        add(logTitle, BorderLayout.NORTH);
        add(itemContainer, BorderLayout.CENTER);

        /* Include/ Ignore Item */
    }

    public int getTotalValue()
    {
        return value;
    }

    boolean matches(final PvpProfitCalcRecord record)
    {
        return record.getTitle().equals(id) && record.getType() == pvpStatsType;
    }

    boolean matches(final String id, final PvpProfitCalcType type)
    {
        if (id == null)
        {
            return true;
        }

        return this.id.equals(id) && pvpStatsType == type;
    }

    void addEntry(final PvpProfitCalcRecord record)
    {
        if (!matches(record))
        {
            throw new IllegalArgumentException(record.toString());
        }

        value = record.getValue();

        outer:
        for (PvpProfitCalcItem item : record.getItems())
        {
            final int mappedItemId = PvpProfitCalcMap.map(item.getId(), item.getName());
            for (int idx = 0; idx < items.size(); ++idx)
            {
                PvpProfitCalcItem i = items.get(idx);
                if (mappedItemId == i.getId())
                {
                    items.set(idx, new PvpProfitCalcItem(i.getId(), i.getName(), i.getQuantity() + item.getQuantity(), i.getGePrice()));
                    continue outer;
                }
            }

            final PvpProfitCalcItem mappedItem = mappedItemId == item.getId()
                    ? item
                    : new PvpProfitCalcItem(mappedItemId, item.getName(), item.getQuantity(), item.getGePrice());
            items.add(mappedItem);
        }
    }

    void rebuild()
    {
        buildItems();

        String priceTypeString = (pvpStatsType == PvpProfitCalcType.DEATH ? "Total Loss: ": "Total Gain: ");

        priceLabel.setText(priceTypeString + QuantityFormatter.quantityToStackSize(totalPrice) + " gp");
        priceLabel.setToolTipText(QuantityFormatter.formatNumber(totalPrice) + " gp");

        final long value = getTotalValue();
        subTitleLabel.setText("x " + value);

        validate();
        repaint();
    }

    void collapse()
    {
        if (!isCollapsed())
        {
            itemContainer.setVisible(false);
            applyDimmer(false, logTitle);
        }
    }

    void expand()
    {
        if (isCollapsed())
        {
            itemContainer.setVisible(true);
            applyDimmer(true, logTitle);
        }
    }

    boolean isCollapsed()
    {
        return !itemContainer.isVisible();
    }

    private void applyDimmer(boolean brighten, JPanel panel)
    {
        for (Component component : panel.getComponents())
        {
            Color color = component.getForeground();

            component.setForeground(brighten ? color.brighter() : color.darker());
        }
    }

    private void buildItems()
    {
        totalPrice = 0;

        List<PvpProfitCalcItem> items = this.items;

        /* Hide Ignored Items */

        boolean isHidden = items.isEmpty();
        setVisible(!isHidden);

        if (isHidden)
        {
            return;
        }

        ToLongFunction<PvpProfitCalcItem> price = PvpProfitCalcItem::getTotalPrice;

        totalPrice = items.stream()
                .mapToLong(price)
                .sum();

        items.sort(Comparator.comparingLong(price).reversed());

        final int rowSize = ((items.size() % ITEMS_PER_ROW == 0) ? 0 : 1) + items.size() / ITEMS_PER_ROW;

        itemContainer.removeAll();
        itemContainer.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 2, 2));

        for (int i = 0; i < rowSize * ITEMS_PER_ROW; i++)
        {
            final JPanel slotContainer = new JPanel();
            slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            if (i < items.size())
            {
                final PvpProfitCalcItem item = items.get(i);
                final JLabel imageLabel = new JLabel();
                imageLabel.setToolTipText(buildToolTip(item));
                imageLabel.setVerticalAlignment(SwingConstants.CENTER);
                imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

                AsyncBufferedImage itemImage = itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1);

                itemImage.addTo(imageLabel);

                slotContainer.add(imageLabel);

                /* Toggle Item */
            }

            itemContainer.add(slotContainer);
        }

        itemContainer.repaint();
    }

    private static String buildToolTip(PvpProfitCalcItem item)
    {
        final String name = item.getName();
        final int quantity = item.getQuantity();
        final long price = item.getTotalPrice();
        final StringBuilder sb = new StringBuilder("<html>");
        sb.append(name).append(" x ").append(QuantityFormatter.formatNumber(quantity));
        if (item.getId() == ItemID.COINS_995)
        {
            sb.append("</html>");
            return sb.toString();
        }

        sb.append("<br>Ge Price: ").append(QuantityFormatter.quantityToStackSize(price));
        if (quantity > 1)
        {
            sb.append(" (").append(QuantityFormatter.quantityToStackSize(item.getGePrice())).append(" ea)");
        }

        sb.append("</html>");
        return sb.toString();
    }
}