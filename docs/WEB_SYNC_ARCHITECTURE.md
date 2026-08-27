# NotCan Web/PWA y sincronización

## Objetivo

NotCan Web no replica una base SQLite de Android. Cada cliente mantiene su propia base local y sincroniza registros mediante UUID estables.

```text
Android (Room)  <->  Backend NotCan  <->  Web/PWA (IndexedDB)
```

La aplicación debe seguir siendo utilizable sin Internet en ambos extremos.

## Primera superficie sincronizable

La primera fase comparte:

- `study_cycles`
- `subjects`
- `class_sessions`
- `note_pages`
- `grade_items`

Se añadirán después horarios, transcripciones, documentos, anotaciones PDF, marcadores, mapas y demás recursos.

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

## Archivos grandes

La sincronización de datos no debe subir automáticamente los audios. PDF, EPUB, DOCX y audio usarán Storage y una política de subida distinta.

Para grabaciones se mantendrá una opción explícita de respaldo/sincronización, porque una sola clase puede producir cientos de MB.

## Conflictos

La base inicial implementa transporte de cambios. Antes de sincronizar edición rica entre Android y Web se añadirá resolución de conflictos.

Para apuntes de texto se propone:

- auto-fusión cuando las ediciones no se pisan;
- comparación de versiones si Android y Web modificaron el mismo contenido;
- nunca descartar silenciosamente una versión del usuario.

## Seguridad

El esquema Supabase usa `user_id` y Row Level Security. Un usuario autenticado solo puede leer y modificar sus propios registros.

Las claves privadas de servicios externos nunca deben quedar en la PWA. Solo se utilizan claves públicas/anon admitidas por el proveedor; secretos de IA y operaciones privilegiadas deben ejecutarse en backend/Edge Functions.
