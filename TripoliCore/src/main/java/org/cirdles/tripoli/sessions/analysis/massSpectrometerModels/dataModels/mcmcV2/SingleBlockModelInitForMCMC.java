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

package org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.mcmcV2;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.ojalgo.RecoverableCondition;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PhysicalStore;
import org.ojalgo.matrix.store.Primitive64Store;
import org.ojalgo.matrix.task.InverterTask;

import java.io.Serializable;
import java.util.*;

import static java.lang.StrictMath.exp;
import static java.lang.StrictMath.log;

/**
 * @author James F. Bowring
 */
enum SingleBlockModelInitForMCMC {
    ;

    static SingleBlockModelRecord initializeModelForSingleBlockMCMC(SingleBlockDataSetRecord singleBlockDataSetRecord) throws RecoverableCondition {

        int baselineCount = singleBlockDataSetRecord.baselineDataSetMCMC().intensityAccumulatorList().size();
        int onPeakFaradayCount = singleBlockDataSetRecord.onPeakFaradayDataSetMCMC().intensityAccumulatorList().size();
        int onPeakPhotoMultCount = singleBlockDataSetRecord.onPeakPhotoMultiplierDataSetMCMC().intensityAccumulatorList().size();
        int totalIntensityCount = baselineCount + onPeakFaradayCount + onPeakPhotoMultCount;

        // Baseline statistics *****************************************************************************************
        /*
            for m=1:d0.Nfar%+1
                x0.BL(m,1) = mean(d0.data(d0.blflag & d0.det_ind(:,m)));
                x0.BLstd(m,1) = std(d0.data(d0.blflag & d0.det_ind(:,m)));
            end
         */
        SingleBlockDataSetRecord.SingleBlockDataRecord baselineDataSetMCMC = singleBlockDataSetRecord.baselineDataSetMCMC();
        List<Integer> detectorOrdinalIndicesAccumulatorList = baselineDataSetMCMC.detectorOrdinalIndicesAccumulatorList();
        List<Double> intensityAccumulatorList = baselineDataSetMCMC.intensityAccumulatorList();
        Map<Integer, DescriptiveStatistics> mapFaradayDetectorIndicesToStatistics = new TreeMap<>();
        Map<Integer, Integer> mapDetectorOrdinalToFaradayIndex = new TreeMap<>();

        int intensityIndex = 0;
        for (Integer detectorOrdinalIndex : detectorOrdinalIndicesAccumulatorList) {
            if (!mapFaradayDetectorIndicesToStatistics.containsKey(detectorOrdinalIndex)) {
                mapFaradayDetectorIndicesToStatistics.put(detectorOrdinalIndex, new DescriptiveStatistics());
            }
            mapFaradayDetectorIndicesToStatistics.get(detectorOrdinalIndex).addValue(intensityAccumulatorList.get(intensityIndex));
            intensityIndex++;
        }

        double[] baselineMeansArray = new double[mapFaradayDetectorIndicesToStatistics.keySet().size()];
        double[] baselineStandardDeviationsArray = new double[baselineMeansArray.length];
        int faradayIndex = 0;
        for (Integer detectorOrdinalIndex : mapFaradayDetectorIndicesToStatistics.keySet()) {
            baselineMeansArray[faradayIndex] = mapFaradayDetectorIndicesToStatistics.get(detectorOrdinalIndex).getMean();
            baselineStandardDeviationsArray[faradayIndex] = mapFaradayDetectorIndicesToStatistics.get(detectorOrdinalIndex).getStandardDeviation();
            mapDetectorOrdinalToFaradayIndex.put(detectorOrdinalIndex, faradayIndex);
            faradayIndex++;
        }

        // OnPeak statistics by faraday ********************************************************************************
        /*
        for m=1:d0.Niso;
            tmpCounts(m,1) = mean(d0.data( (d0.iso_ind(:,m) & d0.axflag)));

            itmp = (d0.iso_ind(:,m) & ~d0.axflag);
            tmpFar(m,1)  = mean(d0.data(itmp)-x0.BL(d0.det_vec(itmp)));
        end
         */
        SingleBlockDataSetRecord.SingleBlockDataRecord onPeakFaradayDataSetMCMC = singleBlockDataSetRecord.onPeakFaradayDataSetMCMC();
        List<Integer> isotopeOrdinalIndicesAccumulatorList = onPeakFaradayDataSetMCMC.isotopeOrdinalIndicesAccumulatorList();
        detectorOrdinalIndicesAccumulatorList = onPeakFaradayDataSetMCMC.detectorOrdinalIndicesAccumulatorList();
        intensityAccumulatorList = onPeakFaradayDataSetMCMC.intensityAccumulatorList();
        Map<Integer, DescriptiveStatistics> mapFaradayIsotopeIndicesToStatistics = new TreeMap<>();

        intensityIndex = 0;
        for (Integer isotopeOrdinalIndex : isotopeOrdinalIndicesAccumulatorList) {
            if (!mapFaradayIsotopeIndicesToStatistics.containsKey(isotopeOrdinalIndex)) {
                mapFaradayIsotopeIndicesToStatistics.put(isotopeOrdinalIndex, new DescriptiveStatistics());
            }
            mapFaradayIsotopeIndicesToStatistics.get(isotopeOrdinalIndex).addValue(
                    intensityAccumulatorList.get(intensityIndex)
                            - baselineMeansArray[mapDetectorOrdinalToFaradayIndex.get(detectorOrdinalIndicesAccumulatorList.get(intensityIndex))]);
            intensityIndex++;
        }

        double[] faradayMeansArray = new double[mapFaradayIsotopeIndicesToStatistics.keySet().size()];
        int isotopeIndex = 0;
        for (Integer isotopeOrdinalIndex : mapFaradayIsotopeIndicesToStatistics.keySet()) {
            faradayMeansArray[isotopeIndex] = mapFaradayIsotopeIndicesToStatistics.get(isotopeOrdinalIndex).getMean();
            isotopeIndex++;
        }

        // OnPeak statistics by photomultiplier ************************************************************************
        SingleBlockDataSetRecord.SingleBlockDataRecord onPeakPhotoMultiplierDataSetMCMC = singleBlockDataSetRecord.onPeakPhotoMultiplierDataSetMCMC();
        isotopeOrdinalIndicesAccumulatorList = onPeakPhotoMultiplierDataSetMCMC.isotopeOrdinalIndicesAccumulatorList();
        intensityAccumulatorList = onPeakPhotoMultiplierDataSetMCMC.intensityAccumulatorList();
        Map<Integer, DescriptiveStatistics> mapPhotoMultiplierIsotopeIndicesToStatistics = new TreeMap<>();

        intensityIndex = 0;
        for (Integer isotopeOrdinalIndex : isotopeOrdinalIndicesAccumulatorList) {
            if (!mapPhotoMultiplierIsotopeIndicesToStatistics.containsKey(isotopeOrdinalIndex)) {
                mapPhotoMultiplierIsotopeIndicesToStatistics.put(isotopeOrdinalIndex, new DescriptiveStatistics());
            }
            mapPhotoMultiplierIsotopeIndicesToStatistics.get(isotopeOrdinalIndex).addValue(
                    intensityAccumulatorList.get(intensityIndex));
            intensityIndex++;
        }

        double[] photoMultiplierMeansArray = new double[mapPhotoMultiplierIsotopeIndicesToStatistics.keySet().size()];
        isotopeIndex = 0;
        for (Integer isotopeOrdinalIndex : mapPhotoMultiplierIsotopeIndicesToStatistics.keySet()) {
            photoMultiplierMeansArray[isotopeIndex] = mapPhotoMultiplierIsotopeIndicesToStatistics.get(isotopeOrdinalIndex).getMean();
            isotopeIndex++;
        }

        /*
            [~,imaxC] = max(tmpCounts);
            iden = d0.Niso;
            x0.DFgain = tmpCounts(imaxC)/tmpFar(imaxC);
            for m=1:d0.Niso
                x0.lograt(m,1) = log(tmpCounts(m)/tmpCounts(iden));
            end
        */
        // find index of photoMultiplierMeansArray max value
        int maxCountIndex = -1;
        double maxCountsMean = Double.MIN_VALUE;
        for (int i = 0; i < photoMultiplierMeansArray.length; i++) {
            if (photoMultiplierMeansArray[i] > maxCountsMean) {
                maxCountsMean = photoMultiplierMeansArray[i];
                maxCountIndex = i;
            }
        }

        // NOTE: the speciesList has been sorted by increasing abundances in the original analysisMethod setup
        //  the ratios are between each species and the most abundant species, with one less ratio than species
        int indexOfMostAbundantIsotope = mapPhotoMultiplierIsotopeIndicesToStatistics.size() - 1;
        double detectorFaradayGain = photoMultiplierMeansArray[maxCountIndex] / faradayMeansArray[maxCountIndex];
        double[] logRatios = new double[mapPhotoMultiplierIsotopeIndicesToStatistics.size() - 1];
        for (int logRatioIndex = 0; logRatioIndex < logRatios.length; logRatioIndex++) {
            logRatios[logRatioIndex] = log(photoMultiplierMeansArray[logRatioIndex] / photoMultiplierMeansArray[indexOfMostAbundantIsotope]);
        }


        /*
        for m=1:d0.Nblock
            II = d0.InterpMat{m};
            dind = ( d0.axflag & d0.block(:,m));
            dd=d0.data(dind)./exp(x0.lograt(d0.iso_vec(dind)));
            [~,dsort]=sort(d0.time_ind(dind));
            dd=dd(dsort);
            I0=(II'*II)^-1*II'*dd;
            x0.I{m} = I0;
        end
         */

        List<Double> dd = new ArrayList<>();
        // NOTE: using the photomultiplier values as set above
        for (int row = 0; row < intensityAccumulatorList.size(); row++) {
            if (isotopeOrdinalIndicesAccumulatorList.get(row) - 1 < logRatios.length) {
                dd.add(intensityAccumulatorList.get(row)
                        / exp(logRatios[isotopeOrdinalIndicesAccumulatorList.get(row) - 1]));
            } else {
                // this used to be the iden/iden ratio, which we eliminated, was 1.0 anyway
                dd.add(intensityAccumulatorList.get(row));
            }
        }
        double[] ddArray = dd.stream().mapToDouble(d -> d).toArray();

        // get indices used in sorting per Matlab [~,dsort]=sort(d0.time_ind(dind));
        double[] timeIndForSortingArray = onPeakPhotoMultiplierDataSetMCMC.timeIndexAccumulatorList().stream().mapToDouble(d -> d).toArray();
        ArrayIndexComparator comparator = new ArrayIndexComparator(timeIndForSortingArray);
        Integer[] dsortIndices = comparator.createIndexArray();
        Arrays.sort(dsortIndices, comparator);

        double[] ddSortedArray = new double[ddArray.length];
        for (int i = 0; i < ddArray.length; i++) {
            ddSortedArray[i] = ddArray[dsortIndices[i]];
        }

        PhysicalStore.Factory<Double, Primitive64Store> storeFactory = Primitive64Store.FACTORY;
        MatrixStore<Double> interpolatedKnotData = singleBlockDataSetRecord.blockKnotInterpolationStore();
        MatrixStore<Double> ddMatrix = storeFactory.columns(ddSortedArray);
        MatrixStore<Double> tempMatrix = interpolatedKnotData.transpose().multiply(interpolatedKnotData);
        InverterTask<Double> inverter = InverterTask.PRIMITIVE.make(tempMatrix, false, false);
        MatrixStore<Double> tempMatrix2 = inverter.invert(tempMatrix);
        double[] I0 = tempMatrix2.multiply(interpolatedKnotData.transpose()).multiply(ddMatrix).toRawCopy1D();

        /*
            %%% MODEL DATA WITH INITIAL MODEL
            II = d0.InterpMat;

            for m=1:d0.Nfar%+1
                d(d0.blflag & d0.det_ind(:,m),1) = x0.BL(m);  blMeansArray
            end

            for n = 1:d0.Nblock
                Intensity{n} = II{n}*x0.I{n};
                for m=1:d0.Niso;
                    itmp = d0.iso_ind(:,m) & d0.axflag & d0.block(:,n);
                    d(itmp) = exp(x0.lograt(m))*Intensity{n}(d0.time_ind(itmp));

                    itmp = d0.iso_ind(:,m) & ~d0.axflag & d0.block(:,n);
                    d(itmp) = exp(x0.lograt(m))*x0.DFgain^-1 *Intensity{n}(d0.time_ind(itmp)) + x0.BL(d0.det_vec(itmp));
                end
            end
         */

        // initialize model data vector
        double[] dataArray = new double[totalIntensityCount];

        // populate dataArray with baseline entries
        detectorOrdinalIndicesAccumulatorList = baselineDataSetMCMC.detectorOrdinalIndicesAccumulatorList();
        for (int dataArrayIndex = 0; dataArrayIndex < baselineCount; dataArrayIndex++) {
            faradayIndex = mapDetectorOrdinalToFaradayIndex.get(detectorOrdinalIndicesAccumulatorList.get(dataArrayIndex));
            dataArray[dataArrayIndex] = baselineMeansArray[faradayIndex];
        }

        MatrixStore<Double> intensities = singleBlockDataSetRecord.blockKnotInterpolationStore().multiply(storeFactory.columns(I0));

        // populate dataArray with onpeak faraday entries
        isotopeOrdinalIndicesAccumulatorList = onPeakFaradayDataSetMCMC.isotopeOrdinalIndicesAccumulatorList();
        detectorOrdinalIndicesAccumulatorList = onPeakFaradayDataSetMCMC.detectorOrdinalIndicesAccumulatorList();
        List<Integer> timeIndexAccumulatorList = onPeakFaradayDataSetMCMC.timeIndexAccumulatorList();
        for (int dataArrayIndex = baselineCount; dataArrayIndex < baselineCount + onPeakFaradayCount; dataArrayIndex++) {
            int sourceIndex = timeIndexAccumulatorList.get(dataArrayIndex - baselineCount);
            isotopeIndex = isotopeOrdinalIndicesAccumulatorList.get(sourceIndex) - 1;
            faradayIndex = mapDetectorOrdinalToFaradayIndex.get(detectorOrdinalIndicesAccumulatorList.get(sourceIndex));
            if (isotopeIndex < logRatios.length) {
                dataArray[dataArrayIndex] = exp(logRatios[isotopeIndex]) / detectorFaradayGain * intensities.get(sourceIndex, 0) + baselineMeansArray[faradayIndex];
            } else {
                dataArray[dataArrayIndex] = 1.0 / detectorFaradayGain * intensities.get(sourceIndex, 0) + baselineMeansArray[faradayIndex];
            }
        }

        // populate dataArray with onpeak photomultiplier entries
        isotopeOrdinalIndicesAccumulatorList = onPeakPhotoMultiplierDataSetMCMC.isotopeOrdinalIndicesAccumulatorList();
        timeIndexAccumulatorList = onPeakPhotoMultiplierDataSetMCMC.timeIndexAccumulatorList();
        for (int dataArrayIndex = baselineCount + onPeakFaradayCount; dataArrayIndex < baselineCount + onPeakFaradayCount + onPeakPhotoMultCount; dataArrayIndex++) {
            int sourceIndex = timeIndexAccumulatorList.get(dataArrayIndex - baselineCount - onPeakFaradayCount);
            isotopeIndex = isotopeOrdinalIndicesAccumulatorList.get(sourceIndex) - 1;
            if (isotopeIndex < logRatios.length) {
                dataArray[dataArrayIndex] = exp(logRatios[isotopeIndex]) * intensities.get(sourceIndex, 0);
            } else {
                dataArray[dataArrayIndex] = intensities.get(sourceIndex, 0);
            }
        }

        /*
            % Define initial sigmas based on baseline
            for m = 1:d0.Nfar%+1
                itmp = d0.det_vec==m & d0.blflag==1;
                x0.sig(m,1) = 1*std(d0.data(itmp));
            end

            x0.sig(d0.Nfar+1,1) = 0;

            for m = 1: d0.Niso;
                itmp = d0.iso_vec==m ;
                x0.sig(d0.Ndet + m,1) = 1.1*10;
            end
         */

        int faradayCount = mapDetectorOrdinalToFaradayIndex.size();
        int isotopeCount = logRatios.length + 1;
        double[] signalNoise = new double[faradayCount + 1 + isotopeCount];
        for (faradayIndex = 0; faradayIndex < faradayCount; faradayIndex++) {
            signalNoise[faradayIndex] = baselineStandardDeviationsArray[faradayIndex];
        }
        // photomultiplier
        signalNoise[faradayIndex + 1] = 0.0;

        for (isotopeIndex = 0; isotopeIndex < isotopeCount; isotopeIndex++) {
            signalNoise[faradayCount + 1 + isotopeIndex] = 11.0;
        }

        return new SingleBlockModelRecord(
                baselineMeansArray,
                baselineStandardDeviationsArray,
                detectorFaradayGain,
                logRatios,
                signalNoise,
                dataArray,
                I0,
                intensities);
    }

    private static class ArrayIndexComparator implements Comparator<Integer>, Serializable {
        private final double[] array;

        public ArrayIndexComparator(double[] array) {
            this.array = array;
        }

        public Integer[] createIndexArray() {
            Integer[] indexes = new Integer[array.length];
            for (int i = 0; i < array.length; i++) {
                indexes[i] = i; // Autoboxing
            }
            return indexes;
        }

        @Override
        public int compare(Integer index1, Integer index2) {
            return Double.compare(array[index1], array[index2]);
        }
    }
}