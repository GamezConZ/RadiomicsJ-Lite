[English](#english) | [Español](#español)
## English

# RadiomicsJ-Lite 🔬

A minimalist, high-throughput Fiji/ImageJ plugin for Radiomic feature extraction. 

**RadiomicsJ-Lite** acts as a direct GUI wrapper for the powerful [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) engine. It is designed with a single philosophy in mind: **100% integration with the Fiji ecosystem for reproducible research.**

If calculating radiomic features is your goal, this plugin provides a straightforward path from image segmentation to data extraction without leaving the native ImageJ environment.

### Key Features
While there is an excellent and comprehensive [RadiomicsJ GUI Plugin](https://github.com/tatsunidas/RadiomicsJ_Plugin) available, this Lite version strips away complex interfaces to provide a raw, native Fiji `GenericDialog`. This architectural choice offers specific advantages for bulk processing:

1. **Macro Recorder Compatibility:** Every parameter you configure is instantly translated into Fiji's Macro language. You can record your setup once and process large cohorts using standard ImageJ macro loops.
2. **Stable 3D Processing:** It includes a hardcoded bypass for computationally prohibitive spatial autocorrelation features in 3D (Moran's I and Geary's C), allowing full volume extractions in minutes rather than hours, while preventing memory exhaustion.
3. **Performance Profiling:** Upon completion, it outputs a detailed execution time report to the Fiji Log, allowing researchers to audit the computational cost of each feature family.
4. **Zero Bloat:** It simply takes an Image and a Mask currently open in Fiji, applies optional preprocessing (resampling, range filtering, outlier removal), and outputs a standard `.csv` file. 

### Installation
1. Download the latest `.jar` file (or `-jar-with-dependencies.jar`) from the [Releases](../../releases) tab.
2. Drag and drop the file into your Fiji `plugins` folder.
3. Restart Fiji.
4. You will find the tool at the bottom of the `Plugins` menu.

### Usage & Macro Example

Open your base image (e.g., a NIfTI or DICOM stack) and your segmentation mask in Fiji, and run the plugin. Ensure both images have the same dimensions. 

Because the plugin uses native ImageJ components, you can fully automate your extraction pipeline. Here is an example of the macro code generated automatically by Fiji:

```ijm
run("RadiomicsJ-Lite", "original_image=tumor.tif mask=tumor_mask.tif base_dimension=[3D basis] mask_label=1 remove_outliers sigma=3.00 range_filtering min=0.00 max=255.00 resampling vx=2.00 vy=2.00 vz=2.00 texture_discretization=[Bin Count] value=16.00 delta=1 alpha=1 ivh_discretization=[Bin Count] ivh_value=16.00 box_sizes=2,3,4,6,8,12,16,32,64 operational diagnostics morphological localintensity intensitystats intensityhistogram volumehistogram glcm glrlm glszm gldzm ngtdm ngldm fractal export_results_to_csv");
```

### Acknowledgements & License
* **RadiomicsJ-Lite** is released under the [MIT License](LICENSE).
* The core mathematical engine is powered by [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) created by @tatsunidas, which is licensed under the Apache License 2.0.

---

## Español

# RadiomicsJ-Lite 🔬

Un plugin minimalista y de alto rendimiento para Fiji/ImageJ diseñado para la extracción de características radiómicas.

**RadiomicsJ-Lite** actúa como una interfaz gráfica (GUI) directa para el potente motor [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ). Está diseñado con una única filosofía en mente: **100% de integración con el ecosistema de Fiji para una investigación reproducible.**

Si tu objetivo es calcular características radiómicas, este plugin proporciona un camino directo desde la segmentación de la imagen hasta la extracción de datos sin salir del entorno nativo de ImageJ.

### Características Principales
Aunque ya existe un [RadiomicsJ GUI Plugin](https://github.com/tatsunidas/RadiomicsJ_Plugin) excelente y muy completo, esta versión Lite elimina las interfaces complejas para ofrecer una ventana basada estrictamente en el `GenericDialog` de Fiji. Esta decisión arquitectónica proporciona ventajas específicas para el procesamiento masivo:

1. **Compatibilidad con el Macro Recorder:** Cada parámetro que ajustas se traduce instantáneamente al lenguaje de macros de Fiji. Puedes grabar tu configuración una vez y procesar grandes cohortes de pacientes usando bucles estándar en ImageJ.
2. **Procesamiento 3D Estable:** Incluye una omisión programada (*bypass*) para características de autocorrelación espacial computacionalmente prohibitivas en 3D (Índice de Moran y C de Geary). Esto permite extracciones volumétricas completas en minutos en lugar de horas, previniendo el agotamiento de la memoria RAM.
3. **Perfilado de Rendimiento (Profiling):** Al finalizar, imprime un reporte detallado del tiempo de ejecución en el Log de Fiji, permitiendo a los investigadores auditar el costo computacional exacto de cada familia de características.
4. **Cero Sobrecarga (Zero Bloat):** Simplemente toma una Imagen y una Máscara abiertas en Fiji, aplica preprocesamiento opcional (remuestreo, filtro de rango, eliminación de outliers) y exporta un archivo `.csv` estándar.

### Instalación
1. Descarga el archivo `.jar` (o `-jar-with-dependencies.jar`) más reciente desde la pestaña de [Releases](../../releases).
2. Arrastra y suelta el archivo dentro de la carpeta `plugins` de tu instalación de Fiji.
3. Reinicia Fiji.
4. Encontrarás la herramienta al final del menú `Plugins`.

### Uso y Ejemplo de Macro

Abre tu imagen base (ej. un volumen NIfTI o DICOM) y tu máscara de segmentación en Fiji, y ejecuta el plugin. Asegúrate de que ambas imágenes tengan las mismas dimensiones.

Debido a que el plugin utiliza componentes nativos de ImageJ, puedes automatizar completamente tu *pipeline* de extracción. Aquí tienes un ejemplo del código de macro generado automáticamente por Fiji:

```ijm
run("RadiomicsJ-Lite", "original_image=tumor.tif mask=tumor_mask.tif base_dimension=[3D basis] mask_label=1 remove_outliers sigma=3.00 range_filtering min=0.00 max=255.00 resampling vx=2.00 vy=2.00 vz=2.00 texture_discretization=[Bin Count] value=16.00 delta=1 alpha=1 ivh_discretization=[Bin Count] ivh_value=16.00 box_sizes=2,3,4,6,8,12,16,32,64 operational diagnostics morphological localintensity intensitystats intensityhistogram volumehistogram glcm glrlm glszm gldzm ngtdm ngldm fractal export_results_to_csv");
```

### Agradecimientos y Licencia
* **RadiomicsJ-Lite** se publica bajo la [Licencia MIT](LICENSE).
* El motor matemático central está impulsado por [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) creado por @tatsunidas, el cual está licenciado bajo la Licencia Apache 2.0.
