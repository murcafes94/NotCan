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
