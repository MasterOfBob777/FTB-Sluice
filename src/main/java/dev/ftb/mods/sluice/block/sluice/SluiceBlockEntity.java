package dev.ftb.mods.sluice.block.sluice;

import dev.ftb.mods.sluice.SluiceConfig;
import dev.ftb.mods.sluice.block.MeshType;
import dev.ftb.mods.sluice.block.SluiceBlockEntities;
import dev.ftb.mods.sluice.capabilities.Energy;
import dev.ftb.mods.sluice.capabilities.FluidCap;
import dev.ftb.mods.sluice.capabilities.ItemsHandler;
import dev.ftb.mods.sluice.item.UpgradeItem;
import dev.ftb.mods.sluice.item.Upgrades;
import dev.ftb.mods.sluice.recipe.FTBSluiceRecipes;
import dev.ftb.mods.sluice.recipe.ItemWithWeight;
import dev.ftb.mods.sluice.recipe.SluiceRecipeInfo;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

public class SluiceBlockEntity extends BlockEntity implements TickableBlockEntity, MenuProvider {
    public final ItemsHandler inventory;
    public final LazyOptional<ItemsHandler> inventoryOptional;
    public final FluidCap tank;
    public final LazyOptional<FluidCap> fluidOptional;
    public final SluiceProperties properties;
    private final boolean isAdvanced;

    private boolean isCreative = false;

    // Upgrade -> slot id
    private static final Object2IntMap<Upgrades> UPGRADE_SLOT_INDEX = new Object2IntOpenHashMap<>();

    static {
        UPGRADE_SLOT_INDEX.put(Upgrades.LUCK, 0);
        UPGRADE_SLOT_INDEX.put(Upgrades.CONSUMPTION, 1);
        UPGRADE_SLOT_INDEX.put(Upgrades.SPEED, 2);
    }

