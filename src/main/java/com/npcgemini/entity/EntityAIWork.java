package com.npcgemini.entity;

import net.minecraft.entity.ai.EntityAIBase;

/** Ejecuta la tarea larga actual del NPC. El combate (prioridad menor) la interrumpe y luego se reanuda. */
public class EntityAIWork extends EntityAIBase {
    private final EntityNPC npc;

    public EntityAIWork(EntityNPC npc) {
        this.npc = npc;
        this.setMutexBits(3);
    }

    @Override public boolean shouldExecute() { return !npc.world.isRemote && npc.getJob() != null; }
    @Override public boolean shouldContinueExecuting() { return !npc.world.isRemote && npc.getJob() != null; }
    @Override public boolean isInterruptible() { return true; }
    @Override public void updateTask() { npc.tickJob(); }
    @Override public void resetTask() { npc.getNavigator().clearPath(); }
}
