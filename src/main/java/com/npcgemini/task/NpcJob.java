package com.npcgemini.task;

import com.npcgemini.entity.EntityNPC;

/**
 * Tarea larga del NPC (talar, minar, ir a un sitio...). Se ejecuta en el servidor, un tick a la vez,
 * desde EntityAIWork. Una tarea solo se anuncia como completada después de comprobar el resultado real.
 */
public abstract class NpcJob {

    protected final EntityNPC npc;
    private boolean finished = false;
    protected int age = 0;

    private double lastX, lastY, lastZ;
    private int sampleTicks = 0, stuckSamples = 0;

    protected NpcJob(EntityNPC npc) {
        this.npc = npc;
        this.lastX = npc.posX; this.lastY = npc.posY; this.lastZ = npc.posZ;
    }

    /** Verbo en infinitivo para mensajes: "talar", "ir a casa"... */
    public abstract String describe();

    protected abstract void tick();

    public final void run() {
        if (finished) return;
        age++;
        tick();
    }

    public boolean isFinished() { return finished; }

    /** El jugador (u otra orden) canceló la tarea. */
    public void cancel() { finished = true; }

    protected void done(String message) {
        if (finished) return;
        finished = true;
        npc.say(message);
    }

    protected void fail(String reason) {
        if (finished) return;
        finished = true;
        npc.say("No pude " + describe() + ": " + reason + ".");
    }

    /** true si el NPC lleva ~4 s intentando avanzar sin moverse. Llamar cada tick mientras camina. */
    protected boolean isStuck() {
        if (++sampleTicks < 20) return stuckSamples >= 4;
        sampleTicks = 0;
        double dx = npc.posX - lastX, dy = npc.posY - lastY, dz = npc.posZ - lastZ;
        boolean notMoving = dx * dx + dy * dy + dz * dz < 0.04D;
        stuckSamples = (notMoving && !npc.getNavigator().noPath()) ? stuckSamples + 1 : 0;
        lastX = npc.posX; lastY = npc.posY; lastZ = npc.posZ;
        return stuckSamples >= 4;
    }

    protected void resetStuck() {
        stuckSamples = 0; sampleTicks = 0;
        lastX = npc.posX; lastY = npc.posY; lastZ = npc.posZ;
    }
}
