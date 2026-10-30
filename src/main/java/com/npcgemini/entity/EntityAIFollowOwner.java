package com.npcgemini.entity;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/** Navegación autónoma básica hacia el jugador que dio la orden. */
public class EntityAIFollowOwner extends EntityAIBase {
    private final EntityNPC npc;
    private EntityPlayer owner;
    private int pathFails = 0;

    public EntityAIFollowOwner(EntityNPC npc) {
        this.npc = npc;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (npc.world.isRemote || npc.getMovementOrder() == EntityNPC.ORDER_IDLE || npc.getJob() != null) return false;
        owner = npc.getOwnerPlayer();
        if (owner == null || !owner.isEntityAlive()) return false;
        double distanceSq = npc.getDistanceSq(owner);
        double stopDistance = npc.getMovementOrder() == EntityNPC.ORDER_FOLLOW ? 3.0D : 2.0D;
        if (npc.getMovementOrder() == EntityNPC.ORDER_COME && distanceSq <= stopDistance * stopDistance) {
            npc.clearMovementOrder();
            npc.say("Aquí estoy.");
            return false;
        }
        return distanceSq > stopDistance * stopDistance;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (npc.world.isRemote || npc.getMovementOrder() == EntityNPC.ORDER_IDLE || npc.getJob() != null) return false;
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
            if (npc.getMovementOrder() == EntityNPC.ORDER_COME) { npc.clearMovementOrder(); npc.say("Aquí estoy."); }
            npc.getNavigator().clearPath();
            return;
        }
        if (npc.getNavigator().noPath() || npc.ticksExisted % 10 == 0) {
            if (!npc.getNavigator().tryMoveToEntityLiving(owner, 1.0D)) {
                if (++pathFails >= 5) {
                    pathFails = 0;
                    if (npc.getMovementOrder() == EntityNPC.ORDER_COME) {
                        npc.clearMovementOrder();
                        npc.say("No encuentro un camino hasta ti.");
                    }
                }
            } else pathFails = 0;
        }
    }

    @Override
    public void resetTask() {
        owner = null;
        npc.getNavigator().clearPath();
    }
}
