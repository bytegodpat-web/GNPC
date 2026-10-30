package com.npcgemini.task;

import com.npcgemini.entity.EntityNPC;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;
import java.util.Map;

/** Recoger los objetos que hay tirados cerca. */
public class PickupJob extends NpcJob {

    private final Map<String, EntityNPC.ItemCount> start;
    private int idle = 0;

    public PickupJob(EntityNPC npc) {
        super(npc);
        this.start = npc.countItems();
    }

    @Override public String describe() { return "recoger objetos"; }

    /** Devuelve un motivo si no hay nada que hacer. */
    public String preflight() {
        if (npc.isInventoryFull()) return "mi inventario está lleno";
        if (nearest() == null) return "no veo objetos en el suelo cerca";
        return null;
    }

    private EntityItem nearest() {
        AxisAlignedBB box = npc.getEntityBoundingBox().grow(16.0D, 6.0D, 16.0D);
        List<EntityItem> items = npc.world.getEntitiesWithinAABB(EntityItem.class, box, e -> e != null && !e.isDead && !e.getItem().isEmpty());
        EntityItem best = null; double bd = Double.MAX_VALUE;
        for (EntityItem i : items) { double d = npc.getDistanceSq(i); if (d < bd) { bd = d; best = i; } }
        return best;
    }

    @Override
    protected void tick() {
        if (npc.isInventoryFull()) { finish("mi inventario se llenó"); return; }
        EntityItem it = nearest();
        if (it == null) { finish(null); return; }
        if (age > 20 * 60) { finish("tardé demasiado"); return; }
        if (npc.getDistanceSq(it) > 2.0D) {
            if (npc.getNavigator().noPath() || age % 15 == 0) {
                if (!npc.getNavigator().tryMoveToEntityLiving(it, 1.15D)) idle++;
            }
            if (isStuck()) idle += 2;
            if (idle > 8) finish("no pude llegar a algunos objetos");
        } else {
            idle = 0;
        }
    }

    private void finish(String stopReason) {
        Map<String, EntityNPC.ItemCount> now = npc.countItems();
        String gained = EntityNPC.describeGain(start, now);
        if (gained.isEmpty()) {
            fail(stopReason != null ? stopReason : "no recogí nada");
        } else {
            done("Recogí: " + gained + "." + (stopReason != null ? " Me detuve porque " + stopReason + "." : ""));
        }
    }
}
