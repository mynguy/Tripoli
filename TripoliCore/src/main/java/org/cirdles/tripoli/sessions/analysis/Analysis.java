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

package org.cirdles.tripoli.sessions.analysis;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.cirdles.tripoli.constants.MassSpectrometerContextEnum;
import org.cirdles.tripoli.plots.PlotBuilder;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.mcmc.*;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.peakShapes.SingleBlockPeakDriver;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataSourceProcessors.MassSpecExtractedData;
import org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.detectorSetups.DetectorSetupBuiltinModelFactory;
import org.cirdles.tripoli.sessions.analysis.methods.AnalysisMethod;
import org.cirdles.tripoli.sessions.analysis.methods.AnalysisMethodBuiltinFactory;
import org.cirdles.tripoli.sessions.analysis.methods.machineMethods.phoenixMassSpec.PhoenixAnalysisMethod;
import org.cirdles.tripoli.utilities.IntuitiveStringComparator;
import org.cirdles.tripoli.utilities.callbacks.LoggingCallbackInterface;
import org.cirdles.tripoli.utilities.exceptions.TripoliException;
import org.cirdles.tripoli.utilities.stateUtilities.TripoliPersistentState;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.cirdles.tripoli.constants.MassSpectrometerContextEnum.PHOENIX_SYNTHETIC;
import static org.cirdles.tripoli.constants.MassSpectrometerContextEnum.UNKNOWN;
import static org.cirdles.tripoli.constants.TripoliConstants.*;
import static org.cirdles.tripoli.sessions.analysis.methods.AnalysisMethodBuiltinFactory.BURDICK_BL_SYNTHETIC_DATA;
import static org.cirdles.tripoli.sessions.analysis.methods.AnalysisMethodBuiltinFactory.KU_204_5_6_7_8_DALY_ALL_FARADAY_PB;

/**
 * @author James F. Bowring
 */
public class Analysis implements Serializable, AnalysisInterface {
    public static final int SKIP = -1;
    public static final int SHOW = 0;
    public static final int RUN = 1;
    @Serial
    private static final long serialVersionUID = 5737165372498262402L;


    private final Map<Integer, PlotBuilder[][]> mapOfBlockIdToPlots = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<Integer, PlotBuilder[]> mapOfBlockIdToPeakPlots = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<Integer, String> mapOfBlockToLogs = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<Integer, Integer> mapOfBlockIdToProcessStatus = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<Integer, List<File>> blockPeakGroups = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<Integer, Integer> mapOfBlockIdToModelsBurnCount = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<Integer, List<EnsemblesStore.EnsembleRecord>> mapBlockIDToEnsembles = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<Integer, SingleBlockRawDataSetRecord> mapOfBlockIdToRawData = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<Integer, SingleBlockModelRecord> mapOfBlockIdToFinalModel = Collections.synchronizedSortedMap(new TreeMap<>());
    private String analysisName;
    private String analystName;
    private String labName;
    private AnalysisMethod analysisMethod;
    private String analysisSampleName;
    private String analysisSampleDescription;
    // note: Path is not serializable
    private String dataFilePathString;
    private MassSpecExtractedData massSpecExtractedData;
    private boolean mutable;

    private Analysis() {
    }

    protected Analysis(String analysisName, AnalysisMethod analysisMethod, String analysisSampleName) {
        this.analysisName = analysisName;
        this.analysisMethod = analysisMethod;
        this.analysisSampleName = analysisSampleName;
        analystName = MISSING_STRING_FIELD;
        labName = MISSING_STRING_FIELD;
        analysisSampleDescription = MISSING_STRING_FIELD;
        dataFilePathString = MISSING_STRING_FIELD;
        massSpecExtractedData = new MassSpecExtractedData();
        mutable = true;
    }

    public Map<Integer, List<EnsemblesStore.EnsembleRecord>> getMapBlockIDToEnsembles() {
        return mapBlockIDToEnsembles;
    }

    public Map<Integer, Integer> getMapOfBlockIdToModelsBurnCount() {
        return mapOfBlockIdToModelsBurnCount;
    }

