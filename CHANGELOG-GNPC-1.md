# GNPC 1 — primera entrega de desarrollo

Esta entrega parte del código de `GNPC-main.zip` y conserva sus archivos y recursos originales.

## Incluido en esta entrega

- Orden autónoma **sígueme**: el NPC usa el navegador de entidades de Minecraft para caminar hacia el jugador que dio la orden y mantener una distancia aproximada de 3 bloques.
- Orden **ven / ven aquí**: el NPC camina hacia el jugador y termina la orden al acercarse a unos 2 bloques.
- Orden **para / detente / quieto**: cancela la navegación y deja al NPC quieto.
- La orden queda asociada al UUID del jugador y se guarda en el NBT de la entidad.
- Estas tres órdenes se ejecutan directamente, sin pedirle a Gemini que controle el movimiento.

## Limitaciones conocidas

- La base actual del mod está marcada como `clientSideOnly` y esta implementación resuelve la entidad del servidor integrado. Por tanto, esta entrega está pensada para un mundo de un jugador; no añade todavía soporte de órdenes en servidores dedicados o multijugador.
- El NPC sigue siendo invulnerable como en la base original.
- Minería, tala, recolección, combate y planificación de tareas aún no están implementados por este cambio. El parser ya reconocía algunas de esas frases, pero eso no significaba que la entidad pudiera ejecutarlas.
- La aparición natural por jugador queda reservada para GNPC 2.

## Instalación del código

1. Haz una copia de seguridad de tu carpeta del proyecto.
2. Extrae el contenido de este ZIP.
3. Abre la carpeta `GNPC-main` como proyecto ForgeGradle 1.12.2.
4. Compila con tu entorno Java 8 y ForgeGradle habitual.
5. Prueba en un mundo de un jugador con el NPC cerca y usa el chat o `/bot <perfil> <orden>`.

No se incluye un JAR porque esta entrega todavía requiere compilación y prueba dentro de Minecraft 1.12.2.
