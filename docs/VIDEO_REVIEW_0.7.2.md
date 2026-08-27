# Revisión visual v0.7.2

Esta revisión se basa exclusivamente en lo que ocurre en pantalla durante la prueba, no en el audio del video.

## Problemas confirmados

1. **Icono Android vacío/blanco.** El recurso binario del icono quedó corrupto en el repositorio; debe regenerarse como recursos `mipmap` estándar y adaptive icon.
2. **Modelos después de la transición de firma.** Los modelos se descargaban en `getExternalFilesDir("models")`, un directorio perteneciente a la instalación. Android lo elimina al desinstalar. La v0.7.2 introdujo una firma estable, por lo que esta reinstalación excepcional podía hacer desaparecer el modelo aunque hubiese sido descargado antes.
3. **Reutilización del GGUF.** NotCan debe permitir seleccionar un `.gguf` ya descargado e importarlo localmente, evitando una segunda descarga por Internet cuando el archivo todavía existe en el dispositivo.
4. **Transcripción.** La pantalla muestra correctamente que Whisper no está instalado; DeepSeek/NotCan AI no sustituye a Whisper. Se debe mantener una separación visual clara entre IA de estudio, Moonshine y Whisper.
5. **Sincronización APK ↔ PWA.** La PWA ya dispone de IndexedDB/outbox y adaptador Supabase, pero Android aún no tiene cliente de sincronización. Hasta implementarlo, no se debe presentar la sincronización Android↔Web como terminada.
6. **Formato de apuntes sincronizable.** El cuerpo de `note_pages.body` seguirá siendo un campo de texto opaco en el contrato de sincronización; cuando contiene HTML enriquecido, la PWA debe conservar ese HTML y no degradarlo a texto/Markdown antes de activar la sincronización Android.

## Regla de compatibilidad

Los UUID de ciclo, materia, clase y apunte se mantienen estables. Los cambios de UI/editor no deben generar nuevas identidades para registros existentes. Los audios pesados y los modelos locales no forman parte de la sincronización cotidiana; se respaldan por separado.
