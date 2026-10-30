package com.npcgemini.entity;

import com.google.common.collect.Multimap;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import com.npcgemini.server.NpcSpeech;
import com.npcgemini.task.NpcJob;
import com.npcgemini.util.ItemNames;
import com.npcgemini.util.ItemQuery;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * NPC de GNPC. Toda la lógica (tareas, combate, inventario) corre en el servidor
 * (el servidor integrado). El cliente solo dibuja y abre menús.
 */
public class EntityNPC extends EntityCreature {

    private static final DataParameter<String> PROFILE_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);
    private static final DataParameter<String> SKIN_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);

    public static final int INV_SIZE = 27;
    public static final int ORDER_IDLE = 0;
    public static final int ORDER_FOLLOW = 1;
    public static final int ORDER_COME = 2;

    /** Daño máximo de cada golpe de respuesta contra un jugador (aviso, no venganza). */
    public static final float RETALIATION_MAX_DAMAGE = 4.0F;

    private UUID ownerUuid;
    private boolean combatEnabled = true;
    private BlockPos homePosition;
    private final Map<String, BlockPos> rememberedPlaces = new HashMap<>();
    private int rescueCooldown = 0;
    private int movementOrder = ORDER_IDLE;
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(INV_SIZE, ItemStack.EMPTY);

    private volatile NpcJob job;
    private int heldSlot = -1;
    private int heldIdle = 0;

    // Respuesta a golpes del jugador: exactamente dos golpes, sin bucle.
    private UUID retaliateTarget;
    private int retaliateLeft = 0, retaliateDelay = 0, retaliateCooldown = 0, retaliateTicks = 0;

    // Solo cliente: burbuja de texto
    private String bubbleText = null;
    private long bubbleUntil = 0L;

    public EntityNPC(World world) {
        super(world);
        this.setSize(0.6F, 1.8F);
        this.setEntityInvulnerable(false);
        this.enablePersistence();
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
        this.tasks.addTask(1, new EntityAIAttackMelee(this, 1.2D, true));
        this.tasks.addTask(2, new EntityAIWork(this));
        this.tasks.addTask(3, new EntityAIFollowOwner(this));
        this.tasks.addTask(5, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(6, new EntityAILookIdle(this));
        this.targetTasks.addTask(1, new EntityAINpcDefend(this));
        this.targetTasks.addTask(2, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(3, new EntityAINearestAttackableTarget<EntityMob>(this, EntityMob.class, 3, true, false, e -> true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
        // EntityCreature NO registra el daño de ataque: sin esto la IA de melee fallaba al golpear.
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(1.0D);
    }

    // ---------------------------------------------------------------- orders

    public void setOwner(UUID owner) { if (!world.isRemote && owner != null) this.ownerUuid = owner; }

    /** Asigna al jugador y activa una orden de movimiento (solo servidor). */
    public void setMovementOrder(UUID owner, int order) {
        if (world.isRemote) return;
        this.ownerUuid = owner;
        this.movementOrder = order;
        if (order != ORDER_IDLE && job != null) cancelJob();
        if (order == ORDER_IDLE) this.getNavigator().clearPath();
    }

    public void setCombatEnabled(boolean enabled) { this.combatEnabled = enabled; if (!enabled) setAttackTarget(null); }
    public boolean isCombatEnabled() { return combatEnabled; }

    public void setHomePosition(BlockPos pos) { if (!world.isRemote) homePosition = pos; }
    public BlockPos getHomePosition() { return homePosition; }
    public void rememberPlace(String name, BlockPos pos) { if (!world.isRemote && name != null && pos != null) rememberedPlaces.put(name.toLowerCase(Locale.ROOT), pos); }
    public BlockPos getRememberedPlace(String name) { return rememberedPlaces.get(name == null ? "" : name.toLowerCase(Locale.ROOT)); }
    public Map<String, BlockPos> getRememberedPlaces() { return Collections.unmodifiableMap(rememberedPlaces); }

    public int getMovementOrder() { return movementOrder; }

    public EntityPlayer getOwnerPlayer() {
        if (ownerUuid == null || world.isRemote) return null;
        return world.getPlayerEntityByUUID(ownerUuid);
    }

    public void clearMovementOrder() {
        if (world.isRemote) return;
        movementOrder = ORDER_IDLE;
        getNavigator().clearPath();
    }

    // ---------------------------------------------------------------- jobs

    public NpcJob getJob() { return job; }

    public String getJobDescription() {
        NpcJob j = job;
        return j == null ? null : j.describe();
    }

    /** Empieza una tarea nueva; cancela la anterior. Solo servidor. */
    public void startJob(NpcJob newJob) {
        if (world.isRemote) return;
        if (job != null) job.cancel();
        movementOrder = ORDER_IDLE;
        getNavigator().clearPath();
        job = newJob;
    }

    /** Cancela la tarea actual. Devuelve su descripción o null si no había. */
    public String cancelJob() {
        NpcJob j = job;
        if (j == null) return null;
        j.cancel();
        job = null;
        getNavigator().clearPath();
        setHeldSlot(-1);
        return j.describe();
    }

    public void tickJob() {
        NpcJob j = job;
        if (j == null) return;
        try {
            j.run();
        } catch (Throwable t) {
            com.npcgemini.NPCGeminiMod.logger.error("Error en la tarea '" + j.describe() + "' de " + getName(), t);
            j.cancel();
            say("Algo salió mal con la tarea (" + j.describe() + ") y la cancelé.");
        }
        if (j.isFinished() && job == j) {
            job = null;
            setHeldSlot(-1);
        }
    }

    /** Habla al dueño (chat) y sobre la cabeza (burbuja). Solo servidor. */
    public void say(String text) { NpcSpeech.say(this, text); }

    // ---------------------------------------------------------------- identity

    public String getProfileId() { return this.dataManager.get(PROFILE_ID); }

    public void setProfileId(String id) {
        if (id == null || id.isEmpty()) id = "luna";
        this.dataManager.set(PROFILE_ID, id.toLowerCase());
        BotProfile p = ProfileManager.get(id);
        if (p != null) {
            setSkinId(p.skinId);
            if (p.name != null && !p.name.trim().isEmpty()) {
                setCustomNameTag(p.name.trim());
                setAlwaysRenderNameTag(true);
            }
        }
    }

    @Override public boolean getAlwaysRenderNameTag() { return hasCustomName(); }
    @Override public boolean getAlwaysRenderNameTagForRender() { return hasCustomName(); }

    public String getSkinId() { return this.dataManager.get(SKIN_ID); }

    public void setSkinId(String id) {
        if (id == null || id.isEmpty()) id = "luna";
        this.dataManager.set(SKIN_ID, id);
    }

    public BotProfile getProfile() {
        BotProfile p = ProfileManager.get(getProfileId());
        if (p == null) {
            p = ProfileManager.getOrCreate(getProfileId());
            if (hasCustomName()) { p.name = getCustomNameTag(); ProfileManager.save(p); }   // no perder el nombre
        }
        return p;
    }

    // ---------------------------------------------------------------- inventory

    public NonNullList<ItemStack> getInventoryList() { return inventory; }

    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= INV_SIZE) return ItemStack.EMPTY;
        return inventory.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INV_SIZE) return;
        inventory.set(slot, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    public int getUsedSlots() {
        int used = 0;
        for (ItemStack stack : inventory) if (!stack.isEmpty()) used++;
        return used;
    }

    public boolean isInventoryFull() {
        for (ItemStack s : inventory) if (s.isEmpty()) return false;
        return true;
    }

    /** Inserta todo lo posible; devuelve lo que no cupo. Solo servidor. */
    public int insertItem(ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) return 0;
        ItemStack remaining = incoming.copy();
        for (int i = 0; i < INV_SIZE && !remaining.isEmpty(); i++) {
            ItemStack current = inventory.get(i);
            if (!current.isEmpty() && ItemStack.areItemsEqual(current, remaining)
                    && ItemStack.areItemStackTagsEqual(current, remaining)) {
                int limit = Math.min(current.getMaxStackSize(), 64);
                int move = Math.min(remaining.getCount(), Math.max(0, limit - current.getCount()));
                if (move > 0) { current.grow(move); remaining.shrink(move); }
            }
        }
        for (int i = 0; i < INV_SIZE && !remaining.isEmpty(); i++) {
            if (inventory.get(i).isEmpty()) {
                int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.copy();
                placed.setCount(move);
                inventory.set(i, placed);
                remaining.shrink(move);
            }
        }
        return remaining.getCount();
    }

    // ----- inventario con cantidades exactas

    public static class ItemCount {
        public final ItemStack template;
        public final String name;
        public int count;
        ItemCount(ItemStack t, String n, int c) { template = t; name = n; count = c; }
    }

    private static String keyOf(ItemStack s) {
        Item it = s.getItem();
        int meta = it.getHasSubtypes() ? s.getMetadata() : 0;
        return (it.getRegistryName() == null ? "?" : it.getRegistryName().toString()) + "@" + meta;
    }

    /** Cantidades exactas por tipo de objeto. */
    public Map<String, ItemCount> countItems() {
        Map<String, ItemCount> m = new LinkedHashMap<>();
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = inventory.get(i);
            if (s.isEmpty()) continue;
            String k = keyOf(s);
            ItemCount ic = m.get(k);
            if (ic == null) { ItemStack t = s.copy(); t.setCount(1); m.put(k, new ItemCount(t, ItemNames.name(s), s.getCount())); }
            else ic.count += s.getCount();
        }
        return m;
    }

    /** Diferencia positiva entre dos inventarios. */
    public static Map<String, ItemCount> gainMap(Map<String, ItemCount> before, Map<String, ItemCount> after) {
        Map<String, ItemCount> gain = new LinkedHashMap<>();
        for (Map.Entry<String, ItemCount> e : after.entrySet()) {
            int was = before.containsKey(e.getKey()) ? before.get(e.getKey()).count : 0;
            int diff = e.getValue().count - was;
            if (diff > 0) gain.put(e.getKey(), new ItemCount(e.getValue().template, e.getValue().name, diff));
        }
        return gain;
    }

    public static String describeGain(Map<String, ItemCount> before, Map<String, ItemCount> after) {
        StringBuilder sb = new StringBuilder();
        for (ItemCount ic : gainMap(before, after).values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ic.count).append(" × ").append(ic.name);
        }
        return sb.toString();
    }

    /** "Llevo: 12 × Tronco de roble, 5 × Carbón" o "No llevo nada." */
    public String describeInventory() {
        Map<String, ItemCount> m = countItems();
        if (m.isEmpty()) return "No llevo nada.";
        StringBuilder sb = new StringBuilder("Llevo: ");
        boolean first = true;
        for (ItemCount ic : m.values()) {
            if (!first) sb.append(", ");
            sb.append(ic.count).append(" × ").append(ic.name);
            first = false;
        }
        return sb.append(" (").append(getUsedSlots()).append("/").append(INV_SIZE).append(" espacios).").toString();
    }

    /** Cuenta lo que coincide con la consulta. */
    public int countMatching(ItemQuery q) {
        int n = 0;
        for (ItemStack s : inventory) if (q.matches(s)) n += s.getCount();
        return n;
    }

    public static class Transfer {
        public int total = 0;
        public boolean playerFull = false;
        public final LinkedHashMap<String, Integer> byName = new LinkedHashMap<>();
        void add(String name, int n) { total += n; byName.merge(name, n, Integer::sum); }
        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : byName.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getValue()).append(" × ").append(e.getKey());
            }
            return sb.toString();
        }
    }

    /**
     * Entrega al jugador objetos que coincidan con la consulta. amount < 0 = todo lo que coincida.
     * Corrige el fallo de la alfa anterior: si al jugador solo le cabía una parte, el NPC
     * seguía conservando todo (objetos duplicados).
     */
    public Transfer giveMatching(EntityPlayer player, ItemQuery q, int amount) {
        Transfer t = new Transfer();
        if (world.isRemote || player == null) return t;
        int left = amount < 0 ? Integer.MAX_VALUE : amount;
        for (int slot = 0; slot < INV_SIZE && left > 0; slot++) {
            ItemStack st = inventory.get(slot);
            if (st.isEmpty() || !q.matches(st)) continue;
            moveToPlayer(player, slot, Math.min(left, st.getCount()), t);
            left = (amount < 0) ? Integer.MAX_VALUE : amount - t.total;
            if (t.playerFull) break;
        }
        return t;
    }

    /** Entrega un objeto exacto (mismo item y subtipo que la plantilla). Devuelve cuántos pasó. */
    public int giveByTemplate(EntityPlayer player, ItemStack template, int amount) {
        Transfer t = new Transfer();
        String key = keyOf(template);
        for (int slot = 0; slot < INV_SIZE && t.total < amount; slot++) {
            ItemStack st = inventory.get(slot);
            if (st.isEmpty() || !keyOf(st).equals(key)) continue;
            moveToPlayer(player, slot, Math.min(amount - t.total, st.getCount()), t);
            if (t.playerFull) break;
        }
        return t.total;
    }

    private void moveToPlayer(EntityPlayer player, int slot, int n, Transfer t) {
        ItemStack st = inventory.get(slot);
        ItemStack out = st.copy();
        out.setCount(n);
        String name = ItemNames.name(out);
        player.inventory.addItemStackToInventory(out);   // deja en 'out' lo que NO cupo
        int moved = n - out.getCount();
        if (moved > 0) {
            st.shrink(moved);
            if (st.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
            t.add(name, moved);
        }
        if (!out.isEmpty()) t.playerFull = true;
    }

    /** Entrega todo el inventario. */
    public Transfer giveAll(EntityPlayer player) {
        return giveMatching(player, ItemQuery.parse(""), -1);
    }

    private void dropInventory() {
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = inventory.get(i);
            if (!s.isEmpty()) { entityDropItem(s.copy(), 0.5F); inventory.set(i, ItemStack.EMPTY); }
        }
    }

    /** Eliminar con /npc kill: el inventario va al jugador y lo que no quepa cae al suelo. */
    public void dismiss(EntityPlayer to) {
        if (world.isRemote) return;
        cancelJob();
        if (to != null) giveAll(to);
        setHeldSlot(-1);
        dropInventory();
        NpcRegistryData.remove(world, getUniqueID());
        setDead();
    }

    // ---------------------------------------------------------------- tools / combat

    public void setHeldSlot(int slot) {
        this.heldSlot = (slot >= 0 && slot < INV_SIZE) ? slot : -1;
        if (heldSlot == -1) setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    public void damageTool(int slot) {
        if (world.isRemote || slot < 0 || slot >= INV_SIZE) return;
        ItemStack st = inventory.get(slot);
        if (st.isEmpty() || !st.isItemStackDamageable()) return;
        st.damageItem(1, this);
        if (st.isEmpty()) { inventory.set(slot, ItemStack.EMPTY); heldSlot = -1; say("Se me rompió una herramienta."); }
    }

    private float weaponBonus(ItemStack st) {
        if (st.isEmpty()) return 0F;
        Multimap<String, AttributeModifier> mm = st.getAttributeModifiers(EntityEquipmentSlot.MAINHAND);
        float sum = 0F;
        for (AttributeModifier m : mm.get(SharedMonsterAttributes.ATTACK_DAMAGE.getName())) if (m.getOperation() == 0) sum += (float) m.getAmount();
        return sum;
    }

    private int bestWeaponSlot() {
        int best = -1; float bd = 0.5F;
        for (int i = 0; i < INV_SIZE; i++) { float d = weaponBonus(inventory.get(i)); if (d > bd) { bd = d; best = i; } }
        return best;
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        int ws = bestWeaponSlot();
        ItemStack weapon = ws >= 0 ? inventory.get(ws) : ItemStack.EMPTY;
        float dmg = 1.0F + weaponBonus(weapon);
        if (target instanceof EntityPlayer) dmg = Math.min(dmg, RETALIATION_MAX_DAMAGE);
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), dmg);
        if (hit) {
            swingArm(EnumHand.MAIN_HAND);
            if (target instanceof net.minecraft.entity.EntityLivingBase) {
                ((net.minecraft.entity.EntityLivingBase) target).knockBack(this, 0.4F,
                        net.minecraft.util.math.MathHelper.sin(rotationYaw * 0.017453292F),
                        -net.minecraft.util.math.MathHelper.cos(rotationYaw * 0.017453292F));
            }
            if (ws >= 0) damageTool(ws);
        }
        return hit;
    }

    @Override
    public void setAttackTarget(net.minecraft.entity.EntityLivingBase target) {
        // Nunca ataca jugadores por la IA (la respuesta a golpes es aparte y limitada a 2),
        // ni ataca nada si el combate está desactivado.
        if (target instanceof EntityPlayer || (!combatEnabled && target != null)) target = null;
        super.setAttackTarget(target);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        boolean hurt = super.attackEntityFrom(source, amount);
        if (hurt && !world.isRemote && combatEnabled && source.getTrueSource() instanceof EntityPlayer
                && retaliateLeft == 0 && retaliateCooldown == 0 && isEntityAlive()) {
            EntityPlayer p = (EntityPlayer) source.getTrueSource();
            retaliateTarget = p.getUniqueID();
            retaliateLeft = 2;       // exactamente dos golpes
            retaliateDelay = 6;
            retaliateTicks = 0;
        }
        return hurt;
    }

    private void tickRetaliation() {
        if (retaliateCooldown > 0) retaliateCooldown--;
        if (retaliateLeft <= 0) return;
        EntityPlayer p = retaliateTarget == null ? null : world.getPlayerEntityByUUID(retaliateTarget);
        if (p == null || !p.isEntityAlive() || getDistanceSq(p) > 36.0D || ++retaliateTicks > 80) {
            retaliateLeft = 0; retaliateCooldown = 60; return;
        }
        faceEntity(p, 30.0F, 30.0F);
        if (--retaliateDelay > 0) return;
        if (getDistanceSq(p) <= 9.0D) {
            attackEntityAsMob(p);
            retaliateLeft--;
            retaliateDelay = 12;                 // separa los golpes para que el segundo cuente
            if (retaliateLeft == 0) retaliateCooldown = 60;
        } else {
            getNavigator().tryMoveToEntityLiving(p, 1.3D);
            retaliateDelay = 4;
        }
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote || isDead) return;

        if (rescueCooldown > 0) rescueCooldown--;
        if (!combatEnabled) setAttackTarget(null);
        tickRetaliation();
        attemptFallRescue();

        if (ticksExisted % 100 == 1) NpcRegistryData.touch(this);
        if (ticksExisted % 80 == 0 && getHealth() < getMaxHealth()) heal(1.0F);

        // Objeto en mano (solo visual: es la misma referencia del inventario, no se guarda en NBT)
        if (getAttackTarget() != null && isEntityAlive()) { int ws = bestWeaponSlot(); if (ws >= 0) heldSlot = ws; heldIdle = 0; }
        else if (job == null) { if (heldSlot >= 0 && ++heldIdle > 40) { heldSlot = -1; heldIdle = 0; } }
        else heldIdle = 0;
        ItemStack want = heldSlot >= 0 ? inventory.get(heldSlot) : ItemStack.EMPTY;
        if (getHeldItemMainhand() != want) setItemStackToSlot(EntityEquipmentSlot.MAINHAND, want);

        // Recoger objetos cercanos
        AxisAlignedBB area = getEntityBoundingBox().grow(1.35D, 0.65D, 1.35D);
        List<EntityItem> drops = world.getEntitiesWithinAABB(EntityItem.class, area);
        for (EntityItem drop : drops) {
            if (drop.isDead) continue;
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) continue;
            int before = stack.getCount();
            int remaining = insertItem(stack);
            if (remaining < before) {
                if (remaining <= 0) drop.setDead();
                else stack.setCount(remaining);
            }
        }
    }

    /** Intenta reducir daño de caída con agua/heno/telaraña si lleva el recurso. */
    private void attemptFallRescue() {
        if (rescueCooldown > 0 || onGround || motionY >= -0.35D || fallDistance < 4.0F) return;
        BlockPos below = new BlockPos(posX, posY - 1.0D, posZ);
        if (!world.isAirBlock(below)) return;
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack st = inventory.get(i);
            if (st.isEmpty()) continue;
            net.minecraft.block.Block place = null;
            if (st.getItem() == Items.WATER_BUCKET) {
                world.setBlockState(below, Blocks.FLOWING_WATER.getDefaultState(), 3);
                inventory.set(i, new ItemStack(Items.BUCKET));
                rescueCooldown = 40; return;
            }
            if (st.getItem() == Item.getItemFromBlock(Blocks.HAY_BLOCK)) place = Blocks.HAY_BLOCK;
            else if (st.getItem() == Item.getItemFromBlock(Blocks.WEB)) place = Blocks.WEB;
            if (place != null) {
                world.setBlockState(below, place.getDefaultState(), 3);
                st.shrink(1); if (st.isEmpty()) inventory.set(i, ItemStack.EMPTY);
                rescueCooldown = 40; return;
            }
        }
    }

    // ---------------------------------------------------------------- death

    @Override
    public void onDeath(DamageSource cause) {
        if (!world.isRemote) {
            cancelJob();
            setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
            dropInventory();   // antes el inventario se perdía al morir
            NpcRegistryData.remove(world, getUniqueID());
        }
        super.onDeath(cause);
    }

    @Override
    protected void dropEquipment(boolean wasRecentlyHit, int lootingModifier) {
        // La mano es una referencia al inventario: ya se soltó en onDeath, no duplicar.
    }

    // ---------------------------------------------------------------- bubble (cliente)

    public void showBubble(String text) {
        if (text == null || text.isEmpty()) { bubbleText = null; bubbleUntil = 0; return; }
        bubbleText = text;
        int words = text.trim().split("\\s+").length;
        long duration = 3000L + words * 250L;
        if (duration > 12000L) duration = 12000L;
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

    // ---------------------------------------------------------------- interaction / NBT

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (world.isRemote && hand == EnumHand.MAIN_HAND) {
            net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                    new com.npcgemini.client.gui.GuiNpcInteract(this));
            return true;
        }
        return super.processInteract(player, hand);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.removeTag("HandItems");   // la mano es solo visual; evita duplicar la herramienta
        compound.setString("ProfileId", getProfileId());
        compound.setString("SkinId", getSkinId());
        compound.setInteger("MovementOrder", movementOrder);
        if (ownerUuid != null) compound.setUniqueId("OwnerUUID", ownerUuid);
        ItemStackHelper.saveAllItems(compound, inventory);
        compound.setBoolean("CombatEnabled", combatEnabled);
        if (homePosition != null) compound.setLong("HomePos", homePosition.toLong());
        NBTTagCompound places = new NBTTagCompound();
        for (Map.Entry<String, BlockPos> e : rememberedPlaces.entrySet()) places.setLong(e.getKey(), e.getValue().toLong());
        compound.setTag("RememberedPlaces", places);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("ProfileId")) setProfileId(compound.getString("ProfileId"));
        if (compound.hasKey("SkinId")) setSkinId(compound.getString("SkinId"));
        movementOrder = compound.getInteger("MovementOrder");
        ownerUuid = compound.hasUniqueId("OwnerUUID") ? compound.getUniqueId("OwnerUUID") : null;
        ItemStackHelper.loadAllItems(compound, inventory);
        combatEnabled = !compound.hasKey("CombatEnabled") || compound.getBoolean("CombatEnabled");
        homePosition = compound.hasKey("HomePos") ? BlockPos.fromLong(compound.getLong("HomePos")) : null;
        rememberedPlaces.clear();
        if (compound.hasKey("RememberedPlaces")) {
            NBTTagCompound places = compound.getCompoundTag("RememberedPlaces");
            for (String key : places.getKeySet()) rememberedPlaces.put(key, BlockPos.fromLong(places.getLong(key)));
        }
    }

    @Override public boolean canBePushed() { return false; }
    @Override protected boolean canDespawn() { return false; }
}
