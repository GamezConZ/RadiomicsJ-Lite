package io.github.gamezconz;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.measure.ResultsTable;
import ij.io.SaveDialog;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Arrays;
import java.io.PrintStream;
import java.io.OutputStream;

// RadiomicsJ Core Imports
import io.github.tatsunidas.radiomics.main.RadiomicsJ;
import io.github.tatsunidas.radiomics.main.ImagePreprocessing;
import io.github.tatsunidas.radiomics.features.*;

/**
 * RadiomicsJ-Lite: A high-performance, macro-recordable Fiji plugin
 * for Radiomic feature extraction using the RadiomicsJ engine.
 */
public class RadiomicsJ_Lite implements PlugIn {

    // --- UI Persistence Variables ---
    private int lastImgIndex = 0, lastMaskIndex = 1;
    private int lastDimIndex = 1; // 0=2D, 1=3D
    private int lastTexDiscIndex = 0, lastIvhDiscIndex = 0;

    private int lastLabel = 1;
    private double lastOutlierSigma = 3.0;
    private double lastRangeMin = 0.0, lastRangeMax = 255.0;
    private double lastVx = 2.0, lastVy = 2.0, lastVz = 2.0;
    private double lastTexDiscVal = 16.0, lastDelta = 1.0;
    
    // (coarseness parameter = 0 is the IBSI-standard neighbour search for NGLDM).
    private double lastAlpha = 0.0;
    private double lastIvhDiscVal = 16.0;
    private String lastBoxSizes = "2,3,4,6,8,12,16,32,64";

    private boolean bOutliers = false, bRange = false, bResample = true;
    private boolean bOper = true, bDiag = true;
    private boolean bMorph = true, bLocInt = true, bIntStat = true, bIntHist = true;
    private boolean bIvh = true, bGlcm = true, bGlrlm = true, bGlszm = true;
    private boolean bGldzm = true, bNgtdm = true, bNgldm = true, bFrac = true;
    private boolean bExportCsv = true;

    /**
     * Maps the IVH dropdown index to the mode integer expected by the engine.
     *
     * IntensityVolumeHistogramFeatures(img, mask, label, mode) modes:
     *   0 = No discretisation - use pixel intensities as-is
     *   1 = Discretise by Bin Width  (reads RadiomicsJ.IVH_binWidth)
     *   2 = Discretise by Bin Count  (reads RadiomicsJ.IVH_binCount)
     *
     * UI dropdown:
     *   index 0 = "Bin Count"  -> mode 2
     *   index 1 = "Bin Width"  -> mode 1
     *   index 2 = "Use As-Is"  -> mode 0
     */
    private static int ivhDropdownToMode(int dropdownIndex) {
        switch (dropdownIndex) {
            case 0:  return 2;  // Bin Count
            case 1:  return 1;  // Bin Width
            case 2:  return 0;  // Use As-Is (no discretisation)
            default: return 0;
        }
    }

