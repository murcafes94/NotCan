# Roadmap de NotCan

## Regla principal: el núcleo no puede depender de pagos futuros

NotCan debe seguir siendo usable sin suscripciones ni consumo obligatorio de APIs de pago.

No es posible garantizar que una API externa gratuita siga siéndolo para siempre. Por eso, una función esencial solo entra al **núcleo** si cumple al menos una de estas condiciones:

- funciona completamente en local;
- usa software/modelos con licencia compatible y descargables por el usuario;
- utiliza estándares y formatos abiertos con una implementación reemplazable;
- cualquier servicio remoto es opcional y existe una ruta local o sustituible.

Los servicios con créditos, cuotas comerciales, planes Pro o condiciones susceptibles de cambiar pueden existir como integración opcional, pero **no deben ser requisito para estudiar, abrir documentos, usar TuNot, transcribir, crear mapas, consultar material local ni exportar datos**.

## Prioridad alta: implementar

### 1. Document Intelligence local

Inspiración: proyectos ChatPDF / Chat-With-Your-PDF y RAG local.

Objetivo:
- indexar PDF, EPUB, DOC/DOCX y transcripciones localmente;
- conservar referencia de documento y número de página/sección en cada chunk;
- recuperación híbrida: coincidencia léxica + embeddings cuando el dispositivo lo permita;
- reranking ligero antes de enviar contexto a TuNot;
- abrir la página exacta al tocar una cita;
- modo `Solo mis fuentes` realmente verificable;
- caché incremental: no reindexar documentos sin cambios;
- procesamiento por lotes para evitar picos de RAM.

Condición económica: **local y sin API obligatoria**.

### 2. OCR local para documentos escaneados

Objetivo:
- detectar PDFs sin capa de texto;
- OCR por página solo cuando haga falta;
- guardar el texto extraído de forma reutilizable;
- permitir corregir OCR antes de indexar;
- diseñar un proveedor intercambiable para poder usar una implementación local estable sin ligar NotCan a una nube.

Condición económica: **ningún OCR de pago como dependencia esencial**.

### 3. Canvas visual nativo y mapas editables

Inspiración: `ipcrm/napkin` (MIT), solo como referencia/algoritmos compatibles; no incrustar Tauri/Svelte dentro del APK.

Objetivo:
- mapas mentales generados por TuNot pero editables por el usuario;
- nodos, conectores, texto, formas, dibujo libre y notas adhesivas;
- conectores ligados a nodos;
- zoom/pan fluido;
- snap, guías de alineación y distribución;
- auto-layout en cuadrícula y force-directed;
- historial/undo-redo y snapshots locales;
- exportación a SVG, PNG, PDF y formato editable propio de NotCan;
- soporte real para stylus y táctil.

Condición económica: **implementación nativa/local**.

### 4. TuNot Learning Memory

Inspiración: `blader/napkin` (MIT), adaptada a estudiantes.

Objetivo:
- memoria curada por materia/perfil, no historial infinito;
- recordar errores recurrentes, conceptos corregidos y estrategias de estudio que sí funcionaron;
- fusionar duplicados y eliminar recuerdos de bajo valor;
- límite estricto por categoría para no inflar prompts;
- controles para ver, editar, borrar y desactivar memoria;
- no guardar silenciosamente datos sensibles;
- recuperación por relevancia antes de cada respuesta.

Condición económica: **Room/local, sin servidor obligatorio**.

### 5. Citation Grounding

Inspiración: Search Arena y sistemas RAG con citas.

Objetivo:
- cada afirmación basada en web/documentos debe enlazar al fragmento recuperado;
- bloquear URLs inventadas o no recuperadas;
- verificar que la cita corresponda al fragmento mostrado;
- diferenciar con claridad: fuente local, web, conocimiento del modelo e inferencia;
- permitir abrir la fuente o página exacta desde la respuesta.

Condición económica: **verificación local/heurística en la ruta normal**; un juez LLM solo en benchmark opcional.

