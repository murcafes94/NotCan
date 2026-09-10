# Arquitectura inicial de NotCan

## Grafo académico

`Ciclo -> Materia -> Clase -> Recursos`

Los recursos de una clase no se organizan como carpetas aisladas: comparten identificadores y contexto para poder recuperarse juntos durante el estudio semestral.

## Offline

- grabación local AAC/M4A;
- edición y maquetación;
- Pencil/stylus;
- biblioteca;
- PDF, EPUB, DOC/DOCX;
- anotaciones y marcadores;
- lectura de transcripciones ya descargadas.

## Online

- Gemini para transcripción en vivo y final;
- asistencia contextual;
- resúmenes, preguntas y mapas mentales;
- Drive para respaldos progresivos y finales.

## Seguridad de grabación

La captura local tiene prioridad absoluta. La transcripción en vivo será un consumidor secundario del flujo de audio: una pérdida de conexión no puede detener ni dañar el archivo local.

## Respaldo

Se mantendrán dos conjuntos independientes:

1. audios del ciclo;
2. material de estudio y metadatos.

La sincronización futura con PC será por entidades versionadas, no mediante una base SQLite compartida directamente.

## TuNot Harness nativo

Desde 0.8.36, la orquestación de TuNot se separa progresivamente del servicio monolítico mediante `ai/harness/TuNotHarness.kt`.

- **ModelRegistry**: Mistral, Gemma 4 local y Local básico se describen como motores intercambiables.
- **ToolRegistry**: apuntes, transcripciones, vocabulario y búsqueda web son herramientas registradas; calendario, calificaciones y documentos quedan preparados para conectarse sin reescribir el router.
- **Policies**: conectividad, Solo mis fuentes, precisión académica católica y privacidad local forman parte explícita del plan de ejecución.
- **ExecutionPlan**: el harness decide motor primario, fallback, herramientas permitidas y perfil de prompt según conectividad, fuentes y tipo de solicitud.

La implementación es nativa Kotlin/Android y no incorpora runtimes Node/Electron al APK. El objetivo es conservar la inferencia LiteRT/GPU y permitir crecimiento modular.

