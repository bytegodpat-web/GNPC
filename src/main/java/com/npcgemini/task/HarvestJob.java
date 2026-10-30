package com.npcgemini.task;

import com.npcgemini.entity.EntityNPC;
import com.npcgemini.util.PathHelper;
import com.npcgemini.util.ToolHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Predicate;

/**
 * Talar árboles y picar bloques con desplazamiento real:
 * buscar -> acercarse (camino verificado) -> romper (con la herramienta adecuada y tiempo real) ->
 * recoger los objetos del suelo -> repetir. Al terminar compara el inventario con el del inicio
 * y solo anuncia lo que de verdad consiguió.
 */
public class HarvestJob extends NpcJob {

    public enum Mode { TREE, ORE, FIXED }
    private enum Stage { SEARCH, APPROACH, BREAK, COLLECT, DELIVER }

    private static final int MAX_TICKS = 20 * 60 * 10;
    private static final double REACH_TREE = 5.0D;
    private static final double REACH_ORE = 4.6D;

    private final Mode mode;
    private final Block[] oreBlocks;
    private final String label;
    private final int wantedBlocks;   // ORE/FIXED: bloques a romper
    private final int wantedLogs;     // TREE: troncos deseados (0 = usar wantedTrees)
    private final int wantedTrees;
    private final boolean deliver;
    private final Block fixedBlock;
    private final BlockPos fixedPos;
    private final Map<String, EntityNPC.ItemCount> startInv;

    private Stage stage = Stage.SEARCH;
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private BlockPos current;
    private boolean currentCounts = true;
    private boolean treeInProgress = false;

    private int broken = 0, brokenLogs = 0, treesDone = 0, unreachable = 0;
    private int stageTicks = 0, breakProgress = 0, breakNeeded = 1, breakSlot = -1;
    private boolean breakCanHarvest = true;
    private int collectTicks = 0;
    private int deliverTicks = 0;
    private String stopReason = null;
    private String pendingSummary = null;
    private Map<String, EntityNPC.ItemCount> gainItems = null;

    private HarvestJob(EntityNPC npc, Mode mode, Block[] ore, String label, int wantedBlocks, int wantedLogs,
                       int wantedTrees, boolean deliver, BlockPos fixedPos) {
        super(npc);
        this.mode = mode; this.oreBlocks = ore; this.label = label;
        this.wantedBlocks = wantedBlocks; this.wantedLogs = wantedLogs; this.wantedTrees = wantedTrees;
        this.deliver = deliver; this.fixedPos = fixedPos;
        this.fixedBlock = fixedPos != null ? npc.world.getBlockState(fixedPos).getBlock() : null;
        this.startInv = npc.countItems();
    }

    public static HarvestJob trees(EntityNPC npc, int trees, int logs, boolean deliver) {
        return new HarvestJob(npc, Mode.TREE, null, "madera", 0, logs, Math.max(1, trees), deliver, null);
    }
    public static HarvestJob ore(EntityNPC npc, Block[] blocks, String label, int amount, boolean deliver) {
        return new HarvestJob(npc, Mode.ORE, blocks, label, Math.max(1, Math.min(amount, 256)), 0, 0, deliver, null);
    }
    public static HarvestJob fixed(EntityNPC npc, BlockPos pos) {
        return new HarvestJob(npc, Mode.FIXED, null, "ese bloque", 1, 0, 0, false, pos);
    }

    @Override
    public String describe() {
        return mode == Mode.TREE ? "talar" : mode == Mode.FIXED ? "romper ese bloque" : "picar " + label;
    }

    // ------------------------------------------------------------ preflight

