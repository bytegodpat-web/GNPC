# GNPC (Gemini NPC)

Mod para Minecraft Forge **1.12.2**: un NPC con personalidad conectado a **Gemini** (Google AI Studio). Solo un jugador.

## Estado

Fases implementadas según `docs/DISENO.md`:

| Fase | Contenido | Estado |
|------|-----------|--------|
| 1 | Entidad + render + skin fija | ✅ |
| 2 | Cliente Gemini, `/bot`, chat y burbuja | ✅ |
| 3 | Perfiles, prompt y menú de personalidad | ✅ |
| 4 | Sistema de skins custom (carpeta + menú) | ⏳ parcial (skin por ID del mod) |
| 5 | Contexto de mundo + captura de pantalla | ✅ |
| 6 | `/botchat`, espontáneos y alertas | ✅ |
| 7 | Límites, errores, HUD, privacidad | ✅ básico |

## Requisitos

- Minecraft Forge **1.12.2-14.23.5.2860**
- API key gratuita de [Google AI Studio](https://aistudio.google.com/apikey)

## Compilar (GitHub Actions)

1. Sube el proyecto al repositorio (incluida `.github`).
2. Abre **Actions** y espera el trabajo **Build**.
3. Descarga el artefacto **npcgemini-jar**.
4. Copia el `.jar` a la carpeta `mods` de Forge 1.12.2.

## Configuración

Al arrancar el juego se crea `config/npcgemini.cfg`. Edita:

```
apiKey=TU_CLAVE_DE_AISTUDIO
model=gemini-2.0-flash
```

Los perfiles de bots se guardan en `config/npcgemini/profiles/`.

## Cómo se usa

1. **Huevo de aparición** en la pestaña creativa *Miscellaneous*.
2. Click derecho normal → saludo. **Click derecho agachado** → menú de personalidad.
3. Hablar con el bot:
   - `/bot Luna hola qué tal`
   - `/botchat Luna` → todo lo que escribas va al bot hasta `/botchat off`
4. La respuesta sale en el chat y como **burbuja** sobre la cabeza del NPC (si está cerca).
5. Si estás cerca y las capturas están activas, se envía una foto de lo que ves.

## Comandos

| Comando | Descripción |
|---------|-------------|
| `/bot <nombre> <mensaje>` | Mensaje suelto al bot |
| `/botchat <nombre>` | Modo conversación |
| `/botchat off` | Salir del modo |

## Privacidad

Las capturas de pantalla se envían a Google cuando están activadas. En el plan gratuito, Google puede usar los datos para entrenar. Puedes desactivar capturas por bot en el menú de personalidad o en la config global.

## Notas técnicas

- 100 % cliente (un jugador). La API key nunca se guarda en el mundo ni en el NBT de la entidad.
- Un solo hilo de peticiones, cooldown y tope diario configurables.
- El mod decide *cuándo* habla y *qué es verdad* del mundo; Gemini solo decide *cómo* se dice.

## GNPC 2 (beta 1)

Órdenes por chat o menú: `tala un árbol`, `pica hierro`, `tráeme 10 madera`, `dame 5 palos`, `qué llevas`,
`recoge los objetos`, `sígueme`, `ven aquí`, `detente`, `esta es tu casa`, `ve a casa`, `pelea` / `no pelees`.

Comandos: `/npc list|info|inv|tp|kill|say|spawn|stop|follow|stay`. Ver `CHANGELOG-GNPC2-BETA1.txt`.
