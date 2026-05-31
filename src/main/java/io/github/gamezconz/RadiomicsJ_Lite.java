package io.github.gamezconz;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.measure.ResultsTable;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.io.SaveDialog;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Arrays;
import java.util.Locale;
import java.io.PrintStream;
import java.io.OutputStream;

// RadiomicsJ Core Imports
import io.github.tatsunidas.radiomics.main.RadiomicsJ;
import io.github.tatsunidas.radiomics.main.ImagePreprocessing;
import io.github.tatsunidas.radiomics.main.Utils;
import io.github.tatsunidas.radiomics.features.*;

/**
 * RadiomicsJ-Lite: a high-throughput, macro-recordable Fiji plugin for radiomic
 * feature extraction on top of the RadiomicsJ engine.
 *
 * INTERNATIONALISATION &amp; MACROS
 * The language is chosen in a small dialog BEFORE the main configuration window
 * is drawn, so the whole UI renders directly in the selected language. Only the
 * "chrome" (titles, section headers, log messages) is translated; the dialog
 * field labels — which are the macro-recorder keys — stay in English, so a
 * recorded macro runs unchanged regardless of the UI language.
 *
 * Supported languages: English, Español, Français, Italiano, Deutsch, 中文, 日本語.
 * (Chinese/Japanese require CJK fonts on the host; otherwise they show as boxes,
 *  which is a display-only limitation and never affects the computation.)
 */
public class RadiomicsJ_Lite implements PlugIn {

    // Language index order MUST match LANG_OPTIONS below.
    // 0=EN  1=ES  2=FR  3=IT  4=DE  5=ZH  6=JA
    private static final String[] LANG_OPTIONS =
            {"English", "Español", "Français", "Italiano", "Deutsch", "中文", "日本語"};

    /** Remembered across runs within the Fiji session (static), seeded from the OS locale. */
    private static int lastLangIndex = defaultLangFromLocale();

    /** Active language for the current run. */
    private int uiLang = 0;

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

    // Spatial autocorrelation (Moran's I, Geary's C). OFF by default: O(N^2)
    // over the ROI, prohibitively slow on large 3D volumes.
    private boolean bSpatialAutocorr = false;

    private boolean bExportCsv = true;

    /** 0=EN by default; pick the matching index if the OS locale is one we support. */
    private static int defaultLangFromLocale() {
        try {
            String lang = Locale.getDefault().getLanguage();
            if ("es".equalsIgnoreCase(lang)) return 1;
            if ("fr".equalsIgnoreCase(lang)) return 2;
            if ("it".equalsIgnoreCase(lang)) return 3;
            if ("de".equalsIgnoreCase(lang)) return 4;
            if ("zh".equalsIgnoreCase(lang)) return 5;
            if ("ja".equalsIgnoreCase(lang)) return 6;
        } catch (Throwable t) { /* fall through */ }
        return 0;
    }

    /**
     * Localisation helper. Pass one string per language in the order
     * EN, ES, FR, IT, DE, ZH, JA. Falls back to English if a variant is missing.
     */
    private String t(String... v) {
        if (uiLang >= 0 && uiLang < v.length && v[uiLang] != null) return v[uiLang];
        return v[0];
    }

    /**
     * Maps the IVH dropdown index to the mode integer expected by the engine.
     *   index 0 = "Bin Count" -> mode 2 (reads RadiomicsJ.IVH_binCount)
     *   index 1 = "Bin Width" -> mode 1 (reads RadiomicsJ.IVH_binWidth)
     *   index 2 = "Use As-Is" -> mode 0 (raw intensities)
     */
    private static int ivhDropdownToMode(int dropdownIndex) {
        switch (dropdownIndex) {
            case 0:  return 2;
            case 1:  return 1;
            case 2:  return 0;
            default: return 0;
        }
    }

