package dev.ftb.mods.sluice.block.sluice;

import dev.ftb.mods.sluice.FTBSluice;
import dev.ftb.mods.sluice.block.MeshType;
import dev.ftb.mods.sluice.block.SluiceBlockEntities;
import dev.ftb.mods.sluice.block.SluiceBlocks;
import dev.ftb.mods.sluice.item.MeshItem;
import dev.ftb.mods.sluice.recipe.FTBSluiceRecipes;
import dev.ftb.mods.sluice.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

public class SluiceBlock extends Block {
    public static final EnumProperty<MeshType> MESH = EnumProperty.create("mesh", MeshType.class);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape NORTH_BODY_SHAPE = Stream.of(Block.box(12.5, 0, 0, 14.5, 1, 1), Block.box(1.5, 0, 13.5, 3.5, 1, 15.5), Block.box(12.5, 0, 13.5, 14.5, 1, 15.5), Block.box(1.5, 0, 0, 3.5, 1, 1), Block.box(1, 1, 0, 15, 2, 16), Block.box(14, 2, 0, 15, 8, 16), Block.box(1, 2, 0, 2, 8, 16), Block.box(2, 5, 0, 14, 8, 1), Block.box(2, 2, 15, 14, 8, 16), Block.box(2, 2, 0, 14, 2.5, 1), Block.box(2, 7, 1, 14, 12, 2), Block.box(2, 7, 14, 14, 12, 15), Block.box(13, 7, 2, 14, 12, 14), Block.box(2, 7, 2, 3, 12, 14)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
    private static final VoxelShape EAST_BODY_SHAPE = Stream.of(Block.box(15, 0, 12.5, 16, 1, 14.5), Block.box(0.5, 0, 1.5, 2.5, 1, 3.5), Block.box(0.5, 0, 12.5, 2.5, 1, 14.5), Block.box(15, 0, 1.5, 16, 1, 3.5), Block.box(0, 1, 1, 16, 2, 15), Block.box(0, 2, 14, 16, 8, 15), Block.box(0, 2, 1, 16, 8, 2), Block.box(15, 5, 2, 16, 8, 14), Block.box(0, 2, 2, 1, 8, 14), Block.box(15, 2, 2, 16, 2.5, 14), Block.box(14, 7, 2, 15, 12, 14), Block.box(1, 7, 2, 2, 12, 14), Block.box(2, 7, 13, 14, 12, 14), Block.box(2, 7, 2, 14, 12, 3)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
    private static final VoxelShape SOUTH_BODY_SHAPE = Stream.of(Block.box(1.5, 0, 15, 3.5, 1, 16), Block.box(12.5, 0, 0.5, 14.5, 1, 2.5), Block.box(1.5, 0, 0.5, 3.5, 1, 2.5), Block.box(12.5, 0, 15, 14.5, 1, 16), Block.box(1, 1, 0, 15, 2, 16), Block.box(1, 2, 0, 2, 8, 16), Block.box(14, 2, 0, 15, 8, 16), Block.box(2, 5, 15, 14, 8, 16), Block.box(2, 2, 0, 14, 8, 1), Block.box(2, 2, 15, 14, 2.5, 16), Block.box(2, 7, 14, 14, 12, 15), Block.box(2, 7, 1, 14, 12, 2), Block.box(2, 7, 2, 3, 12, 14), Block.box(13, 7, 2, 14, 12, 14)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
    private static final VoxelShape WEST_BODY_SHAPE = Stream.of(Block.box(0, 0, 1.5, 1, 1, 3.5), Block.box(13.5, 0, 12.5, 15.5, 1, 14.5), Block.box(13.5, 0, 1.5, 15.5, 1, 3.5), Block.box(0, 0, 12.5, 1, 1, 14.5), Block.box(0, 1, 1, 16, 2, 15), Block.box(0, 2, 1, 16, 8, 2), Block.box(0, 2, 14, 16, 8, 15), Block.box(0, 5, 2, 1, 8, 14), Block.box(15, 2, 2, 16, 8, 14), Block.box(0, 2, 2, 1, 2.5, 14), Block.box(1, 7, 2, 2, 12, 14), Block.box(14, 7, 2, 15, 12, 14), Block.box(2, 7, 2, 14, 12, 3), Block.box(2, 7, 13, 14, 12, 14)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    private static final VoxelShape NORTH_FRONT_SHAPE = Stream.of(Block.box(2, 1.5, 12, 14, 2.5, 13), Block.box(1, 2, 0, 2, 4, 16), Block.box(2, 1.5, 8, 14, 2.5, 9), Block.box(2, 1.5, 4, 14, 2.5, 5), Block.box(1, 1, 0, 15, 2, 16), Block.box(14, 2, 0, 15, 4, 16), Block.box(12.5, 0, 0.5, 14.5, 1, 2.5), Block.box(1.5, 0, 0.5, 3.5, 1, 2.5), Block.box(1.5, 0, 15, 3.5, 1, 16), Block.box(12.5, 0, 15, 14.5, 1, 16)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
    private static final VoxelShape EAST_FRONT_SHAPE = Stream.of(Block.box(3, 1.5, 2, 4, 2.5, 14), Block.box(0, 2, 1, 16, 4, 2), Block.box(7, 1.5, 2, 8, 2.5, 14), Block.box(11, 1.5, 2, 12, 2.5, 14), Block.box(0, 1, 1, 16, 2, 15), Block.box(0, 2, 14, 16, 4, 15), Block.box(13.5, 0, 12.5, 15.5, 1, 14.5), Block.box(13.5, 0, 1.5, 15.5, 1, 3.5), Block.box(0, 0, 1.5, 1, 1, 3.5), Block.box(0, 0, 12.5, 1, 1, 14.5)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
    private static final VoxelShape SOUTH_FRONT_SHAPE = Stream.of(Block.box(2, 1.5, 3, 14, 2.5, 4), Block.box(14, 2, 0, 15, 4, 16), Block.box(2, 1.5, 7, 14, 2.5, 8), Block.box(2, 1.5, 11, 14, 2.5, 12), Block.box(1, 1, 0, 15, 2, 16), Block.box(1, 2, 0, 2, 4, 16), Block.box(1.5, 0, 13.5, 3.5, 1, 15.5), Block.box(12.5, 0, 13.5, 14.5, 1, 15.5), Block.box(12.5, 0, 0, 14.5, 1, 1), Block.box(1.5, 0, 0, 3.5, 1, 1)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
    private static final VoxelShape WEST_FRONT_SHAPE = Stream.of(Block.box(12, 1.5, 2, 13, 2.5, 14), Block.box(0, 2, 14, 16, 4, 15), Block.box(8, 1.5, 2, 9, 2.5, 14), Block.box(4, 1.5, 2, 5, 2.5, 14), Block.box(0, 1, 1, 16, 2, 15), Block.box(0, 2, 1, 16, 4, 2), Block.box(0.5, 0, 1.5, 2.5, 1, 3.5), Block.box(0.5, 0, 12.5, 2.5, 1, 14.5), Block.box(15, 0, 12.5, 16, 1, 14.5), Block.box(15, 0, 1.5, 16, 1, 3.5)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    private static final HashMap<Direction, Pair<VoxelShape, VoxelShape>> SHAPES;

    static {
        SHAPES = new HashMap<>();
        SHAPES.put(Direction.NORTH, Pair.of(NORTH_BODY_SHAPE, NORTH_FRONT_SHAPE));
        SHAPES.put(Direction.EAST, Pair.of(EAST_BODY_SHAPE, EAST_FRONT_SHAPE));
        SHAPES.put(Direction.SOUTH, Pair.of(SOUTH_BODY_SHAPE, SOUTH_FRONT_SHAPE));
        SHAPES.put(Direction.WEST, Pair.of(WEST_BODY_SHAPE, WEST_FRONT_SHAPE));
    }

    private final SluiceProperties props;

    public SluiceBlock(SluiceProperties props) {
        super(Properties.of(Material.METAL).sound(SoundType.METAL).strength(0.9F));
        this.registerDefaultState(this.getStateDefinition().any()
                .setValue(MESH, MeshType.NONE)
                .setValue(PART, Part.MAIN)
                .setValue(HORIZONTAL_FACING, Direction.NORTH));

        this.props = props;
    }
    
    @Override
    public boolean hasTileEntity(BlockState state) {
        return state.getValue(PART) != Part.FUNNEL;
    }

    @Override
    public BlockEntity createTileEntity(BlockState state, BlockGetter world) {
        if (state.getValue(PART) == Part.FUNNEL) {
            return null;
        }

        if (state.getBlock() == SluiceBlocks.OAK_SLUICE.get()) {
            return SluiceBlockEntities.OAK_SLUICE.get().create();
        } else if (state.getBlock() == SluiceBlocks.IRON_SLUICE.get()) {
            return SluiceBlockEntities.IRON_SLUICE.get().create();
        } else if (state.getBlock() == SluiceBlocks.DIAMOND_SLUICE.get()) {
            return SluiceBlockEntities.DIAMOND_SLUICE.get().create();
        } else if (state.getBlock() == SluiceBlocks.EMPOWERED_SLUICE.get()) {
            return SluiceBlockEntities.EMPOWERED_SLUICE.get().create();
        } else {
            return SluiceBlockEntities.NETHERITE_SLUICE.get().create();
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState p_220076_1_, LootContext.Builder p_220076_2_) {
        return p_220076_1_.getValue(PART) == Part.FUNNEL ? new ArrayList<>() : super.getDrops(p_220076_1_, p_220076_2_);
    }

    @Override
    @Deprecated
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(HORIZONTAL_FACING);
        if (!SHAPES.containsKey(direction)) {
            // HOW?!
            return Shapes.empty();
        }

        Pair<VoxelShape, VoxelShape> bodyFrontShapes = SHAPES.get(direction);
        return state.getValue(PART) == Part.MAIN
                ? bodyFrontShapes.getKey()
                : bodyFrontShapes.getValue();
    }

    @Override
    @Deprecated
    @OnlyIn(Dist.CLIENT)
    public float getShadeBrightness(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 1F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(PART) == Part.FUNNEL) {
            return InteractionResult.PASS;
        }

        ItemStack itemStack = player.getItemInHand(hand);
        BlockEntity tileEntity = world.getBlockEntity(pos);

        if (!(tileEntity instanceof SluiceBlockEntity)) {
            return InteractionResult.SUCCESS;
        }


        SluiceBlockEntity sluice = (SluiceBlockEntity) tileEntity;

        if (itemStack.isEmpty() && !world.isClientSide() && !player.isCrouching() && sluice.properties.config.upgradeable.get()) {
            NetworkHooks.openGui((ServerPlayer) player, sluice, pos);
            return InteractionResult.SUCCESS;
        } else if (player.isCrouching()) {
            if (state.getValue(MESH) != MeshType.NONE && itemStack.isEmpty()) {
                ItemStack current = state.getValue(MESH).getItemStack();
                world.setBlock(pos, state.setValue(MESH, MeshType.NONE), 3);

                if (!world.isClientSide()) {
                    ItemHandlerHelper.giveItemToPlayer(player, current);
                }

                sluice.clearCache();
            }

            return InteractionResult.SUCCESS;
        } else if (itemStack.getItem() instanceof MeshItem) {
            MeshType type = ((MeshItem) itemStack.getItem()).mesh;
            if (state.getValue(MESH) != type) {
                if (type == MeshType.BLAZING && state.getBlock() != SluiceBlocks.EMPOWERED_SLUICE.get()) {
                    if (world.isClientSide) {
                        player.displayClientMessage(new TranslatableComponent(FTBSluice.MOD_ID + ".block.sluice.warning.wrong_sluice"), true);
                    }

                    return InteractionResult.FAIL;
                }

                ItemStack current = state.getValue(MESH).getItemStack();
                world.setBlock(pos, state.setValue(MESH, type), 3);
                if (!player.abilities.instabuild) {
                    itemStack.shrink(1);

                    if (!world.isClientSide()) {
                        ItemHandlerHelper.giveItemToPlayer(player, current);
                    }
                }

                sluice.clearCache();
            }

            return InteractionResult.SUCCESS;
        } else if (itemStack.getItem() instanceof BucketItem || itemStack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).isPresent()) {
            if (!world.isClientSide()) {
                FluidUtil.interactWithFluidHandler(player, hand, sluice.tank);
            }
        } else if (FTBSluiceRecipes.itemIsSluiceInput(state.getValue(MESH), itemStack)) {
            if (!world.isClientSide()) {
                if (sluice.inventory.getStackInSlot(0).isEmpty()) {
                    sluice.clearCache();
                    ItemStack copy = itemStack.copy();
                    copy.setCount(1);
                    sluice.inventory.internalInsert(0, copy, false);
                    itemStack.shrink(1);
                }
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MESH, PART, HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos offsetPos = context.getClickedPos().relative(context.getHorizontalDirection().getOpposite());
        return context.getLevel().getBlockState(offsetPos).canBeReplaced(context)
                ? this.defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite()).setValue(PART, Part.MAIN)
                : null;
    }

    @Override
    @Deprecated
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos pos, BlockPos facingPos) {
        return super.updateShape(state, facing, facingState, world, pos, facingPos);
    }

    @Override
    public BlockState rotate(BlockState p_185499_1_, Rotation p_185499_2_) {
        return p_185499_1_;
    }

    @Override
    public BlockState rotate(BlockState state, LevelAccessor world, BlockPos pos, Rotation direction) {
        return state;
    }

    @Override
    public BlockState mirror(BlockState p_185471_1_, Mirror p_185471_2_) {
        return p_185471_1_;
    }

    @Override
    @Deprecated
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            Direction direction = state.getValue(HORIZONTAL_FACING);
            BlockPos endPos = pos.relative(state.getValue(PART) == Part.FUNNEL
                    ? direction.getOpposite()
                    : direction);
            BlockState endState = world.getBlockState(endPos);

            // Don't act on the funnel
            if (state.getValue(PART) == Part.FUNNEL) {
                if (endState.getBlock() instanceof SluiceBlock && endState.getValue(PART) == Part.MAIN) {
                    world.removeBlock(endPos, false);
                    if (world instanceof ServerLevel) {
                        List<ItemStack> drops = getDrops(state.setValue(PART, Part.MAIN), (ServerLevel) world, pos, null);
                        drops.forEach(e -> popResource(world, pos, e));
                    }
                }
            } else {
                BlockEntity tileEntity = world.getBlockEntity(pos);

                if (tileEntity instanceof SluiceBlockEntity) {
                    SluiceBlockEntity sluice = (SluiceBlockEntity) tileEntity;
                    popResource(world, pos, sluice.inventory.getStackInSlot(0));
                    world.updateNeighbourForOutputSignal(pos, this);

                    // Drop the upgrade inventory
                    for (int i = 0; i < sluice.upgradeInventory.getSlots(); i++) {
                        popResource(world, pos, sluice.upgradeInventory.getStackInSlot(i));
                    }
                }

                world.removeBlock(endPos, false);
                popResource(world, pos, state.getValue(MESH).getItemStack());

                super.onRemove(state, world, pos, newState, isMoving);
            }
        } else {
            super.onRemove(state, world, pos, newState, isMoving);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable  BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        boolean isShift = Screen.hasShiftDown();

        tooltip.add(new TextComponent("" + TextUtil.INFO)
                .append(new TextComponent(" (Shift)").withStyle(isShift ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY))
                .withStyle(ChatFormatting.BLUE));

        if (isShift) {
            tooltip.add(new TranslatableComponent("ftbsluice.properties.processing_time",
                    new TextComponent(props.config.timeMod.get() + "").withStyle(TextUtil.COLOUR_HIGHLIGHT)).withStyle(ChatFormatting.GRAY));
            tooltip.add(new TranslatableComponent("ftbsluice.properties.fluid_usage",
                    new TextComponent(props.config.fluidMod.get() + "").withStyle(TextUtil.COLOUR_HIGHLIGHT)).withStyle(ChatFormatting.GRAY));
            tooltip.add(new TranslatableComponent("ftbsluice.properties.tank",
                    new TextComponent(props.config.tankCap.get() + "").withStyle(TextUtil.COLOUR_HIGHLIGHT)).withStyle(ChatFormatting.GRAY));

            tooltip.add(new TranslatableComponent("ftbsluice.properties.auto",
                    new TranslatableComponent("ftbsluice.properties.auto.item").withStyle(props.config.allowsIO.get() ? TextUtil.COLOUR_TRUE : TextUtil.COLOUR_FALSE),
                    new TranslatableComponent("ftbsluice.properties.auto.fluid").withStyle(props.config.allowsTank.get() ? TextUtil.COLOUR_TRUE : TextUtil.COLOUR_FALSE)
            ).withStyle(ChatFormatting.GRAY));

            if (props.config.upgradeable.get()) {
                tooltip.add(new TranslatableComponent("ftbsluice.properties.upgradeable").withStyle(TextUtil.COLOUR_INFO));
            }
        } else {
            tooltip.add(new TranslatableComponent("ftbsluice.tooltip.sluice_" + this.props.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack item) {
        super.setPlacedBy(level, pos, state, entity, item);
        if (!level.isClientSide) {
            BlockPos lv = pos.relative(state.getValue(HORIZONTAL_FACING));
            level.setBlock(lv, state.setValue(PART, Part.FUNNEL), 3);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    public enum Part implements StringRepresentable {
        MAIN("main"),
        FUNNEL("funnel");

        String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