    /** Comprueba que la tarea es posible. Devuelve el motivo si no lo es; null si puede empezar. */
    public String preflight() {
        World w = npc.world;
        if (npc.isInventoryFull()) return "mi inventario está lleno";
        switch (mode) {
            case TREE: {
                List<BlockPos> t = findTree();
                if (t == null) return "no veo árboles que pueda talar en 24 bloques";
                queue.addAll(t); treeInProgress = true;
                return null;
            }
            case ORE: {
                BlockPos p = findOre();
                if (p == null) return "no veo " + label + " a mi alcance en 24 bloques (solo puedo picar lo que está expuesto o cerca)";
                IBlockState s = w.getBlockState(p);
                if (!ToolHelper.choose(npc, s).canHarvest) return "necesito " + ToolHelper.requiredToolName(s) + " para sacar " + label;
                queue.add(p);
                return null;
            }
            default: {
                if (fixedPos == null) return "no sé qué bloque romper";
                IBlockState s = w.getBlockState(fixedPos);
                if (s.getBlock().isAir(s, w, fixedPos)) return "ahí no hay ningún bloque";
                if (s.getBlockHardness(w, fixedPos) < 0) return "ese bloque es irrompible";
                if (!ToolHelper.choose(npc, s).canHarvest) return "necesito " + ToolHelper.requiredToolName(s) + " para romperlo";
                if (!reachableSpot(fixedPos, REACH_ORE)) return "no veo cómo llegar hasta ese bloque";
                queue.add(fixedPos);
                return null;
            }
        }
    }

    // ------------------------------------------------------------ main loop

    @Override
    protected void tick() {
        if (age > MAX_TICKS) { stopReason = "se me acabó el tiempo"; endWork(); return; }
        switch (stage) {
            case SEARCH: doSearch(); break;
            case APPROACH: doApproach(); break;
            case BREAK: doBreak(); break;
            case COLLECT: doCollect(); break;
            case DELIVER: doDeliver(); break;
        }
    }

    @Override
    public void cancel() {
        if (current != null) npc.world.sendBlockBreakProgress(npc.getEntityId(), current, -1);
        npc.setHeldSlot(-1);
        super.cancel();
    }

    private boolean goalReached() {
        switch (mode) {
            case TREE: return wantedLogs > 0 ? brokenLogs >= wantedLogs : treesDone >= wantedTrees;
            default: return broken >= wantedBlocks;
        }
    }

    private void doSearch() {
        if (goalReached()) { endWork(); return; }
        if (npc.isInventoryFull()) { stopReason = "mi inventario está lleno"; endWork(); return; }
        if (skipped.size() > 80) { stopReason = "hay demasiados obstáculos"; endWork(); return; }
        if (queue.isEmpty()) {
            if (treeInProgress) { treesDone++; treeInProgress = false; if (goalReached()) { endWork(); return; } }
            if (mode == Mode.TREE) {
                List<BlockPos> t = findTree();
                if (t == null) { stopReason = broken > 0 ? "no quedan más árboles a mi alcance" : "no veo árboles que pueda talar"; endWork(); return; }
                queue.addAll(t); treeInProgress = true;
            } else if (mode == Mode.ORE) {
                BlockPos p = findOre();
                if (p == null) { stopReason = "no veo más " + label + " a mi alcance"; endWork(); return; }
                queue.add(p);
            } else {
                stopReason = null; endWork(); return;
            }
        }
        current = queue.pollFirst();
        currentCounts = true;
        stage = Stage.APPROACH; stageTicks = 0;
        resetStuck();
    }

    private boolean isTarget(IBlockState s) {
        Block b = s.getBlock();
        switch (mode) {
            case TREE: return b instanceof BlockLog;
            case ORE: for (Block o : oreBlocks) if (b == o) return true; return false;
            default: return b == fixedBlock;
        }
    }

    private void skip(String why) {
        if (current != null) {
            skipped.add(current);
            if (currentCounts && mode == Mode.TREE) unreachable++;
            npc.world.sendBlockBreakProgress(npc.getEntityId(), current, -1);
        }
        npc.getNavigator().clearPath();
        stage = Stage.SEARCH;
    }

    // ------------------------------------------------------------ approach