### 6. Tutor pedagógico adaptativo con comprobación de dominio

Inspiración: `XenogenesisXtreme/Master-Pedagogy-Skill` (MIT), tomando sus ideas pedagógicas y no su dependencia de Claude/Manus.

Objetivo:
- diagnóstico breve de prerrequisitos antes de enseñar temas complejos;
- explicar por niveles según Colegio, Universidad, Seminario o Personalizado;
- bucle enseñar -> comprobar -> reforzar -> avanzar;
- modo socrático para guiar razonamientos paso a paso;
- rutas `Profundizar`, `Aplicar` y `Continuar` después de una comprobación;
- preguntas de recuperación espaciada para conceptos que el alumno falló antes;
- no avanzar solo porque el modelo "cree" que explicó bien: pedir evidencia de comprensión cuando corresponda;
- gamificación opcional y discreta, nunca obligatoria ni infantilizante.

Condición económica: **lógica local dentro de TuNot Harness; sin proveedor remoto obligatorio**.

### 7. NotCan Bench

Inspiración: Arena-Hard, Arena-Rank y milabench, sin incorporar sus runtimes al APK.

Objetivo:
- conjunto de pruebas para Colegio, Universidad, Seminario y Personalizado;
- medir RAG, fidelidad a fuentes, citas, precisión, pedagogía, longitud y formato;
- registrar primer token, tiempo total, tokens/s, RAM y backend CPU/GPU;
- comparar versiones antes de publicar;
- comparaciones A/B opcionales entre motores;
- evitar regresiones al cambiar prompts, modelos o routing.

Condición económica: **ejecución local/CI con modelos ya disponibles; sin juez remoto obligatorio**.

## Prioridad media: implementar cuando el núcleo anterior esté estable

### 8. Presentation Studio local y editable

Inspiración: `presenton/presenton` (Apache-2.0). Presenton demuestra que es viable generar presentaciones de forma self-hosted, usar modelos locales como Ollama y exportar PPTX editable sin SaaS obligatorio.

Objetivo de NotCan:
- generar esquema de presentación desde una materia, PDF, transcripción o apuntes;
- editor de diapositivas basado en un modelo de documento propio de NotCan;
- plantillas académicas locales;
- exportación a PPTX y PDF sin depender de Gamma ni otra nube;
- generación del contenido con Gemma/local cuando sea suficiente;
- imágenes opcionales: usar recursos locales o proveedores configurables, nunca bloquear la presentación si no hay un generador de imágenes;
- poder corregir manualmente títulos, bloques, orden y notas del expositor;
- reutilizar el mismo documento entre Android y PWA.

Arquitectura:
- **no** incrustar Electron/Python/Docker de Presenton dentro del APK;
- crear `PresentationDocument` + `PresentationProvider` propios;
- opcionalmente permitir en PC/PWA conectar a una instancia self-hosted de Presenton, pero la exportación básica de NotCan debe seguir funcionando sin ella.

Condición económica: **núcleo local; software de referencia Apache-2.0; sin SaaS requerido**.

### 9. Investigación académica abierta

Inspiración: `mila-iqia/paperoni` y su enfoque multi-fuente.

Objetivo:
- adaptadores para OpenAlex, Crossref y arXiv;
- Semantic Scholar como fuente opcional si sus límites/condiciones siguen siendo adecuados;
- deduplicación por DOI/título;
- ficha con título, autores, año, DOI, abstract y fuente;
- añadir resultados a una materia o trabajo;
- generar referencias APA 7 desde metadatos verificados;
- cachear metadatos para reducir llamadas externas.

Condición económica: **ninguna fuente remota será imprescindible**. Si una API deja de ser gratuita o cambia condiciones, debe poder desactivarse sin romper NotCan.

### 10. TuNot Skills: habilidades modulares con carga progresiva

Inspiración: `Simpleyyt/ai-manus` (MIT) y el estándar de Agent Skills.

