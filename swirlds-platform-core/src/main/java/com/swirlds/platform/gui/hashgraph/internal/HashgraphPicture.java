/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.platform.gui.hashgraph.internal;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.gui.hashgraph.internal.HashgraphGuiUtils.HASHGRAPH_PICTURE_FONT;

import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ItemEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.time.Instant;
import java.util.Arrays;
import javax.swing.JPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This panel has the hashgraph picture, and appears in the window to the right of all the settings.
 */
public class HashgraphPicture extends JPanel {
    @Serial private static final long serialVersionUID = 1L;
    private static final Logger log = LogManager.getLogger(HashgraphPicture.class);
    private final HashgraphGuiSource hashgraphSource;
    private final HashgraphPictureOptions options;
    private PictureMetadata pictureMetadata;
    /** used to store an image when the freeze checkbox is checked */
    private BufferedImage image = null;

    private AddressBookMetadata nonExpandedMetadata;
    private AddressBookMetadata expandedMetadata;

    public HashgraphPicture(
            final HashgraphGuiSource hashgraphSource, final HashgraphPictureOptions options) {
        this.hashgraphSource = hashgraphSource;
        this.options = options;
        createMetadata();
    }

    private void createMetadata() {
        if ((expandedMetadata == null || nonExpandedMetadata == null)
                && hashgraphSource.isReady()) {
            expandedMetadata = new AddressBookMetadata(hashgraphSource.getAddressBook(), true);
            nonExpandedMetadata = new AddressBookMetadata(hashgraphSource.getAddressBook(), false);
        }
    }

    @Override
    public void paintComponent(final Graphics g) {
        super.paintComponent(g);
        try {
            if (image != null) {
                g.drawImage(image, 0, 0, null);
                return;
            }
            if (!hashgraphSource.isReady()) {
                return;
            }
            createMetadata();
            g.setFont(HASHGRAPH_PICTURE_FONT);
            final FontMetrics fm = g.getFontMetrics();
            final int numMem = hashgraphSource.getAddressBook().getSize();
            final AddressBookMetadata currentMetadata =
                    options.isExpanded() ? expandedMetadata : nonExpandedMetadata;

            PlatformEvent[] events;
            if (options.displayLatestEvents()) {
                final long startGen =
                        Math.max(
                                hashgraphSource.getMaxGeneration()
                                        - options.getNumGenerationsDisplay(),
                                GraphGenerations.FIRST_GENERATION);
                options.setStartGeneration(startGen);
                events = hashgraphSource.getEvents(startGen, options.getNumGenerationsDisplay());
            } else {
                events =
                        hashgraphSource.getEvents(
                                options.getStartGeneration(), options.getNumGenerationsDisplay());
            }
            // in case the state has events from creators that don't exist, don't show them
            if (events == null) { // in case a screen refresh happens before any events
                return;
            }
            events =
                    Arrays.stream(events)
                            .filter(e -> e.getCreatorId() < numMem)
                            .toArray(PlatformEvent[]::new);

            pictureMetadata = new PictureMetadata(fm, this.getSize(), currentMetadata, events);

            g.setColor(Color.BLACK);

            for (int i = 0; i < currentMetadata.getNumColumns(); i++) {
                final String name = currentMetadata.getName(i);

                // gap between columns
                final int betweenGap = pictureMetadata.getGapBetweenColumns();
                // gap between leftmost column and left edge (and similar on right)
                final int sideGap = pictureMetadata.getSideGap();
                final int x = sideGap + (i) * betweenGap;
                g.drawLine(x, pictureMetadata.getYmin(), x, pictureMetadata.getYmax());
                final Rectangle2D rect = fm.getStringBounds(name, g);
                g.drawString(
                        name,
                        (int) (x - rect.getWidth() / 2),
                        (int) (pictureMetadata.getYmax() + rect.getHeight()));
            }

            final int d = (int) (2 * pictureMetadata.getR());

            // for each event, draw 2 downward lines to its parents
            for (final PlatformEvent event : events) {
                drawLinksToParents(g, event);
            }

            // for each event, draw its circle
            for (final PlatformEvent event : events) {
                drawEventCircle(g, event, options, d);
            }
        } catch (final Exception e) {
            log.error(EXCEPTION.getMarker(), "error while painting", e);
        }
    }