    public void extractMassSpecDataFromPath(Path dataFilePath)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException, JAXBException, TripoliException {
        dataFilePathString = dataFilePath.toString();
        MassSpectrometerContextEnum massSpectrometerContext = AnalysisInterface.determineMassSpectrometerContextFromDataFile(dataFilePath);
        if (0 != massSpectrometerContext.compareTo(UNKNOWN)) {
            Class<?> clazz = massSpectrometerContext.getClazz();
            Method method = clazz.getMethod(massSpectrometerContext.getMethodName(), Path.class);
            massSpecExtractedData = (MassSpecExtractedData) method.invoke(null, dataFilePath);
        } else {
            massSpecExtractedData = new MassSpecExtractedData();
        }
        massSpecExtractedData.setMassSpectrometerContext(massSpectrometerContext);

        // TODO: remove this temp hack for synthetic demos
        if (0 == massSpectrometerContext.compareTo(PHOENIX_SYNTHETIC)) {
            massSpecExtractedData.setDetectorSetup(DetectorSetupBuiltinModelFactory.detectorSetupBuiltinMap.get(PHOENIX_SYNTHETIC.getName()));
            if (massSpecExtractedData.getHeader().methodName().toUpperCase(Locale.ROOT).contains("SYNTHETIC")) {
                analysisMethod = AnalysisMethodBuiltinFactory.analysisMethodsBuiltinMap.get(BURDICK_BL_SYNTHETIC_DATA);
            } else {
                analysisMethod = AnalysisMethodBuiltinFactory.analysisMethodsBuiltinMap.get(KU_204_5_6_7_8_DALY_ALL_FARADAY_PB);
            }
            // initialize block processing state - see parallel below
            for (Integer blockID : massSpecExtractedData.getBlocksData().keySet()) {
                mapOfBlockIdToProcessStatus.put(blockID, RUN);
                mapOfBlockIdToModelsBurnCount.put(blockID, 0);
            }
        } else {
            // attempt to load specified method
            File selectedMethodFile = new File((Path.of(dataFilePathString).getParent().getParent().toString()
                    + File.separator + "Methods" + File.separator + massSpecExtractedData.getHeader().methodName()).toLowerCase(Locale.getDefault()));
            File getPeakCentresFolder = new File((Path.of(dataFilePathString).getParent().toString()
                    + File.separator + "PeakCentres"));
            if (selectedMethodFile.exists()) {
                analysisMethod = extractAnalysisMethodfromPath(Path.of(selectedMethodFile.toURI()));
                TripoliPersistentState.getExistingPersistentState().setMRUMethodXMLFolderPath(selectedMethodFile.getParent());
            } else {
                throw new TripoliException(
                        "Method File not found: " + massSpecExtractedData.getHeader().methodName()
                                + "\n\n at location: " + Path.of(dataFilePathString).getParent().getParent().toString() + File.separator + "Methods");
            }

            // initialize block processing state
            for (Integer blockID : massSpecExtractedData.getBlocksData().keySet()) {
                mapOfBlockIdToProcessStatus.put(blockID, RUN);
                mapOfBlockIdToModelsBurnCount.put(blockID, 0);
            }

            // collects the file objects from PeakCentres folder +++++++++++++++++++++++++++++++++++++++++++++++++++++++
            List<File> fileList = new ArrayList<>();
            if (getPeakCentresFolder.exists() && getPeakCentresFolder.isDirectory()) {
                File[] peakCentreFiles = getPeakCentresFolder.listFiles();
                Pattern p = Pattern.compile("^(.*?)\\.TXT$");
                for (File file : peakCentreFiles) {
                    Matcher m = p.matcher(file.getName());
                    if (m.matches()) {
                        fileList.add(file);
                    }
                }

                IntuitiveStringComparator<String> intuitiveStringComparator = new IntuitiveStringComparator<>();
                fileList.sort((file1, file2) -> intuitiveStringComparator.compare(file1.getName(), file2.getName()));
                if (0 < blockPeakGroups.size()) {
                    for (Integer blockID : blockPeakGroups.keySet()) {
                        blockPeakGroups.get(blockID).clear();
                    }
                }

                // groups isotopic files that are in the same block
                if (!fileList.isEmpty()) {
                    File[] files = fileList.toArray(new File[0]);

                    p = Pattern.compile("-S(.*?)C1");

                    for (File file : files) {

                        Matcher groupMatch = p.matcher(file.getName());
                        if (groupMatch.find()) {
                            int value = Integer.parseInt(groupMatch.group(1).substring(2));
                            if (blockPeakGroups.containsKey(value)) {
                                blockPeakGroups.get(value).add(file);
                            } else {
                                blockPeakGroups.put(value, new ArrayList<>());
                                blockPeakGroups.get(value).add(file);
                            }
                        }
                    }

                    for (Map.Entry<Integer, List<File>> entry : blockPeakGroups.entrySet()) {
                        List<File> peakFile = entry.getValue();
                        peakFile.sort((file1, file2) -> intuitiveStringComparator.compare(file1.getName(), file2.getName()));
                    }
                }
            } else {
                throw new TripoliException(
                        "PeakCentres folder not found at location: " + Path.of(dataFilePathString).getParent().toString() + File.separator + "PeakCentres");
            }
        }
    }

