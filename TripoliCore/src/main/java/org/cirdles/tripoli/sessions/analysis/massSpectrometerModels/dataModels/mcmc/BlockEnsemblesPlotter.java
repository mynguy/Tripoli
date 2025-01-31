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

package org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.mcmc;

import com.google.common.collect.BiMap;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.cirdles.tripoli.plots.PlotBuilder;
import org.cirdles.tripoli.plots.histograms.HistogramBuilder;
import org.cirdles.tripoli.plots.histograms.RatioHistogramBuilder;
import org.cirdles.tripoli.plots.linePlots.MultiLinePlotBuilder;
import org.cirdles.tripoli.sessions.analysis.Analysis;
import org.cirdles.tripoli.sessions.analysis.AnalysisInterface;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.detectorSetups.Detector;
import org.cirdles.tripoli.sessions.analysis.methods.AnalysisMethod;
import org.cirdles.tripoli.species.IsotopicRatio;
import org.cirdles.tripoli.species.SpeciesRecordInterface;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PhysicalStore;
import org.ojalgo.matrix.store.Primitive64Store;

import java.util.List;

import static java.lang.StrictMath.exp;
import static org.cirdles.tripoli.constants.TripoliConstants.*;

/**
 * @author James F. Bowring
 */
public enum BlockEnsemblesPlotter {
    ;

