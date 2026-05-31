[English](#english) | [Español](#español)

---

## English

# RadiomicsJ-Lite 🔬

A minimalist, macro-recordable Fiji/ImageJ plugin for radiomic feature extraction.

RadiomicsJ-Lite is a direct GUI wrapper around the [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) engine. Its only goal is **full integration with the Fiji ecosystem for reproducible research**: from segmentation to CSV without leaving ImageJ.

A more complete alternative exists ([RadiomicsJ GUI Plugin](https://github.com/tatsunidas/RadiomicsJ_Plugin)); this Lite version drops the complex UI in favour of a native `GenericDialog`, which is what enables the points below.

### Key features

- **Macro Recorder ready** — every parameter becomes a macro key, so a single recording scales to a full cohort with a standard ImageJ loop.
- **Multilingual UI (7 languages)** — English, Español, Français, Italiano, Deutsch, 中文, 日本語. The language is picked in a small dialog **before** the main window opens, so everything renders in your language from the start. Only the chrome is translated; field labels (the macro keys) stay in English, so macros remain portable across languages. *(Chinese/Japanese need CJK fonts installed; otherwise they show as boxes — a display-only issue that never affects results.)*
- **Stable 3D extraction** — spatial autocorrelation features (Moran's I, Geary's C) are O(N²) over the ROI and can take many minutes to hours in 3D, so they ship **off by default** behind a single checkbox; the rest of the volume extraction runs in minutes. `3D basis` is the validated mode.
- **Performance report** — per-family execution times printed to the Log on completion, along with the voxel calibration it detected.
- **Zero bloat** — takes an open image and mask, applies optional preprocessing (resampling, range filter, outlier removal), exports a standard `.csv`.

### Installation

1. Download the latest `.jar` (or `-jar-with-dependencies.jar`) from [Releases](../../releases).
2. Drop it into Fiji's `plugins` folder and restart.
3. Find the tool at the bottom of the `Plugins` menu.

### Usage

Open the image and the segmentation mask, then run the plugin. Image and mask must share the same dimensions **and the same voxel calibration in mm**: every 3D volume depends on `Image ▸ Properties` being correct. TIFFs often arrive with a 1×1×1 default — the plugin logs the spacing it sees and warns when this looks suspicious.

Auto-recorded macro (all defaults, every feature family enabled). `language=` is recorded for completeness but is purely cosmetic — it does not affect the computation:

```ijm
run("RadiomicsJ-Lite", "language=English original_image=tumor.nii mask=tumor_mask.nii base_dimension=[3D basis] mask_label=1 sigma=3.00 min=0.00 max=255.00 resampling vx=2.00 vy=2.00 vz=2.00 texture_discretization=[Bin Count] value=16.00 delta=1 alpha=0 ivh_discretization=[Bin Count] ivh_value=16.00 box_sizes=2,3,4,6,8,12,16,32,64 operational diagnostics morphological localintensity intensitystats intensityhistogram volumehistogram glcm glrlm glszm gldzm ngtdm ngldm fractal export_to_csv");
```

Append `spatial_autocorrelation` to also compute Moran's I and Geary's C (slow in 3D).

### Acknowledgements & License

- RadiomicsJ-Lite is released under the [MIT License](LICENSE).
- The mathematical engine is [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) by @tatsunidas, under the Apache License 2.0.

---

## Español

# RadiomicsJ-Lite 🔬

Un plugin minimalista para Fiji/ImageJ que extrae características radiómicas y se puede grabar como macro.

RadiomicsJ-Lite es una interfaz directa para el motor [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ). Su única meta es **integración total con el ecosistema Fiji para investigación reproducible**: de la segmentación al CSV sin salir de ImageJ.

Existe una alternativa más completa ([RadiomicsJ GUI Plugin](https://github.com/tatsunidas/RadiomicsJ_Plugin)); esta versión Lite cambia la UI compleja por un `GenericDialog` nativo, que es lo que habilita lo siguiente.

### Características principales

- **Compatible con el Macro Recorder** — cada parámetro se convierte en una clave de macro, así una sola grabación procesa toda una cohorte con un bucle estándar de ImageJ.
- **UI multilingüe (7 idiomas)** — English, Español, Français, Italiano, Deutsch, 中文, 日本語. El idioma se elige en un cuadro de diálogo **antes** de abrir la ventana principal, así todo se dibuja directamente en tu idioma. Solo se traduce el "envoltorio"; las etiquetas de los campos (las claves de las macros) siguen en inglés, así una macro se ejecuta igual en cualquier idioma. *(El chino/japonés requieren fuentes CJK instaladas; si no, se ven como cuadros — un problema solo de visualización que nunca afecta los resultados.)*
- **Extracción 3D estable** — las características de autocorrelación espacial (Índice de Moran, C de Geary) son O(N²) sobre el ROI y pueden tardar de minutos a horas en 3D, así que vienen **desactivadas por defecto** detrás de una casilla; el resto del volumen se extrae en minutos. `3D basis` es el modo validado.
- **Reporte de rendimiento** — tiempos por familia de características impresos en el Log al terminar, junto con la calibración de vóxel detectada.
- **Cero sobrecarga** — toma una imagen y una máscara ya abiertas, aplica preprocesamiento opcional (remuestreo, filtro de rango, eliminación de outliers) y exporta un `.csv` estándar.

### Instalación

1. Descarga el `.jar` (o `-jar-with-dependencies.jar`) más reciente desde [Releases](../../releases).
2. Suéltalo dentro de la carpeta `plugins` de Fiji y reinicia.
3. La herramienta aparece al final del menú `Plugins`.

### Uso

Abre la imagen base y la máscara de segmentación, y ejecuta el plugin. Imagen y máscara deben tener las mismas dimensiones **y la misma calibración de vóxel en mm**: todo el cálculo 3D depende de que `Image ▸ Properties` esté bien. Los TIFF suelen llegar con un 1×1×1 por defecto — el plugin registra en el Log el espaciado que detecta y avisa cuando parece sospechoso.

Macro grabada automáticamente (todos los valores por defecto, todas las familias activadas). `language=` se graba por completitud pero es puramente estético — no afecta el cálculo:

```ijm
run("RadiomicsJ-Lite", "language=English original_image=tumor.nii mask=tumor_mask.nii base_dimension=[3D basis] mask_label=1 sigma=3.00 min=0.00 max=255.00 resampling vx=2.00 vy=2.00 vz=2.00 texture_discretization=[Bin Count] value=16.00 delta=1 alpha=0 ivh_discretization=[Bin Count] ivh_value=16.00 box_sizes=2,3,4,6,8,12,16,32,64 operational diagnostics morphological localintensity intensitystats intensityhistogram volumehistogram glcm glrlm glszm gldzm ngtdm ngldm fractal export_to_csv");
```

Añade `spatial_autocorrelation` al final para calcular además el Índice de Moran y la C de Geary (lento en 3D).

### Agradecimientos y Licencia

- RadiomicsJ-Lite se publica bajo la [Licencia MIT](LICENSE).
- El motor matemático es [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) de @tatsunidas, bajo Apache License 2.0.