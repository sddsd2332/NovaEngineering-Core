package github.kasuminova.novaeng.common.item.estorage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import com.mekeng.github.util.helpers.GasCellConfig;
import github.kasuminova.novaeng.NovaEngineeringCore;
import github.kasuminova.novaeng.common.block.estorage.prop.DriveStorageLevel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

public class EStorageCellGas extends EStorageCell<IAEGasStack> {

    public static final EStorageCellGas LEVEL_A = new EStorageCellGas(DriveStorageLevel.A, 16, 4);
    public static final EStorageCellGas LEVEL_B = new EStorageCellGas(DriveStorageLevel.B, 64, 16);
    public static final EStorageCellGas LEVEL_C = new EStorageCellGas(DriveStorageLevel.C, 256, 64);

    public EStorageCellGas(final DriveStorageLevel level, final int millionBytes, final int byteMultiplier) {
        super(level, millionBytes, byteMultiplier);
        setRegistryName(new ResourceLocation(NovaEngineeringCore.MOD_ID, "estorage_cell_gas_" + millionBytes + "m"));
        setTranslationKey(NovaEngineeringCore.MOD_ID + '.' + "estorage_cell_gas_" + millionBytes + "m");
    }

    @Override
    public int getTotalTypes(@Nonnull final ItemStack cellItem) {
        return 25;
    }

    @Override
    public int getBytesPerType(@Nonnull final ItemStack cellItem) {
        return byteMultiplier * 1024;
    }

    @Override
    public IItemHandler getConfigInventory(final ItemStack is) {
        return new GasCellConfig(is);
    }

    @Nonnull
    @Override
    public IStorageChannel<IAEGasStack> getChannel() {
        return AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
    }
}
