# NotCan

NotCan es un ecosistema académico personal con una aplicación Android local-first y una PWA web sincronizable.

## Idea central

Cada clase mantiene unidos sus recursos: audio, transcripción, apuntes, documentos, anotaciones, marcadores y mapas mentales. Las clases de una misma materia forman un continuo para permitir un estudio global al finalizar el semestre.

## Principios

- **Local-first:** grabación, edición, maquetación, biblioteca y anotación deben seguir funcionando sin Internet.
- **IA híbrida:** la asistencia inteligente puede usar servicios online y, cuando el dispositivo lo permita, modelos locales opcionales.
- **Grabación segura:** el audio local es la fuente principal; una transcripción en vivo nunca debe interrumpir ni comprometer la grabación.
- **Tablet-first en Android:** interfaz optimizada para pantallas grandes y stylus/pencil.
- **Web/PWA:** la versión de navegador sirve como espacio amplio para organizar, redactar, estudiar, administrar documentos y usar IA desde cualquier PC.
- **Documentos:** arquitectura preparada para PDF, EPUB y DOC/DOCX.
- **Respaldo separado:** audios y material de estudio se respaldan con una política distinta a la sincronización cotidiana de datos.
- **Sincronización por registros:** Android y Web conservan UUID estables y sincronizan cambios; nunca comparten una base SQLite/IndexedDB viva.

## Arquitectura de datos

La estructura base compartida es:

`Ciclo -> Materia -> Clase -> Recursos`

Android usa Room como almacenamiento local. NotCan Web usa IndexedDB. Un backend común será la fuente de intercambio entre dispositivos, preservando los mismos UUID.

## NotCan Web

La primera base de la PWA vive en `web/` e incluye:

- React + TypeScript + Vite;
- instalación como PWA;
- almacenamiento offline con IndexedDB/Dexie;
- cola local (`outbox`) para cambios todavía no enviados;
- modelo de datos alineado con las entidades Android;
- adaptador inicial para sincronización con Supabase;
- esquema SQL con Row Level Security en `supabase/schema.sql`.

Mientras el backend no esté configurado, la web continúa funcionando de forma local y conserva los cambios pendientes.

## Próximos pasos de sincronización

1. Crear el backend Supabase de NotCan y aplicar `supabase/schema.sql`.
2. Añadir inicio de sesión a la PWA.
3. Probar sincronización real Web <-> nube con ciclos, materias, clases, apuntes y calificaciones.
4. Añadir el cliente de sincronización Android sobre Room sin reemplazar la base local.
5. Implementar resolución explícita de conflictos para contenido editado en dos dispositivos.
6. Extender sincronización a horarios, transcripciones, mapas, marcadores y anotaciones.
7. Definir Storage para documentos y una política separada/opt-in para audios pesados.

## Licencias de terceros

El proyecto mantiene un registro explícito de cualquier código o componente reutilizado. Consulta `THIRD_PARTY_NOTICES.md` antes de incorporar código externo.