    /**
     * Reset every RadiomicsJ static field this plugin relies on back to its
     * IBSI default. RadiomicsJ keeps its configuration in static fields that
     * survive the whole Fiji session, so this guarantees deterministic runs
     * regardless of what ran before.
     */
    private static void resetEngineStateToIBSIDefaults() {
        RadiomicsJ.debug = false;
        RadiomicsJ.discretiseImp = null;
        RadiomicsJ.interpolation2D = ImageProcessor.NEAREST_NEIGHBOR;
        RadiomicsJ.interpolation_mask2D = ImageProcessor.NEAREST_NEIGHBOR;
        RadiomicsJ.interpolation3D = RadiomicsJ.TRILINEAR;
        RadiomicsJ.interpolation_mask3D = RadiomicsJ.TRILINEAR;
        RadiomicsJ.mask_PartialVolumeThreshold = 0.5;
        RadiomicsJ.normalize = false;
        RadiomicsJ.weightingNorm = null;
        RadiomicsJ.densityShift = 0d;
        RadiomicsJ.zScore = 3d;
        RadiomicsJ.IVH_binCount = 1000;
        RadiomicsJ.IVH_binWidth = 2.5;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    /**
     * Small dialog shown BEFORE the main window so the configuration UI renders
     * directly in the chosen language. Returns false if the user cancels.
     */
    private boolean chooseLanguage() {
        GenericDialog lg = new GenericDialog("RadiomicsJ-Lite");
        // Neutral, language-independent prompt (we don't know the language yet).
        lg.addMessage("Language / Idioma / Langue / Lingua / Sprache / 语言 / 言語");
        int def = (lastLangIndex >= 0 && lastLangIndex < LANG_OPTIONS.length) ? lastLangIndex : 0;
        // Label "Language:" stays in English: it is the macro-recorder key.
        lg.addChoice("Language:", LANG_OPTIONS, LANG_OPTIONS[def]);
        lg.showDialog();
        if (lg.wasCanceled()) return false;
        lastLangIndex = lg.getNextChoiceIndex();
        uiLang = lastLangIndex;
        return true;
    }

    @Override
    public void run(String arg) {

        // Language is selected up front, then the main window is drawn in it.
        if (!chooseLanguage()) return;

        while (true) {
            uiLang = lastLangIndex;

            int[] wList = WindowManager.getIDList();
            if (wList == null || wList.length < 2) {
                IJ.error("RadiomicsJ-Lite",
                        t("Please open at least two images (Original Image and Mask).",
                          "Abra al menos dos imágenes (Imagen Original y Máscara).",
                          "Ouvrez au moins deux images (Image originale et Masque).",
                          "Apri almeno due immagini (Immagine originale e Maschera).",
                          "Öffnen Sie mindestens zwei Bilder (Originalbild und Maske).",
                          "请至少打开两幅图像（原始图像和掩膜）。",
                          "少なくとも2つの画像（元画像とマスク）を開いてください。"));
                return;
            }

            String[] titles = new String[wList.length];
            for (int i = 0; i < wList.length; i++) titles[i] = WindowManager.getImage(wList[i]).getTitle();

            if (lastImgIndex  >= titles.length) lastImgIndex  = 0;
            if (lastMaskIndex >= titles.length) lastMaskIndex = titles.length > 1 ? 1 : 0;

            GenericDialog gd = new GenericDialog(
                    t("RadiomicsJ-Lite Configuration", "Configuración de RadiomicsJ-Lite",
                      "Configuration de RadiomicsJ-Lite", "Configurazione di RadiomicsJ-Lite",
                      "RadiomicsJ-Lite Konfiguration", "RadiomicsJ-Lite 配置", "RadiomicsJ-Lite 設定"));

            // --- Section: Data Input ---
            gd.addMessage("=== " + t("DATA INPUT", "DATOS DE ENTRADA", "DONNÉES D'ENTRÉE",
                    "DATI DI INGRESSO", "EINGABEDATEN", "输入数据", "入力データ") + " ===");
            gd.addChoice("Original_Image:", titles, titles[lastImgIndex]);
            gd.addChoice("Mask:", titles, titles[lastMaskIndex]);

            // --- Section: Computational Dimension (note moved to the Log) ---
            gd.addMessage("=== " + t("COMPUTATIONAL DIMENSION", "DIMENSIÓN DE CÁLCULO",
                    "DIMENSION DE CALCUL", "DIMENSIONE DI CALCOLO", "BERECHNUNGSDIMENSION",
                    "计算维度", "計算次元") + " ===");
            gd.addChoice("Base_Dimension:", new String[]{"2D basis", "3D basis"}, lastDimIndex == 0 ? "2D basis" : "3D basis");

            // --- Section: Preprocessing ---
            gd.addMessage("=== " + t("PREPROCESSING", "PREPROCESAMIENTO", "PRÉTRAITEMENT",
                    "PREELABORAZIONE", "VORVERARBEITUNG", "预处理", "前処理") + " ===");
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
            gd.addMessage("=== " + t("TEXTURE PARAMETERS (Global)", "PARÁMETROS DE TEXTURA (Global)",
                    "PARAMÈTRES DE TEXTURE (Global)", "PARAMETRI DI TESSITURA (Globale)",
                    "TEXTURPARAMETER (Global)", "纹理参数（全局）", "テクスチャパラメータ（全体）") + " ===");
            gd.addChoice("Texture_Discretization:", new String[]{"Bin Count", "Bin Width"}, lastTexDiscIndex == 0 ? "Bin Count" : "Bin Width");
            gd.addNumericField("Value (Bins/Width):", lastTexDiscVal, 2);
            gd.addNumericField("delta:", lastDelta, 0);
            gd.addToSameRow();
            gd.addNumericField("alpha:", lastAlpha, 0);

            gd.addMessage("=== " + t("INTENSITY PARAMETERS (IVH)", "PARÁMETROS DE INTENSIDAD (IVH)",
                    "PARAMÈTRES D'INTENSITÉ (IVH)", "PARAMETRI DI INTENSITÀ (IVH)",
                    "INTENSITÄTSPARAMETER (IVH)", "强度参数（IVH）", "強度パラメータ（IVH）") + " ===");
            gd.addChoice("IVH_Discretization:", new String[]{"Bin Count", "Bin Width", "Use As-Is"}, lastIvhDiscIndex == 0 ? "Bin Count" : (lastIvhDiscIndex == 1 ? "Bin Width" : "Use As-Is"));
            gd.addNumericField("IVH_Value:", lastIvhDiscVal, 2);

            gd.addMessage("=== " + t("FRACTAL PARAMETERS", "PARÁMETROS FRACTALES", "PARAMÈTRES FRACTALS",
                    "PARAMETRI FRATTALI", "FRAKTAL-PARAMETER", "分形参数", "フラクタルパラメータ") + " ===");
            gd.addStringField("Box_sizes:", lastBoxSizes, 30);

            // --- Section: Feature Selection (single wide grid: 2 rows x 7 columns) ---
            gd.addMessage("=== " + t("FEATURE FAMILIES TO EXTRACT", "FAMILIAS DE CARACTERÍSTICAS A EXTRAER",
                    "FAMILLES DE CARACTÉRISTIQUES À EXTRAIRE", "FAMIGLIE DI CARATTERISTICHE DA ESTRARRE",
                    "ZU EXTRAHIERENDE MERKMALSFAMILIEN", "要提取的特征族", "抽出する特徴ファミリー") + " ===");
            String[] famLabels = {
                "Operational", "Diagnostics", "Morphological", "LocalIntensity", "IntensityStats", "IntensityHistogram", "VolumeHistogram",
                "GLCM", "GLRLM", "GLSZM", "GLDZM", "NGTDM", "NGLDM", "Fractal"
            };
            boolean[] famStates = {
                bOper, bDiag, bMorph, bLocInt, bIntStat, bIntHist, bIvh,
                bGlcm, bGlrlm, bGlszm, bGldzm, bNgtdm, bNgldm, bFrac
            };
            gd.addCheckboxGroup(2, 7, famLabels, famStates);

            // Spatial autocorrelation opt-in (explanation goes to the Log).
            gd.addCheckbox("Spatial_Autocorrelation", bSpatialAutocorr);

            // --- Section: Output ---
            gd.addMessage("=== " + t("OUTPUT", "SALIDA", "SORTIE", "USCITA", "AUSGABE", "输出", "出力") + " ===");
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
            // Preprocessing checkboxes (added BEFORE the feature grid; must be read first).
            bOutliers        = gd.getNextBoolean();
            bRange           = gd.getNextBoolean();
            bResample        = gd.getNextBoolean();
            // Feature-family checkboxes, in the same order as famLabels above.
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
            bSpatialAutocorr = gd.getNextBoolean();
            bExportCsv       = gd.getNextBoolean();

            // --- Execution Logic ---
            ImagePlus image = WindowManager.getImage(wList[lastImgIndex]);
            ImagePlus mask  = WindowManager.getImage(wList[lastMaskIndex]);

            IJ.log("\\Clear");
            IJ.log("==================================================");
            IJ.log(" RadiomicsJ-Lite ");
            IJ.log("==================================================");

            PrintStream originalOut = System.out;

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                long startTime = System.currentTimeMillis();
                IJ.log(">> " + t("Extraction started at: ", "Extracción iniciada a las: ",
                        "Extraction démarrée à : ", "Estrazione avviata alle: ",
                        "Extraktion gestartet um: ", "提取开始于：", "抽出開始時刻: ") + sdf.format(new Date(startTime)));

                ResultsTable rt = new ResultsTable();

                // Reset all relevant static state, then apply this run's config on top.
                resetEngineStateToIBSIDefaults();
                RadiomicsJ.force2D = (lastDimIndex == 0);

                // --- Dimension report + the note that used to crowd the dialog ---
                IJ.log("   " + t("Dimension: ", "Dimensión: ", "Dimension : ", "Dimensione: ",
                        "Dimension: ", "维度：", "次元: ") + (RadiomicsJ.force2D ? "2D basis" : "3D basis"));
                if (RadiomicsJ.force2D) {
                    IJ.log("   " + t(
                        "[NOTE] '2D basis' selected: only the resampling is 2D. Texture and morphological matrices are still computed over the full 3D volume (the slice-by-slice IBSI-2D pipeline is not implemented). For SPECT volumetry use '3D basis'.",
                        "[NOTA] '2D basis' seleccionado: solo el remuestreo es 2D. Las matrices de textura y morfología se siguen calculando sobre el volumen 3D completo (el pipeline IBSI-2D corte a corte no está implementado). Para volumetría SPECT use '3D basis'.",
                        "[NOTE] '2D basis' sélectionné : seul le rééchantillonnage est en 2D. Les matrices de texture et de morphologie sont toujours calculées sur le volume 3D complet (le pipeline IBSI-2D coupe par coupe n'est pas implémenté). Pour la volumétrie SPECT, utilisez '3D basis'.",
                        "[NOTA] '2D basis' selezionato: solo il ricampionamento è 2D. Le matrici di tessitura e morfologia vengono comunque calcolate sull'intero volume 3D (la pipeline IBSI-2D fetta per fetta non è implementata). Per la volumetria SPECT usare '3D basis'.",
                        "[HINWEIS] '2D basis' gewählt: nur das Resampling ist 2D. Textur- und Morphologie-Matrizen werden weiterhin über das gesamte 3D-Volumen berechnet (die schichtweise IBSI-2D-Pipeline ist nicht implementiert). Für SPECT-Volumetrie '3D basis' verwenden.",
                        "[注意] 已选择 '2D basis'：仅重采样为 2D。纹理与形态学矩阵仍在整个 3D 体积上计算（未实现逐层 IBSI-2D 流程）。SPECT 体积测量请使用 '3D basis'。",
                        "[注意] '2D basis' を選択: リサンプリングのみ2Dです。テクスチャと形態の行列は引き続き3Dボリューム全体で計算されます（スライスごとのIBSI-2Dパイプラインは未実装）。SPECTの体積計測には '3D basis' を使用してください。"));
                }

                // --- Calibration sanity check (critical for all 3D measurements) ---
                Calibration ic = image.getCalibration();
                Calibration mc = mask.getCalibration();
                IJ.log("   " + t("Image voxel size (mm): ", "Tamaño de vóxel imagen (mm): ",
                        "Taille de voxel image (mm) : ", "Dimensione voxel immagine (mm): ",
                        "Voxelgröße Bild (mm): ", "图像体素大小 (mm)：", "画像のボクセルサイズ (mm): ")
                        + fmt(ic.pixelWidth) + " x " + fmt(ic.pixelHeight) + " x " + fmt(ic.pixelDepth) + "  [" + ic.getUnit() + "]");
                IJ.log("   " + t("Mask voxel size (mm): ", "Tamaño de vóxel máscara (mm): ",
                        "Taille de voxel masque (mm) : ", "Dimensione voxel maschera (mm): ",
                        "Voxelgröße Maske (mm): ", "掩膜体素大小 (mm)：", "マスクのボクセルサイズ (mm): ")
                        + fmt(mc.pixelWidth) + " x " + fmt(mc.pixelHeight) + " x " + fmt(mc.pixelDepth) + "  [" + mc.getUnit() + "]");
                if (ic.pixelWidth == 1.0 && ic.pixelHeight == 1.0 && ic.pixelDepth == 1.0) {
                    IJ.log("   " + t(
                        "[WARNING] Image spacing is 1x1x1 (uncalibrated?). 3D volumes will be in voxel units, not mm^3. Check Image > Properties.",
                        "[AVISO] El espaciado de la imagen es 1x1x1 (¿sin calibrar?). Los volúmenes 3D estarán en unidades de vóxel, no en mm^3. Revise Image > Properties.",
                        "[AVERTISSEMENT] L'espacement de l'image est 1x1x1 (non calibré ?). Les volumes 3D seront en unités de voxel, pas en mm^3. Vérifiez Image > Properties.",
                        "[AVVISO] La spaziatura dell'immagine è 1x1x1 (non calibrata?). I volumi 3D saranno in unità di voxel, non in mm^3. Controlla Image > Properties.",
                        "[WARNUNG] Bildabstand ist 1x1x1 (nicht kalibriert?). 3D-Volumina sind in Voxel-Einheiten, nicht in mm^3. Prüfen Sie Image > Properties.",
                        "[警告] 图像间距为 1x1x1（未校准？）。3D 体积将以体素为单位，而非 mm^3。请检查 Image > Properties。",
                        "[警告] 画像の間隔が1x1x1です（未校正？）。3D体積はmm^3ではなくボクセル単位になります。Image > Properties を確認してください。"));
                }
                if (Math.abs(ic.pixelWidth - mc.pixelWidth) > 1e-6
                        || Math.abs(ic.pixelHeight - mc.pixelHeight) > 1e-6
                        || Math.abs(ic.pixelDepth - mc.pixelDepth) > 1e-6) {
                    IJ.log("   " + t(
                        "[WARNING] Image and mask voxel sizes differ. Make sure both share the same calibration.",
                        "[AVISO] El tamaño de vóxel de imagen y máscara difiere. Asegúrese de que ambas compartan la misma calibración.",
                        "[AVERTISSEMENT] Les tailles de voxel de l'image et du masque diffèrent. Assurez-vous qu'elles partagent la même calibration.",
                        "[AVVISO] Le dimensioni dei voxel di immagine e maschera differiscono. Assicurati che condividano la stessa calibrazione.",
                        "[WARNUNG] Voxelgrößen von Bild und Maske unterscheiden sich. Stellen Sie sicher, dass beide dieselbe Kalibrierung haben.",
                        "[警告] 图像与掩膜的体素大小不同。请确保两者使用相同的校准。",
                        "[警告] 画像とマスクのボクセルサイズが異なります。両方が同じ校正を共有していることを確認してください。"));
                }

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
                    IJ.log(">> " + t("Executing image preprocessing...", "Ejecutando preprocesamiento de imagen...",
                            "Exécution du prétraitement de l'image...", "Esecuzione preelaborazione immagine...",
                            "Bildvorverarbeitung wird ausgeführt...", "正在执行图像预处理…", "画像の前処理を実行中..."));

                    int calcLabel = 1;

                    // Analysis-ready copies at the ORIGINAL geometry:
                    //  - image -> float32 (applies density calibration; matches the engine's
                    //    createImageCopyAsFloat instead of a raw duplicate)
                    //  - mask  -> float, target label normalised to 1
                    ImagePlus impFloat = Utils.createImageCopyAsFloat(image, false);
                    ImagePlus mskL1    = Utils.initMaskAsFloatAndConvertLabelOne(mask, lastLabel);

                    // Snapshots for DiagnosticsInfo. Use the label-1 mask (calcLabel == 1),
                    // otherwise ROI voxel counts use the wrong label when Mask_Label != 1.
                    ImagePlus originalImp  = impFloat;
                    ImagePlus originalMask = mskL1;

                    ImagePlus imp = impFloat;
                    ImagePlus msk = mskL1;

                    if (bResample) {
                        IJ.log("   - " + t("Applying resampling...", "Aplicando remuestreo...",
                                "Application du rééchantillonnage...", "Applicazione del ricampionamento...",
                                "Resampling wird angewendet...", "正在应用重采样…", "リサンプリングを適用中..."));
                        if (RadiomicsJ.force2D) {
                            imp = Utils.resample2D(imp, false, lastVx, lastVy, RadiomicsJ.interpolation2D);
                            msk = Utils.resample2D(msk, true,  lastVx, lastVy, RadiomicsJ.interpolation2D);
                        } else {
                            imp = Utils.resample3D(imp, false, lastVx, lastVy, lastVz);
                            msk = Utils.resample3D(msk, true,  lastVx, lastVy, lastVz);
                        }
                    }

                    ImagePlus resampledImp  = imp;
                    ImagePlus resampledMask = msk;

                    if (bRange) {
                        IJ.log("   - " + t("Applying range filtering...", "Aplicando filtro de rango...",
                                "Application du filtrage par plage...", "Applicazione del filtro di intervallo...",
                                "Bereichsfilterung wird angewendet...", "正在应用范围过滤…", "範囲フィルタを適用中..."));
                        msk = ImagePreprocessing.rangeFiltering(imp, msk, calcLabel, lastRangeMax, lastRangeMin);
                    }
                    if (bOutliers) {
                        IJ.log("   - " + t("Removing outliers...", "Eliminando outliers...",
                                "Suppression des valeurs aberrantes...", "Rimozione degli outlier...",
                                "Ausreißer werden entfernt...", "正在移除离群值…", "外れ値を除去中..."));
                        RadiomicsJ.zScore = lastOutlierSigma;
                        msk = ImagePreprocessing.outlierFiltering(imp, msk, calcLabel);
                    }
                    ImagePlus resegmentedMask = msk;

                    tPre = System.currentTimeMillis() - mark;

                    rt.incrementCounter();
                    rt.addValue("ID", image.getTitle());

                    // --- PHASE 2: FEATURE EXTRACTION ---
                    IJ.log(">> " + t("Starting feature extraction...", "Iniciando extracción de características...",
                            "Démarrage de l'extraction des caractéristiques...", "Avvio estrazione caratteristiche...",
                            "Merkmalsextraktion wird gestartet...", "开始特征提取…", "特徴抽出を開始..."));

                    if (bOper) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "Operational...");
                        OperationalInfoFeatures oif = new OperationalInfoFeatures(imp);
                        java.util.HashMap<String, String> info = oif.getInfo();
                        for (String key : info.keySet()) {
                            String val = info.get(key);
                            rt.addValue("OperationalInfo_" + key, val != null ? val : "NaN");
                        }
                        tOper = System.currentTimeMillis() - mark;
                    }

                    if (bDiag) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "Diagnostics...");
                        DiagnosticsInfo di = new DiagnosticsInfo(
                                originalImp, originalMask,
                                resampledImp, resampledMask,
                                resegmentedMask, calcLabel);
                        for (DiagnosticsInfoType dinfo : DiagnosticsInfoType.values()) {
                            Double v = di.getDiagnosticsBy(dinfo.name());
                            rt.addValue("Diagnostics_" + dinfo.name(), v != null ? v : Double.NaN);
                        }
                        tDiag = System.currentTimeMillis() - mark;
                    }

                    if (bMorph) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "Morphological...");

                        // One-time explanation of the autocorrelation behaviour (was a dialog note).
                        if (!bSpatialAutocorr) {
                            IJ.log("     " + t(
                                "[NOTE] Spatial autocorrelation OFF: Moran's I and Geary's C reported as NaN. They are O(N^2) over the ROI; on a 3D volume that can take many minutes to hours, hence disabled by default. Tick 'Spatial_Autocorrelation' to compute them.",
                                "[NOTA] Autocorrelación espacial DESACTIVADA: el Índice de Moran y la C de Geary se reportan como NaN. Son O(N^2) sobre el ROI; en un volumen 3D pueden tardar de varios minutos a horas, por eso están desactivadas por defecto. Marque 'Spatial_Autocorrelation' para calcularlas.",
                                "[NOTE] Autocorrélation spatiale DÉSACTIVÉE : l'indice de Moran et le C de Geary sont indiqués comme NaN. Ils sont en O(N^2) sur le ROI ; sur un volume 3D cela peut prendre de plusieurs minutes à plusieurs heures, ils sont donc désactivés par défaut. Cochez 'Spatial_Autocorrelation' pour les calculer.",
                                "[NOTA] Autocorrelazione spaziale DISATTIVATA: l'indice di Moran e il C di Geary sono riportati come NaN. Sono O(N^2) sul ROI; su un volume 3D possono richiedere da diversi minuti a ore, quindi disattivati per impostazione predefinita. Seleziona 'Spatial_Autocorrelation' per calcolarli.",
                                "[HINWEIS] Räumliche Autokorrelation AUS: Morans I und Gearys C werden als NaN ausgegeben. Sie sind O(N^2) über die ROI; bei einem 3D-Volumen kann das viele Minuten bis Stunden dauern, daher standardmäßig deaktiviert. Aktivieren Sie 'Spatial_Autocorrelation', um sie zu berechnen.",
                                "[注意] 空间自相关已关闭：Moran's I 与 Geary's C 报告为 NaN。它们在 ROI 上为 O(N^2)；在 3D 体积上可能需要数分钟到数小时，因此默认禁用。勾选 'Spatial_Autocorrelation' 以计算。",
                                "[注意] 空間的自己相関はオフ: Moran's I と Geary's C は NaN として報告されます。これらはROIに対してO(N^2)で、3Dボリュームでは数分から数時間かかることがあるため既定で無効です。計算するには 'Spatial_Autocorrelation' をオンにしてください。"));
                        } else {
                            IJ.log("     " + t(
                                "[NOTE] Spatial autocorrelation ON: computing Moran's I and Geary's C. O(N^2) over the ROI; on a 3D volume this step alone can take many minutes.",
                                "[NOTA] Autocorrelación espacial ACTIVADA: calculando el Índice de Moran y la C de Geary. O(N^2) sobre el ROI; en un volumen 3D este paso por sí solo puede tardar varios minutos.",
                                "[NOTE] Autocorrélation spatiale ACTIVÉE : calcul de l'indice de Moran et du C de Geary. O(N^2) sur le ROI ; sur un volume 3D, cette étape seule peut prendre plusieurs minutes.",
                                "[NOTA] Autocorrelazione spaziale ATTIVATA: calcolo dell'indice di Moran e del C di Geary. O(N^2) sul ROI; su un volume 3D questo passaggio da solo può richiedere diversi minuti.",
                                "[HINWEIS] Räumliche Autokorrelation EIN: Morans I und Gearys C werden berechnet. O(N^2) über die ROI; bei einem 3D-Volumen kann allein dieser Schritt viele Minuten dauern.",
                                "[注意] 空间自相关已开启：正在计算 Moran's I 与 Geary's C。它们在 ROI 上为 O(N^2)；在 3D 体积上仅此步骤就可能需要数分钟。",
                                "[注意] 空間的自己相関はオン: Moran's I と Geary's C を計算します。ROIに対してO(N^2)で、3Dボリュームではこのステップだけで数分かかることがあります。"));
                        }

                        MorphologicalFeatures mf = new MorphologicalFeatures(imp, msk, calcLabel);
                        for (MorphologicalFeatureType tpe : MorphologicalFeatureType.values()) {
                            boolean isAutocorr = tpe.name().equals("MoransIIndex") || tpe.name().equals("GearysCMeasure");
                            if (isAutocorr && !bSpatialAutocorr) {
                                rt.addValue("Morphological_" + tpe.name(), Double.NaN);
                                continue;
                            }
                            if (isAutocorr && !RadiomicsJ.force2D) {
                                IJ.log("     > " + tCalc() + tpe.name() + " (3D)...");
                            }
                            Double v = mf.calculate(tpe.id());
                            rt.addValue("Morphological_" + tpe.name(), v != null ? v : Double.NaN);
                        }
                        tMorph = System.currentTimeMillis() - mark;
                    }

                    if (bLocInt) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "LocalIntensity...");
                        LocalIntensityFeatures lif = new LocalIntensityFeatures(imp, msk, calcLabel);
                        for (LocalIntensityFeatureType t : LocalIntensityFeatureType.values()) {
                            Double v = lif.calculate(t.id());
                            rt.addValue("LocalIntensity_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tLocInt = System.currentTimeMillis() - mark;
                    }

                    if (bIntStat) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "IntensityStats...");
                        IntensityBasedStatisticalFeatures isf = new IntensityBasedStatisticalFeatures(imp, msk, calcLabel);
                        for (IntensityBasedStatisticalFeatureType t : IntensityBasedStatisticalFeatureType.values()) {
                            Double v = isf.calculate(t.id());
                            rt.addValue("IntensityStats_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tIntStat = System.currentTimeMillis() - mark;
                    }

                    if (bIntHist) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "IntensityHistogram...");
                        IntensityHistogramFeatures ihf = new IntensityHistogramFeatures(imp, msk, calcLabel, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (IntensityHistogramFeatureType t : IntensityHistogramFeatureType.values()) {
                            Double v = ihf.calculate(t.id());
                            rt.addValue("IntensityHistogram_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tIntHist = System.currentTimeMillis() - mark;
                    }

                    if (bIvh) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "VolumeHistogram (IVH)...");
                        IntensityVolumeHistogramFeatures ivhf = new IntensityVolumeHistogramFeatures(imp, msk, calcLabel, ivhMode);
                        for (IntensityVolumeHistogramFeatureType t : IntensityVolumeHistogramFeatureType.values()) {
                            Double v = ivhf.calculate(t.id());
                            rt.addValue("VolumeHistogram_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tIvh = System.currentTimeMillis() - mark;
                    }

                    if (bGlcm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "GLCM...");
                        GLCMFeatures glcm = new GLCMFeatures(imp, msk, calcLabel, (int) lastDelta, useBinCountGlob, binCountGlob, binWidthGlob, null);
                        for (GLCMFeatureType t : GLCMFeatureType.values()) {
                            Double v = glcm.calculate(t.id());
                            rt.addValue("GLCM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tGlcm = System.currentTimeMillis() - mark;
                    }

                    if (bGlrlm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "GLRLM...");
                        GLRLMFeatures glrlm = new GLRLMFeatures(imp, msk, calcLabel, useBinCountGlob, binCountGlob, binWidthGlob, null);
                        for (GLRLMFeatureType t : GLRLMFeatureType.values()) {
                            Double v = glrlm.calculate(t.id());
                            rt.addValue("GLRLM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tGlrlm = System.currentTimeMillis() - mark;
                    }

                    if (bGlszm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "GLSZM...");
                        GLSZMFeatures glszm = new GLSZMFeatures(imp, msk, calcLabel, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (GLSZMFeatureType t : GLSZMFeatureType.values()) {
                            Double v = glszm.calculate(t.id());
                            rt.addValue("GLSZM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tGlszm = System.currentTimeMillis() - mark;
                    }

                    if (bGldzm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "GLDZM...");
                        GLDZMFeatures gldzm = new GLDZMFeatures(imp, msk, calcLabel, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (GLDZMFeatureType t : GLDZMFeatureType.values()) {
                            Double v = gldzm.calculate(t.id());
                            rt.addValue("GLDZM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tGldzm = System.currentTimeMillis() - mark;
                    }

                    if (bNgtdm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "NGTDM...");
                        NGTDMFeatures ngtdm = new NGTDMFeatures(imp, msk, calcLabel, (int) lastDelta, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (NGTDMFeatureType t : NGTDMFeatureType.values()) {
                            Double v = ngtdm.calculate(t.id());
                            rt.addValue("NGTDM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tNgtdm = System.currentTimeMillis() - mark;
                    }

                    if (bNgldm) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "NGLDM...");
                        NGLDMFeatures ngldm = new NGLDMFeatures(imp, msk, calcLabel, (int) lastAlpha, (int) lastDelta, useBinCountGlob, binCountGlob, binWidthGlob);
                        for (NGLDMFeatureType t : NGLDMFeatureType.values()) {
                            Double v = ngldm.calculate(t.id());
                            rt.addValue("NGLDM_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tNgldm = System.currentTimeMillis() - mark;
                    }

                    if (bFrac) {
                        mark = System.currentTimeMillis();
                        IJ.log("   > " + tCalc() + "Fractal...");
                        FractalFeatures ff = new FractalFeatures(imp, msk, calcLabel, boxArray);
                        for (FractalFeatureType t : FractalFeatureType.values()) {
                            Double v = ff.calculate(t.id());
                            rt.addValue("Fractal_" + t.name(), v != null ? v : Double.NaN);
                        }
                        tFrac = System.currentTimeMillis() - mark;
                    }

                    long endTime = System.currentTimeMillis();
                    long durationSec = (endTime - startTime) / 1000;

                    // --- PERFORMANCE REPORT (family labels kept in English to match CSV columns) ---
                    IJ.log("\n==================================================");
                    IJ.log(" " + t("PERFORMANCE REPORT (Profiling)", "REPORTE DE RENDIMIENTO (Profiling)",
                            "RAPPORT DE PERFORMANCE (Profilage)", "RAPPORTO PRESTAZIONI (Profiling)",
                            "LEISTUNGSBERICHT (Profiling)", "性能报告（Profiling）", "パフォーマンスレポート（プロファイリング）"));
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

                    IJ.log("\n " + t("End time: ", "Hora de fin: ", "Heure de fin : ", "Ora di fine: ",
                            "Endzeit: ", "结束时间：", "終了時刻: ") + sdf.format(new Date(endTime)));
                    IJ.log(" " + t("Total processing time: ", "Tiempo total de procesamiento: ",
                            "Temps total de traitement : ", "Tempo totale di elaborazione: ",
                            "Gesamte Verarbeitungszeit: ", "总处理时间：", "総処理時間: ")
                            + (durationSec / 60) + " min " + (durationSec % 60) + " sec\n");

                    if (rt.getCounter() == 0) throw new Exception(t("Empty results table.", "Tabla de resultados vacía.",
                            "Table de résultats vide.", "Tabella dei risultati vuota.", "Leere Ergebnistabelle.",
                            "结果表为空。", "結果テーブルが空です。"));

                    rt.show("RadiomicsJ-Lite Results");

                    if (bExportCsv) {
                        SaveDialog sd = new SaveDialog(t("Save Results", "Guardar Resultados", "Enregistrer les résultats",
                                "Salva risultati", "Ergebnisse speichern", "保存结果", "結果を保存"),
                                "radiomics_features", ".csv");
                        if (sd.getDirectory() != null && sd.getFileName() != null) {
                            File csvFile = new File(sd.getDirectory(), sd.getFileName());
                            rt.save(csvFile.getAbsolutePath());
                            IJ.log(">> " + t("Exported to: ", "Exportado a: ", "Exporté vers : ", "Esportato in: ",
                                    "Exportiert nach: ", "已导出到：", "エクスポート先: ") + csvFile.getAbsolutePath());
                        }
                    }

                } finally {
                    System.setOut(originalOut);
                }

                break;

            } catch (Throwable e) {
                IJ.log("\n[ERROR] " + t("Critical failure during extraction:", "Fallo crítico durante la extracción:",
                        "Échec critique pendant l'extraction :", "Errore critico durante l'estrazione:",
                        "Kritischer Fehler während der Extraktion:", "提取过程中发生严重错误：", "抽出中に重大なエラーが発生しました:"));
                IJ.log(e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) IJ.log("  " + element.toString());
                IJ.log("\n" + t("Returning to configuration window...", "Volviendo a la ventana de configuración...",
                        "Retour à la fenêtre de configuration...", "Ritorno alla finestra di configurazione...",
                        "Zurück zum Konfigurationsfenster...", "正在返回配置窗口…", "設定ウィンドウに戻ります..."));
            }
        }
    }

    /** Localised "Calculating " prefix; feature-family names stay English (they match CSV columns). */
    private String tCalc() {
        return t("Calculating ", "Calculando ", "Calcul de ", "Calcolo di ", "Berechne ", "正在计算 ", "計算中: ");
    }
}