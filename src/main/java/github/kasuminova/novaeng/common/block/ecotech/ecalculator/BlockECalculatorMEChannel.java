package github.kasuminova.novaeng.common.block.ecotech.ecalculator;

import github.kasuminova.novaeng.NovaEngineeringCore;
import github.kasuminova.novaeng.common.tile.ecotech.ecalculator.ECalculatorMEChannel;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BlockECalculatorMEChannel extends BlockECalculatorPart {

    public static final BlockECalculatorMEChannel INSTANCE = new BlockECalculatorMEChannel();

    protected BlockECalculatorMEChannel() {
        super(Material.IRON);
        this.setDefaultState(this.blockState.getBaseState());
        this.setRegistryName(new ResourceLocation(NovaEngineeringCore.MOD_ID, "ecalculator_me_channel"));
        this.setTranslationKey(NovaEngineeringCore.MOD_ID + '.' + "ecalculator_me_channel");
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(final World worldIn, final int meta) {
        return new ECalculatorMEChannel();
    }

}
