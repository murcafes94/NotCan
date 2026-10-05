# NotCan

NotCan es una aplicación Android académica **local-first**. La app funciona sin conexión y, cuando el usuario inicia sesión, usa Supabase únicamente como backend para sincronizar sus datos entre sus propios dispositivos.

## Idea central

Cada clase mantiene unidos sus recursos: audio, transcripción, apuntes, documentos, anotaciones, marcadores, mapas, tarjetas y cuestionarios. Las clases de una misma materia forman un continuo para estudiar y repasar el ciclo académico completo.

## Principios

- **App Android únicamente:** no se mantiene una versión web/PWA de NotCan.
- **Local-first:** grabación, edición, biblioteca, calendario, repaso y material ya descargado deben seguir funcionando sin Internet.
- **Cuenta opcional:** iniciar sesión habilita sincronización entre dispositivos; no bloquea el uso local.
- **Sincronización por registros:** Room conserva los datos locales y Supabase intercambia cambios usando los mismos UUID.
- **Calendario del dispositivo:** el horario académico puede publicarse en un calendario Android editable y se identifica con IDs estables de NotCan para evitar duplicados entre dispositivos.
- **Núcleo sin pago obligatorio:** las funciones esenciales no dependen de una suscripción.
- **IA híbrida:** los proveedores remotos son sustituibles y el procesamiento local se utiliza cuando el dispositivo lo permite.
- **Archivos pesados local-first:** audios y documentos no se suben automáticamente.

## Arquitectura

```text
NotCan Android (dispositivo A)
          │
          │ cuenta + sincronización
          ▼
       Supabase
   Auth + PostgreSQL
          ▲
          │
NotCan Android (dispositivo B)
```

Supabase sincroniza actualmente los datos académicos pequeños: ciclos, materias, horario semanal, clases, apuntes, transcripciones, tareas y calificaciones. Los eventos del calendario del sistema son específicos de cada dispositivo, pero se reconstruyen desde el horario sincronizado.

## Datos y privacidad

Las sesiones de Supabase se guardan cifradas mediante Android Keystore. Las tablas remotas usan Row Level Security (RLS), de modo que cada cuenta solo puede acceder a sus propios registros.

El audio y los documentos pesados permanecen locales hasta que exista una política explícita de respaldo opt-in y cuotas seguras.

## Backend

El esquema base está documentado en `supabase/schema.sql`. El backend no sustituye la base Room: si no hay red o Supabase no está disponible, NotCan sigue trabajando con la copia local y sincroniza después.

## Roadmap

Las mejoras priorizadas se mantienen en `docs/ROADMAP.md`.

## Licencias de terceros

Consulta `THIRD_PARTY_NOTICES.md` antes de incorporar código externo.
