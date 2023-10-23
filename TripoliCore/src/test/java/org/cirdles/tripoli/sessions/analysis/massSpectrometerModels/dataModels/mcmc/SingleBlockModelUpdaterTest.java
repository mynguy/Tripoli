package org.cirdles.tripoli.sessions.analysis.massSpectrometerModels.dataModels.mcmc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleBlockModelUpdaterTest {

    // IMPORTANT: These numeric values must match the data from MATLAB.
    // Please execute the MATLAB code and obtain the results to ensure consistency.

    // OVERVIEW: This process involves providing Tripoli with identical initial numeric inputs
    // and subsequently evaluating the Tripoli algorithm to determine if it produces results that match MATLAB.

    // STEP 1: Ensure that Tripoli receives the exact same initial numerical values.
    // STEP 2: Execute the Tripoli algorithm with the provided inputs.
    // STEP 3: Compare the results obtained from Tripoli with those generated by MATLAB.

    // NOTE: Pay particular attention to the Tripoli transformation when M = 20.

    // FUTURE WORK:
    // Consider conducting an additional test at a later stage in the overall process.
    // Eventually test when M is 50+ - Intense Data

    SingleBlockModelRecord singleBlockModelRecord_Test;
    SingleBlockModelRecord singleBlockModelRecord;

    // Data pulled from Matlab - 10/21
    double [] baseLineMeansArray = {-3.998259626249467e+05, -2.997573248486633e+05, -1.996907909202233e+05,
                                    -9.995615539918667e+04, -1.034287000033334e+02, 1.000739996192033e+05,
                                    2.001169734613367e+05, 2.994251939133499e+05};

    // Data pulled from Matlab - 10/21
    double [] baseLineStdArray = {4.982654488334372e+03, 4.780096984329671e+03, 5.193362894109419e+03, 4.907056423299858e+03,
                                  4.714765807627592e+03, 5.160457416333462e+03, 5.046707504648971e+03, 4.697629292897528e+03};

    // Data pulled from Matlab - 10/21
    double[] logRatios = {-6.117457281624248, -3.002819021695439, -1.000244523695487, -9.234974374397734e-05};

    // Data pulled from Matlab - 10/21
    double[] IO = {6.134069044869326e+05, 6.008566237422095e+05, 5.908634763865512e+05, 5.514048865900711e+05, 5.425026802749305e+05,
                   5.335331142532417e+05, 5.244925341811369e+05, 5.156072911272954e+05, 5.075442507656551e+05, 4.997544044326933e+05,
                   4.925957369655827e+05};

    // Pulled from the Matlab - Iteration m=1
    double[] delx ={0.012819918042855, -0.002522883826911, 3.935264800372901e-04, 1.454957603494240e-04,
            -1.077723039283252, -0.155257802641788, 0.051309012094972, 0.731607236842978,
            0.484253710141819, -0.504649362912455, 0.972666278739997, 0.303149131444137,
            -0.014099257508162, 0.280949206906329, -0.103641649512469, -0.037798167587677,
            0.506248636267279, 0.392192426857346, 0.290672536067569, 0.123634068116009,
            -0.280785436272478, 0.158800537540473, 0.496492908288080, 9.979502297411170e-05};
    int m = 20; // Iteration 20

    @BeforeEach
    void setUp() {

        // Create Sudo ModelRecord

        singleBlockModelRecord_Test = new SingleBlockModelRecord(
                1, // Ask next meeting on this data on Matlab
                8, // Ask next meeting on this data on Matlab
                11, // Ask next meeting on this data on Matlab
                4, // Ask next meeting on this data on Matlab
                null,
                baseLineMeansArray,
                baseLineStdArray,
                1.000000000000057e-08, // Data pulled from Matlab
                null,
                logRatios,
                null,
                null,
                new double[0],
                new double[0],
                IO,
                new double[0]
        );
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void randomOperMS() {
        SingleBlockModelUpdater singleBlockModelUpdater = new SingleBlockModelUpdater();
        String oper = singleBlockModelUpdater.randomOperMS(true);

        assertTrue(singleBlockModelUpdater.getOperations().contains(oper));
    }

//    @Test
//    void testUpdateMeanCovMS2() {
//
//        SingleBlockModelUpdater modelUpdater = new SingleBlockModelUpdater();
//        singleBlockModelRecord = modelUpdater.updateMSv2("changedfg", singleBlockModelRecord_Test,
//                ProposedModelParameters.buildProposalRangesRecord(null), delx, true);
//
//        //----------------------------------- STEP 1 ProposedModel Check ------------------------------------------
//
//        // Confirm that the 'ProposedModelParameters.buildProposalRangesRecord(null)' call yields the same result
//        // Verified that the numbers indeed matches the results from variable 'prior' obtained via Matlab - m = 1
//        // My Nguyen 10/22
//
//        //---------------------------------- STEP 2 singleBlockModelRecord Check ----------------------------------
//
//        // Ensure that singleBlockModelRecord is the same as 'x2' from Matlab
//
//        SingleBlockModelUpdater.UpdatedCovariancesRecord result = modelUpdater.updateMeanCovMS2(
//                singleBlockModelRecord, new double[24][24], new double[24], m);
//
//        result.dataMean(); // Utilize Debug to extract and compare numbers
//
//    }
    // Do Assertion with EPSILON to compare doubles
}