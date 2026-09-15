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

### Qwen3-0.6B-GGUF
- Modelo: `Qwen/Qwen3-0.6B-GGUF`.
- Licencia: Apache-2.0.
- Uso en NotCan desde v0.7.5: cerebro local ligero del asistente académico.
- Cuantización seleccionada: `Qwen3-0.6B-Q8_0.gguf`, aproximadamente 639 MB.
- Se descarga bajo petición explícita del usuario desde Hugging Face y se ejecuta localmente mediante llama.cpp.
- NotCan usa por defecto `/no_think` para reducir latencia y consumo en Android.
- El modelo anterior `DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf` se elimina cuando Qwen3 queda instalado para evitar ocupar aproximadamente 1,1 GB adicionales.

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

### Omi
- Repositorio: `BasedHardware/omi`.
- Licencia del repositorio principal: MIT.
- Uso actual: referencia arquitectónica para audio, streaming, memoria, transcripción y herramientas; no se ha incorporado código de Omi en v0.7.5.

### Presenton
- Repositorio: `presenton/presenton`.
- Licencia: Apache-2.0.
- Uso previsto: referencia para generación local/self-hosted de presentaciones, plantillas, edición y exportación PPTX/PDF.
- Estado de código incorporado: **ninguno**.
- Nota: no se incrustará su stack Electron/Python/Docker dentro del APK; NotCan mantendrá un modelo de documento y exportación propios, con posible conector opcional a una instancia self-hosted.

### AI Manus
- Repositorio: `Simpleyyt/ai-manus`.
- Licencia: MIT.
- Uso previsto: referencia para plan-and-execute, tool calling estructurado, catálogo de tools y skills con carga progresiva.
- Estado de código incorporado: **ninguno**.
- Nota: no se incorporarán Docker, MongoDB, Redis ni un shell/browser irrestricto al APK; las ideas se adaptarán al TuNot Harness nativo con permisos explícitos.

### Master Pedagogy Skill
- Repositorio: `XenogenesisXtreme/Master-Pedagogy-Skill`.
- Licencia: MIT.
- Uso previsto: referencia para comprobación de prerrequisitos, mastery checks, modo socrático, rutas de profundidad y recuperación de conceptos fallados.
- Estado de código incorporado: **ninguno**.
- Nota: la gamificación se tratará como opcional y la implementación no dependerá de Claude ni Manus.

### Planning with Files
- Repositorio: `OthmanAdi/planning-with-files`.
- Licencia: MIT.
- Uso previsto: referencia para persistir objetivos, fases, hallazgos y progreso fuera del contexto del LLM y reanudar tareas largas sin perder el estado.
- Estado de código incorporado: **ninguno**.
- Nota: en NotCan se trasladará el patrón a Room/archivos locales en lugar de hooks propios de agentes de desarrollo.

### Frappe Builder
- Repositorio: `frappe/builder`.
- Licencia: MIT.
- Uso actual: referencia únicamente para edición visual responsive, cambios reversibles y edición asistida por IA sobre estado real.
- Estado de código incorporado: **ninguno**.
- Nota: NotCan no añadirá un constructor de sitios web; solo se consideran patrones útiles para la PWA/editor.

### Kimi K2
- Repositorio: `MoonshotAI/Kimi-K2`.
- Licencia: Modified MIT License.
- Uso actual: referencia de arquitectura/benchmark para modelos agentic y tool use.
- Estado de código/modelo incorporado: **ninguno**.
- Nota: su tamaño (1T parámetros totales, 32B activos) lo descarta como modelo local Android.

### Kimi K3
- Repositorio: `MoonshotAI/Kimi-K3`.
- Licencia: Kimi K3 License, con condiciones específicas para ciertos usos comerciales y de Model-as-a-Service.
- Uso actual: referencia de arquitectura/benchmark para multimodalidad, contexto largo y agentes.
- Estado de código/modelo incorporado: **ninguno**.
- Nota: su tamaño (2.8T parámetros totales, 104B activos) lo descarta como modelo local Android; cualquier uso futuro sería mediante endpoint externo opcional y tras volver a revisar su licencia.

