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

### 6. NotCan Bench

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

### 7. Investigación académica abierta

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

### 8. Academic Integrity Assistant

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

### 9. GraphRAG ligero para materias grandes

Objetivo:
- extraer conceptos y relaciones de material local;
- crear un grafo por materia/ciclo;
- recuperar relaciones además de chunks textuales;
- usarlo solo cuando aporte precisión real y sin cargar todo el grafo en RAM;
- mantener ruta RAG simple como fallback.

Condición económica: **local y opcional**.

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
4. Canvas visual nativo editable.
5. NotCan Bench y pruebas de regresión.
6. OCR local.
7. Investigación académica abierta + APA 7.
8. Academic Integrity Assistant.
9. GraphRAG ligero cuando los benchmarks demuestren que mejora el RAG normal.

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
