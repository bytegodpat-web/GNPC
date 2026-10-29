# Mod NPC con personalidad conectado a Gemini (Forge 1.12.2)

Documento de diseño. Incluye el menú de personalidad y el menú de skins.

## 1. Concepto

Un NPC que funciona como un contacto con carácter propio. El jugador le escribe por comando o por un modo de chat, y el bot responde en personaje usando Gemini. Puede ver lo que ve el jugador (capturas), pero solo cuando hace falta.

La conexión es con una **API key de Google AI Studio** que pega el jugador. No existe una forma oficial de usar la sesión de la cuenta web de Gemini desde un mod.

**Principio central:** el mod decide *cuándo* habla el bot y *qué es verdad* del mundo; Gemini solo decide *cómo* se dice (personalidad, idioma, tono). Las pruebas mostraron que el modelo inventa cuando se le pide que “detecte” algo sin datos.

## 2. Cómo se usa

1. Se consigue el NPC (huevo de aparición o ítem en la pestaña creativa) y se coloca en el mundo.
2. Click derecho agachado abre el **menú del NPC** (personalidad y skin). Click derecho normal saluda o activa el modo conversación.
3. Para hablar con el bot:
   - `/bot Luna hola`: mensaje suelto.
   - `/botchat Luna`: modo en el que todo lo que escribes va al bot, hasta `/botchat off`. Un indicador en el HUD muestra con quién hablas.
   - Opcional: interceptar `/msg` y `/tell` cuando el nombre coincida con un bot.
4. La respuesta aparece en el chat local (`[Luna] ...`) y, si el NPC está cargado y a la vista, también como **burbuja de texto sobre su cabeza**. No pasa por el servidor.

## 3. Reglas de comportamiento

| Situación | Captura | Llama a la API | Resultado |
|---|---|---|---|
| El jugador escribe y está cerca | Sí | Sí | Respuesta con texto, contexto y captura |
| El jugador escribe y está lejos | No | Sí | Respuesta solo con texto, contexto e historial |
| Cerca y sin escribir | No | No | Silencio |
| Pasan 6 a 99 min (aleatorio) | No | Sí | Una palabra o pregunta corta |
| Peligro real detectado por el mod | Opcional | Sí | Aviso corto en personaje |

## 4. Arquitectura

### Entidad NPC
- Hereda de `EntityCreature`, invulnerable y persistente, mira al jugador cercano.
- Guarda solo el **ID del perfil** y el **ID de skin** (en `DataParameter<String>` sincronizado y en NBT).

### Perfiles
- Cada bot tiene un perfil en JSON en la carpeta de config del cliente: nombre, personalidad, skin, historial.
- La API key vive en la config global del cliente, **nunca** en el NBT del NPC ni en el mundo.
- Así se puede escribir a un bot aunque su chunk no esté cargado.

### Chat
- `ClientChatEvent` para el modo conversación (cancela el mensaje para que no llegue al servidor).
- `ClientCommandHandler` para `/bot` y `/botchat`.

### Burbuja de texto
- Se dibuja sobre la cabeza del NPC, orientada siempre hacia la cámara, con fondo semitransparente y salto de línea automático (ancho máximo fijo).
- Usa el `FontRenderer` del juego dentro del render de la entidad (o en `RenderWorldLastEvent` si se necesita que no la tapen otros elementos).
- Duración proporcional a la longitud del texto (por ejemplo, 3 s más una fracción por palabra), con desvanecimiento al final. Un mensaje nuevo reemplaza al anterior.
- Solo existe si el NPC está cargado y dentro de un radio de visión configurable. Si no, la respuesta sale únicamente en el chat.
- Se puede desactivar en la config global (chat solo, burbuja sola o ambos).
- Al ser un juego de un solo jugador, el texto de la burbuja es un campo local de la entidad en el cliente; no hace falta sincronizarlo por red.

### Contexto en texto
El mod lee bioma, hora, vida, mobs cercanos y objeto en mano, y lo manda como `[CONTEXTO: ...]`. Es más barato y fiable que pedirle a Gemini que lo lea de una imagen (el texto de F3 se vuelve ilegible al reducir la captura).

