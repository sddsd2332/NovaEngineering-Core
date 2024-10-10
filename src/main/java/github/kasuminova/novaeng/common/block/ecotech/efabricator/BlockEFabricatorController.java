package github.kasuminova.novaeng.common.block.ecotech.efabricator;

import github.kasuminova.novaeng.NovaEngineeringCore;
import github.kasuminova.novaeng.common.CommonProxy;
import github.kasuminova.novaeng.common.tile.ecotech.efabricator.EFabricatorController;
import hellfirepvp.modularmachinery.ModularMachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineRegistry;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class BlockEFabricatorController extends BlockController {
    public static final Map<ResourceLocation, BlockEFabricatorController> REGISTRY = new LinkedHashMap<>();
    public static final BlockEFabricatorController L4;
    public static final BlockEFabricatorController L6;
    public static final BlockEFabricatorController L9;

    static {
        L4 = new BlockEFabricatorController("l4");
        REGISTRY.put(L4.registryName, L4);
        L6 = new BlockEFabricatorController("l6");
        REGISTRY.put(L6.registryName, L6);
        L9 = new BlockEFabricatorController("l9");
        REGISTRY.put(L9.registryName, L9);
    }

    protected final ResourceLocation registryName;
    protected final ResourceLocation machineRegistryName;

    public BlockEFabricatorController(final String level) {
        this.setHardness(20.0F);
        this.setResistance(2000.0F);
        this.setHarvestLevel("pickaxe", 2);
        this.fullBlock = false;

        registryName = new ResourceLocation(NovaEngineeringCore.MOD_ID, "extendable_fabricator_subsystem_" + level);
        machineRegistryName = new ResourceLocation(ModularMachinery.MODID, registryName.getPath());
        setRegistryName(registryName);
        setTranslationKey(NovaEngineeringCore.MOD_ID + '.' + registryName.getPath());
    }

    @Nonnull
    public IBlockState getActualState(@Nonnull IBlockState state, @Nonnull IBlockAccess worldIn, @Nonnull BlockPos pos) {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            return super.getActualState(state, worldIn, pos);
        }
        return state;
    }

    @Override
    public void getDrops(@Nonnull final NonNullList<ItemStack> drops, @Nonnull final IBlockAccess world, @Nonnull final BlockPos pos, @Nonnull final IBlockState state, final int fortune) {
//        Random rand = world instanceof World ? ((World) world).rand : RANDOM;
//
//        TileEntity te = world.getTileEntity(pos);
//        if (te instanceof EFabricatorController ctrl && ctrl.getOwner() != null) {
//            UUID ownerUUID = ctrl.getOwner();
//            Item dropped = getItemDropped(state, rand, fortune);
//            ItemStack stackCtrl = new ItemStack(dropped, 1);
//            if (ownerUUID != null) {
//                NBTTagCompound tag = new NBTTagCompound();
//                tag.setString("owner", ownerUUID.toString());
//                stackCtrl.setTagCompound(tag);
//            }
//            drops.add(stackCtrl);
//        } else {
//            super.getDrops(drops, world, pos, state, fortune);
//        }
    }

    @Override
    public void breakBlock(World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        Random rand = worldIn.rand;
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof EFabricatorController ctrl) {
            IOInventory inv = ctrl.getInventory();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    spawnAsEntity(worldIn, pos, stack);
                    inv.setStackInSlot(i, ItemStack.EMPTY);
                }
            }

            UUID ownerUUID = ctrl.getOwner();
            Item dropped = getItemDropped(state, rand, damageDropped(state));
            ItemStack stackCtrl = new ItemStack(dropped, 1);
            if (ownerUUID != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("owner", ownerUUID.toString());
                stackCtrl.setTagCompound(tag);
            }
            spawnAsEntity(worldIn, pos, stackCtrl);
        }

        // TODO MM warn.
        super.breakBlock(worldIn, pos, state);
    }

    @Override
    public int getLightValue(@Nonnull final IBlockState state) {
        return state.getValue(FORMED) ? 10 : 0;
    }

    @Override
    public boolean onBlockActivated(final World worldIn, @Nonnull final BlockPos pos, @Nonnull final IBlockState state, @Nonnull final EntityPlayer playerIn, @Nonnull final EnumHand hand, @Nonnull final EnumFacing facing, final float hitX, final float hitY, final float hitZ) {
        if (!worldIn.isRemote) {
            TileEntity te = worldIn.getTileEntity(pos);
            if (te instanceof EFabricatorController) {
                playerIn.openGui(NovaEngineeringCore.MOD_ID, CommonProxy.GuiType.EFABRICATOR_CONTROLLER.ordinal(), worldIn, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }

    public DynamicMachine getParentMachine() {
        return MachineRegistry.getRegistry().getMachine(machineRegistryName);
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(final World world, final IBlockState state) {
        return new EFabricatorController(machineRegistryName);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(final World worldIn, final int meta) {
        return new EFabricatorController(machineRegistryName);
    }
}
