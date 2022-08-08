/*
 * Copyright 2022 James Bowring, Noah McLean, Scott Burdick, and CIRDLES.org.
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

package org.cirdles.tripoli.gui.dataViews.plots;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.cirdles.tripoli.visualizationUtilities.histograms.HistogramBuilder;

/**
 * @author James F. Bowring
 */
public class HistogramPlot extends AbstractDataView {

    private final HistogramBuilder histogramBuilder;

    /**
     * @param bounds
     */
    public HistogramPlot(Rectangle bounds, HistogramBuilder histogramBuilder) {
        super(bounds, 35, 5);
        this.histogramBuilder = histogramBuilder;
    }

    @Override
    public void preparePanel() {

        xAxisData = histogramBuilder.getBinCenters();
        minX = xAxisData[0];
        maxX = xAxisData[xAxisData.length - 1];

        ticsX = TicGeneratorForAxes.generateTics(minX, maxX, (int) (graphWidth / 50.0));
        double xMarginStretch = TicGeneratorForAxes.generateMarginAdjustment(minX, maxX, 0.05);
        minX -= xMarginStretch;
        maxX += xMarginStretch;

        yAxisData = histogramBuilder.getBinCounts();
        minY = Double.MAX_VALUE;
        maxY = -Double.MAX_VALUE;

        for (double yAxisDatum : yAxisData) {
            minY = StrictMath.min(minY, yAxisDatum);
            maxY = StrictMath.max(maxY, yAxisDatum);
        }

        // customized for histogram
        minY = 0;
        ticsY = TicGeneratorForAxes.generateTics(minY, maxY, (int) (graphHeight / 10.0));

        // check for no data
        if ((ticsY != null) && (ticsY.length > 1)) {
            // force y to tics
            minY = ticsY[0].doubleValue();
            maxY = ticsY[ticsY.length - 1].doubleValue();
            // adjust margins
            double yMarginStretch = TicGeneratorForAxes.generateMarginAdjustment(minY, maxY, 0.05);
            minY -= yMarginStretch;
            maxY += yMarginStretch;
        }

        setDisplayOffsetY(0.0);
        setDisplayOffsetX(0.0);

        this.repaint();
    }

    @Override
    public void paint(GraphicsContext g2d) {
        super.paint(g2d);

        Text text = new Text();
        text.setFont(Font.font("SansSerif", 12));
        int textWidth = 0;

        g2d.setFont(Font.font("SansSerif", FontWeight.SEMI_BOLD, 12));
        g2d.setFill(Paint.valueOf("RED"));
        g2d.fillText(histogramBuilder.getTitle(), 10, 15);

        // plot bins
        g2d.setLineWidth(2.0);
        for (int i = 0; i < xAxisData.length; i++) {
            g2d.fillRect(
                    mapX(xAxisData[i] - histogramBuilder.getBinWidth() / 2.0),
                    mapY(yAxisData[i]),
                    mapX(xAxisData[1]) - mapX(xAxisData[0]),
                    mapY(0.0) - mapY(yAxisData[i]));
        }

        if (ticsY.length > 1) {
            // border and fill
            g2d.setLineWidth(0.5);
            g2d.setStroke(Paint.valueOf("BLACK"));
            g2d.strokeRect(
                    mapX(minX),
                    mapY(ticsY[ticsY.length - 1].doubleValue()),
                    graphWidth,
                    StrictMath.abs(mapY(ticsY[ticsY.length - 1].doubleValue()) - mapY(ticsY[0].doubleValue())));
            g2d.setFill(new Color(1, 1, 224 / 255, 0.1));
            g2d.fillRect(
                    mapX(minX),
                    mapY(ticsY[ticsY.length - 1].doubleValue()),
                    graphWidth,
                    StrictMath.abs(mapY(ticsY[ticsY.length - 1].doubleValue()) - mapY(ticsY[0].doubleValue())));
            g2d.setFill(Paint.valueOf("BLACK"));

            // ticsY
            float verticalTextShift = 3.2f;
            g2d.setFont(Font.font("SansSerif", 10));
            if (ticsY != null) {
                for (int i = 0; i < ticsY.length; i++) {
                    g2d.strokeLine(
                            mapX(minX), mapY(ticsY[i].doubleValue()), mapX(maxX), mapY(ticsY[i].doubleValue()));

                    // left side
                    text.setText(ticsY[i].toString());
                    textWidth = (int) text.getLayoutBounds().getWidth();
                    g2d.fillText(text.getText(),//
                            (float) mapX(minX) - textWidth + 5f,
                            (float) mapY(ticsY[i].doubleValue()) + verticalTextShift);

                    // right side
                    text.setText(ticsY[i].toString());
                    g2d.fillText(text.getText(),//
                            (float) mapX(maxX) + 5f,
                            (float) mapY(ticsY[i].doubleValue()) + verticalTextShift);
                }
            }
            // ticsX
            if (ticsX != null) {
                for (int i = 0; i < ticsX.length - 1; i++) {
                    try {
                        g2d.strokeLine(
                                mapX(ticsX[i].doubleValue()),
                                mapY(ticsY[0].doubleValue()),
                                mapX(ticsX[i].doubleValue()),
                                mapY(ticsY[0].doubleValue()) + 2);

                        // bottom
                        String xText = ticsX[i].toPlainString();
                        g2d.fillText(xText,
                                (float) mapX(ticsX[i].doubleValue()) - 5f,
                                (float) mapY(ticsY[0].doubleValue()) + 10);

                    } catch (Exception e) {
                    }
                }
            }
        }
    }
}