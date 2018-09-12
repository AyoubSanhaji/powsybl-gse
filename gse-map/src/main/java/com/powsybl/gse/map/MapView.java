/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.gse.map;

import com.google.common.io.ByteStreams;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.schedulers.Schedulers;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MapView extends Region {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapView.class);

    private final TileManager tileManager;

    private final Canvas tileCanvas = new Canvas();

    private final ObjectProperty<Coordinate> center = new SimpleObjectProperty<>(new Coordinate(0, 0));

    private final IntegerProperty zoom;

    private final Map<Tile, Point2D> tilesToDraw = new HashMap<>();

    public MapView(TileManager tileManager) {
        this.tileManager = Objects.requireNonNull(tileManager);
        // bounded property to limit zoom level
        zoom = new SimpleIntegerProperty(tileManager.getServerInfo().getMinZoomLevel()) {
            @Override
            public void set(int newValue) {
                if (newValue < tileManager.getServerInfo().getMinZoomLevel()) {
                    super.set(tileManager.getServerInfo().getMinZoomLevel());
                } else if (newValue > tileManager.getServerInfo().getMaxZoomLevel()) {
                    super.set(tileManager.getServerInfo().getMaxZoomLevel());
                } else {
                    super.set(newValue);
                }
            }
        };
        getChildren().addAll(tileCanvas);
        zoom.addListener((observable, oldValue, newValue) -> requestLayout());
        center.addListener((observable, oldValue, newValue) -> requestLayout());

        tileManager.serverInfoProperty().addListener((observable, oldValue, newValue) -> requestLayout());

        // panning
        ObjectProperty<Point2D> mouseDown = new SimpleObjectProperty<>();
        tileCanvas.setOnMousePressed(event -> {
            mouseDown.set(new Point2D(event.getX(), event.getY()));
        });
        tileCanvas.setOnMouseDragged(event -> {
            double dx = event.getX() - mouseDown.get().getX();
            double dy = event.getY() - mouseDown.get().getY();

            // shift center
            TilePoint p1 = tileManager.project(center.get(), zoom.get());
            TilePoint p2 = new TilePoint(p1.getX() - dx / tileManager.getServerInfo().getTileWidth(),
                                         p1.getY() - dy / tileManager.getServerInfo().getTileHeight(),
                                         zoom.get(),
                                         tileManager.getServerInfo());
            center.set(p2.getCoordinate());

            mouseDown.set(new Point2D(event.getX(), event.getY()));
        });
    }

    public IntegerProperty zoomProperty() {
        return zoom;
    }

    public ObjectProperty<Coordinate> centerProperty() {
        return center;
    }

    public void addLayer(MapLayer layer) {
        Objects.requireNonNull(layer);
    }

    public void removeLayer(MapLayer layer) {
        Objects.requireNonNull(layer);
    }

    private void loadAndDrawTile(GraphicsContext g, Tile tile) {
        tileManager.getHttpClient().request(tile)
                .subscribeOn(Schedulers.computation())
                .subscribe(response -> {
                    if (response.getStatusCode() == HttpResponseStatus.OK.code()) {
                        // write to cache
                        try (InputStream is = response.getResponseBodyAsStream();
                             OutputStream os = tileManager.getCache().writeTile(tile)) {
                            ByteStreams.copy(is, os);
                        }

                        // draw tile
                        Platform.runLater(() -> {
                            Point2D pointScreen = tilesToDraw.remove(tile);
                            if (pointScreen != null) {
                                try (InputStream is2 = tileManager.getCache().readTile(tile).orElseThrow(AssertionError::new)) {
                                    g.drawImage(new Image(is2), pointScreen.getX(), pointScreen.getY());
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                        });
                    }
                }, throwable -> LOGGER.error(throwable.toString(), throwable));
    }

    private static void drawWhiteTile(GraphicsContext g, double xScreen, double yScreen, double tileWidth, double tileHeight) {
        g.setFill(Color.WHITE);
        g.fillRect(xScreen, yScreen, tileWidth, tileHeight);
    }

    private void drawTiles() {
        TilePoint centerTilePoint = tileManager.project(center.get(), zoom.get());
        Tile centerTile = centerTilePoint.getTile();

        double tileWidth = tileManager.getServerInfo().getTileWidth();
        double tileHeight = tileManager.getServerInfo().getTileHeight();

        // compute center tile screen point
        double xScreenCenterTile = getWidth() / 2 - tileWidth * (centerTilePoint.getX() - centerTile.getX());
        double yScreenCenterTile = getHeight() / 2 - tileHeight * (centerTilePoint.getY() - centerTile.getY());

        // compute number of tiles needed to fill the screen
        int w1 = (int) Math.ceil(xScreenCenterTile / tileWidth);
        int w2 = (int) Math.ceil((getWidth() - xScreenCenterTile - tileWidth) / tileWidth);
        int h1 = (int) Math.ceil(yScreenCenterTile / tileHeight);
        int h2 = (int) Math.ceil((getHeight() - yScreenCenterTile - tileHeight) / tileHeight);

        tilesToDraw.clear();

        // draw tiles
        GraphicsContext g = tileCanvas.getGraphicsContext2D();
        int n = tileManager.getTileCount(zoom.get());
        for (int i = -w1; i <= w2; i++) {
            for (int j = -h1; j <= h2; j++) {
                int tileX = centerTile.getX() + i;
                int tileY = centerTile.getY() + j;
                double xScreen = xScreenCenterTile + i * tileWidth;
                double yScreen = yScreenCenterTile + j * tileHeight;
                if (tileX < 0 || tileY < 0 || tileX > n - 1 || tileY > n - 1) {
                    drawWhiteTile(g, xScreen, yScreen, tileWidth, tileHeight);
                } else {
                    Tile tile = new Tile(tileX, tileY, zoom.get(), tileManager.getServerInfo());
                    try (InputStream is = tileManager.getCache().readTile(tile).orElse(null)) {
                        if (is != null) {
                            // tile is in the cache, we can draw it
                            g.drawImage(new Image(is), xScreen, yScreen);
                        } else {
                            tilesToDraw.put(tile, new Point2D(xScreen, yScreen));

                            // paint white tile
                            drawWhiteTile(g, xScreen, yScreen, tileWidth, tileHeight);

                            // load tile in background
                            loadAndDrawTile(g, tile);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
    }

    @Override
    protected void layoutChildren() {
        // resize canvas to fit the parent region
        tileCanvas.setWidth(getWidth());
        tileCanvas.setHeight(getHeight());

        // draw tiles
        drawTiles();
    }
}