    private void drawLinksToParents(final Graphics g, final PlatformEvent event) {
        g.setColor(HashgraphGuiUtils.eventColor(event, options));
        final PlatformEvent e1 = event.getSelfParent();
        PlatformEvent e2 = event.getOtherParent();
        if (e2 != null && e2.getCreatorId() >= hashgraphSource.getNumMembers()) {
            // if the creator of the other parent has been removed,
            // treat it as if there is no other parent
            e2 = null;
        }
        if (e1 != null && e1.getGeneration() >= pictureMetadata.getMinGen()) {
            g.drawLine(
                    pictureMetadata.xpos(e2, event),
                    pictureMetadata.ypos(event),
                    pictureMetadata.xpos(e2, event),
                    pictureMetadata.ypos(e1));
        }
        if (e2 != null && e2.getGeneration() >= pictureMetadata.getMinGen()) {
            g.drawLine(
                    pictureMetadata.xpos(e2, event),
                    pictureMetadata.ypos(event),
                    pictureMetadata.xpos(event, e2),
                    pictureMetadata.ypos(e2));
        }
    }

    private void drawEventCircle(
            final Graphics g,
            final PlatformEvent event,
            final HashgraphPictureOptions options,
            final int d) {
        final FontMetrics fm = g.getFontMetrics();
        final int fa = fm.getMaxAscent();
        final int fd = fm.getMaxDescent();
        final PlatformEvent e2 = event.getOtherParent();
        final Color color = HashgraphGuiUtils.eventColor(event, options);
        g.setColor(color);
        g.fillOval(
                pictureMetadata.xpos(e2, event) - d / 2, pictureMetadata.ypos(event) - d / 2, d, d);
        g.setFont(g.getFont().deriveFont(Font.BOLD));

        String s = "";

        if (options.writeRoundCreated()) {
            s += " " + event.getRoundCreated();
        }
        if (options.writeRoundReceived() && event.getRoundReceived() > 0) {
            s += " " + event.getRoundReceived();
        }
        // if not consensus, then there's no order yet
        if (options.writeConsensusOrder() && event.isConsensus()) {
            s += " " + event.getConsensusOrder();
        }
        if (options.writeConsensusTimeStamp()) {
            final Instant t = event.getConsensusTimestamp();
            if (t != null) {
                s += " " + HashgraphGuiUtils.FORMATTER.format(t);
            }
        }
        if (options.writeGeneration()) {
            s += " " + event.getGeneration();
        }
        if (!s.isEmpty()) {
            final Rectangle2D rect = fm.getStringBounds(s, g);
            final int x = (int) (pictureMetadata.xpos(e2, event) - rect.getWidth() / 2. - fa / 4.);
            final int y = (int) (pictureMetadata.ypos(event) + rect.getHeight() / 2. - fd / 2);
            g.setColor(HashgraphGuiUtils.LABEL_OUTLINE);
            g.drawString(s, x - 1, y - 1);
            g.drawString(s, x + 1, y - 1);
            g.drawString(s, x - 1, y + 1);
            g.drawString(s, x + 1, y + 1);
            g.setColor(color);
            g.drawString(s, x, y);
        }
    }

    public void freezeChanged(final ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            try { // capture a bitmap of "picture" from the screen
                image =
                        (new Robot())
                                .createScreenCapture(
                                        new Rectangle(
                                                this.getLocationOnScreen(),
                                                this.getVisibleRect().getSize()));
                // to write the image to disk:
                // ImageIO.write(image, "jpg", new File("image.jpg"));
            } catch (final AWTException err) {
                // ignore exception
            }
        } else if (e.getStateChange() == ItemEvent.DESELECTED) {
            image = null; // erase the saved image, stop freezing
        }
    }
}