    public synchronized static void blockEnsemblePlotEngine(
            int blockID,
            AnalysisInterface analysis) {

        PlotBuilder[][] plotBuilders = analysis.getMapOfBlockIdToPlots().get(blockID);
        int initialModelsBurnCount = ((Analysis) analysis).getMapOfBlockIdToModelsBurnCount().get(blockID);

        List<EnsemblesStore.EnsembleRecord> ensembleRecordsList = ((Analysis) analysis).getMapBlockIDToEnsembles().get(blockID);
        int countOfEnsemblesUsed = ensembleRecordsList.size() - initialModelsBurnCount;

        AnalysisMethod analysisMethod = analysis.getAnalysisMethod();
        SingleBlockRawDataSetRecord singleBlockRawDataSetRecord = analysis.getMapOfBlockIdToRawData().get(blockID);
        List<IsotopicRatio> isotopicRatioList = analysisMethod.getIsotopicRatiosList();

        /*
            %% Analysis and Plotting

            initialModelsBurnCount = 1000; % Number of models to discard
            ens_rat =[ensemble.lograt];

            % Calculate mean and st dev of ratios after initialModelsBurnCount in time
            ratmean = mean(ens_rat(:,initialModelsBurnCount:cnt),2);  % Log ratios
            ratstd = std(ens_rat(:,initialModelsBurnCount:cnt),[],2);

            BLmean = mean(ens_BL(:,initialModelsBurnCount:cnt),2);  % Baselines
            BLstd = std(ens_BL(:,initialModelsBurnCount:cnt),[],2);

            sigmean = mean(ens_sig(:,initialModelsBurnCount:cnt),2);   % Noise hyperparams
            sigstd = std(ens_sig(:,initialModelsBurnCount:cnt),[],2);

            DFmean = mean(ens_DF(:,initialModelsBurnCount:cnt),2);   % Daly-Far gain
            DFstd = std(ens_DF(:,initialModelsBurnCount:cnt),[],2);

         */

        // log ratios
        double[][] ensembleSetOfLogRatios = new double[isotopicRatioList.size()][countOfEnsemblesUsed];
        double[][] ensembleRatios = new double[isotopicRatioList.size()][countOfEnsemblesUsed];
        double[] logRatioMean = new double[isotopicRatioList.size()];
        double[] logRatioStdDev = new double[isotopicRatioList.size()];
        DescriptiveStatistics descriptiveStatisticsLogRatios = new DescriptiveStatistics();
        for (int ratioIndex = 0; ratioIndex < isotopicRatioList.size(); ratioIndex++) {
            for (int index = initialModelsBurnCount; index < countOfEnsemblesUsed + initialModelsBurnCount; index++) {
                ensembleSetOfLogRatios[ratioIndex][index - initialModelsBurnCount] = ensembleRecordsList.get(index).logRatios()[ratioIndex];
                descriptiveStatisticsLogRatios.addValue(ensembleSetOfLogRatios[ratioIndex][index - initialModelsBurnCount]);
                ensembleRatios[ratioIndex][index - initialModelsBurnCount] = exp(ensembleSetOfLogRatios[ratioIndex][index - initialModelsBurnCount]);
            }
            logRatioMean[ratioIndex] = descriptiveStatisticsLogRatios.getMean();
            logRatioStdDev[ratioIndex] = descriptiveStatisticsLogRatios.getStandardDeviation();

            isotopicRatioList.get(ratioIndex).setRatioValuesForBlockEnsembles(ensembleRatios[ratioIndex]);
            isotopicRatioList.get(ratioIndex).setLogRatioValuesForBlockEnsembles(ensembleSetOfLogRatios[ratioIndex]);
        }

        // derived ratios
        List<IsotopicRatio> derivedIsotopicRatiosList = analysisMethod.getDerivedIsotopicRatiosList();
        int countOfDerivedRatios = derivedIsotopicRatiosList.size();
        double[][] derivedEnsembleRatios = new double[countOfDerivedRatios][countOfEnsemblesUsed];
        double[][] derivedEnsembleLogRatios = new double[countOfDerivedRatios][countOfEnsemblesUsed];
        int derivedRatioIndex = 0;
        // derive the ratios
        for (IsotopicRatio isotopicRatio : derivedIsotopicRatiosList) {
            SpeciesRecordInterface numerator = isotopicRatio.getNumerator();
            SpeciesRecordInterface denominator = isotopicRatio.getDenominator();
            SpeciesRecordInterface highestAbundanceSpecies = analysisMethod.retrieveHighestAbundanceSpecies();
            if (numerator != highestAbundanceSpecies) {
                IsotopicRatio numeratorRatio = new IsotopicRatio(numerator, highestAbundanceSpecies, false);
                int indexNumeratorRatio = isotopicRatioList.indexOf(numeratorRatio);
                IsotopicRatio denominatorRatio = new IsotopicRatio(denominator, highestAbundanceSpecies, false);
                int indexDenominatorRatio = isotopicRatioList.indexOf(denominatorRatio);
                for (int ensembleIndex = 0; ensembleIndex < countOfEnsemblesUsed; ensembleIndex++) {
                    derivedEnsembleRatios[derivedRatioIndex][ensembleIndex] =
                            ensembleRatios[indexNumeratorRatio][ensembleIndex] / ensembleRatios[indexDenominatorRatio][ensembleIndex];
                    derivedEnsembleLogRatios[derivedRatioIndex][ensembleIndex] =
                            StrictMath.log(derivedEnsembleRatios[derivedRatioIndex][ensembleIndex]);
                }
            } else {
                // assume we are dealing with the inverses of isotopicRatiosList
                IsotopicRatio targetRatio = new IsotopicRatio(denominator, highestAbundanceSpecies, false);
                int indexOfTargetRatio = isotopicRatioList.indexOf(targetRatio);
                for (int ensembleIndex = 0; ensembleIndex < countOfEnsemblesUsed; ensembleIndex++) {
                    derivedEnsembleRatios[derivedRatioIndex][ensembleIndex] =
                            1.0 / ensembleRatios[indexOfTargetRatio][ensembleIndex];
                    derivedEnsembleLogRatios[derivedRatioIndex][ensembleIndex] =
                            StrictMath.log(derivedEnsembleRatios[derivedRatioIndex][ensembleIndex]);
                }
            }
            derivedIsotopicRatiosList.get(derivedRatioIndex).setRatioValuesForBlockEnsembles(derivedEnsembleRatios[derivedRatioIndex]);
            derivedIsotopicRatiosList.get(derivedRatioIndex).setLogRatioValuesForBlockEnsembles(derivedEnsembleLogRatios[derivedRatioIndex]);
            derivedRatioIndex++;
        }


        // baseLines
        int baselineSize = analysisMethod.getSequenceTable().findFaradayDetectorsUsed().size();
        double[][] ensembleBaselines = new double[baselineSize][countOfEnsemblesUsed];

        for (int row = 0; row < baselineSize; row++) {
            DescriptiveStatistics descriptiveStatisticsBaselines = new DescriptiveStatistics();
            for (int index = initialModelsBurnCount; index < countOfEnsemblesUsed + initialModelsBurnCount; index++) {
                // todo: fix magic number
                ensembleBaselines[row][index - initialModelsBurnCount] = ensembleRecordsList.get(index).baseLine()[row];//TODO: Decide / 6.24e7 * 1e6;
                descriptiveStatisticsBaselines.addValue(ensembleBaselines[row][index - initialModelsBurnCount]);
            }
        }

        // dalyFaraday gains
        double[] ensembleDalyFaradayGain = new double[countOfEnsemblesUsed];
        DescriptiveStatistics descriptiveStatisticsDalyFaradayGain = new DescriptiveStatistics();
        for (int index = initialModelsBurnCount; index < countOfEnsemblesUsed + initialModelsBurnCount; index++) {
            ensembleDalyFaradayGain[index - initialModelsBurnCount] = ensembleRecordsList.get(index).dfGain();
            descriptiveStatisticsDalyFaradayGain.addValue(ensembleDalyFaradayGain[index - initialModelsBurnCount]);
        }
        double dalyFaradayGainMean = descriptiveStatisticsDalyFaradayGain.getMean();

        /*
            for m=1:d0.Nblock
                for n = 1:cnt;
                    ens_I{m}(:,n) =[ensemble(n).I{m}];
                end
                Imean{m} = mean(ens_I{m}(:,initialModelsBurnCount:cnt),2);
                Istd{m} = std(ens_I{m}(:,initialModelsBurnCount:cnt),[],2);
            end
         */

        // Intensity
        int knotsCount = ensembleRecordsList.get(0).I0().length;
        double[][] ensembleIntensity = new double[knotsCount][countOfEnsemblesUsed];
        double[] intensityMeans = new double[knotsCount];
        double[] intensityStdDevs = new double[knotsCount];

        for (int knotIndex = 0; knotIndex < knotsCount; knotIndex++) {
            DescriptiveStatistics descriptiveStatisticsIntensity = new DescriptiveStatistics();
            for (int index = initialModelsBurnCount; index < countOfEnsemblesUsed + initialModelsBurnCount; index++) {
                ensembleIntensity[knotIndex][index - initialModelsBurnCount] = ensembleRecordsList.get(index).I0()[knotIndex];
                descriptiveStatisticsIntensity.addValue(ensembleIntensity[knotIndex][index - initialModelsBurnCount]);
            }
            intensityMeans[knotIndex] = descriptiveStatisticsIntensity.getMean();
            intensityStdDevs[knotIndex] = descriptiveStatisticsIntensity.getStandardDeviation();
        }

        // calculate mean Intensities and knots for plotting
        double[][] yDataIntensityMeans = new double[2][];
        PhysicalStore.Factory<Double, Primitive64Store> storeFactory = Primitive64Store.FACTORY;
        MatrixStore<Double> intensityMeansMatrix = storeFactory.columns(intensityMeans);

        double[][] blockKnotInterpolationStoreArray = singleBlockRawDataSetRecord.blockKnotInterpolationArray();
        Primitive64Store blockKnotInterpolationStore = Primitive64Store.FACTORY.rows(blockKnotInterpolationStoreArray);
        MatrixStore<Double> yDataMeanIntensitiesMatrix =
                blockKnotInterpolationStore.multiply(intensityMeansMatrix).multiply(1.0 / dalyFaradayGainMean);//(1.0 / (dalyFaradayGainMean * 6.24e7)) * 1e6);
        yDataIntensityMeans[0] = yDataMeanIntensitiesMatrix.toRawCopy1D();
        MatrixStore<Double> yDataTrueIntensitiesMatrix = intensityMeansMatrix.multiply(1.0 / dalyFaradayGainMean);//(1.0 / (dalyFaradayGainMean * 6.24e7)) * 1e6);
        yDataIntensityMeans[1] = yDataTrueIntensitiesMatrix.toRawCopy1D();

        double[][] xDataIntensityMeans = new double[2][];
        int xDataSize = yDataIntensityMeans[0].length;
        xDataIntensityMeans[0] = new double[xDataSize];
        for (int i = 0; i < xDataSize; i++) {
            xDataIntensityMeans[0][i] = i;
        }
        int xKnotsSize = singleBlockRawDataSetRecord.onPeakStartingIndicesOfCycles().length;
        xDataIntensityMeans[1] = new double[xKnotsSize];
        for (int i = 0; i < xKnotsSize; i++) {
            xDataIntensityMeans[1][i] = singleBlockRawDataSetRecord.onPeakStartingIndicesOfCycles()[i];
        }

        // visualization - Ensembles tab

        BiMap<IsotopicRatio, IsotopicRatio> biMapOfRatiosAndInverses = analysisMethod.getBiMapOfRatiosAndInverses();
        plotBuilders[PLOT_INDEX_RATIOS] = new PlotBuilder[ensembleRatios.length + derivedEnsembleRatios.length];
        for (int i = 0; i < ensembleRatios.length; i++) {
            plotBuilders[PLOT_INDEX_RATIOS][i] =
                    RatioHistogramBuilder.initializeRatioHistogram(
                            blockID,
                            isotopicRatioList.get(i),
                            biMapOfRatiosAndInverses.get(isotopicRatioList.get(i)),
                            25);
            analysisMethod.getMapOfRatioNamesToInvertedFlag().put(isotopicRatioList.get(i).prettyPrint(), false);
        }
        for (int i = 0; i < derivedEnsembleRatios.length; i++) {
            plotBuilders[PLOT_INDEX_RATIOS][i + ensembleRatios.length] =
                    RatioHistogramBuilder.initializeRatioHistogram(
                            blockID,
                            derivedIsotopicRatiosList.get(i),
                            (null != biMapOfRatiosAndInverses.get(derivedIsotopicRatiosList.get(i))) ?
                                    (biMapOfRatiosAndInverses.get(derivedIsotopicRatiosList.get(i))) :
                                    (biMapOfRatiosAndInverses.inverse().get(derivedIsotopicRatiosList.get(i))),
                            25);
            analysisMethod.getMapOfRatioNamesToInvertedFlag().put(derivedIsotopicRatiosList.get(i).prettyPrint(), false);
        }

        plotBuilders[PLOT_INDEX_BASELINES] = new PlotBuilder[ensembleBaselines.length];
        List<Detector> faradayDetectorsUsed = analysisMethod.getSequenceTable().findFaradayDetectorsUsed();
        for (int i = 0; i < ensembleBaselines.length; i++) {
            plotBuilders[PLOT_INDEX_BASELINES][i] = HistogramBuilder.initializeHistogram(blockID, ensembleBaselines[i],
                    25, new String[]{faradayDetectorsUsed.get(i).getDetectorName() + " Baseline"}, "Baseline Counts", "Frequency", true);
        }

        plotBuilders[PLOT_INDEX_DFGAINS][0] = HistogramBuilder.initializeHistogram(blockID, ensembleDalyFaradayGain,
                25, new String[]{"Daly/Faraday Gain"}, "Gain", "Frequency", true);

        plotBuilders[PLOT_INDEX_MEANINTENSITIES][0] = MultiLinePlotBuilder.initializeLinePlot(
                xDataIntensityMeans, yDataIntensityMeans, new String[]{"Mean Intensity w/ Knots"}, "Time Index", "Intensity (counts)", true, blockID);
    }

}