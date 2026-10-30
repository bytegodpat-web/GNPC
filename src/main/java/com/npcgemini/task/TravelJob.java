package com.npcgemini.task;

import net.minecraft.util.math.BlockPos;
import com.npcgemini.entity.EntityNPC;

/** Ir a un punto (casa, sitio recordado). Anuncia la llegada solo si llegó de verdad. */
public class TravelJob extends NpcJob {

    private final BlockPos dest;
    private final String label;
    private int pathFailures = 0;
    private int repath = 0;

    public TravelJob(EntityNPC npc, BlockPos dest, String label) {
        super(npc);
        this.dest = dest;
        this.label = label;
    }

    @Override public String describe() { return "ir a " + label; }

    @Override
    protected void tick() {
        double dx = npc.posX - (dest.getX() + 0.5), dz = npc.posZ - (dest.getZ() + 0.5);
        double dy = npc.posY - dest.getY();
        if (dx * dx + dz * dz <= 2.25D && Math.abs(dy) <= 2.5D) {
            npc.getNavigator().clearPath();
            done("Llegué a " + label + ".");
            return;
        }
        if (age > 20 * 90) { fail("tardé demasiado y no llegué (a " + (int) Math.sqrt(dx * dx + dz * dz) + " bloques)"); return; }
        if (npc.getNavigator().noPath()) {
            if (repath-- <= 0) {
                repath = 10;
                boolean ok = npc.getNavigator().tryMoveToXYZ(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 1.0D);
                if (!ok && ++pathFailures >= 3) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    fail(dist > 48 ? "está demasiado lejos (" + (int) dist + " bloques)" : "no encuentro un camino hasta allí");
                    return;
                }
                resetStuck();
            }
        } else if (isStuck()) {
            if (++pathFailures >= 3) { fail("me quedé atascada por el camino"); return; }
            npc.getNavigator().clearPath();
        }
    }
}
