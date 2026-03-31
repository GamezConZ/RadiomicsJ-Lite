[English](#english) | [Español](#español)
## English
# RadiomicsJ-Lite

A minimalist, high-throughput Fiji/ImageJ plugin for Radiomic feature extraction. 

**RadiomicsJ-Lite** acts as a direct GUI wrapper for the powerful [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) engine (v2.x). It is designed with one single philosophy in mind: **100% integration with the Fiji ecosystem.**

If calculating radiomic features is your goal, this plugin is designed to be the immediate next step after learning the basics of Fiji.

## Why "Lite"?
There is already an excellent and comprehensive [RadiomicsJ GUI Plugin](https://github.com/tatsunidas/RadiomicsJ-ImageJ-Plugin) that includes Weka segmentation, advanced visualization, and folder-based batch processing. 

**So, why use this Lite version?**
This version strips away the charts, the machine learning models, and the complex UI to provide a raw, native Fiji `GenericDialog` interface. This architectural choice provides three critical advantages for reproducible research:

1. **Macro Recorder Compatibility:** Every single parameter you tweak (from IBSI standardization bins to 3D interpolation) is instantly translated into Fiji's Macro language. You can record your setup once and process thousands of patients using standard ImageJ macro loops.
2. **Zero Bloat:** It only does two things: takes an Image and a Mask currently open in Fiji, and outputs a `.csv` file. 
3. **Live Verbosely:** It hijacks the internal engine's console and outputs the progress directly to the Fiji Log and Status Bar, so you always know what your processor is doing during heavy 3D matrix calculations.

## Installation
1. Download the latest `RadiomicsJ_Lite-jar-with-dependencies.jar` from the [Releases](../../releases) tab.
2. Drag and drop the `.jar` file into your Fiji `plugins` folder.
3. Restart Fiji.
4. You will find the tool at the bottom of the `Plugins` menu.

## Usage & Macro Example

Simply open your base image (e.g., a NIfTI or DICOM stack) and your segmentation mask in Fiji, and run the plugin.

Because the plugin uses native ImageJ components, you can fully automate your IBSI-compliant extraction pipeline. Here is an example of the macro code generated automatically by Fiji when using this plugin:

```ijm
run("RadiomicsJ Lite", "imagen_original=001.tif mascara=001-1.tif dimension_base=[3D basis] texture_discretization=[Bin Count] ivh_discretization=[Bin Count] label_value=255 sigma=3 min=0 max=255 vx=1 vy=1 vz=1 texture_bin_value=16 delta=1 alpha=1 ivh_bin_value=16 box_sizes=2,3,4,6,8,12,16,32,64 resampling operational diagnostics morphological localintensity intensitystats intensityhistogram volumehistogram glcm glrlm glszm gldzm ngtdm ngldm fractal exportar_csv guardar=C:/Path/To/Your/radiomics_features.csv");
```

## Acknowledgements & License
* **RadiomicsJ-Lite** is released under the [MIT License](LICENSE).
* The core mathematical engine is powered by [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) created by @tatsunidas, which is licensed under the Apache License 2.0.

---

## Español
# RadiomicsJ-Lite

Un plugin minimalista y de alto rendimiento para Fiji/ImageJ diseñado para la extracción de características radiómicas.

**RadiomicsJ-Lite** actúa como una interfaz gráfica (GUI) directa para el potente motor [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) (v2.x). Está diseñado con una única filosofía en mente: **100% de integración con el ecosistema de Fiji.**

Si tu objetivo es calcular características radiómicas, este plugin está diseñado para ser el siguiente paso lógico e inmediato después de aprender lo básico de Fiji.

## ¿Por qué "Lite"?
Ya existe un [RadiomicsJ GUI Plugin](https://github.com/tatsunidas/RadiomicsJ-ImageJ-Plugin) excelente y muy completo que incluye segmentación con Weka, visualización avanzada y procesamiento por lotes basado en carpetas.

**Entonces, ¿por qué usar esta versión Lite?**
Esta versión elimina los gráficos, los modelos de *machine learning* y las interfaces complejas para ofrecer una interfaz directa y nativa basada en el `GenericDialog` de ImageJ. Esta decisión arquitectónica proporciona tres ventajas críticas para la investigación reproducible:

1. **Compatibilidad con el Macro Recorder:** Cada parámetro que ajustas (desde los *bins* de estandarización IBSI hasta la interpolación 3D) se traduce instantáneamente al lenguaje de macros de Fiji. Puedes grabar tu configuración una vez y procesar miles de pacientes usando bucles estándar en ImageJ.
2. **Cero Sobrecarga (Zero Bloat):** Hace estrictamente dos cosas: toma una Imagen y una Máscara que ya estén abiertas en Fiji, y exporta un archivo `.csv`.
3. **Verbosidad en Vivo:** Intercepta la consola interna del motor matemático y muestra el progreso directamente en la ventana de Logs y en la barra de estado de Fiji. De esta forma, siempre sabrás qué está haciendo tu procesador durante los pesados cálculos de matrices 3D.

## Instalación
1. Descarga el archivo `RadiomicsJ_Lite-jar-with-dependencies.jar` más reciente desde la pestaña de [Releases](../../releases).
2. Arrastra y suelta el archivo `.jar` dentro de la carpeta `plugins` de tu instalación de Fiji.
3. Reinicia Fiji.
4. Encontrarás la herramienta al final del menú `Plugins`.

## Uso y Ejemplo de Macro

Simplemente abre tu imagen base (ej. un volumen NIfTI o DICOM) y tu máscara de segmentación en Fiji, y ejecuta el plugin.

Debido a que el plugin utiliza componentes nativos de ImageJ, puedes automatizar completamente tu *pipeline* de extracción compatible con la normativa IBSI. Aquí tienes un ejemplo del código de macro generado automáticamente por Fiji al usar este plugin:

```ijm
run("RadiomicsJ Lite", "imagen_original=001.tif mascara=001-1.tif dimension_base=[3D basis] texture_discretization=[Bin Count] ivh_discretization=[Bin Count] label_value=255 sigma=3 min=0 max=255 vx=1 vy=1 vz=1 texture_bin_value=16 delta=1 alpha=1 ivh_bin_value=16 box_sizes=2,3,4,6,8,12,16,32,64 resampling operational diagnostics morphological localintensity intensitystats intensityhistogram volumehistogram glcm glrlm glszm gldzm ngtdm ngldm fractal exportar_csv guardar=C:/Ruta/Hacia/Tu/radiomics_features.csv");
```

## Agradecimientos y Licencia
* **RadiomicsJ-Lite** se publica bajo la [Licencia MIT](LICENSE).
* El motor matemático central está impulsado por [RadiomicsJ](https://github.com/tatsunidas/RadiomicsJ) creado por @tatsunidas, el cual está licenciado bajo la Licencia Apache 2.0.