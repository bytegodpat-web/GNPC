package com.npcgemini.entity;

import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import com.npcgemini.task.ActionParser;
import com.npcgemini.task.NPCTask;
import com.npcgemini.util.BlockFinder;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityNPC extends EntityCreature {

    private static final DataParameter<String> PROFILE_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);
    private static final DataParameter<String> SKIN_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);

    public static final int INV_SIZE = 27;

    private final ItemStack[] inventory = new ItemStack[INV_SIZE];
    private final Map<String, BlockPos> waypoints = new HashMap<>();

    private NPCTask task = NPCTask.IDLE;
    private EntityPlayer followTarget;
    private BlockPos gotoPos;
    private BlockPos minePos;
    private Block mineBlock;
    private int mineTicks;
    private int taskTicks;
    private boolean combatMode = false;
    private EntityLivingBase combatTarget;

    private String bubbleText = null;
    private long bubbleUntil = 0L;

    public EntityNPC(World world) {
        super(world);
        this.setSize(0.6F, 1.8F);
        this.enablePersistence();
        this.setAlwaysRenderNameTag(true);
        for (int i = 0; i < INV_SIZE; i++) inventory[i] = ItemStack.EMPTY;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(PROFILE_ID, "luna");
        this.dataManager.register(SKIN_ID, "luna");
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(2, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.28D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(3.0D);
    }

    public String getProfileId() { return this.dataManager.get(PROFILE_ID); }

    public void setProfileId(String id) {
        if (id == null || id.isEmpty()) id = "luna";
        this.dataManager.set(PROFILE_ID, id.toLowerCase());
        BotProfile p = ProfileManager.get(id);
        if (p != null) {
            setSkinId(p.skinId);
            applyNameFromProfile(p);
        }
    }

    public String getSkinId() { return this.dataManager.get(SKIN_ID); }

    public void setSkinId(String id) {
        if (id == null || id.isEmpty()) id = "luna";
        this.dataManager.set(SKIN_ID, id);
    }

    public BotProfile getProfile() { return ProfileManager.getOrCreate(getProfileId()); }

    public void applyNameFromProfile(BotProfile p) {
        if (p == null) return;
        this.setCustomNameTag(p.name);
        this.setAlwaysRenderNameTag(true);
    }

    @Override
    public String getName() {
        BotProfile p = ProfileManager.get(getProfileId());
        if (p != null && p.name != null && !p.name.isEmpty()) return p.name;
        String tag = this.getCustomNameTag();
        if (tag != null && !tag.isEmpty()) return tag;
        return super.getName();
    }

    public void showBubble(String text) {
        if (text == null || text.isEmpty()) { bubbleText = null; bubbleUntil = 0; return; }
        bubbleText = text;
        int words = text.trim().split("\\s+").length;
        long duration = Math.min(12000L, 3000L + words * 250L);
        bubbleUntil = System.currentTimeMillis() + duration;
    }

    public String getBubbleText() {
        if (bubbleText == null) return null;
        if (System.currentTimeMillis() > bubbleUntil) { bubbleText = null; return null; }
        return bubbleText;
    }

    public float getBubbleAlpha() {
        if (bubbleText == null) return 0f;
        long left = bubbleUntil - System.currentTimeMillis();
        if (left > 800) return 1f;
        if (left <= 0) return 0f;
        return left / 800f;
    }

    public ItemStack getStackInSlot(int i) {
        if (i < 0 || i >= INV_SIZE) return ItemStack.EMPTY;
        return inventory[i] == null ? ItemStack.EMPTY : inventory[i];
    }

    public void setStackInSlot(int i, ItemStack stack) {
        if (i < 0 || i >= INV_SIZE) return;
        inventory[i] = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack;
    }

    public boolean addItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() == null || stack.getItem() == Items.AIR) return false;
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = getStackInSlot(i);
            if (!s.isEmpty() && s.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(s, stack)) {
                int space = Math.min(s.getMaxStackSize() - s.getCount(), stack.getCount());
                if (space > 0) {
                    s.grow(space);
                    stack.shrink(space);
                    if (stack.isEmpty()) return true;
                }
            }
        }
        for (int i = 0; i < INV_SIZE; i++) {
            if (getStackInSlot(i).isEmpty()) {
                setStackInSlot(i, stack.copy());
                stack.setCount(0);
                return true;
            }
        }
        return false;
    }

    public int deliverAllTo(EntityPlayer player) {
        int given = 0;
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = getStackInSlot(i);
            if (s.isEmpty()) continue;
            ItemStack copy = s.copy();
            boolean ok = player.inventory.addItemStackToInventory(copy);
            if (ok || copy.getCount() < s.getCount()) {
                if (copy.isEmpty()) {
                    setStackInSlot(i, ItemStack.EMPTY);
                    given++;
                } else {
                    s.setCount(copy.getCount());
                    given++;
                }
            }
        }
        return given;
    }

    public boolean hasPickaxe() {
        for (int i = 0; i < INV_SIZE; i++) {
            Item it = getStackInSlot(i).getItem();
            if (it == Items.WOODEN_PICKAXE || it == Items.STONE_PICKAXE || it == Items.IRON_PICKAXE
                    || it == Items.GOLDEN_PICKAXE || it == Items.DIAMOND_PICKAXE) return true;
        }
        return false;
    }

    public boolean hasAxe() {
        for (int i = 0; i < INV_SIZE; i++) {
            Item it = getStackInSlot(i).getItem();
            if (it == Items.WOODEN_AXE || it == Items.STONE_AXE || it == Items.IRON_AXE
                    || it == Items.GOLDEN_AXE || it == Items.DIAMOND_AXE) return true;
        }
        return false;
    }

    public String describeInventory() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (n > 0) sb.append(", ");
            sb.append(s.getCount()).append("x ").append(s.getDisplayName());
            n++;
            if (n >= 12) { sb.append("..."); break; }
        }
        return n == 0 ? "(vacio)" : sb.toString();
    }

    public void setWaypoint(String name, BlockPos pos) {
        if (name == null || pos == null) return;
        waypoints.put(name.toLowerCase(), pos.toImmutable());
    }

    public BlockPos getWaypoint(String name) {
        if (name == null) return null;
        return waypoints.get(name.toLowerCase());
    }

    public String listWaypoints() {
        if (waypoints.isEmpty()) return "(ningun sitio guardado)";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BlockPos> e : waypoints.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            BlockPos p = e.getValue();
            sb.append(e.getKey()).append(" (").append(p.getX()).append(",").append(p.getY()).append(",").append(p.getZ()).append(")");
        }
        return sb.toString();
    }

    public NPCTask getTask() { return task; }
    public boolean isCombatMode() { return combatMode; }

    public String getStatusLine() {
        StringBuilder sb = new StringBuilder(task.name());
        if (combatMode) sb.append(" | combate");
        if (getHealth() < getMaxHealth() * 0.35f) sb.append(" | vida baja");
        return sb.toString();
    }

    public String applyAction(ActionParser.Result action, EntityPlayer player) {
        if (action == null || action.type == ActionParser.Type.NONE) return null;

        switch (action.type) {
            case STOP:
                clearTask();
                combatMode = false;
                combatTarget = null;
                return "OK: parado.";
            case FOLLOW:
                task = NPCTask.FOLLOW;
                followTarget = player;
                gotoPos = null;
                minePos = null;
                return "OK: te sigo.";
            case COME:
                task = NPCTask.COME;
                followTarget = player;
                gotoPos = player.getPosition();
                return "OK: voy hacia ti.";
            case TP:
                if (player != null) {
                    this.setPosition(player.posX + 1.0, player.posY, player.posZ + 1.0);
                    clearTask();
                    return "OK: teletransporte.";
                }
                return "Error: no hay jugador.";
            case INVENTORY:
                return "Inventario: " + describeInventory();
            case OPEN_INV:
                return "OPEN_INV";
            case DELIVER: {
                if (player == null) return "Error: no hay jugador.";
                int n = deliverAllTo(player);
                return n > 0 ? "OK: te entregue items (" + n + " stacks)." : "OK: no tenia nada que darte.";
            }
            case COMBAT_ON:
                combatMode = true;
                if (task == NPCTask.IDLE) task = NPCTask.COMBAT;
                return "OK: modo combate ON. Atacare hostiles cerca.";
            case COMBAT_OFF:
                combatMode = false;
                combatTarget = null;
                if (task == NPCTask.COMBAT) task = NPCTask.IDLE;
                return "OK: modo combate OFF.";
            case HOME_SET:
                setWaypoint("casa", getPosition());
                return "OK: casa guardada aqui.";
            case HOME_GO: {
                BlockPos home = getWaypoint("casa");
                if (home == null) return "Error: no hay casa. Di: esta es tu casa";
                task = NPCTask.GOTO;
                gotoPos = home;
                return "OK: voy a casa.";
            }
            case WAYPOINTS:
                return "Sitios: " + listWaypoints();
            case REMEMBER: {
                String wp = action.waypoint != null ? action.waypoint : "punto";
                setWaypoint(wp, this.getPosition());
                return "OK: recuerdo '" + wp + "' aqui.";
            }
            case GOTO: {
                if (action.waypoint == null) return "Error: di el nombre del sitio (ej: ve a la cueva).";
                BlockPos p = getWaypoint(action.waypoint);
                if (p == null) return "Error: no conozco '" + action.waypoint + "'.";
                task = NPCTask.GOTO;
                gotoPos = p;
                return "OK: voy a '" + action.waypoint + "'.";
            }
            case CHOP: {
                BlockPos log = BlockFinder.findNearestLog(world, getPosition(), 32);
                if (log == null) return "Error: no veo arboles cerca (radio 32).";
                task = NPCTask.CHOP;
                minePos = log;
                mineBlock = world.getBlockState(log).getBlock();
                mineTicks = 0;
                return "OK: voy a talar." + (hasAxe() ? "" : " (sin hacha, ira lento)");
            }
            case MINE: {
                BlockPos target = null;
                Block want = action.block;
                if (want == null && player != null) {
                    target = BlockFinder.lookingAt(player, 6);
                    if (target != null) want = world.getBlockState(target).getBlock();
                }
                if (target == null && want != null) {
                    target = BlockFinder.findNearest(world, getPosition(), want, 32);
                }
                if (target == null) {
                    return "Error: no veo " + ActionParser.blockName(want) + " cerca (radio 32).";
                }
                if (isOre(want) && !hasPickaxe()) {
                    return "Error: no tengo pico. Tiramelo cerca.";
                }
                task = NPCTask.MINE;
                minePos = target;
                mineBlock = want != null ? want : world.getBlockState(target).getBlock();
                mineTicks = 0;
                return "OK: voy a picar " + ActionParser.blockName(mineBlock) + ".";
            }
            default:
                return null;
        }
    }

    private boolean isOre(Block b) {
        return b == Blocks.COAL_ORE || b == Blocks.IRON_ORE || b == Blocks.GOLD_ORE
                || b == Blocks.DIAMOND_ORE || b == Blocks.REDSTONE_ORE || b == Blocks.LIT_REDSTONE_ORE
                || b == Blocks.LAPIS_ORE || b == Blocks.EMERALD_ORE;
    }

    public void clearTask() {
        task = combatMode ? NPCTask.COMBAT : NPCTask.IDLE;
        followTarget = null;
        gotoPos = null;
        minePos = null;
        mineBlock = null;
        mineTicks = 0;
        taskTicks = 0;
        getNavigator().clearPath();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();

        if (world.isRemote) {
            if (ticksExisted % 40 == 0) {
                BotProfile p = ProfileManager.get(getProfileId());
                if (p != null) {
                    String current = getCustomNameTag();
                    if (current == null || !current.equals(p.name)) applyNameFromProfile(p);
                }
            }
            return;
        }

        if (ticksExisted % 10 == 0) pickupNearby();

        if (combatMode) tickCombat();

        taskTicks++;
        switch (task) {
            case FOLLOW: tickFollow(); break;
            case COME: tickCome(); break;
            case GOTO: tickGoto(); break;
            case MINE:
            case CHOP: tickMine(); break;
            case COMBAT: /* handled in tickCombat */ break;
            default: break;
        }
    }

    private void tickCombat() {
        if (!combatMode) return;
        // Buscar hostil cercano
        if (combatTarget == null || !combatTarget.isEntityAlive() || getDistance(combatTarget) > 16) {
            combatTarget = null;
            AxisAlignedBB box = getEntityBoundingBox().grow(12.0);
            List<EntityMob> mobs = world.getEntitiesWithinAABB(EntityMob.class, box, e -> e.isEntityAlive());
            double best = Double.MAX_VALUE;
            for (EntityMob m : mobs) {
                double d = getDistanceSq(m);
                if (d < best) { best = d; combatTarget = m; }
            }
        }
        if (combatTarget == null) {
            if (task == NPCTask.COMBAT) getNavigator().clearPath();
            return;
        }
        double d = getDistance(combatTarget);
        if (d > 2.0) {
            getNavigator().tryMoveToEntityLiving(combatTarget, 1.1);
        } else {
            getNavigator().clearPath();
            getLookHelper().setLookPositionWithEntity(combatTarget, 30, 30);
            if (ticksExisted % 15 == 0) {
                this.swingArm(EnumHand.MAIN_HAND);
                combatTarget.attackEntityFrom(DamageSource.causeMobDamage(this), 3.0F);
            }
        }
        if (task == NPCTask.IDLE) task = NPCTask.COMBAT;
    }

    private void tickFollow() {
        if (followTarget == null || !followTarget.isEntityAlive()) { clearTask(); return; }
        double d = getDistance(followTarget);
        if (d > 2.5) getNavigator().tryMoveToEntityLiving(followTarget, 1.0);
        else getNavigator().clearPath();
        if (d > 40) setPosition(followTarget.posX + 1, followTarget.posY, followTarget.posZ);
    }

    private void tickCome() {
        if (followTarget == null) { clearTask(); return; }
        double d = getDistance(followTarget);
        if (d <= 2.0) { clearTask(); return; }
        getNavigator().tryMoveToEntityLiving(followTarget, 1.05);
        if (taskTicks > 20 * 30) {
            setPosition(followTarget.posX + 1, followTarget.posY, followTarget.posZ);
            clearTask();
        }
    }

    private void tickGoto() {
        if (gotoPos == null) { clearTask(); return; }
        if (getDistanceSq(gotoPos) <= 4.0) { clearTask(); return; }
        getNavigator().tryMoveToXYZ(gotoPos.getX() + 0.5, gotoPos.getY(), gotoPos.getZ() + 0.5, 1.0);
        if (taskTicks > 20 * 60) clearTask();
    }

    private void tickMine() {
        if (minePos == null) { clearTask(); return; }
        IBlockState state = world.getBlockState(minePos);
        Block b = state.getBlock();
        if (b == Blocks.AIR || (mineBlock != null && b != mineBlock
                && !(mineBlock == Blocks.REDSTONE_ORE && b == Blocks.LIT_REDSTONE_ORE))) {
            if (task == NPCTask.CHOP) {
                BlockPos next = BlockFinder.findNearestLog(world, getPosition(), 16);
                if (next != null) { minePos = next; mineTicks = 0; return; }
            } else if (mineBlock != null) {
                BlockPos next = BlockFinder.findNearest(world, getPosition(), mineBlock, 16);
                if (next != null) { minePos = next; mineTicks = 0; return; }
            }
            clearTask();
            return;
        }
        if (getDistanceSq(minePos) > 6.25) {
            getNavigator().tryMoveToXYZ(minePos.getX() + 0.5, minePos.getY(), minePos.getZ() + 0.5, 1.0);
            return;
        }
        getNavigator().clearPath();
        getLookHelper().setLookPosition(minePos.getX() + 0.5, minePos.getY() + 0.5, minePos.getZ() + 0.5, 30, 30);
        mineTicks++;
        if (mineTicks % 5 == 0) swingArm(EnumHand.MAIN_HAND);
        int need = (hasPickaxe() || (task == NPCTask.CHOP && hasAxe())) ? 25 : 45;
        if (mineTicks >= need) {
            world.destroyBlock(minePos, true);
            mineTicks = 0;
            if (task == NPCTask.CHOP) {
                minePos = BlockFinder.findNearestLog(world, getPosition(), 12);
                if (minePos == null) clearTask();
            } else if (mineBlock != null) {
                minePos = BlockFinder.findNearest(world, getPosition(), mineBlock, 12);
                if (minePos == null) clearTask();
            } else clearTask();
        }
        if (taskTicks > 20 * 90) clearTask();
    }

    private void pickupNearby() {
        AxisAlignedBB box = getEntityBoundingBox().grow(1.5, 0.5, 1.5);
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, box);
        for (EntityItem ei : items) {
            if (ei.isDead) continue;
            ItemStack stack = ei.getItem();
            if (stack.isEmpty()) continue;
            ItemStack copy = stack.copy();
            if (addItem(copy)) {
                if (copy.isEmpty()) ei.setDead();
                else ei.setItem(copy);
            }
        }
    }

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) return false;

        if (!world.isRemote) {
            ItemStack held = player.getHeldItemMainhand();
            if (!player.isSneaking()
                    && held != null && !held.isEmpty()
                    && held.getItem() != null && held.getItem() != Items.AIR) {
                ItemStack one = held.copy();
                one.setCount(1);
                if (addItem(one)) {
                    held.shrink(1);
                    player.sendMessage(new TextComponentString(
                            "\u00a7d[" + getName() + "] \u00a7f*recibe " + one.getDisplayName() + "*"));
                    return true;
                }
            }
        }

        if (world.isRemote && player.isSneaking()) {
            // Shift+click: inventario grafico
            net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                    new com.npcgemini.client.gui.GuiNPCInventory(this));
            return true;
        }
        if (world.isRemote && !player.isSneaking()) {
            BotProfile p = getProfile();
            if (p != null) {
                applyNameFromProfile(p);
                player.sendStatusMessage(new TextComponentString(
                        "\u00a7d[" + p.name + "] \u00a7f*te mira*  \u00a77(" + getStatusLine() + ")"), true);
            }
            return true;
        }
        return super.processInteract(player, hand);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setString("ProfileId", getProfileId());
        compound.setString("SkinId", getSkinId());
        compound.setString("Task", task.name());
        compound.setBoolean("Combat", combatMode);

        NBTTagList inv = new NBTTagList();
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = getStackInSlot(i);
            if (!s.isEmpty()) {
                NBTTagCompound c = new NBTTagCompound();
                c.setByte("Slot", (byte) i);
                s.writeToNBT(c);
                inv.appendTag(c);
            }
        }
        compound.setTag("Items", inv);

        NBTTagList wps = new NBTTagList();
        for (Map.Entry<String, BlockPos> e : waypoints.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Name", e.getKey());
            c.setInteger("X", e.getValue().getX());
            c.setInteger("Y", e.getValue().getY());
            c.setInteger("Z", e.getValue().getZ());
            wps.appendTag(c);
        }
        compound.setTag("Waypoints", wps);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("ProfileId")) setProfileId(compound.getString("ProfileId"));
        if (compound.hasKey("SkinId")) setSkinId(compound.getString("SkinId"));
        try {
            if (compound.hasKey("Task")) task = NPCTask.valueOf(compound.getString("Task"));
        } catch (Exception ignored) { task = NPCTask.IDLE; }
        combatMode = compound.getBoolean("Combat");

        for (int i = 0; i < INV_SIZE; i++) inventory[i] = ItemStack.EMPTY;
        NBTTagList inv = compound.getTagList("Items", 10);
        for (int i = 0; i < inv.tagCount(); i++) {
            NBTTagCompound c = inv.getCompoundTagAt(i);
            int slot = c.getByte("Slot") & 255;
            if (slot >= 0 && slot < INV_SIZE) inventory[slot] = new ItemStack(c);
        }

        waypoints.clear();
        NBTTagList wps = compound.getTagList("Waypoints", 10);
        for (int i = 0; i < wps.tagCount(); i++) {
            NBTTagCompound c = wps.getCompoundTagAt(i);
            waypoints.put(c.getString("Name"), new BlockPos(c.getInteger("X"), c.getInteger("Y"), c.getInteger("Z")));
        }

        BotProfile p = ProfileManager.get(getProfileId());
        if (p != null) applyNameFromProfile(p);
    }

    @Override
    public boolean canBePushed() { return true; }

    @Override
    protected boolean canDespawn() { return false; }
}