    public AnalysisMethod extractAnalysisMethodfromPath(Path phoenixAnalysisMethodDataFilePath) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(PhoenixAnalysisMethod.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        PhoenixAnalysisMethod phoenixAnalysisMethod = (PhoenixAnalysisMethod) jaxbUnmarshaller.unmarshal(phoenixAnalysisMethodDataFilePath.toFile());
        return AnalysisMethod.createAnalysisMethodFromPhoenixAnalysisMethod(phoenixAnalysisMethod, massSpecExtractedData.getDetectorSetup(), massSpecExtractedData.getMassSpectrometerContext());
    }


    public PlotBuilder[][] updatePlotsByBlock(int blockID, LoggingCallbackInterface loggingCallback) throws TripoliException {
        PlotBuilder[][] retVal = new PlotBuilder[0][];
        if (RUN == mapOfBlockIdToProcessStatus.get(blockID)) {
            mapOfBlockIdToPlots.remove(blockID);
        }
        if (mapOfBlockIdToPlots.containsKey(blockID)) {
            retVal = mapOfBlockIdToPlots.get(blockID);
            loggingCallback.receiveLoggingSnippet("1000 >%");
        } else {
            try {
                PlotBuilder[][] plotBuilders;
                plotBuilders = SingleBlockModelDriver.buildAndRunModelForSingleBlock(blockID, this, loggingCallback);
                mapOfBlockIdToPlots.put(blockID, plotBuilders);
                mapOfBlockIdToProcessStatus.put(blockID, SHOW);
                retVal = mapOfBlockIdToPlots.get(blockID);
            } catch (IOException e) {
                System.out.println("PROBLEM EXPORTING ENSEMBLES");
            }
        }
        return retVal;
    }

    public void updateShadeWidthsForConvergenceLinePlots(int blockID, double shadeWidth) {
        // PlotBuilder indices for convergence LinePlotBuilders = 5,6,8,9
        // TODO: make these indices into constants
        // PlotBuilder indices for convergence MultiLinePlotBuilders = 10
        PlotBuilder[][] plotBuilders = mapOfBlockIdToPlots.get(blockID);
        if (plotBuilders != null) {
            updatePlotBuildersWithShades(plotBuilders[5], shadeWidth);
            updatePlotBuildersWithShades(plotBuilders[6], shadeWidth);
            updatePlotBuildersWithShades(plotBuilders[8], shadeWidth);
            updatePlotBuildersWithShades(plotBuilders[9], shadeWidth);
            updatePlotBuildersWithShades(plotBuilders[10], shadeWidth);
        }
    }

    private void updatePlotBuildersWithShades(PlotBuilder[] linePlotBuilders, double shadeWidth) {
        for (int i = 0; i < linePlotBuilders.length; i++) {
            linePlotBuilders[i].setShadeWidthForModelConvergence(shadeWidth);
        }
    }

