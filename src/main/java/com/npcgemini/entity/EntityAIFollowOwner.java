package com.npcgemini.entity;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/** Navegación autónoma básica hacia el jugador que dio la orden. */
public class EntityAIFollowOwner extends EntityAIBase {
    private final EntityNPC npc;
    private EntityPlayer owner;

    public EntityAIFollowOwner(EntityNPC npc) {
        this.npc = npc;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (npc.world.isRemote || npc.getMovementOrder() == EntityNPC.ORDER_IDLE) return false;
        owner = npc.getOwnerPlayer();
        if (owner == null || !owner.isEntityAlive()) return false;
        double distanceSq = npc.getDistanceSq(owner);
        double stopDistance = npc.getMovementOrder() == EntityNPC.ORDER_FOLLOW ? 3.0D : 2.0D;
        if (npc.getMovementOrder() == EntityNPC.ORDER_COME && distanceSq <= stopDistance * stopDistance) {
            npc.clearMovementOrder();
            return false;
        }
        return distanceSq > stopDistance * stopDistance;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (npc.world.isRemote || npc.getMovementOrder() == EntityNPC.ORDER_IDLE) return false;
        owner = npc.getOwnerPlayer();
        if (owner == null || !owner.isEntityAlive()) return false;
        double stopDistance = npc.getMovementOrder() == EntityNPC.ORDER_FOLLOW ? 3.0D : 2.0D;
        return npc.getDistanceSq(owner) > stopDistance * stopDistance;
    }

    @Override
    public void startExecuting() {
        updateTask();
    }

    @Override
    public void updateTask() {
        if (owner == null || !owner.isEntityAlive()) {
            npc.clearMovementOrder();
            return;
        }
        double stopDistance = npc.getMovementOrder() == EntityNPC.ORDER_FOLLOW ? 3.0D : 2.0D;
        if (npc.getDistanceSq(owner) <= stopDistance * stopDistance) {
            if (npc.getMovementOrder() == EntityNPC.ORDER_COME) npc.clearMovementOrder();
            npc.getNavigator().clearPath();
            return;
        }
        npc.getNavigator().tryMoveToEntityLiving(owner, 1.0D);
    }

    @Override
    public void resetTask() {
        owner = null;
        npc.getNavigator().clearPath();
    }
}
