package io.github.gamezconz;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.measure.ResultsTable;
import ij.io.SaveDialog;
import java.io.File;
import java.util.HashSet;
import java.io.PrintStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import io.github.tatsunidas.radiomics.main.RadiomicsJ;

public class RadiomicsJ_Lite implements PlugIn {

    // --- MEMORY VARIABLES (To remember user selections across executions) ---
    private int lastImgIndex = 0, lastMaskIndex = 1;
    private int lastDimIndex = 1; 
    private int lastTexDiscIndex = 0, lastIvhDiscIndex = 0; 
    
    private int lastLabel = 1;
    private double lastOutlierSigma = 3.0;
    private double lastRangeMin = 0.0, lastRangeMax = 255.0;
    private double lastVx = 1.0, lastVy = 1.0, lastVz = 1.0;
    private double lastTexDiscVal = 16.0, lastDelta = 1.0, lastAlpha = 1.0;
    private double lastIvhDiscVal = 16.0;
    private String lastBoxSizes = "2,3,4,6,8,12,16,32,64";

    private boolean bOutliers = false, bRange = false, bResample = false;
    private boolean bOper = true, bDiag = true;
    private boolean bMorph = true, bLocInt = true, bIntStat = true, bIntHist = true;
    private boolean bIvh = true, bGlcm = true, bGlrlm = true, bGlszm = true;
    private boolean bGldzm = true, bNgtdm = true, bNgldm = true, bFrac = true;
    private boolean bExportCsv = true;