    private void doApproach() {
        World w = npc.world;
        IBlockState st = w.getBlockState(current);
        if (st.getBlock().isAir(st, w, current) || (currentCounts && !isTarget(st))) { stage = Stage.SEARCH; return; }

        double reach = mode == Mode.TREE ? REACH_TREE : REACH_ORE;
        Vec3d eye = npc.getPositionEyes(1.0F);
        Vec3d c = PathHelper.center(current);
        npc.getLookHelper().setLookPosition(c.x, c.y, c.z, 30.0F, 30.0F);

        if (eye.distanceTo(c) <= reach) {
            BlockPos obs = PathHelper.obstruction(npc, current);
            if (obs == null) { startBreak(current); return; }
            IBlockState os = w.getBlockState(obs);
            if (diggable(os, obs) && eye.distanceTo(PathHelper.center(obs)) <= reach && ToolHelper.choose(npc, os).canHarvest) {
                queue.addFirst(current);
                current = obs; currentCounts = false;
                startBreak(current);
                return;
            }
        }

        stageTicks++;
        if (stageTicks > 700) { skip("tiempo"); return; }
        if (stageTicks == 1 || (npc.getNavigator().noPath() && stageTicks % 20 == 0)) {
            if (!startMoveTo(current, reach)) { skip("sin camino"); return; }
            resetStuck();
        } else if (isStuck()) {
            skip("atascada");
        }
    }

    private boolean startMoveTo(BlockPos target, double reach) {
        List<BlockPos> cands = PathHelper.standCandidates(npc, target, reach);
        int tries = 0;
        for (BlockPos c : cands) {
            if (++tries > 6) break;
            Path p = npc.getNavigator().getPathToPos(c);
            if (p == null) continue;
            PathPoint f = p.getFinalPathPoint();
            if (f == null) continue;
            if (Math.abs(f.x - c.getX()) > 1 || Math.abs(f.z - c.getZ()) > 1 || Math.abs(f.y - c.getY()) > 1) continue;
            if (npc.getNavigator().setPath(p, 1.0D)) return true;
        }
        return false;
    }

    private boolean reachableSpot(BlockPos p, double reach) {
        Vec3d eye = npc.getPositionEyes(1.0F);
        if (eye.distanceTo(PathHelper.center(p)) <= reach && PathHelper.obstruction(npc, p) == null) return true;
        return !PathHelper.standCandidates(npc, p, reach).isEmpty();
    }

    /** Solo se abre paso por material natural; nunca por construcciones del jugador. */
    private boolean diggable(IBlockState s, BlockPos p) {
        Material m = s.getMaterial();
        if (s.getBlockHardness(npc.world, p) < 0) return false;
        return m == Material.ROCK || m == Material.GROUND || m == Material.GRASS || m == Material.SAND
                || m == Material.LEAVES || m == Material.CLAY || m == Material.SNOW || m == Material.CRAFTED_SNOW
                || m == Material.PLANTS || m == Material.VINE;
    }

    // ------------------------------------------------------------ break

    private void startBreak(BlockPos pos) {
        World w = npc.world;
        IBlockState s = w.getBlockState(pos);
        ToolHelper.Choice ch = ToolHelper.choose(npc, s);
        if (!ch.canHarvest) {
            if (currentCounts) { stopReason = "necesito " + ToolHelper.requiredToolName(s); endWork(); }
            else skip("sin herramienta");
            return;
        }
        float hard = s.getBlockHardness(w, pos);
        if (hard < 0) { skip("irrompible"); return; }
        breakSlot = ch.slot;
        breakCanHarvest = ch.canHarvest;
        if (hard == 0.0F) breakNeeded = 1;
        else {
            float perTick = ch.speed / hard / (ToolHelper.needsTool(s) ? 30.0F : 30.0F);
            breakNeeded = Math.max(1, Math.min(400, (int) Math.ceil(1.0F / perTick)));
        }
        breakProgress = 0;
        npc.getNavigator().clearPath();
        npc.setHeldSlot(breakSlot);
        stage = Stage.BREAK;
    }

