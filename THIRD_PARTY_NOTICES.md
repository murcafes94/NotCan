# Third-party notices

NotCan se desarrolla con una política explícita de procedencia y licencias. La versión local-first no requiere un proveedor de IA de pago por tokens.

## Dependencias incorporadas

### whisper-android / whisper.cpp
- AAR: `dev.ffmpegkit-maintained:whisper-android:1.0.0`.
- Motor subyacente: `ggml-org/whisper.cpp`.
- Licencia declarada por ambos proyectos: MIT.
- Uso en NotCan: transcripción final de archivos completamente local en Android.
- El modelo `ggml-large-v3-turbo.bin` no se empaqueta en la APK; el usuario lo descarga por separado desde el repositorio de modelos de whisper.cpp/Hugging Face.
- NotCan no incorpora código de NotelyVoice para esta función.

### llama.cpp Android
- Proyecto: `ggml-org/llama.cpp`.
- Licencia: MIT.
- Commit fijado en el submódulo `third_party/llama.cpp`: `192067b72d1b7a3653b3f0c59190303b18596637`.
- Uso en NotCan: inferencia GGUF local para el asistente académico, mediante el puente Android/JNI oficial del proyecto.
- El modelo generativo no se empaqueta dentro de la APK.

### DeepSeek-R1-Distill-Qwen-1.5B
- Modelo original: `deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B`, publicado por DeepSeek como modelo destilado basado en Qwen2.5.
- Uso en NotCan: cerebro local inicial del asistente académico.
- Cuantización utilizada: `bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF`, archivo `Q4_K_M`, aproximadamente 1,1 GB.
- SHA-256 publicado para el GGUF seleccionado: `1741e5b2d062b07acf048bf0d2c514dadf2a48f94e2b4aa0cfe069af3838ee2f`.
- Se descarga bajo petición del usuario y se ejecuta localmente; NotCan no envía las fuentes académicas a una API de DeepSeek.

### sherpa-onnx
- Proyecto: `k2-fsa/sherpa-onnx`.
- Licencia: Apache-2.0.
- Runtime Android fijado: `sherpa-onnx-1.13.6.aar`.
- SHA-256 del AAR verificado durante la compilación: `0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698`.
- Uso en NotCan: transcripción provisional local mientras se graba una clase.
- Modelo: `sherpa-onnx-moonshine-base-es-quantized-2026-02-27`, español, aproximadamente 63 MB; se descarga por separado desde los releases oficiales de sherpa-onnx.

### Apache Commons Compress
- Artefacto: `org.apache.commons:commons-compress:1.27.1`.
- Licencia: Apache-2.0.
- Uso: extraer localmente el paquete `.tar.bz2` del modelo Moonshine después de la descarga.

### Compose Rich Editor
- Artefacto: `com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc10`.
- Proyecto: `MohamedRejeb/compose-rich-editor`.
- Licencia: Apache-2.0.
- Uso en NotCan: editor WYSIWYG de apuntes; la persistencia interna usa Markdown para conservar formato sin mostrar marcadores al usuario.

## Repositorios estudiados

### NotelyVoice
- Repositorio: `Notely-Voice/NotelyVoice`
- Licencia declarada: GPL-3.0-only.
- Uso actual en NotCan: referencia arquitectónica y funcional para grabación, notas y manejo de audios largos.
- Estado de código incorporado: **ninguno**.
- Nota: si en el futuro se copia o adapta código GPL-3.0 de forma que constituya una obra derivada distribuida, habrá que cumplir íntegramente las obligaciones de GPL-3.0.

### Say
- Repositorio: `addyosmani/say`
- Licencia: MIT.
- Uso actual: referencia funcional/UI para grabación, forma de onda, transcripción y edición.
- Código incorporado: ninguno.

### Handy
- Repositorio: `cjpais/Handy`
- Licencia: MIT.
- Uso actual: referencia para procesamiento de audio, VAD y flujo de transcripción.
- Código incorporado: ninguno.

### IA-PARA-TODOS
- Repositorio: `0xnavarro/IA-PARA-TODOS`
- Licencia declarada: Apache-2.0.
- Uso actual: referencia conceptual para RAG, agentes y memoria/contexto.
- Código incorporado: ninguno.

## Política

Antes de copiar o adaptar código externo se debe:

1. verificar la licencia en el commit concreto utilizado;
2. registrar archivo, origen y modificación;
3. conservar avisos de copyright y licencia cuando corresponda;
4. revisar compatibilidad con la licencia general de NotCan antes de distribuir binarios.