    @Override
    public void run(String arg) {

        while (true) {
            int[] wList = WindowManager.getIDList();
            if (wList == null || wList.length < 2) {
                IJ.error("RadiomicsJ-Lite", "Please open at least two images (Original Image and Mask).");
                return;
            }

            String[] titles = new String[wList.length];
            for (int i = 0; i < wList.length; i++) titles[i] = WindowManager.getImage(wList[i]).getTitle();

            if (lastImgIndex  >= titles.length) lastImgIndex  = 0;
            if (lastMaskIndex >= titles.length) lastMaskIndex = titles.length > 1 ? 1 : 0;

            GenericDialog gd = new GenericDialog("RadiomicsJ-Lite Configuration");

            // --- Section: Data Input ---
            gd.addMessage("=== DATA INPUT ===");
            gd.addChoice("Original_Image:", titles, titles[lastImgIndex]);
            gd.addChoice("Mask:", titles, titles[lastMaskIndex]);

            // --- Section: Computational Dimension ---
            gd.addMessage("=== COMPUTATIONAL DIMENSION ===");
            gd.addChoice("Base_Dimension:", new String[]{"2D basis", "3D basis"}, lastDimIndex == 0 ? "2D basis" : "3D basis");

            // --- Section: Preprocessing ---
            gd.addMessage("=== PREPROCESSING ===");
            gd.addNumericField("Mask_Label:", lastLabel, 0);
            gd.addCheckbox("Remove_Outliers", bOutliers);
            gd.addToSameRow();
            gd.addNumericField("Sigma:", lastOutlierSigma, 2);

            gd.addCheckbox("Range_Filtering", bRange);
            gd.addNumericField("min:", lastRangeMin, 2);
            gd.addToSameRow();
            gd.addNumericField("max:", lastRangeMax, 2);

            gd.addCheckbox("Resampling", bResample);
            gd.addNumericField("vx:", lastVx, 2);
            gd.addToSameRow();
            gd.addNumericField("vy:", lastVy, 2);
            gd.addToSameRow();
            gd.addNumericField("vz:", lastVz, 2);

            // --- Section: Texture and Intensity Params ---
            gd.addMessage("=== TEXTURE PARAMETERS (Global) ===");
            gd.addChoice("Texture_Discretization:", new String[]{"Bin Count", "Bin Width"}, lastTexDiscIndex == 0 ? "Bin Count" : "Bin Width");
            gd.addNumericField("Value (Bins/Width):", lastTexDiscVal, 2);
            gd.addNumericField("delta:", lastDelta, 0);
            gd.addToSameRow();
            gd.addNumericField("alpha:", lastAlpha, 0);

            gd.addMessage("=== INTENSITY PARAMETERS (IVH) ===");
            gd.addChoice("IVH_Discretization:", new String[]{"Bin Count", "Bin Width", "Use As-Is"}, lastIvhDiscIndex == 0 ? "Bin Count" : (lastIvhDiscIndex == 1 ? "Bin Width" : "Use As-Is"));
            gd.addNumericField("IVH_Value:", lastIvhDiscVal, 2);

            gd.addMessage("=== FRACTAL PARAMETERS ===");
            gd.addStringField("Box_sizes:", lastBoxSizes, 30);

            // --- Section: Feature Selection ---
            gd.addMessage("=== FEATURE FAMILIES TO EXTRACT ===");
            String[] cbLabels1 = {"Operational", "Diagnostics"};
            boolean[] cbStates1 = {bOper, bDiag};
            gd.addCheckboxGroup(1, 2, cbLabels1, cbStates1);

            String[] cbLabels2 = {
                "Morphological", "LocalIntensity", "IntensityStats", "IntensityHistogram",
                "VolumeHistogram", "GLCM", "GLRLM", "GLSZM",
                "GLDZM", "NGTDM", "NGLDM", "Fractal"
            };
            boolean[] cbStates2 = {
                bMorph, bLocInt, bIntStat, bIntHist,
                bIvh, bGlcm, bGlrlm, bGlszm,
                bGldzm, bNgtdm, bNgldm, bFrac
            };
            gd.addCheckboxGroup(4, 3, cbLabels2, cbStates2);

            // --- Section: Output ---
            gd.addMessage("=== OUTPUT ===");
            gd.addCheckbox("Export_to_CSV", bExportCsv);

            gd.showDialog();
            if (gd.wasCanceled()) break;

            // --- Update Memory Variables (order must exactly match addXxx() calls above) ---
            lastImgIndex     = gd.getNextChoiceIndex();
            lastMaskIndex    = gd.getNextChoiceIndex();
            lastDimIndex     = gd.getNextChoiceIndex();
            lastTexDiscIndex = gd.getNextChoiceIndex();
            lastIvhDiscIndex = gd.getNextChoiceIndex();
            lastLabel        = (int) gd.getNextNumber();
            lastOutlierSigma = gd.getNextNumber();
            lastRangeMin     = gd.getNextNumber();
            lastRangeMax     = gd.getNextNumber();
            lastVx           = gd.getNextNumber();
            lastVy           = gd.getNextNumber();
            lastVz           = gd.getNextNumber();
            lastTexDiscVal   = gd.getNextNumber();
            lastDelta        = gd.getNextNumber();
            lastAlpha        = gd.getNextNumber();
            lastIvhDiscVal   = gd.getNextNumber();
            lastBoxSizes     = gd.getNextString();
            bOutliers        = gd.getNextBoolean();
            bRange           = gd.getNextBoolean();
            bResample        = gd.getNextBoolean();
            bOper            = gd.getNextBoolean();
            bDiag            = gd.getNextBoolean();
            bMorph           = gd.getNextBoolean();
            bLocInt          = gd.getNextBoolean();
            bIntStat         = gd.getNextBoolean();
            bIntHist         = gd.getNextBoolean();
            bIvh             = gd.getNextBoolean();
            bGlcm            = gd.getNextBoolean();
            bGlrlm           = gd.getNextBoolean();
            bGlszm           = gd.getNextBoolean();
            bGldzm           = gd.getNextBoolean();
            bNgtdm           = gd.getNextBoolean();
            bNgldm           = gd.getNextBoolean();
            bFrac            = gd.getNextBoolean();
            bExportCsv       = gd.getNextBoolean();

            // --- Execution Logic ---
            ImagePlus image = WindowManager.getImage(wList[lastImgIndex]);
            ImagePlus mask  = WindowManager.getImage(wList[lastMaskIndex]);

            IJ.log("\\Clear");
            IJ.log("==================================================");
            IJ.log(" RadiomicsJ-Lite ");
            IJ.log("==================================================");

            // clause can always restore it, even when an exception is thrown.
            PrintStream originalOut = System.out;

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                long startTime = System.currentTimeMillis();
                IJ.log(">> Extraction started at: " + sdf.format(new Date(startTime)));

                ResultsTable rt = new ResultsTable();

                // Engine Setup
                RadiomicsJ.debug   = false;
                RadiomicsJ.force2D = (lastDimIndex == 0);

                // Texture discretisation parameters (shared by all matrix-based families)
                boolean useBinCountGlob = (lastTexDiscIndex == 0);
                Integer binCountGlob    = (int) lastTexDiscVal;
                Double  binWidthGlob    = useBinCountGlob ? null : lastTexDiscVal;

                int ivhMode = ivhDropdownToMode(lastIvhDiscIndex);
                if (ivhMode == 2) {
                    RadiomicsJ.IVH_binCount = (int) lastIvhDiscVal;
                } else if (ivhMode == 1) {
                    RadiomicsJ.IVH_binWidth = lastIvhDiscVal;
                }
                // ivhMode == 0: Use As-Is -- no static field needed, engine reads raw pixels.

                int[] boxArray = Arrays.stream(lastBoxSizes.split(","))
                                       .map(String::trim)
                                       .mapToInt(Integer::parseInt)
                                       .toArray();

                // Console interceptor -- routes engine stdout to the Fiji Log
                PrintStream interceptor = new PrintStream(new OutputStream() {
                    StringBuilder sb = new StringBuilder();
                    @Override
                    public void write(int b) {
                        if (b == '\r') return;
                        if (b == '\n') {
                            String line = sb.toString();
                            IJ.log(line);
                            String lower = line.toLowerCase();
                            if (lower.contains("features") || line.contains("====")) {
                                String clean = line.replace("=", "").trim();
                                if (!clean.isEmpty()) IJ.showStatus("RadiomicsJ: " + clean);
                            }
                            sb.setLength(0);
                        } else {
                            sb.append((char) b);
                        }
                    }
                });
                System.setOut(interceptor);

                // Profiling variables
                long tPre = 0, tOper = 0, tDiag = 0;
                long tMorph = 0, tLocInt = 0, tIntStat = 0, tIntHist = 0;
                long tIvh = 0, tGlcm = 0, tGlrlm = 0, tGlszm = 0, tGldzm = 0, tNgtdm = 0, tNgldm = 0, tFrac = 0;
                long mark;

                try {
                    // --- PHASE 1: PREPROCESSING ---
                    mark = System.currentTimeMillis();
                    IJ.log(">> Executing image preprocessing...");

                    // Keep references to the original images for DiagnosticsInfo
                    ImagePlus originalImp  = image;
                    ImagePlus originalMask = mask;

                    ImagePlus imp = image.duplicate();
                    ImagePlus msk = io.github.tatsunidas.radiomics.main.Utils
                            .initMaskAsFloatAndConvertLabelOne(mask, lastLabel);
                    int calcLabel = 1; // initMaskAsFloatAndConvertLabelOne normalises the label to 1

                    if (bResample) {
                        IJ.log("   - Applying resampling...");
                        if (RadiomicsJ.force2D) {
                            imp = io.github.tatsunidas.radiomics.main.Utils.resample2D(imp, false, lastVx, lastVy, RadiomicsJ.interpolation2D);
                            msk = io.github.tatsunidas.radiomics.main.Utils.resample2D(msk, true,  lastVx, lastVy, RadiomicsJ.interpolation2D);
                        } else {
                            imp = io.github.tatsunidas.radiomics.main.Utils.resample3D(imp, false, lastVx, lastVy, lastVz);
                            msk = io.github.tatsunidas.radiomics.main.Utils.resample3D(msk, true,  lastVx, lastVy, lastVz);
                        }
                    }

                    // Snapshot after resampling but before resegmentation filters,
                    // as DiagnosticsInfo requires both states separately.
                    ImagePlus resampledImp  = imp;
                    ImagePlus resampledMask = msk;

                    if (bRange) {
                        IJ.log("   - Applying range filtering...");
                        msk = ImagePreprocessing.rangeFiltering(imp, msk, calcLabel, lastRangeMax, lastRangeMin);
                    }
                    if (bOutliers) {
                        IJ.log("   - Removing outliers...");
                        RadiomicsJ.zScore = lastOutlierSigma;
                        msk = ImagePreprocessing.outlierFiltering(imp, msk, calcLabel);
                    }
                    // msk is now the fully resegmented mask (post range-filter + outlier removal)
                    ImagePlus resegmentedMask = msk;

                    tPre = System.currentTimeMillis() - mark;

                    rt.incrementCounter();
                    rt.addValue("ID", image.getTitle());

                    // --- PHASE 2: FEATURE EXTRACTION ---
                    IJ.log(">> Starting feature extraction...");

                    if (bOper) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating Operational Info...");
                        io.github.tatsunidas.radiomics.features.OperationalInfoFeatures oif =
                            new io.github.tatsunidas.radiomics.features.OperationalInfoFeatures(imp);
                        java.util.HashMap<String, String> info = oif.getInfo();
                        for (String key : info.keySet()) {
                            String val = info.get(key);
                            rt.addValue("OperationalInfo_" + key, val != null ? val : "NaN");
                        }
                        tOper = System.currentTimeMillis() - mark;
                    }

                    if (bDiag) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating Diagnostics Info...");
                        io.github.tatsunidas.radiomics.features.DiagnosticsInfo di =
                            new io.github.tatsunidas.radiomics.features.DiagnosticsInfo(
                                originalImp, originalMask,
                                resampledImp, resampledMask,
                                resegmentedMask, calcLabel);
                        for (io.github.tatsunidas.radiomics.features.DiagnosticsInfoType dinfo :
                                io.github.tatsunidas.radiomics.features.DiagnosticsInfoType.values()) {
                            Double v = di.getDiagnosticsBy(dinfo.name());
                            rt.addValue("Diagnostics_" + dinfo.name(), v != null ? v : Double.NaN);
                        }
                        tDiag = System.currentTimeMillis() - mark;
                    }

                    if (bMorph) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating Morphological features...");
                        MorphologicalFeatures mf = new MorphologicalFeatures(imp, msk, calcLabel);
                        for (MorphologicalFeatureType t : MorphologicalFeatureType.values()) {
                            // Bypass Moran's I and Geary's C in 3D due to O(N^2) complexity
                            if (!RadiomicsJ.force2D && (t.name().equals("MoransIIndex") || t.name().equals("GearysCMeasure"))) {
                                IJ.log("     [!] Skipping " + t.name() + " in 3D (Computationally expensive)");
                                continue;
                            }
                            Double v = mf.calculate(t.id());
                            rt.addValue("Morphological_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tMorph = System.currentTimeMillis() - mark;
                    }

                    if (bLocInt) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating Local Intensity...");
                        LocalIntensityFeatures lif = new LocalIntensityFeatures(imp, msk, calcLabel);
                        for (LocalIntensityFeatureType t : LocalIntensityFeatureType.values()) {
                            Double v = lif.calculate(t.id());
                            rt.addValue("LocalIntensity_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tLocInt = System.currentTimeMillis() - mark;
                    }

                    if (bIntStat) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating Intensity Stats...");
                        IntensityBasedStatisticalFeatures isf = new IntensityBasedStatisticalFeatures(imp, msk, calcLabel);
                        for (IntensityBasedStatisticalFeatureType t : IntensityBasedStatisticalFeatureType.values()) {
                            Double v = isf.calculate(t.id());
                            rt.addValue("IntensityStats_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tIntStat = System.currentTimeMillis() - mark;
                    }

                    if (bIntHist) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating Intensity Histogram...");
                        IntensityHistogramFeatures ihf = new IntensityHistogramFeatures(imp, msk, calcLabel, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (IntensityHistogramFeatureType t : IntensityHistogramFeatureType.values()) {
                            Double v = ihf.calculate(t.id());
                            rt.addValue("IntensityHistogram_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tIntHist = System.currentTimeMillis() - mark;
                    }

                    if (bIvh) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating Volume Histogram (IVH)...");
                        // Static fields already set correctly above (after FIX #3)
                        IntensityVolumeHistogramFeatures ivhf = new IntensityVolumeHistogramFeatures(imp, msk, calcLabel, ivhMode);
                        for (IntensityVolumeHistogramFeatureType t : IntensityVolumeHistogramFeatureType.values()) {
                            Double v = ivhf.calculate(t.id());
                            rt.addValue("VolumeHistogram_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tIvh = System.currentTimeMillis() - mark;
                    }

                    if (bGlcm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating GLCM Texture...");
                        GLCMFeatures glcm = new GLCMFeatures(imp, msk, calcLabel, (int) lastDelta, useBinCountGlob, binCountGlob, binWidthGlob, null);
                        for (GLCMFeatureType t : GLCMFeatureType.values()) {
                            Double v = glcm.calculate(t.id());
                            rt.addValue("GLCM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tGlcm = System.currentTimeMillis() - mark;
                    }

                    if (bGlrlm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating GLRLM Texture...");
                        GLRLMFeatures glrlm = new GLRLMFeatures(imp, msk, calcLabel, useBinCountGlob, binCountGlob, binWidthGlob, null);
                        for (GLRLMFeatureType t : GLRLMFeatureType.values()) {
                            Double v = glrlm.calculate(t.id());
                            rt.addValue("GLRLM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tGlrlm = System.currentTimeMillis() - mark;
                    }

                    if (bGlszm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating GLSZM Texture...");
                        GLSZMFeatures glszm = new GLSZMFeatures(imp, msk, calcLabel, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (GLSZMFeatureType t : GLSZMFeatureType.values()) {
                            Double v = glszm.calculate(t.id());
                            rt.addValue("GLSZM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tGlszm = System.currentTimeMillis() - mark;
                    }

                    if (bGldzm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating GLDZM Texture...");
                        GLDZMFeatures gldzm = new GLDZMFeatures(imp, msk, calcLabel, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (GLDZMFeatureType t : GLDZMFeatureType.values()) {
                            Double v = gldzm.calculate(t.id());
                            rt.addValue("GLDZM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tGldzm = System.currentTimeMillis() - mark;
                    }

                    if (bNgtdm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating NGTDM Texture...");
                        NGTDMFeatures ngtdm = new NGTDMFeatures(imp, msk, calcLabel, (int) lastDelta, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (NGTDMFeatureType t : NGTDMFeatureType.values()) {
                            Double v = ngtdm.calculate(t.id());
                            rt.addValue("NGTDM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tNgtdm = System.currentTimeMillis() - mark;
                    }

                    if (bNgldm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating NGLDM Texture...");
                        NGLDMFeatures ngldm = new NGLDMFeatures(imp, msk, calcLabel, (int) lastAlpha, (int) lastDelta, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (NGLDMFeatureType t : NGLDMFeatureType.values()) {
                            Double v = ngldm.calculate(t.id());
                            rt.addValue("NGLDM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tNgldm = System.currentTimeMillis() - mark;
                    }

                    if (bFrac) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > Calculating Fractals...");
                        FractalFeatures ff = new FractalFeatures(imp, msk, calcLabel, boxArray);
                        for (FractalFeatureType t : FractalFeatureType.values()) {
                            Double v = ff.calculate(t.id());
                            rt.addValue("Fractal_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tFrac = System.currentTimeMillis() - mark;
                    }

                    long endTime = System.currentTimeMillis();
                    long durationSec = (endTime - startTime) / 1000;

                    // --- PERFORMANCE REPORT ---
                    IJ.log("\n==================================================");
                    IJ.log(" PERFORMANCE REPORT (Profiling)");
                    IJ.log("==================================================");
                    IJ.log(String.format(" - Preprocessing:    %.2f s", tPre / 1000.0));
                    if (bOper)   IJ.log(String.format(" - Operational:      %.2f s", tOper / 1000.0));
                    if (bDiag)   IJ.log(String.format(" - Diagnostics:      %.2f s", tDiag / 1000.0));
                    if (bMorph)  IJ.log(String.format(" - Morphological:    %.2f s", tMorph / 1000.0));
                    if (bLocInt) IJ.log(String.format(" - Local Intensity:  %.2f s", tLocInt / 1000.0));
                    if (bIntStat)IJ.log(String.format(" - Intensity Stats:  %.2f s", tIntStat / 1000.0));
                    if (bIntHist)IJ.log(String.format(" - Int. Histogram:   %.2f s", tIntHist / 1000.0));
                    if (bIvh)    IJ.log(String.format(" - Vol. Histogram:   %.2f s", tIvh / 1000.0));
                    if (bGlcm)   IJ.log(String.format(" - GLCM Texture:     %.2f s", tGlcm / 1000.0));
                    if (bGlrlm)  IJ.log(String.format(" - GLRLM Texture:    %.2f s", tGlrlm / 1000.0));
                    if (bGlszm)  IJ.log(String.format(" - GLSZM Texture:    %.2f s", tGlszm / 1000.0));
                    if (bGldzm)  IJ.log(String.format(" - GLDZM Texture:    %.2f s", tGldzm / 1000.0));
                    if (bNgtdm)  IJ.log(String.format(" - NGTDM Texture:    %.2f s", tNgtdm / 1000.0));
                    if (bNgldm)  IJ.log(String.format(" - NGLDM Texture:    %.2f s", tNgldm / 1000.0));
                    if (bFrac)   IJ.log(String.format(" - Fractals:         %.2f s", tFrac / 1000.0));
                    IJ.log("==================================================");

                    IJ.log("\n End time: " + sdf.format(new Date(endTime)));
                    IJ.log(" Total processing time: " + (durationSec / 60) + " min " + (durationSec % 60) + " sec\n");

                    if (rt.getCounter() == 0) throw new Exception("Empty results table.");

                    rt.show("RadiomicsJ-Lite Results");

                    if (bExportCsv) {
                        SaveDialog sd = new SaveDialog("Save Results", "radiomics_features", ".csv");
                        if (sd.getDirectory() != null && sd.getFileName() != null) {
                            File csvFile = new File(sd.getDirectory(), sd.getFileName());
                            rt.save(csvFile.getAbsolutePath());
                            IJ.log(">> Exported to: " + csvFile.getAbsolutePath());
                        }
                    }

                } finally {
                    System.setOut(originalOut);
                }

                break;

            } catch (Throwable e) {
                IJ.log("\n[ERROR] Critical failure during extraction:");
                IJ.log(e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) IJ.log("  " + element.toString());
                IJ.log("\nReturning to configuration window...");
            }
        }
    }
}