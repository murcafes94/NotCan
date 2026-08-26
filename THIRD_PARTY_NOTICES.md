# Third-party notices

NotCan se desarrolla con una política explícita de procedencia y licencias.

## Dependencias incorporadas

### whisper-android / whisper.cpp
- AAR: `dev.ffmpegkit-maintained:whisper-android:1.0.0`.
- Motor subyacente: `ggml-org/whisper.cpp`.
- Licencia declarada por ambos proyectos: MIT.
- Uso en NotCan: transcripción de archivos completamente local en Android.
- El modelo `ggml-large-v3-turbo.bin` no se empaqueta en la APK; el usuario lo descarga por separado desde el repositorio de modelos de whisper.cpp/Hugging Face.
- NotCan no incorpora código de NotelyVoice para esta función.

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