    @Override
    public void run(String arg) {
        
        // Infinite loop to keep the tool alive if an error occurs
        while (true) {
            int[] wList = WindowManager.getIDList();
            if (wList == null || wList.length < 2) {
                IJ.error("RadiomicsJ-Lite", "Please open at least two images (Original image and Mask).");
                return; 
            }
            
            String[] titles = new String[wList.length];
            for (int i = 0; i < wList.length; i++) {
                titles[i] = WindowManager.getImage(wList[i]).getTitle();
            }
            if (lastImgIndex >= titles.length) lastImgIndex = 0;
            if (lastMaskIndex >= titles.length) lastMaskIndex = titles.length > 1 ? 1 : 0;

            GenericDialog gd = new GenericDialog("RadiomicsJ-Lite: Advanced Configuration");

            // =========================================================
            // 1. VISUAL INTERFACE CONSTRUCTION
            // =========================================================
            gd.addMessage("=== DATA INPUT ===");
            gd.addChoice("Original_Image:", titles, titles[lastImgIndex]);
            gd.addChoice("Mask:", titles, titles[lastMaskIndex]);
            
            gd.addMessage("=== COMPUTATIONAL DIMENSION ===");
            gd.addChoice("Base_Dimension:", new String[]{"2D basis", "3D basis"}, lastDimIndex == 0 ? "2D basis" : "3D basis");
            
            gd.addMessage("=== MASK SETTINGS ===");
            gd.addNumericField("Label_value:", lastLabel, 0);
            gd.addCheckbox("Remove_Outliers", bOutliers);
            gd.addToSameRow(); // Horizontal alignment magic
            gd.addNumericField("Sigma:", lastOutlierSigma, 2);
            
            gd.addMessage("=== RANGE FILTERING ===");
            gd.addCheckbox("Range_Filtering", bRange);
            gd.addNumericField("min:", lastRangeMin, 2);
            gd.addToSameRow(); 
            gd.addNumericField("max:", lastRangeMax, 2);
            
            gd.addMessage("=== RESAMPLING ===");
            gd.addCheckbox("Resampling", bResample);
            gd.addNumericField("vx:", lastVx, 2);
            gd.addToSameRow(); 
            gd.addNumericField("vy:", lastVy, 2);
            gd.addToSameRow(); 
            gd.addNumericField("vz:", lastVz, 2);

            gd.addMessage("=== TEXTURE FAMILY PARAMS (Global) ===");
            gd.addChoice("Texture_Discretization:", new String[]{"Bin Count", "Bin Width"}, lastTexDiscIndex == 0 ? "Bin Count" : "Bin Width");
            gd.addNumericField("Texture_Bin_Value:", lastTexDiscVal, 2);
            gd.addNumericField("delta:", lastDelta, 0);
            gd.addToSameRow();
            gd.addNumericField("alpha:", lastAlpha, 0);

            gd.addMessage("=== INTENSITY FAMILY PARAM ===");
            gd.addChoice("IVH_Discretization:", new String[]{"Bin Count", "Bin Width", "Use As-Is"}, lastIvhDiscIndex == 0 ? "Bin Count" : (lastIvhDiscIndex == 1 ? "Bin Width" : "Use As-Is"));
            gd.addNumericField("IVH_Bin_Value:", lastIvhDiscVal, 2);

            gd.addMessage("=== FRACTAL FAMILY PARAM ===");
            gd.addStringField("Box_sizes:", lastBoxSizes, 30);

            gd.addMessage("=== FEATURES GROUP TO EXTRACT ===");
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
            gd.addCheckboxGroup(4, 3, cbLabels2, cbStates2); // 4 rows, 3 columns for compact layout
            
            gd.addMessage("=== OUTPUT ===");
            gd.addCheckbox("Export_CSV", bExportCsv);

            gd.showDialog();
            if (gd.wasCanceled()) break;

            // =========================================================
            // 2. PARAMETER READING (Strict sequential order for ImageJ)
            // =========================================================
            lastImgIndex = gd.getNextChoiceIndex();
            lastMaskIndex = gd.getNextChoiceIndex();
            lastDimIndex = gd.getNextChoiceIndex();
            lastTexDiscIndex = gd.getNextChoiceIndex();
            lastIvhDiscIndex = gd.getNextChoiceIndex();

            lastLabel = (int) gd.getNextNumber();
            lastOutlierSigma = gd.getNextNumber();
            lastRangeMin = gd.getNextNumber();
            lastRangeMax = gd.getNextNumber();
            lastVx = gd.getNextNumber();
            lastVy = gd.getNextNumber();
            lastVz = gd.getNextNumber();
            lastTexDiscVal = gd.getNextNumber();
            lastDelta = gd.getNextNumber();
            lastAlpha = gd.getNextNumber();
            lastIvhDiscVal = gd.getNextNumber();

            lastBoxSizes = gd.getNextString();

            bOutliers = gd.getNextBoolean();
            bRange = gd.getNextBoolean();
            bResample = gd.getNextBoolean();
            bOper = gd.getNextBoolean();
            bDiag = gd.getNextBoolean();
            bMorph = gd.getNextBoolean();
            bLocInt = gd.getNextBoolean();
            bIntStat = gd.getNextBoolean();
            bIntHist = gd.getNextBoolean();
            bIvh = gd.getNextBoolean();
            bGlcm = gd.getNextBoolean();
            bGlrlm = gd.getNextBoolean();
            bGlszm = gd.getNextBoolean();
            bGldzm = gd.getNextBoolean();
            bNgtdm = gd.getNextBoolean();
            bNgldm = gd.getNextBoolean();
            bFrac = gd.getNextBoolean();
            bExportCsv = gd.getNextBoolean();

            // =========================================================
            // 3. EXECUTION & ENGINE SETUP
            // =========================================================
            ImagePlus image = WindowManager.getImage(wList[lastImgIndex]);
            ImagePlus mask = WindowManager.getImage(wList[lastMaskIndex]);

            IJ.log("\\Clear"); 
            IJ.log("=====================================");
            IJ.log(" RadiomicsJ-Lite (v0.0.1)");
            IJ.log("=====================================");

            try {
                // Force engine to print internal steps
                RadiomicsJ.debug = true;
                
                RadiomicsJ.force2D = (lastDimIndex == 0);
                RadiomicsJ.removeOutliers = bOutliers;
                
                // Discretization mapping
                if (lastTexDiscIndex == 0) { 
                    RadiomicsJ.BOOL_USE_FixedBinNumber = true;
                    RadiomicsJ.nBins = (int) lastTexDiscVal;
                } else { 
                    RadiomicsJ.BOOL_USE_FixedBinNumber = false;
                    RadiomicsJ.binWidth = lastTexDiscVal;
                }

                // --- CONSOLE HIJACKING (Interceptor) ---
                PrintStream originalOut = System.out;
                PrintStream interceptor = new PrintStream(new OutputStream() {
                    StringBuilder sb = new StringBuilder();
                    @Override
                    public void write(int b) {
                        if (b == '\r') return;
                        if (b == '\n') {
                            String line = sb.toString();
                            IJ.log(line); // Print engine output to Fiji Log
                            
                            // Parse engine output to update Fiji Status Bar
                            String lowerLine = line.toLowerCase();
                            if (lowerLine.contains("features") || line.contains("====") || lowerLine.contains("summary")) {
                                String cleanLine = line.replace("=", "").trim();
                                if (!cleanLine.isEmpty()) IJ.showStatus("RadiomicsJ: " + cleanLine + "...");
                            }
                            sb.setLength(0);
                        } else {
                            sb.append((char) b);
                        }
                    }
                });

                System.setOut(interceptor); 

                try {
                    RadiomicsJ radiomics = new RadiomicsJ();
                    HashSet<String> excluded = radiomics.getExcludedFeatures();
                    excluded.clear(); 
                    
                    // Manage feature exclusion list based on UI
                    if (!bMorph) excluded.add("Morphological");
                    if (!bLocInt) excluded.add("LocalIntensity");
                    if (!bIntStat) excluded.add("IntensityBasedStatistics");
                    if (!bIntHist) excluded.add("IntensityHistogram");
                    if (!bIvh) excluded.add("IntensityVolumeHistogram");
                    if (!bGlcm) excluded.add("GLCM");
                    if (!bGlrlm) excluded.add("GLRLM");
                    if (!bGlszm) excluded.add("GLSZM");
                    if (!bGldzm) excluded.add("GLDZM");
                    if (!bNgtdm) excluded.add("NGTDM");
                    if (!bNgldm) excluded.add("NGLDM");
                    if (!bFrac) excluded.add("Fractal");

                    // --- TIME FORMATTING ---
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    long startTime = System.currentTimeMillis();
                    
                    IJ.log("\n▶ Calculation start time: " + sdf.format(new Date(startTime)));
                    IJ.log("WARNING: Calculating complex matrices (especially in 3D) is resource-intensive.");
                    IJ.log("This might take several minutes depending on the image volume. Please wait...\n");
                    
                    // CALCULATION TRIGGER
                    ResultsTable rt = radiomics.execute(image, mask, lastLabel);

                    long endTime = System.currentTimeMillis();
                    long durationSeconds = (endTime - startTime) / 1000;
                    long min = durationSeconds / 60;
                    long sec = durationSeconds % 60;

                    IJ.log("\n⏹ Calculation end time: " + sdf.format(new Date(endTime)));
                    IJ.log("Total processing time: " + min + " min " + sec + " sec\n");

                    if (rt == null || rt.getCounter() == 0) {
                        throw new Exception("The engine returned an empty table. Check your ROI label or parameters.");
                    }

                    IJ.showStatus("RadiomicsJ: Extraction Completed in " + min + "m " + sec + "s!");
                    rt.show("RadiomicsJ Results");

                    // Handle CSV export
                    if (bExportCsv) {
                        SaveDialog sd = new SaveDialog("Save results", "radiomics_features", ".csv");
                        if (sd.getDirectory() != null && sd.getFileName() != null) { 
                            File csvFile = new File(sd.getDirectory(), sd.getFileName());
                            rt.save(csvFile.getAbsolutePath()); 
                            IJ.log(">> Exported to: " + csvFile.getAbsolutePath());
                        }
                    }

                } finally {
                    // --- CRITICAL CONSOLE RESTORATION ---
                    // Ensures standard output is restored even if the plugin crashes
                    System.setOut(originalOut);
                }
                
                break; // Exit the infinite loop on success

            } catch (Throwable e) { 
                IJ.showStatus("RadiomicsJ: Extraction failed!");
                IJ.log("\n[ERROR] Mathematical engine failed:");
                IJ.log(e.getMessage());
                IJ.log("Reopening configuration window...");
            }
        }
    }
}