### Dokie AI PPT Skill
- Repositorio: `MYZY-AI/dokie-ai-ppt`.
- Commit revisado: `3e7010c865d07049480eb34d680db31722af75c9`.
- Licencia: el README declara MIT, pero en el commit revisado **no existe un archivo LICENSE en la raíz del repositorio**.
- Uso previsto: referencia conceptual para workflow de presentaciones, temas, HTML interactivo, gráficos, animaciones y validación posterior de calidad.
- Estado de código incorporado: **ninguno**.
- Nota: no se copiará código hasta que la licencia quede inequívocamente documentada; NotCan no dependerá de `dokie-cli` ni de Dokie Cloud.

### Skywork DeepResearchAgent
- Repositorio: `SkyworkAI/DeepResearchAgent`.
- Commit revisado: `5e3c95d14266f8c4aa6a5deae1fe165c7cd1b87b`.
- Licencia: MIT.
- Uso previsto: referencia para recursos versionados, lifecycle, tracing, memoria, herramientas componibles y ciclo seguro de proponer/evaluar/confirmar cambios con rollback.
- Estado de código incorporado: **ninguno**.
- Nota: NotCan no permitirá auto-modificación libre del agente en producción; cualquier evolución automática deberá pasar por benchmark, versionado y aprobación explícita.

### Skywork Skills
- Repositorio: `SkyworkAI/Skywork-Skills`.
- Commit revisado: `c8c6aeb742c3d6a2b728992142796702464b6fce`.
- Licencia: MIT.
- Uso previsto: referencia para organización de skills de documentos, presentaciones, hojas de cálculo e investigación, y para operaciones locales de PPTX.
- Estado de código incorporado: **ninguno**.
- Nota: varias skills dependen de servicios remotos. `skywork-ppt` requiere `SKYWORK_API_KEY` para generación/imitación/edición y contempla casos que exigen mejorar la membresía; esas rutas no serán dependencia del núcleo de NotCan.

### chatLLM (knowusuboaky)
- Repositorio: `knowusuboaky/chatLLM`.
- Commit revisado: `d2b7d8b2f525875f764106e7014de024102996cb`.
- Licencia: MIT + archivo LICENSE, declarada en `DESCRIPTION` del paquete R.
- Uso previsto: referencia para una interfaz uniforme entre proveedores, descubrimiento de modelos, endpoints personalizados, contexto multi-mensaje y manejo de timeout/retry/backoff.
- Estado de código incorporado: **ninguno**.
- Nota: no se incorpora su runtime R. Los patrones se adaptarán a Kotlin/Android mediante `ModelProviderAdapter`/`ModelProviderRegistry`; ningún proveedor remoto será requisito del núcleo.

### ChatLLM (GiulioRusso)
- Repositorio: `GiulioRusso/ChatLLM`.
- Commit revisado: `f6b441091d41176e2769c3a5b52271e6e897dc47`.
- Licencia: **no hay una licencia explícita vigente en la rama principal**. El commit revisado eliminó del README la afirmación de licencia MIT y la raíz actual no contiene un archivo `LICENSE`.
- Uso previsto: solo referencia conceptual para soporte multi-modelo, streaming y prompts separados.
- Estado de código incorporado: **ninguno**.
- Nota: no se copiará ni adaptará código de este repositorio salvo que el autor publique una licencia inequívoca.

## Política

Antes de copiar o adaptar código externo se debe:

1. verificar la licencia en el commit concreto utilizado;
2. registrar archivo, origen y modificación;
3. conservar avisos de copyright y licencia cuando corresponda;
4. revisar compatibilidad con la licencia general de NotCan antes de distribuir binarios.