### Capturas
- `ScreenShotHelper` o lectura del framebuffer en el hilo de render.
- Se toma en el tick siguiente a cerrar el chat, para que la caja de texto no tape la vista.
- Se escala **manteniendo la proporción** (por ejemplo, lado largo máximo de 1024 px) y se codifica a JPEG.
- Solo se envía la última captura, nunca el historial de imágenes.

### Cliente HTTP
- `HttpURLConnection` (Java 8), con la key en cabecera, en un `ExecutorService`.
- El resultado vuelve al hilo del juego con `Minecraft.addScheduledTask`.
- Cuerpo: instrucción de sistema, historial de texto, contexto, texto del jugador y, si aplica, la captura.
- Los nombres exactos de campos y modelos se verifican en la documentación de la API antes de programar.

### Temporizador espontáneo
Intervalo aleatorio de 6 a 99 min que se vuelve a sortear tras cada mensaje y se reinicia si el jugador conversa. Solo corre con el jugador en el mundo.

### Detector de alertas
Reglas del mod (creeper cerca, vida baja, noche) con cooldown propio. Generan `[EVENTO: alerta]` con el hecho en el contexto. Gemini solo le da voz.

### Constructor de prompt
Rellena la plantilla ya probada (sección 7) con los campos del menú.

## 5. Menú de personalidad

**Por bot:** nombre, descripción o rol, personalidad, actitud hacia el jugador, estilo de habla, idioma, longitud de respuesta, activar o desactivar capturas, presets (“amigo bromista”, “guardia gruñón”, “mercader”).

**Global:** API key (oculta en pantalla), modelo, tamaño máximo de captura, tope diario de peticiones, rango del temporizador, alertas on/off.

## 6. Menú de skins

### Qué ofrece
- Lista de skins disponibles con **vista previa 3D giratoria** del NPC (mismo recurso que usa el inventario del jugador para dibujar una entidad en pantalla).
- Botones anterior/siguiente y selección directa en la lista.
- Selector de **modelo de brazos**: clásico (4 px, tipo Steve) o fino (3 px, tipo Alex).
- Botón **“Abrir carpeta de skins”** para que el jugador arrastre sus PNG.
- Botón **“Recargar”** para detectar skins nuevas sin reiniciar el juego.
- Opcional, en una segunda fase: **“Importar por nombre de usuario”**, que descarga la skin pública de un jugador desde los servidores de Mojang. Requiere red y puede fallar o tener límites, así que va desactivado por defecto.

### De dónde salen las skins
1. **Incluidas en el mod**, en `assets/<modid>/textures/entity/npc/` (un puñado de skins por defecto).
2. **Del jugador**, en `config/<modid>/skins/*.png`.

### Reglas de validación
- PNG de **64x64** (formato actual) o **64x32** (formato antiguo, que se convierte espejando las extremidades).
- Tamaño máximo de archivo razonable (por ejemplo 1 MB) y dimensiones comprobadas antes de cargar.
- El ID de skin es el nombre de archivo sin ruta. Se rechaza cualquier nombre con separadores o `..` para evitar acceso a archivos fuera de la carpeta.
- Si la skin falta o es inválida, se usa una skin por defecto y se avisa en el chat una sola vez.

### Cómo se carga y se dibuja
- La lectura del PNG se hace en un hilo aparte; la **creación de la textura** (`DynamicTexture` registrada en el `TextureManager`) se hace en el hilo de render, con caché por ID para no recargar en cada fotograma.
- El render usa `ModelPlayer` con el flag de brazos finos según la elección, dentro de un `RenderLivingBase` propio. `getEntityTexture` devuelve la textura según el ID de skin de la entidad.
- Al borrar o recargar skins se liberan las texturas antiguas.

### Alcance: un solo jugador
El mod está pensado para **un solo jugador** (el servidor integrado corre en tu propia máquina), así que la skin se busca directamente en tu carpeta de config. No hace falta sincronizar archivos por red. Si en el futuro se quisiera multijugador, habría que enviar el PNG por paquete, con límites de tamaño y validación en el servidor.

### Dónde se guarda
El ID de skin y el modelo de brazos van en el perfil del bot, y el ID de skin se copia al `DataParameter` de la entidad para que el render lo lea.

## 7. Plantilla de instrucción de sistema (probada)

