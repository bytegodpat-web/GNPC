package com.npcgemini.entity;

import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIBase;
import java.util.UUID;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import java.util.List;
import net.minecraft.world.World;

public class EntityNPC extends EntityCreature {

    private static final DataParameter<String> PROFILE_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);
    private static final DataParameter<String> SKIN_ID =
            EntityDataManager.createKey(EntityNPC.class, DataSerializers.STRING);

    public static final int INV_SIZE = 27;
    public static final int ORDER_IDLE = 0;
    public static final int ORDER_FOLLOW = 1;
    public static final int ORDER_COME = 2;
    private UUID ownerUuid;
    private boolean combatEnabled = true;
    private BlockPos homePosition;
    private final java.util.Map<String, BlockPos> rememberedPlaces = new java.util.HashMap<>();
    private int workCooldown = 0;
    private int rescueCooldown = 0;
    private int movementOrder = ORDER_IDLE;
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(INV_SIZE, ItemStack.EMPTY);

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
        this.tasks.addTask(2, new EntityAIFollowOwner(this));
        this.tasks.addTask(3, new EntityAIAttackMelee(this, 1.15D, true));
        this.targetTasks.addTask(1, new EntityAINearestAttackableTarget<EntityMob>(this, EntityMob.class, 10, true, false, null));
        this.tasks.addTask(1, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(2, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25D);
    }

    /** Asigna al jugador y activa una orden de movimiento (solo servidor). */
    public void setMovementOrder(UUID owner, int order) {
        if (world.isRemote) return;
        this.ownerUuid = owner;
        this.movementOrder = order;
        if (order == ORDER_IDLE) this.getNavigator().clearPath();
    }

    public void setCombatEnabled(boolean enabled) { this.combatEnabled = enabled; }
    public boolean isCombatEnabled() { return combatEnabled; }

    public void setHomePosition(BlockPos pos) { if (!world.isRemote) homePosition = pos; }
    public BlockPos getHomePosition() { return homePosition; }
    public void rememberPlace(String name, BlockPos pos) { if (!world.isRemote && name != null && pos != null) rememberedPlaces.put(name.toLowerCase(java.util.Locale.ROOT), pos); }
    public BlockPos getRememberedPlace(String name) { return rememberedPlaces.get(name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)); }
    public java.util.Map<String, BlockPos> getRememberedPlaces() { return java.util.Collections.unmodifiableMap(rememberedPlaces); }


    public int getMovementOrder() {
        return movementOrder;
    }

    public EntityPlayer getOwnerPlayer() {
        if (ownerUuid == null || world.isRemote) return null;
        return world.getPlayerEntityByUUID(ownerUuid);
    }

    public void clearMovementOrder() {
        if (world.isRemote) return;
        movementOrder = ORDER_IDLE;
        getNavigator().clearPath();
    }

    public String getProfileId() {
        return this.dataManager.get(PROFILE_ID);
    }

    public void setProfileId(String id) {
        if (id == null || id.isEmpty()) id = "luna";
        this.dataManager.set(PROFILE_ID, id.toLowerCase());
        BotProfile p = ProfileManager.get(id);
        if (p != null) {
            setSkinId(p.skinId);
            // El nombre del perfil también es el nombre visible de la entidad.
            // setCustomNameTag se guarda automáticamente en el NBT base de Entity.
            if (p.name != null && !p.name.trim().isEmpty()) {
                setCustomNameTag(p.name.trim());
                setAlwaysRenderNameTag(true);
            }
        }
    }

    @Override
    public boolean getAlwaysRenderNameTag() {
        return hasCustomName();
    }

    @Override
    public boolean getAlwaysRenderNameTagForRender() {
        return hasCustomName();
    }

    public String getSkinId() {
        return this.dataManager.get(SKIN_ID);
    }

    public void setSkinId(String id) {
        if (id == null || id.isEmpty()) id = "luna";
        this.dataManager.set(SKIN_ID, id);
    }

    public BotProfile getProfile() {
        return ProfileManager.getOrCreate(getProfileId());
    }

    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= INV_SIZE) return ItemStack.EMPTY;
        return inventory.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INV_SIZE) return;
        inventory.set(slot, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    /** Extrae una cantidad concreta y devuelve una copia independiente del inventario. */
    public ItemStack extractFromSlot(int slot, int amount) {
        if (world.isRemote || slot < 0 || slot >= INV_SIZE || amount <= 0) return ItemStack.EMPTY;
        ItemStack current = inventory.get(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        int taken = Math.min(amount, current.getCount());
        ItemStack result = current.copy();
        result.setCount(taken);
        current.shrink(taken);
        if (current.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
        return result;
    }

    /** Pasa todo el inventario del NPC al jugador. Devuelve cuantos stacks se entregaron completos. */
    public int deliverAllTo(EntityPlayer player) {
        int delivered = 0;
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack s = inventory.get(i);
            if (s.isEmpty()) continue;
            ItemStack copy = s.copy();
            if (player.inventory.addItemStackToInventory(copy)) {
                inventory.set(i, ItemStack.EMPTY);
                delivered++;
            } else if (copy.getCount() < s.getCount()) {
                s.setCount(copy.getCount());
            }
        }
        return delivered;
    }

    /** Solo cliente */
    public void showBubble(String text) {
        if (text == null || text.isEmpty()) {
            bubbleText = null;
            bubbleUntil = 0;
            return;
        }
        bubbleText = text;
        int words = text.trim().split("\\s+").length;
        long duration = 3000L + words * 250L;
        if (duration > 12000L) duration = 12000L;
        bubbleUntil = System.currentTimeMillis() + duration;
    }

    public String getBubbleText() {
        if (bubbleText == null) return null;
        if (System.currentTimeMillis() > bubbleUntil) {
            bubbleText = null;
            return null;
        }
        return bubbleText;
    }

    public float getBubbleAlpha() {
        if (bubbleText == null) return 0f;
        long left = bubbleUntil - System.currentTimeMillis();
        if (left > 800) return 1f;
        if (left <= 0) return 0f;
        return left / 800f;
    }

    /** Recoge objetos cercanos en el servidor y los almacena respetando los límites de stack. */
    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote || isDead) return;
        if (workCooldown > 0) workCooldown--;
        if (rescueCooldown > 0) rescueCooldown--;
        if (!combatEnabled) setAttackTarget(null);
        attemptFallRescue();
        AxisAlignedBB area = getEntityBoundingBox().grow(1.35D, 0.65D, 1.35D);
        List<EntityItem> drops = world.getEntitiesWithinAABB(EntityItem.class, area);
        for (EntityItem drop : drops) {
            if (drop.isDead || drop.getPickupDelay() > 0) continue;
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) continue;
            int before = stack.getCount();
            int remaining = insertItem(stack);
            if (remaining < before) {
                if (remaining <= 0) drop.setDead();
                else stack.setCount(remaining);
                // Pequeña pausa para evitar procesar repetidamente el mismo objeto en el mismo tick.
                drop.setPickupDelay(2);
            }
        }
    }

    /** Inserta todo lo posible; devuelve la cantidad que no cupo. Solo debe llamarse en servidor. */
    public int insertItem(ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) return 0;
        ItemStack remaining = incoming.copy();
        // Primero completar stacks compatibles existentes.
        for (int i = 0; i < INV_SIZE && !remaining.isEmpty(); i++) {
            ItemStack current = inventory.get(i);
            if (!current.isEmpty() && ItemStack.areItemsEqual(current, remaining)
                    && ItemStack.areItemStackTagsEqual(current, remaining)) {
                int limit = Math.min(current.getMaxStackSize(), 64);
                int move = Math.min(remaining.getCount(), Math.max(0, limit - current.getCount()));
                if (move > 0) {
                    current.grow(move);
                    remaining.shrink(move);
                }
            }
        }
        // Después ocupar huecos vacíos.
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

    /** Entrega una cantidad solicitada al jugador, dejando el sobrante en el inventario. */
    public int giveItemsTo(EntityPlayer player, Item wanted, int amount) {
        if (world.isRemote || player == null || amount <= 0) return 0;
        int delivered = 0;
        for (int i = 0; i < INV_SIZE && delivered < amount; i++) {
            ItemStack st = inventory.get(i);
            if (st.isEmpty() || (wanted != null && st.getItem() != wanted)) continue;
            int n = Math.min(amount - delivered, st.getCount());
            ItemStack out = st.copy(); out.setCount(n);
            if (player.inventory.addItemStackToInventory(out)) {
                st.shrink(n); delivered += n;
                if (st.isEmpty()) inventory.set(i, ItemStack.EMPTY);
            }
        }
        return delivered;
    }

    /** Orden de trabajo simple: localiza y rompe bloques del tipo pedido dentro de un radio seguro. */
    public int workOnBlock(net.minecraft.block.Block target, int radius) {
        if (world.isRemote || target == null || workCooldown > 0) return 0;
        BlockPos origin = getPosition();
        for (BlockPos pos : BlockPos.getAllInBox(origin.add(-radius, -3, -radius), origin.add(radius, 3, radius))) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() != target) continue;
            if (getDistanceSqToCenter(pos) > (radius * radius)) continue;
            if (world.destroyBlock(pos, true, this)) { workCooldown = 12; return 1; }
        }
        return 0;
    }

    public int chopNearbyTree(int radius) {
        if (world.isRemote || workCooldown > 0) return 0;
        BlockPos origin = getPosition();
        for (BlockPos pos : BlockPos.getAllInBox(origin.add(-radius, -2, -radius), origin.add(radius, 8, radius))) {
            net.minecraft.block.Block b = world.getBlockState(pos).getBlock();
            if (b == Blocks.LOG || b == Blocks.LOG2) {
                if (world.destroyBlock(pos, true, this)) { workCooldown = 12; return 1; }
            }
        }
        return 0;
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

    public int getUsedSlots() {
        int used = 0;
        for (ItemStack stack : inventory) if (!stack.isEmpty()) used++;
        return used;
    }

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
        compound.setString("ProfileId", getProfileId());
        compound.setString("SkinId", getSkinId());
        compound.setInteger("MovementOrder", movementOrder);
        if (ownerUuid != null) compound.setUniqueId("OwnerUUID", ownerUuid);
        ItemStackHelper.saveAllItems(compound, inventory);
        compound.setBoolean("CombatEnabled", combatEnabled);
        if (homePosition != null) compound.setLong("HomePos", homePosition.toLong());
        NBTTagCompound places = new NBTTagCompound();
        for (java.util.Map.Entry<String, BlockPos> e : rememberedPlaces.entrySet()) places.setLong(e.getKey(), e.getValue().toLong());
        compound.setTag("RememberedPlaces", places);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("ProfileId")) {
            setProfileId(compound.getString("ProfileId"));
        }
        if (compound.hasKey("SkinId")) {
            setSkinId(compound.getString("SkinId"));
        }
        movementOrder = compound.getInteger("MovementOrder");
        ownerUuid = compound.hasUniqueId("OwnerUUID") ? compound.getUniqueId("OwnerUUID") : null;
        ItemStackHelper.loadAllItems(compound, inventory);
        combatEnabled = !compound.hasKey("CombatEnabled") || compound.getBoolean("CombatEnabled");
        homePosition = compound.hasKey("HomePos") ? BlockPos.fromLong(compound.getLong("HomePos")) : null;
        rememberedPlaces.clear();
        if (compound.hasKey("RememberedPlaces")) { NBTTagCompound places = compound.getCompoundTag("RememberedPlaces"); for (String key : places.getKeySet()) rememberedPlaces.put(key, BlockPos.fromLong(places.getLong(key))); }
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }
}
