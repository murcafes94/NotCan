# NotCan Web/PWA y sincronización

## Objetivo

NotCan Web no replica una base SQLite de Android. Cada cliente mantiene su propia base local y sincroniza registros mediante UUID estables.

```text
Android (Room)  <->  Supabase  <->  Web/PWA (IndexedDB)
                       |
                       +-- Auth
                       +-- PostgreSQL + RLS
                       +-- Realtime / Edge Functions
                       |
                       +-- metadatos de archivos
                               |
                               +--> Cloudflare R2 (respaldo remoto opcional)
```

La aplicación debe seguir siendo utilizable sin Internet en ambos extremos y su funcionamiento esencial no puede depender de una API de pago.

## Backend principal: Supabase

Supabase es el servidor principal de NotCan para:

- autenticación;
- datos académicos estructurados;
- sincronización Android/Web;
- RLS por `user_id`;
- Realtime cuando aporte valor;
- funciones auxiliares como TuNot.

La web y Android comparten los mismos identificadores y el mismo modelo de propiedad por usuario.

## Primera superficie sincronizable

La base compartida incluye:

- `study_cycles`;
- `subjects`;
- `class_sessions`;
- `note_pages`;
- `grade_items`;
- `file_assets` para metadatos de documentos y respaldos.

Se añadirán después horarios, transcripciones completas, anotaciones PDF, marcadores, mapas, tarjetas y otros recursos.

## Metadatos de sincronización

Cada registro sincronizable conserva:

- `id`: UUID estable generado por el cliente;
- `revision`: contador de versión;
- `deviceId`: dispositivo que produjo el cambio;
- `updatedAtEpochMs` en los clientes;
- `updated_at` en el backend;
- `deleted_at` para borrado lógico.

## Outbox local

NotCan Web escribe primero en IndexedDB. Cada modificación genera además una operación en `outbox`.

Cuando hay conexión:

1. se envían los cambios de `outbox`;
2. se descargan los cambios remotos desde la última sincronización;
3. se actualiza la copia local;
4. se elimina de `outbox` únicamente lo confirmado por el servidor.

Esto evita que un fallo de red haga perder un apunte.

## Archivos grandes: local primero, R2 después

PDF, DOCX, EPUB, TXT, imágenes, backups y audio no se mezclan con la sincronización ordinaria de filas.

La política es:

1. al importar desde la web, el archivo real se guarda primero en IndexedDB;
2. `file_assets` conserva metadatos como nombre, tamaño, MIME, materia/clase y proveedor de almacenamiento;
3. `storage_provider = local` indica que solo existe en el dispositivo;
4. `storage_provider = r2` indica un respaldo remoto en Cloudflare R2 y exige `object_key`;
5. los audios nunca se suben automáticamente: el usuario decide cuándo respaldarlos.

Así Supabase no se usa como depósito principal de archivos pesados y el funcionamiento local no depende de R2.

## TuNot

TuNot es el asistente académico propio de NotCan. La ruta gratuita es la predeterminada.

En la web:

- prioriza apuntes y materiales del usuario;
- puede resumir, generar preguntas y producir mapas conceptuales textuales;
- se ejecuta mediante una Edge Function de Supabase sin requerir claves de Gemini, OpenAI, DeepSeek ni otros proveedores de pago;
- si el backend no está disponible, existe una respuesta básica en el navegador basada en las fuentes locales;
- Ollama queda como motor local opcional para quien quiera un modelo generativo en su propio equipo.

La aplicación no debe enviar secretos privados al navegador ni incluir claves de proveedores externos en el repositorio.

## Conflictos

La base inicial implementa transporte de cambios. Antes de sincronizar edición rica entre Android y Web se añadirá resolución de conflictos.

Para apuntes de texto se propone:

- auto-fusión cuando las ediciones no se pisan;
- comparación de versiones si Android y Web modificaron el mismo contenido;
- nunca descartar silenciosamente una versión del usuario.

## Seguridad

Todas las tablas expuestas que contienen datos del usuario usan Row Level Security. `file_assets` limita SELECT/INSERT/UPDATE/DELETE al propietario autenticado mediante `auth.uid() = user_id`.

Los archivos remotos deberán servirse mediante una capa autenticada que valide al usuario antes de emitir una operación o URL temporal de R2. Las credenciales de R2 nunca deben estar en la PWA ni en el APK.

## Regla de costos

La arquitectura base se diseña para funcionar dentro de planes gratuitos. Una integración que pueda generar cobros no puede convertirse en requisito silencioso del funcionamiento de NotCan. Si en el futuro se añade un proveedor de pago, deberá ser explícitamente opcional.