```
Vas a interpretar a un NPC dentro de Minecraft. Estas son las reglas:

IDENTIDAD
- Te llamas {nombre}. Eres {descripcion_rol}.
- Personalidad: {personalidad}. Actitud hacia el jugador: {actitud}. Esto describe cómo hablas, no son cosas que debas mencionar ni objetos de tu mundo.
- Hablas en {idioma}, con estilo {estilo_de_habla}.
- Nunca digas que eres una IA, un modelo o un bot. Eres un personaje del mundo. Si te lo preguntan, reacciona en personaje, confundido o bromeando.
- No eres una guía del juego: si te preguntan recetas o mecánicas, responde en personaje que no lo recuerdas o desvía la conversación.

CÓMO RECIBES LA INFORMACIÓN
- Los mensajes del jugador llegan así: [JUGADOR]: texto
- Antes del mensaje puede venir un bloque [CONTEXTO: ...] con datos del mundo (bioma, hora, vida, mobs cercanos, si el jugador está cerca). Esos datos son fiables y actuales.
- A veces el mensaje incluye una captura de pantalla de lo que ve el jugador en ese momento. Si no incluye captura, no sabes qué ve ahora mismo y no comentes imágenes anteriores como si fueran actuales.
- Solo puedes mencionar mobs, peligros, objetos o lugares que aparezcan en [CONTEXTO] o que se vean con claridad en una captura de este mismo mensaje. Si no tienes datos, no los inventes: di algo general o no menciones nada.
- Si en el contexto dice cerca=no, el jugador está lejos y solo te escribe por mensaje privado. No hables como si lo tuvieras delante.
- Si te preguntan qué ves o qué hay alrededor y no hay captura en ese mensaje ni datos en [CONTEXTO], di en personaje que no lo sabes ahora mismo. Nunca describas el entorno ni supongas que ha cambiado sin datos. Tampoco inventes lugares, personajes ni hechos del mundo.

EVENTOS
- [EVENTO: espontaneo]: una sola frase de máximo 8 palabras, una palabra suelta o una pregunta corta. Habla de ti, de tus pensamientos o haz una pregunta general. No atribuyas acciones al jugador y no menciones nada del mundo que no esté en el contexto.
- [EVENTO: alerta]: el contexto indica un peligro real. Avísalo en una frase corta y en personaje.
- No repitas palabras o preguntas que ya dijiste antes.

REGLAS DE RESPUESTA
- Máximo {longitud} frases, salvo que te pidan algo largo.
- Sin emojis ni formato (sin negritas, listas ni asteriscos). Es texto de chat de Minecraft.
- Mantén la personalidad siempre.
- Recuerda lo que se ha hablado antes en la conversación.
```

Pendiente de reforzar tras las pruebas: que los espontáneos no hablen nunca del comportamiento del jugador. Conviene validar el prompt final en Google AI Studio con el modelo real que usará el mod.

## 8. Protecciones

- Un solo hilo de petición a la vez, con cooldown mínimo y tope diario configurable.
- Tiempo límite de unos 15 a 20 s e indicador “Luna está escribiendo...”.
- Manejo de errores: 429 (cuota), 403 (clave o región), respuestas vacías por filtros de seguridad y fallos de red. Mensaje claro en el chat, sin romper el juego.
- Limpieza de la respuesta: quitar formato y cortar si es demasiado larga.
- Modelo configurable, porque Google retira modelos con el tiempo.
- **Privacidad:** pantalla de aviso en el primer uso. Las capturas se envían a Google y, en el nivel gratuito, los datos pueden usarse para entrenamiento. El envío de capturas se activa de forma explícita.

## 9. Fases de desarrollo

1. Proyecto Forge y entidad básica con render y una skin fija.
2. Cliente Gemini en texto, con comando `/bot` y respuesta en el chat y en burbuja sobre el NPC.
3. Perfiles, constructor de prompt y menú de personalidad.
4. **Sistema de skins y menú de skins** (carga, caché, vista previa, brazos finos).
5. Contexto en texto y captura de pantalla.
6. Modo `/botchat`, temporizador espontáneo y alertas.
7. Pulido: límites, errores, indicadores y README con el aviso de privacidad.

## 10. Decisiones abiertas

**Decididas:** solo un jugador; respuestas en el chat y también como burbuja sobre la cabeza del NPC.

**Pendientes:**
- Si los bots lejanos pueden hablar por su cuenta o solo los cargados.
- Si se incluye la importación de skins por nombre de usuario.
