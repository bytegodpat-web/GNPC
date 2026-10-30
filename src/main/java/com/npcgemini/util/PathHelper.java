package com.npcgemini.util;

import com.npcgemini.entity.EntityNPC;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Búsqueda de bloques, posiciones desde donde trabajar y línea de visión. */
public class PathHelper {

    public static boolean isPassable(World w, BlockPos p) {
        IBlockState s = w.getBlockState(p);
        return s.getCollisionBoundingBox(w, p) == null && !s.getMaterial().isLiquid();
    }

    public static boolean isStandable(World w, BlockPos p) {
        if (!isPassable(w, p) || !isPassable(w, p.up())) return false;
        BlockPos below = p.down();
        IBlockState b = w.getBlockState(below);
        if (b.getMaterial().isLiquid()) return false;
        return w.isSideSolid(below, EnumFacing.UP);
    }

    /** Busca bloques que cumplan el predicado; devuelve los más cercanos primero. */
    public static List<BlockPos> findBlocks(World w, BlockPos origin, Predicate<IBlockState> pred,
                                            int radius, int below, int above, int limit) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -below; dy <= above; dy++) {
                    mp.setPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (mp.getY() < 0 || mp.getY() > 255) continue;
                    if (!w.isBlockLoaded(mp)) continue;
                    if (pred.test(w.getBlockState(mp))) out.add(mp.toImmutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(origin::distanceSq));
        if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }

    public static Vec3d center(BlockPos p) {
        return new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    /** Celdas donde el NPC puede pararse y alcanzar el objetivo, ordenadas por cercanía al NPC. */
    public static List<BlockPos> standCandidates(EntityNPC npc, BlockPos target, double reach) {
        World w = npc.world;
        Vec3d tc = center(target);
        List<BlockPos> cands = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -7; dy <= 1; dy++) {
                    BlockPos p = target.add(dx, dy, dz);
                    if (p.equals(target) || p.getY() < 1) continue;
                    if (!w.isBlockLoaded(p)) continue;
                    if (!isStandable(w, p)) continue;
                    double eyeDist = tc.distanceTo(new Vec3d(p.getX() + 0.5, p.getY() + 1.62, p.getZ() + 0.5));
                    if (eyeDist > reach - 0.3) continue;
                    cands.add(p);
                }
            }
        }
        cands.sort(Comparator.comparingDouble(npc::getDistanceSqToCenter));
        return cands;
    }

    /** null si hay línea libre al bloque; si no, el bloque que estorba. */
    public static BlockPos obstruction(EntityNPC npc, BlockPos target) {
        Vec3d eye = npc.getPositionEyes(1.0F);
        RayTraceResult rt = npc.world.rayTraceBlocks(eye, center(target), false, true, false);
        if (rt == null || rt.typeOfHit != RayTraceResult.Type.BLOCK) return null;
        BlockPos hit = rt.getBlockPos();
        return hit.equals(target) ? null : hit;
    }
}
