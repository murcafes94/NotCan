# Arquitectura de NotCan

## Propósito

NotCan 1.0 se plantea como una plataforma académica general para **colegio, universidad, seminario y estudio personalizado**. El núcleo de datos es común; TuNot adapta lenguaje, profundidad, fuentes y políticas según el perfil del estudiante y el área detectada.

## Grafo académico

`Ciclo -> Materia -> Clase -> Recursos`

Los recursos de una clase no se organizan como carpetas aisladas: comparten identificadores y contexto para poder recuperarse juntos durante el ciclo académico.

## Offline

- grabación local AAC/M4A;
- edición y maquetación;
- Pencil/stylus;
- biblioteca;
- PDF, EPUB, DOC/DOCX;
- anotaciones y marcadores;
- lectura de transcripciones ya descargadas;
- TuNot con Gemma 4 mediante LiteRT-LM cuando el modelo está instalado.

## Online

- Mistral como motor remoto configurable de TuNot;
- búsqueda web independiente del modelo;
- Groq/Whisper para transcripción final cuando está configurado;
- sincronización y servicios académicos que requieran red.

## Seguridad de grabación

La captura local tiene prioridad absoluta. La transcripción en vivo es un consumidor secundario del flujo de audio: una pérdida de conexión no puede detener ni dañar el archivo local.

## Respaldo

Se mantienen dos conjuntos independientes:

1. audios del ciclo;
2. material de estudio y metadatos.

La sincronización futura con PC se plantea por entidades versionadas, no mediante una base SQLite compartida directamente.

## TuNot Harness

Desde 0.8.36 la orquestación de TuNot se separó del servicio monolítico. En **NotCan 1.0** el harness pasa a ser además consciente del perfil académico y del dominio de la materia.

- **ModelRegistry**: Mistral, Gemma 4 local y Local básico son motores intercambiables.
- **ToolRegistry**: apuntes, transcripciones, vocabulario y búsqueda web son herramientas registradas; calendario, calificaciones y documentos quedan preparados para conectarse sin reescribir el router.
- **AcademicProfile**: Colegio, Universidad, Seminario y Personalizado modifican el enfoque pedagógico sin duplicar la app.
- **SubjectDomain**: TuNot detecta de forma ligera Teología, STEM, Salud, Derecho/Ciencias Sociales, Humanidades, Idiomas o General a partir de materia y pregunta.
- **Policies**: conectividad, Solo mis fuentes, adaptación académica, privacidad local/remota, precisión católica cuando corresponde y grounding de citas forman parte explícita del plan.
- **ExecutionPlan**: decide motor primario, fallback, herramientas, perfil de prompt, contexto académico y motivo de routing.
- **PrivacyGuard**: antes de enviar contenido a un proveedor remoto puede ocultar correos, teléfonos e identificadores largos detectados con alta confianza.
- **CitationGuard**: una respuesta web no puede presentar como recuperada una URL que NotCan no haya obtenido realmente.
- **QualityEvaluator**: existe una capa heurística sin coste de inferencia destinada a diagnóstico y futuros benchmarks; no se ejecuta como un segundo LLM en cada respuesta normal.

La implementación continúa siendo nativa Kotlin/Android. No se incorpora Node, Electron, FastChat ni Arena al APK: se adoptan sus patrones de evaluación y routing manteniendo LiteRT/GPU y el funcionamiento offline.

## Dirección de evaluación

La siguiente evolución del harness puede incorporar un benchmark propio de NotCan con preguntas escolares, universitarias y de seminario, además de comparaciones A/B opcionales entre motores. El objetivo es medir fidelidad a fuentes, precisión, pertinencia, pedagogía y formato sin aumentar la latencia de la conversación normal.