    public final ItemStackHandler upgradeInventory = new ItemStackHandler(3) {
        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack.getItem() instanceof UpgradeItem ? super.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (stack.getItem() instanceof UpgradeItem) {
                return UPGRADE_SLOT_INDEX.getInt(((UpgradeItem) stack.getItem()).getUpgrade()) == slot;
            }

            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            SluiceBlockEntity.this.updateUpgradeCache(this);
        }
    };

    public Energy energy;
    public LazyOptional<Energy> energyOptional;
    /**
     * Amount of progress the processing step has made, 100 being fully processed and can drop
     * the outputs
     */
    public int processed;
    public int maxProcessed;
    private int fluidUsage;

    // Upgrade type, multiplication
    public final Object2IntMap<Upgrades> upgradeCache = new Object2IntOpenHashMap<>();
    public int lastPowerCost = 0;

    public SluiceBlockEntity(BlockEntityType<?> type, SluiceProperties properties) {
        super(type);

        // Finds the correct properties from the block for the specific sluice tier
        this.properties = properties;

        int powerCost = this.properties.config.costPerUse.get();
        this.isAdvanced = powerCost > 0;

        this.energy = new Energy(!isAdvanced
                ? 0
                : (int) Math.min(Math.pow(SluiceConfig.GENERAL.exponentialCostBaseN.get(), SluiceConfig.GENERAL.maxUpgradeStackSize.get() * 3 + 1)
                * powerCost, Integer.MAX_VALUE), e -> {
            // Shouldn't be needed but it's better safe.
            if (!this.isAdvanced) {
                return;
            }
            this.setChanged();
        });

        this.energyOptional = LazyOptional.of(() -> this.energy);
        this.maxProcessed = -1;
        this.fluidUsage = -1;

        // Handles state changing
        this.tank = new FluidCap(true, properties.config.tankCap.get(), e -> true);
        this.fluidOptional = LazyOptional.of(() -> this.tank);

        this.inventory = new ItemsHandler(!properties.config.allowsIO.get(), 1) {
            @Override
            protected void onContentsChanged(int slot) {
                SluiceBlockEntity.this.setChanged();
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (FTBSluiceRecipes.itemIsSluiceInput(SluiceBlockEntity.this.getBlockState().getValue(SluiceBlock.MESH), stack)) {
                    return super.insertItem(slot, stack, simulate);
                }

                return stack;
            }

            @Nonnull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }
        };

        this.inventoryOptional = LazyOptional.of(() -> this.inventory);
    }

    /**
     * Computes a list of resulting output items based on an input. We get the outputting items from the
     * custom recipe.
     */
    public List<ItemStack> getRandomResult(SluiceBlockEntity sluice, ItemStack input) {
        List<ItemStack> outputResults = new ArrayList<>();
        if (sluice.level == null) {
            return outputResults;
        }

        SluiceRecipeInfo recipe = FTBSluiceRecipes.getSluiceRecipes(sluice.tank.getFluid().getFluid(), sluice.level, sluice.getBlockState().getValue(SluiceBlock.MESH), input);

        List<ItemWithWeight> items = recipe.getItems();
        if (this.isCreative) {
            return items.stream().map(e -> e.getItem().copy()).collect(Collectors.toList());
        }

        // Luck calculation
        int additional = 0;
        if (sluice.upgradeCache.containsKey(Upgrades.LUCK)) {
            additional += Upgrades.LUCK.effectedChange * sluice.upgradeCache.getInt(Upgrades.LUCK);
        }

        Collections.shuffle(items); // Spin the wheel to make it a little less predictable
        for (ItemWithWeight result : items) {
            float number = sluice.level.getRandom().nextFloat();
            if (number <= Mth.clamp(result.weight + (additional / 100D), 0, 1)) {
                if (outputResults.size() >= recipe.getMaxDrops()) {
                    break;
                }

                outputResults.add(result.item.copy());
            }
        }

        return outputResults;
    }

    @Override
    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof SluiceBlock)) {
            return;
        }

        ItemStack input = this.inventory.getStackInSlot(0);

        if (this.maxProcessed < 0) {
            this.startProcessing(this.level, input);
        } else {
            if (this.processed < this.maxProcessed) {
                if (getBlockState().getValue(SluiceBlock.MESH) == MeshType.NONE) {
                    cancelProcessing(level, input);
                    return;
                }
                this.processed++;

                // Finish instantly
                if (this.isCreative) {
                    this.finishProcessing(this.level, state, input);
                }
            } else {
                this.finishProcessing(this.level, state, input);
            }

            if (this.processed % 4 == 0) {
                this.setChanged();
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Constants.BlockFlags.DEFAULT_AND_RERENDER);
            }
        }

        if (level.getGameTime() % 10L == 0) {
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Constants.BlockFlags.DEFAULT_AND_RERENDER);
        }
    }

    /**
     * Starts the processing process as long as we have enough fluid and an item in the inventory.
     * We also push a block update to make sure the TES is up to date.
     */
    private void startProcessing(@Nonnull Level level, ItemStack stack) {
        // No energy, no go.
        if (this.isAdvanced && this.energy.getEnergyStored() <= 0) {
            return;
        }

        if (stack.isEmpty()) {
            return;
        }

        // Throw out any residual stacks if the player has removed the mesh
        if (getBlockState().getValue(SluiceBlock.MESH) == MeshType.NONE) {
            cancelProcessing(level, stack);
            return;
        }

        // Reject if we don't have enough power to process the resource
        if (this.tank.isEmpty() || this.isAdvanced && this.energy.getEnergyStored() < computePowerCost()) {
            return;
        }

        SluiceRecipeInfo recipe = FTBSluiceRecipes.getSluiceRecipes(this.tank.getFluid().getFluid(), level, this.getBlockState().getValue(SluiceBlock.MESH), stack);

        double baseFluidUsage = recipe.getFluidUsed() * this.properties.config.fluidMod.get();
        int fluidRequirement = Math.max(40, (int) Math.round(baseFluidUsage - (baseFluidUsage * (computeEffectModifier(Upgrades.CONSUMPTION) / 100f))));
        if (this.tank.getFluidAmount() < fluidRequirement) {
            return;
        }

        // Throw items out if we don't have a recipe from them. It's simpler than giving the cap a world and mesh.
        if (recipe.getItems().isEmpty()) {
            cancelProcessing(level, stack);
            return;
        }

        this.processed = 0;

        double baseProcessingTime = recipe.getProcessingTime() * this.properties.config.timeMod.get();
        this.maxProcessed = Math.max(1, (int) Math.round(baseProcessingTime - (baseProcessingTime * (computeEffectModifier(Upgrades.SPEED) / 100f))));
        this.fluidUsage = fluidRequirement;

        this.setChanged();
        level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Constants.BlockFlags.DEFAULT_AND_RERENDER);
    }

    /**
     * Handles the output of the process. If we're using netherite, we have different uses here.
     *
     * @param itemStack the input item from the start of the process.
     */
    private void finishProcessing(@Nonnull Level level, BlockState state, ItemStack itemStack) {
        this.processed = 0;
        this.maxProcessed = -1;

        this.getRandomResult(this, itemStack)
                .forEach(e -> this.ejectItem(level, state.getValue(HORIZONTAL_FACING), e));

        this.inventory.setStackInSlot(0, ItemStack.EMPTY);

        if (!this.isCreative) {
            this.tank.internalDrain(this.fluidUsage, IFluidHandler.FluidAction.EXECUTE);
        }

        this.fluidUsage = -1;

        if (this.isAdvanced && !this.isCreative) {
            this.energy.consumeEnergy(computePowerCost(), false);
        }

        this.setChanged();
        level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Constants.BlockFlags.DEFAULT_AND_RERENDER);
    }

    private void cancelProcessing(Level level, ItemStack stack) {
        this.ejectItem(level, this.getBlockState().getValue(HORIZONTAL_FACING), stack);
        this.inventory.setStackInSlot(0, ItemStack.EMPTY);
        this.processed = 0;
        this.maxProcessed = -1;
        this.fluidUsage = -1;
        this.setChanged();
        level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Constants.BlockFlags.DEFAULT_AND_RERENDER);
    }

    private int computePowerCost() {
        int cost = this.properties.config.costPerUse.get();
        if (!upgradeCache.isEmpty()) {
            int sum = 0;
            for (int i : upgradeCache.values()) {
                sum += i;
            }

            cost = (int) Math.min(Math.pow(SluiceConfig.GENERAL.exponentialCostBaseN.get(), sum) * cost, Integer.MAX_VALUE);
        }
        this.lastPowerCost = cost;
        return cost;
    }

    private int computeEffectModifier(Upgrades upgrade) {
        return upgradeCache.getOrDefault(upgrade, 0) * upgrade.effectedChange;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        CompoundTag fluidTag = new CompoundTag();
        this.tank.writeToNBT(fluidTag);

        compound.put("Inventory", this.inventory.serializeNBT());
        compound.put("Fluid", fluidTag);
        compound.putInt("Processed", this.processed);
        compound.putInt("MaxProcessed", this.maxProcessed);
        compound.putInt("FluidUsage", this.fluidUsage);
        compound.putInt("LastPowerCost", this.lastPowerCost);

        if (this.isCreative) {
            compound.putBoolean("isCreative", true);
        }

        if (this.isAdvanced) {
            compound.put("Upgrades", upgradeInventory.serializeNBT());
            this.updateUpgradeCache(this.upgradeInventory);
            this.energyOptional.ifPresent(e -> compound.put("Energy", e.serializeNBT()));
        }

        return super.save(compound);
    }

    @Override
    public void load(BlockState state, CompoundTag compound) {
        super.load(state, compound);

        this.inventory.deserializeNBT(compound.getCompound("Inventory"));
        this.processed = compound.getInt("Processed");
        this.maxProcessed = compound.getInt("MaxProcessed");
        this.fluidUsage = compound.getInt("FluidUsage");
        this.lastPowerCost = compound.getInt("LastPowerCost");

        if (compound.contains("isCreative")) {
            this.isCreative = compound.getBoolean("isCreative");
        }

        if (this.isAdvanced) {
            this.energyOptional.ifPresent(e -> e.deserializeNBT(compound.getCompound("Energy")));
            this.upgradeInventory.deserializeNBT(compound.getCompound("Upgrades"));
            this.updateUpgradeCache(this.upgradeInventory);
        }

        if (compound.contains("Fluid")) {
            this.tank.readFromNBT(compound.getCompound("Fluid"));
        }
    }

    private void updateUpgradeCache(ItemStackHandler handler) {
        SluiceBlockEntity.this.upgradeCache.clear();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!(stack.getItem() instanceof UpgradeItem)) {
                continue;
            }

            SluiceBlockEntity.this.upgradeCache.put(((UpgradeItem) stack.getItem()).getUpgrade(), Math.min(stack.getCount(), 18));
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && this.properties.config.allowsIO.get()) {
            return this.inventoryOptional.cast();
        }

        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && this.properties.config.allowsTank.get()) {
            return this.fluidOptional.cast();
        }

        if (cap == CapabilityEnergy.ENERGY && this.isAdvanced) {
            return this.energyOptional.cast();
        }

        return super.getCapability(cap, side);
    }

    private void ejectItem(Level w, Direction direction, ItemStack stack) {
        if (this.properties.config.allowsIO.get()) {
            // Find the closest inventory to the block.
            IItemHandler handler = this.seekNearestInventory(w).orElseGet(EmptyHandler::new);

            // Empty handler does not have slots and is thus very simple to check against.
            if (handler.getSlots() != 0) {
                stack = ItemHandlerHelper.insertItemStacked(handler, stack, false);
            }
        }

        if (!stack.isEmpty()) {
            BlockPos pos = this.worldPosition.relative(direction);

            double my = 0.14D * (w.random.nextFloat() * 0.4D);

            QuickDropItemEntity itemEntity = new QuickDropItemEntity(w, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack, (this instanceof OakSluiceBlockEntity || this instanceof IronSluiceBlockEntity ? 60 : 10) * 20);

            itemEntity.setNoPickUpDelay();
            itemEntity.setDeltaMovement(0, my, 0);
            w.addFreshEntity(itemEntity);
        }
    }

    /**
     * @param level level to find the inventory from
     * @return A valid IItemHandler or a empty optional
     */
    private LazyOptional<IItemHandler> seekNearestInventory(Level level) {
        BlockPos pos = this.getBlockPos().relative(this.getBlockState().getValue(HORIZONTAL_FACING), 2);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && !(blockEntity instanceof SluiceBlockEntity)) {
            LazyOptional<IItemHandler> capability = blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
            if (capability.isPresent()) {
                return capability;
            }
        }

        return LazyOptional.empty();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public AABB getRenderBoundingBox() {
        return new AABB(this.getBlockPos()).inflate(1);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundTag tag) {
        this.load(state, tag);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 0, this.save(new CompoundTag()));
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        this.load(this.getBlockState(), pkt.getTag());
    }

    @Override
    public Component getDisplayName() {
        return new TextComponent("Sluice");
    }

    @Override
    public AbstractContainerMenu createMenu(int i, Inventory arg, Player arg2) {
        return !this.isAdvanced ? null : new SluiceBlockContainer(i, arg, this);
    }

    public static class OakSluiceBlockEntity extends SluiceBlockEntity {
        public OakSluiceBlockEntity() {
            super(SluiceBlockEntities.OAK_SLUICE.get(), SluiceProperties.OAK);
        }
    }

    public static class IronSluiceBlockEntity extends SluiceBlockEntity {
        public IronSluiceBlockEntity() {
            super(SluiceBlockEntities.IRON_SLUICE.get(), SluiceProperties.IRON);
        }
    }

    public static class DiamondSluiceBlockEntity extends SluiceBlockEntity {
        public DiamondSluiceBlockEntity() {
            super(SluiceBlockEntities.DIAMOND_SLUICE.get(), SluiceProperties.DIAMOND);
        }
    }

    public static class NetheriteSluiceBlockEntity extends SluiceBlockEntity {
        public NetheriteSluiceBlockEntity() {
            super(SluiceBlockEntities.NETHERITE_SLUICE.get(), SluiceProperties.NETHERITE);
        }
    }

    public static class EmpoweredSluiceBlockEntity extends SluiceBlockEntity {
        public EmpoweredSluiceBlockEntity() {
            super(SluiceBlockEntities.EMPOWERED_SLUICE.get(), SluiceProperties.EMPOWERED);
        }
    }
}