    private void doBreak() {
        World w = npc.world;
        IBlockState s = w.getBlockState(current);
        if (s.getBlock().isAir(s, w, current)) { stage = Stage.COLLECT; collectTicks = 0; return; }
        double reach = (mode == Mode.TREE ? REACH_TREE : REACH_ORE) + 1.0D;
        Vec3d eye = npc.getPositionEyes(1.0F);
        Vec3d c = PathHelper.center(current);
        if (eye.distanceTo(c) > reach) { w.sendBlockBreakProgress(npc.getEntityId(), current, -1); stage = Stage.APPROACH; stageTicks = 0; return; }
        npc.getLookHelper().setLookPosition(c.x, c.y, c.z, 30.0F, 30.0F);
        if (breakProgress % 5 == 0) npc.swingArm(EnumHand.MAIN_HAND);
        breakProgress++;
        int stageImg = Math.min(9, (int) (10.0F * breakProgress / breakNeeded));
        w.sendBlockBreakProgress(npc.getEntityId(), current, stageImg);
        if (breakProgress < breakNeeded) return;

        w.sendBlockBreakProgress(npc.getEntityId(), current, -1);
        Block block = s.getBlock();
        boolean ok = w.destroyBlock(current, breakCanHarvest);
        if (!ok) { skip("no se rompió"); return; }
        npc.damageTool(breakSlot);
        if (currentCounts) {
            broken++;
            if (block instanceof BlockLog) brokenLogs++;
        }
        stage = Stage.COLLECT; collectTicks = 0;
    }

    // ------------------------------------------------------------ collect

    private EntityItem nearestDrop() {
        AxisAlignedBB box = npc.getEntityBoundingBox().grow(7.0D, 3.0D, 7.0D);
        List<EntityItem> items = npc.world.getEntitiesWithinAABB(EntityItem.class, box, e -> e != null && !e.isDead && !e.getItem().isEmpty());
        EntityItem best = null; double bd = Double.MAX_VALUE;
        for (EntityItem i : items) { double d = npc.getDistanceSq(i); if (d < bd) { bd = d; best = i; } }
        return best;
    }

    private void doCollect() {
        collectTicks++;
        if (collectTicks < 6) return;               // que los objetos caigan y se asienten
        EntityItem it = nearestDrop();
        if (it == null || collectTicks > 160 || npc.isInventoryFull()) { stage = Stage.SEARCH; npc.getNavigator().clearPath(); return; }
        if (npc.getDistanceSq(it) > 1.5D) {
            if (npc.getNavigator().noPath() || collectTicks % 12 == 0) npc.getNavigator().tryMoveToEntityLiving(it, 1.15D);
        }
    }

    // ------------------------------------------------------------ finishing

    private void endWork() {
        npc.getNavigator().clearPath();
        npc.setHeldSlot(-1);
        if (current != null) npc.world.sendBlockBreakProgress(npc.getEntityId(), current, -1);

        Map<String, EntityNPC.ItemCount> now = npc.countItems();
        String gained = EntityNPC.describeGain(startInv, now);
        gainItems = EntityNPC.gainMap(startInv, now);

        if (broken == 0) { fail(stopReason != null ? stopReason : "no conseguí romper nada"); return; }

        StringBuilder sb = new StringBuilder();
        if (mode == Mode.TREE) {
            sb.append("Corté ").append(brokenLogs).append(brokenLogs == 1 ? " tronco" : " troncos");
            if (treesDone > 0) sb.append(" (").append(treesDone).append(treesDone == 1 ? " árbol" : " árboles").append(")");
        } else {
            sb.append("Rompí ").append(broken).append(broken == 1 ? " bloque de " : " bloques de ").append(label);
        }
        if (!gained.isEmpty()) sb.append(". Conseguí: ").append(gained);
        else sb.append(", pero no logré recoger ningún objeto");
        if (stopReason != null && !goalReached()) sb.append(". Me detuve porque ").append(stopReason);
        if (unreachable > 0) sb.append(" (").append(unreachable).append(unreachable == 1 ? " tronco" : " troncos").append(" quedaron sin cortar por estar fuera de mi alcance)");
        sb.append(".");

        if (deliver && gainItems != null && !gainItems.isEmpty()) {
            pendingSummary = sb.toString();
            stage = Stage.DELIVER;
            deliverTicks = 0;
            return;
        }
        done(sb.toString());
    }