    @Override
    public PlotBuilder[] updatePeakPlotsByBlock(int blockID) throws TripoliException {
        PlotBuilder[] retVal;
        if (RUN == mapOfBlockIdToProcessStatus.get(blockID)) {
            mapOfBlockIdToPeakPlots.remove(blockID);
        }

        if (mapOfBlockIdToPeakPlots.containsKey(blockID)) {
            retVal = mapOfBlockIdToPeakPlots.get(blockID);
        } else {
            PlotBuilder[] peakPlotBuilders = SingleBlockPeakDriver.buildForSinglePeakBlock(blockID, blockPeakGroups);
            mapOfBlockIdToPeakPlots.put(blockID, peakPlotBuilders);
            retVal = mapOfBlockIdToPeakPlots.get(blockID);
        }
        return retVal;
    }

    // Updates Peak Centre plots


    public void updateRatiosPlotBuilderDisplayStatus(int indexOfIsotopicRatio, boolean displayed) {
        for (Integer blockID : mapOfBlockIdToPlots.keySet()) {
            PlotBuilder[] plotBuilder = mapOfBlockIdToPlots.get(blockID)[PLOT_INDEX_RATIOS];
            if (null != plotBuilder[indexOfIsotopicRatio]) {
                plotBuilder[indexOfIsotopicRatio].setDisplayed(displayed);
            }
        }
    }

    public String uppdateLogsByBlock(int blockID, String logEntry) {
        String log = "";
        if (mapOfBlockToLogs.containsKey(blockID)) {
            log = mapOfBlockToLogs.get(blockID);
        }
        String retVal = log + "\n" + logEntry;
        mapOfBlockToLogs.put(blockID, retVal);

        return retVal;
    }


    public AllBlockInitForOGTripoli.PlottingData assemblePostProcessPlottingData() {
        Map<Integer, SingleBlockRawDataSetRecord> singleBlockRawDataSetRecordMap = getMapOfBlockIdToRawData();
        SingleBlockRawDataSetRecord[] singleBlockRawDataSetRecords = new SingleBlockRawDataSetRecord[singleBlockRawDataSetRecordMap.keySet().size()];
        int index = 0;
        for (SingleBlockRawDataSetRecord singleBlockRawDataSetRecord : singleBlockRawDataSetRecordMap.values()) {
            singleBlockRawDataSetRecords[index] = singleBlockRawDataSetRecord;
            index++;
        }

        Map<Integer, SingleBlockModelRecord> singleBlockModelRecordMap = getMapOfBlockIdToFinalModel();
        SingleBlockModelRecord[] singleBlockModelRecords = new SingleBlockModelRecord[singleBlockModelRecordMap.keySet().size()];
        index = 0;
        for (SingleBlockModelRecord singleBlockModelRecord : singleBlockModelRecordMap.values()) {
            singleBlockModelRecords[index] = singleBlockModelRecord;
            index++;
        }

        return new AllBlockInitForOGTripoli.PlottingData(singleBlockRawDataSetRecords, singleBlockModelRecords, false);
    }

    public final String prettyPrintAnalysisSummary() {
        return analysisName +
                SPACES_100.substring(0, 40 - analysisName.length()) +
                (null == analysisMethod ? "NO Method" : analysisMethod.prettyPrintMethodSummary(false));
    }

    public final String prettyPrintAnalysisMetaData() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%30s", "Mass Spectrometer: "))
                .append(String.format("%-15s", massSpecExtractedData.getMassSpectrometerContext().getMassSpectrometerName()))
                .append(String.format("%-30s", "Context: " + massSpecExtractedData.getMassSpectrometerContext().getName()));
        if (0 == massSpecExtractedData.getMassSpectrometerContext().compareTo(UNKNOWN)) {
            sb.append("\n\n\n\t\t\t\t   >>>  Unable to parse data file.  <<<");
        } else {
            sb.append(String.format("%30s", "Software Version: ")).append(massSpecExtractedData.getHeader().softwareVersion())
                    .append("\n").append(String.format("%30s", "File Name: ")).append(String.format("%-45s", massSpecExtractedData.getHeader().filename()))
                    .append(String.format("%30s", "Corrected?: ")).append(massSpecExtractedData.getHeader().isCorrected())
                    .append("\n").append(String.format("%30s", "Method Name: ")).append(String.format("%-45s", massSpecExtractedData.getHeader().methodName()))
                    .append(String.format("%30s", "BChannels?: ")).append(massSpecExtractedData.getHeader().hasBChannels())
                    .append("\n").append(String.format("%30s", "Time Zero: ")).append(String.format("%-45s", massSpecExtractedData.getHeader().localDateTimeZero()));
        }