Objetivo:
- dividir capacidades complejas de TuNot en skills independientes en lugar de agrandar el prompt global;
- catálogo local de skills: estudio, investigación, documentos, presentaciones, citas, matemáticas, idiomas, teología, cuestionarios, flashcards, etc.;
- cargar primero solo nombre + descripción y traer instrucciones completas únicamente cuando una tarea las necesite;
- activar/desactivar skills desde Configuración;
- versionar las skills y poder sustituirlas sin tocar el núcleo del Harness;
- skills oficiales firmadas/validadas; no ejecutar scripts importados de terceros sin revisión;
- no sincronizar paquetes arbitrarios a un shell del teléfono.

Condición económica: **formato abierto y runtime propio de NotCan, completamente local**.

### 11. Academic Task Agent con plan-and-execute restringido

Inspiración: `Simpleyyt/ai-manus` (MIT), pero adaptado a un entorno académico móvil seguro.

Objetivo:
- permitir tareas de varios pasos como `investiga -> compara fuentes -> crea resumen -> cuestionario -> presentación`;
- planificación estructurada, no JSON frágil dentro de prompts;
- ejecutar únicamente herramientas registradas por NotCan;
- permisos explícitos por herramienta y confirmación antes de acciones externas o destructivas;
- poder detener, reanudar y revisar el plan;
- registrar qué herramienta produjo cada artefacto;
- evitar shell, Docker, navegador automatizado sin límites, MongoDB/Redis u otros componentes de servidor dentro del APK.

Condición económica: **orquestación nativa/local; servicios externos solo como herramientas opcionales**.

### 12. Planes persistentes de estudio, investigación y proyectos

Inspiración: `OthmanAdi/planning-with-files` (MIT), trasladando el principio de memoria persistente fuera del contexto del LLM.

Objetivo:
- guardar objetivo, fases, hallazgos y progreso de tareas largas en Room/archivos locales;
- recuperar el punto exacto de trabajo después de cerrar la app, reiniciar el modelo o perder contexto;
- impedir que TuNot marque una tarea como terminada si aún existen fases verificables pendientes;
- historial compacto de pruebas/resultados, no transcripciones infinitas;
- soportar varias tareas activas por materia sin mezclar sus estados;
- resumir el plan antes de enviarlo al modelo para ahorrar contexto.

Condición económica: **persistencia local, sin servidor**.

### 13. Academic Integrity Assistant

No implementar un veredicto tipo "este texto es X% IA".

Objetivo:
- detectar afirmaciones sin cita;
- cambios bruscos de estilo;
- repetición excesiva;
- variación sintáctica y longitud de frases;
- coherencia entre párrafos;
- citas incompletas;
- métricas experimentales como perplexity solo como señal auxiliar;
- explicar limitaciones y no acusar autoría automática.

Condición económica: **análisis local y heurístico**.

### 14. GraphRAG ligero para materias grandes

Objetivo:
- extraer conceptos y relaciones de material local;
- crear un grafo por materia/ciclo;
- recuperar relaciones además de chunks textuales;
- usarlo solo cuando aporte precisión real y sin cargar todo el grafo en RAM;
- mantener ruta RAG simple como fallback.

Condición económica: **local y opcional**.

## Ideas estudiadas que NO se convierten en una función propia por ahora

### AI website builders

El tópico `ai-website-builder` contiene proyectos útiles como `frappe/builder` (MIT), con editor visual, responsive y cambios de IA reversibles/grounded. Sin embargo, crear sitios web no es una necesidad central de NotCan.

Se pueden reutilizar **patrones** para NotCan Web:
- edición reversible;
- vista responsive;
- reutilización de componentes y tokens visuales;
- IA que edita sobre el estado real del documento y no inventa estructura.

No se añade un "creador de sitios web" al producto para evitar scope creep.

### Ecosistema manus / manus-skill

El tópico es heterogéneo y contiene muchas skills pequeñas sin suficiente mantenimiento o utilidad académica. No se importará un catálogo externo indiscriminadamente.