    private void doDeliver() {
        EntityPlayer owner = npc.getOwnerPlayer();
        if (owner == null || owner.world != npc.world) {
            done(pendingSummary + " No encuentro a quién entregárselo; me lo quedo.");
            return;
        }
        deliverTicks++;
        if (npc.getDistanceSq(owner) <= 9.0D) {
            StringBuilder given = new StringBuilder();
            boolean full = false;
            for (EntityNPC.ItemCount ic : gainItems.values()) {
                int moved = npc.giveByTemplate(owner, ic.template, ic.count);
                if (moved > 0) { if (given.length() > 0) given.append(", "); given.append(moved).append(" × ").append(ic.name); }
                if (moved < ic.count) full = true;
            }
            done(pendingSummary + (given.length() > 0 ? " Aquí tienes: " + given + "." : "")
                    + (full ? " No me cupo todo en tu inventario; lo demás me lo quedo." : ""));
            return;
        }
        if (deliverTicks > 20 * 45) { done(pendingSummary + " No logré alcanzarte para entregarlo; lo guardo."); return; }
        if (npc.getNavigator().noPath() || deliverTicks % 20 == 0) npc.getNavigator().tryMoveToEntityLiving(owner, 1.1D);
    }

    // ------------------------------------------------------------ search helpers

    private List<BlockPos> findTree() {
        World w = npc.world;
        List<BlockPos> logs = PathHelper.findBlocks(w, npc.getPosition(), s -> s.getBlock() instanceof BlockLog, 24, 4, 14, 120);
        Set<BlockPos> visited = new HashSet<>();
        final BlockPos here = npc.getPosition();
        for (BlockPos start : logs) {
            if (skipped.contains(start) || visited.contains(start)) continue;
            List<BlockPos> cluster = floodTree(start);
            visited.addAll(cluster);
            if (!isNaturalTree(cluster)) { skipped.addAll(cluster); continue; }
            List<BlockPos> todo = new ArrayList<>();
            for (BlockPos p : cluster) if (!skipped.contains(p)) todo.add(p);
            if (todo.isEmpty()) continue;
            todo.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(here::distanceSq));
            return todo;
        }
        return null;
    }

    private List<BlockPos> floodTree(BlockPos start) {
        World w = npc.world;
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> dq = new ArrayDeque<>();
        dq.add(start); seen.add(start);
        while (!dq.isEmpty() && out.size() < 160) {
            BlockPos p = dq.poll();
            out.add(p);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos n = p.add(dx, dy, dz);
                if (Math.abs(n.getX() - start.getX()) > 7 || Math.abs(n.getZ() - start.getZ()) > 7) continue;
                if (!seen.add(n)) continue;
                if (w.isBlockLoaded(n) && w.getBlockState(n).getBlock() instanceof BlockLog) dq.add(n);
            }
        }
        return out;
    }

    /** Un árbol natural tiene hojas pegadas; una casa o una pila de troncos no. Así no se tocan construcciones. */
    private boolean isNaturalTree(List<BlockPos> cluster) {
        World w = npc.world;
        Set<BlockPos> seen = new HashSet<>();
        int leaves = 0;
        for (BlockPos p : cluster) {
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos n = p.offset(f);
                if (seen.add(n) && w.getBlockState(n).getBlock() instanceof BlockLeaves) leaves++;
            }
        }
        return leaves >= 3;
    }

    private BlockPos findOre() {
        World w = npc.world;
        Predicate<IBlockState> pred = s -> { Block b = s.getBlock(); for (Block o : oreBlocks) if (b == o) return true; return false; };
        List<BlockPos> cands = PathHelper.findBlocks(w, npc.getPosition(), pred, 24, 14, 14, 60);
        BlockPos under = npc.getPosition().down();
        for (BlockPos p : cands) {
            if (skipped.contains(p) || p.equals(under)) continue;   // no minar el suelo bajo sus pies
            if (reachableSpot(p, REACH_ORE)) return p;
        }
        return null;
    }
}