        return sb.toString();
    }

    public final String prettyPrintAnalysisDataSummary() {
        StringBuilder sb = new StringBuilder();
        if (massSpecExtractedData.getBlocksData().isEmpty()) {
            sb.append("No data extracted.");
        } else {
            sb.append(String.format("%30s", "Column headers: "));
            for (String header : massSpecExtractedData.getColumnHeaders()) {
                sb.append(header + " ");
            }
            sb.append("\n");
            sb.append(String.format("%30s", "Block count: "))
                    .append(String.format("%-3s", massSpecExtractedData.getBlocksData().size()))
                    .append(String.format("%-55s", "each with count of integrations for Baseline = " + massSpecExtractedData.getBlocksData().get(1).baselineIDs().length))
                    .append(String.format("%-30s", "and Onpeak = " + massSpecExtractedData.getBlocksData().get(1).onPeakIDs().length));
            sb.append(String.format("\n%30s", "Baseline sequences: "));
            Set<String> baselineNames = new TreeSet<>(List.of(massSpecExtractedData.getBlocksData().get(1).baselineIDs()));
            for (String baselineName : baselineNames) {
                sb.append(baselineName + " ");
            }
            sb.append(String.format("\n%30s", "Onpeak sequences: "));
            Set<String> onPeakNames = new TreeSet<>(List.of(massSpecExtractedData.getBlocksData().get(1).onPeakIDs()));
            for (String onPeakName : onPeakNames) {
                sb.append(onPeakName + " ");
            }
        }

        return sb.toString();
    }

    @Override
    public String getAnalysisName() {
        return analysisName;
    }

    @Override
    public void setAnalysisName(String analysisName) {
        this.analysisName = analysisName;
    }

    public String getAnalystName() {
        return analystName;
    }

    public void setAnalystName(String analystName) {
        this.analystName = analystName;
    }

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    public String getAnalysisSampleName() {
        return analysisSampleName;
    }

    public void setAnalysisSampleName(String analysisSampleName) {
        this.analysisSampleName = analysisSampleName;
    }

    public String getAnalysisSampleDescription() {
        return analysisSampleDescription;
    }

    public void setAnalysisSampleDescription(String analysisSampleDescription) {
        this.analysisSampleDescription = analysisSampleDescription;
    }

    @Override
    public AnalysisMethod getMethod() {
        return analysisMethod;
    }

    @Override
    public void setMethod(AnalysisMethod analysisMethod) {
        this.analysisMethod = analysisMethod;
    }

    public MassSpecExtractedData getMassSpecExtractedData() {
        return massSpecExtractedData;
    }

    public void setMassSpecExtractedData(MassSpecExtractedData massSpecExtractedData) {
        this.massSpecExtractedData = massSpecExtractedData;
    }

    public AnalysisMethod getAnalysisMethod() {
        return analysisMethod;
    }

    public void setAnalysisMethod(AnalysisMethod analysisMethod) {
        this.analysisMethod = analysisMethod;
    }

    public String getDataFilePathString() {
        return dataFilePathString;
    }

    public void setDataFilePathString(String dataFilePathString) {
        this.dataFilePathString = dataFilePathString;
    }

    public boolean isMutable() {
        return mutable;
    }

    public void setMutable(boolean mutable) {
        this.mutable = mutable;
    }

    public Map<Integer, Integer> getMapOfBlockIdToProcessStatus() {
        return mapOfBlockIdToProcessStatus;
    }

    public Map<Integer, PlotBuilder[][]> getMapOfBlockIdToPlots() {
        return mapOfBlockIdToPlots;
    }

    public Map<Integer, PlotBuilder[]> getMapOfBlockIdToPeakPlots() {
        return mapOfBlockIdToPeakPlots;
    }


    public Map<Integer, SingleBlockRawDataSetRecord> getMapOfBlockIdToRawData() {
        return mapOfBlockIdToRawData;
    }

    public Map<Integer, SingleBlockModelRecord> getMapOfBlockIdToFinalModel() {
        return mapOfBlockIdToFinalModel;
    }

}