Solo se toman por ahora tres ideas verificadas:
- skills con carga progresiva (`ai-manus`);
- plan-and-execute con herramientas estructuradas (`ai-manus`);
- mastery checks / modo socrático (`Master-Pedagogy-Skill`).

## Integraciones que NO serán dependencia del núcleo

### Gamma API

Es útil para generar presentaciones, pero depende de un servicio externo y puede requerir planes/cuotas. No entra como requisito de NotCan.

Solo se considerará en el futuro como `PresentationProvider` opcional si:
- usa la cuenta propia del usuario;
- no se guarda una clave global en el APK;
- su ausencia no limita la creación/edición/exportación básica;
- existe una alternativa local o manual.

### Napkin AI API

Útil para generar visuales, pero no será dependencia del núcleo por sus términos/servicio remoto. El trabajo prioritario es el canvas nativo local.

### Kimi K2 / Kimi K3

Son modelos open-weight potentes y muy interesantes como referencia de agentes, tool use, multimodalidad y contextos largos, pero no son candidatos para el motor local Android de NotCan:
- Kimi K2 es un MoE de 1T parámetros totales y 32B activos;
- Kimi K3 es un MoE multimodal de 2.8T parámetros totales y 104B activos;
- requieren infraestructura muy superior a un teléfono/tablet;
- Kimi K2 usa Modified MIT y Kimi K3 una licencia propia con condiciones comerciales específicas.

NotCan puede permitir en el futuro un **endpoint OpenAI-compatible opcional** para que un usuario avanzado conecte un Kimi autoalojado en otro equipo, pero nunca será requisito del núcleo ni se descargará al teléfono.

### GPTZero y detectores comerciales

No integrar como autoridad de autoría ni depender de servicios de detección de pago. Solo reutilizar ideas estadísticas cuando sean técnicamente justificables y se ejecuten localmente.

### LLMs remotos, transcripción cloud y servicios con créditos

Mistral, Groq u otros proveedores pueden mantenerse como aceleradores opcionales configurables, pero cada flujo esencial debe conservar una ruta local o degradación funcional clara.

### Bases vectoriales hospedadas

No convertir Pinecone, Weaviate Cloud, Qdrant Cloud u otros servicios similares en requisito. Para Android/PWA usar almacenamiento e índices locales; un backend remoto, si existe, debe ser intercambiable.

## Sincronización y almacenamiento

La sincronización hospedada puede tener cuotas en cualquier proveedor. Por ello:
- Room/IndexedDB siguen siendo la fuente local de trabajo;
- exportación y copia local siempre disponibles;
- backend mediante interfaz/adaptador, no acoplado a un único proveedor;
- mantener posibilidad de self-hosting o migración;
- documentos y audios nunca deben quedar inaccesibles si el servicio remoto desaparece.

## Orden recomendado de ejecución

1. Document Intelligence local con citas por página.
2. Citation Grounding y `Solo mis fuentes` verificable.
3. TuNot Learning Memory.
4. Tutor pedagógico adaptativo con comprobación de dominio.
5. Canvas visual nativo editable.
6. Presentation Studio local y exportación PPTX/PDF.
7. NotCan Bench y pruebas de regresión.
8. OCR local.
9. Investigación académica abierta + APA 7.
10. TuNot Skills con carga progresiva.
11. Academic Task Agent restringido.
12. Planes persistentes de estudio/proyectos.
13. Academic Integrity Assistant.
14. GraphRAG ligero cuando los benchmarks demuestren que mejora el RAG normal.

## Criterio para nuevos repositorios o servicios

Antes de añadir una idea al roadmap se debe comprobar:
- utilidad real para estudiar;
- licencia;
- mantenimiento;
- coste de RAM/CPU/batería/almacenamiento;
- compatibilidad Android/PWA;
- privacidad;
- posibilidad de funcionar sin red;
- riesgo de lock-in;
- si exige pagos, créditos o un plan que pueda romper la experiencia gratuita.

Si una alternativa local y mantenible logra casi lo mismo, se prioriza la alternativa local.
