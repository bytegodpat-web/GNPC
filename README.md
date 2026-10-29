# GNPC (Gemini NPC)

Mod para Minecraft Forge 1.12.2: un NPC con personalidad conectado a Gemini. Solo un jugador.

Estado actual: **fase 1**. Compila, registra el NPC y lo dibuja con una skin fija.
El diseno completo esta en `docs/DISENO.md`.

## Compilar (GitHub Actions)

1. Sube todo el contenido de este proyecto al repositorio, incluida la carpeta `.github`.
2. Abre la pestana **Actions** y espera a que termine el trabajo **Build** (unos minutos).
3. Entra en la ejecucion y descarga el artefacto **npcgemini-jar**.
4. Descomprime el zip y copia el `.jar` a la carpeta `mods` de Forge 1.12.2 (14.23.5.2860).

Si falla, copia las lineas de error del log y pasaselas a Claude.

## Probar en el juego

El huevo de aparicion esta en la pestana creativa de varios (Miscellaneous). Al colocarlo aparece
el NPC, mira al jugador cercano y no recibe dano.

## Si `.github/workflows/build.yml` no se sube

Algunos exploradores ocultan las carpetas con punto. En GitHub usa **Add file > Create new file**,
escribe `.github/workflows/build.yml` como nombre y pega el contenido del archivo.
