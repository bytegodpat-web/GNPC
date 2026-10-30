package com.npcgemini.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Busca bloques en un radio (chunks cargados).
 */
public class BlockFinder {

    public static BlockPos findNearest(World world, BlockPos origin, Block target, int radius) {
        if (world == null || origin == null) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int r = Math.max(4, Math.min(radius, 48));

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = origin.add(x, y, z);
                    if (!world.isBlockLoaded(p)) continue;
                    IBlockState st = world.getBlockState(p);
                    Block b = st.getBlock();
                    if (matches(b, target)) {
                        double d = origin.distanceSq(p);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p.toImmutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    public static BlockPos findNearestLog(World world, BlockPos origin, int radius) {
        if (world == null || origin == null) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int r = Math.max(4, Math.min(radius, 48));

        for (int x = -r; x <= r; x++) {
            for (int y = -8; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = origin.add(x, y, z);
                    if (!world.isBlockLoaded(p)) continue;
                    Block b = world.getBlockState(p).getBlock();
                    if (b instanceof BlockLog || b == Blocks.LOG || b == Blocks.LOG2) {
                        double d = origin.distanceSq(p);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p.toImmutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    public static BlockPos lookingAt(EntityLivingBase entity, int reach) {
        // simple ray: step forward
        net.minecraft.util.math.Vec3d start = entity.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = entity.getLook(1.0F);
        net.minecraft.util.math.Vec3d end = start.addVector(look.x * reach, look.y * reach, look.z * reach);
        net.minecraft.util.math.RayTraceResult rt = entity.world.rayTraceBlocks(start, end, false, false, true);
        if (rt != null && rt.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            return rt.getBlockPos();
        }
        return null;
    }

    private static boolean matches(Block b, Block target) {
        if (target == null) return false;
        if (b == target) return true;
        // lit redstone ore
        if (target == Blocks.REDSTONE_ORE && b == Blocks.LIT_REDSTONE_ORE) return true;
        return false;
    }
}
