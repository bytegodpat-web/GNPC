package com.npcgemini.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;

/** Defiende al dueño: ataca al hostil que lo está golpeando (nunca a jugadores). */
public class EntityAINpcDefend extends EntityAITarget {
    private final EntityNPC npc;
    private EntityLivingBase attacker;

    public EntityAINpcDefend(EntityNPC npc) {
        super(npc, false);
        this.npc = npc;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!npc.isCombatEnabled()) return false;
        EntityPlayer owner = npc.getOwnerPlayer();
        if (owner == null || !owner.isEntityAlive()) return false;
        EntityLivingBase t = owner.getRevengeTarget();
        if (t == null || t instanceof EntityPlayer || !(t instanceof IMob) || !t.isEntityAlive()) return false;
        if (npc.getDistanceSq(t) > 24.0D * 24.0D) return false;
        attacker = t;
        return true;
    }

    @Override
    public void startExecuting() {
        npc.setAttackTarget(attacker);
        super.startExecuting();
    }
}
