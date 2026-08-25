# NotCan

NotCan es una aplicación Android personal para estudiar por ciclos, materias y clases conectadas.

## Idea central

Cada clase mantiene unidos sus recursos: audio, transcripción, apuntes, documentos, anotaciones, marcadores y mapas mentales. Las clases de una misma materia forman un continuo para permitir un estudio global al finalizar el semestre.

## Principios

- **Local-first:** grabación, edición, maquetación, biblioteca y anotación funcionan sin Internet.
- **IA online:** la transcripción y asistencia inteligente se integrarán mediante Gemini, sin descargar modelos pesados al dispositivo.
- **Grabación segura:** el audio local es la fuente principal; una futura transcripción en vivo nunca debe interrumpir ni comprometer la grabación.
- **Tablet-first:** interfaz optimizada para pantallas grandes y stylus/pencil.
- **Documentos:** arquitectura preparada para PDF, EPUB y DOC/DOCX.
- **Respaldo separado:** audios y material de estudio se respaldarán por separado.
- **Sincronización futura:** el modelo de datos usa identificadores estables para permitir sincronización con PC sin compartir una base SQLite viva.

## Primera etapa

La versión inicial contiene:

- estructura Android nativa con Kotlin y Jetpack Compose;
- tema visual oscuro de NotCan;
- modelo `Ciclo -> Materia -> Clase -> Recursos`;
- pantalla principal de demostración;
- estados de grabación `inactivo / grabando / pausado`;
- control compacto `● -> pausa/reanudar + stop`;
- marcador de momento importante;
- servicio Android de grabación en primer plano preparado para captura local.

## Próximos módulos

1. Persistencia local con Room.
2. Grabación AAC/M4A completa y reproducción.
3. Editor enriquecido y lienzo con stylus.
4. Importación y anotación PDF.
5. Importación EPUB y DOC/DOCX.
6. Gemini: transcripción en vivo y final, resumen, preguntas y mapas mentales.
7. Google Drive: respaldo progresivo y paquetes finales separados.
8. Sincronización con NotCan Desktop.

## Licencias de terceros

El proyecto mantendrá un registro explícito de cualquier código o componente reutilizado. Consulta `THIRD_PARTY_NOTICES.md` antes de incorporar código externo.
