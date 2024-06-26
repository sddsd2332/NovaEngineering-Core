package github.kasuminova.novaeng.common.network;

import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.tile.inventory.AppEngCellInventory;
import github.kasuminova.novaeng.client.gui.GuiEStorageController;
import github.kasuminova.novaeng.common.block.estorage.prop.DriveStorageLevel;
import github.kasuminova.novaeng.common.block.estorage.prop.DriveStorageType;
import github.kasuminova.novaeng.common.container.data.EStorageCellData;
import github.kasuminova.novaeng.common.container.data.EStorageEnergyData;
import github.kasuminova.novaeng.common.estorage.ECellDriveWatcher;
import github.kasuminova.novaeng.common.estorage.EStorageCellHandler;
import github.kasuminova.novaeng.common.item.estorage.EStorageCell;
import github.kasuminova.novaeng.common.tile.estorage.EStorageCellDrive;
import github.kasuminova.novaeng.common.tile.estorage.EStorageController;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PktEStorageControllerGUIData implements IMessage, IMessageHandler<PktEStorageControllerGUIData, IMessage> {

    protected final List<EStorageCellData> dataList = new ArrayList<>();
    protected EStorageEnergyData energyData = null;

    public PktEStorageControllerGUIData() {
    }

    public PktEStorageControllerGUIData(final EStorageController controller) {
        List<EStorageCellDrive> drives = controller.getCellDrives();
        drives.forEach(drive -> {
            AppEngCellInventory driveInv = drive.getDriveInv();
            ItemStack stack = driveInv.getStackInSlot(0);
            if (stack.isEmpty()) {
                return;
            }
            EStorageCellHandler handler = EStorageCellHandler.getHandler(stack);
            if (handler == null) {
                return;
            }
            EStorageCell<?> cell = (EStorageCell<?>) stack.getItem();
            DriveStorageType type = EStorageCellDrive.getCellType(cell);
            if (type == null) {
                return;
            }
            DriveStorageLevel level = cell.getLevel();
            ECellDriveWatcher<IAEItemStack> watcher = drive.getWatcher();
            if (watcher == null) {
                return;
            }
            ICellInventoryHandler<?> cellInventory = (ICellInventoryHandler<?>) watcher.getInternal();
            if (cellInventory == null) {
                return;
            }
            ICellInventory<?> cellInv = cellInventory.getCellInv();
            if (cellInv == null) {
                return;
            }
            long storedTypes = cellInv.getStoredItemTypes();
            long usedBytes = cellInv.getUsedBytes();
            dataList.add(new EStorageCellData(type, level, (int) storedTypes, usedBytes));
        });
        energyData = new EStorageEnergyData(controller.getEnergyStored(), controller.getMaxEnergyStore(), controller.getEnergyConsumePerTick());
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            int type = buf.readByte();
            int level = buf.readByte();
            int usedTypes = buf.readByte();
            long usedBytes = buf.readLong();
            dataList.add(new EStorageCellData(DriveStorageType.values()[type], DriveStorageLevel.values()[level], usedTypes, usedBytes));
        }
        energyData = new EStorageEnergyData(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(dataList.size());
        dataList.forEach(data -> {
            int type = data.type().ordinal();
            int level = data.level().ordinal();
            int usedTypes = data.usedTypes();
            long usedBytes = data.usedBytes();
            buf.writeByte(type);
            buf.writeByte(level);
            buf.writeByte(usedTypes);
            buf.writeLong(usedBytes);
        });
        buf.writeDouble(energyData.energyStored());
        buf.writeDouble(energyData.maxEnergyStore());
        buf.writeDouble(energyData.energyConsumePerTick());
    }

    @Override
    public IMessage onMessage(final PktEStorageControllerGUIData message, final MessageContext ctx) {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            processPacket(message);
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    protected static void processPacket(final PktEStorageControllerGUIData message) {
        List<EStorageCellData> dataList = message.dataList;
        EStorageEnergyData energyData = message.energyData;
        GuiScreen cur = Minecraft.getMinecraft().currentScreen;
        if (!(cur instanceof GuiEStorageController)) {
            return;
        }
        List<EStorageCellData> sorted = dataList.stream()
                .sorted((o1, o2) -> {
                    int byteResult = Long.compare(o2.usedBytes(), o1.usedBytes());
                    if (byteResult != 0) {
                        return byteResult;
                    }
                    int typeResult = Integer.compare(o2.usedTypes(), o1.usedTypes());
                    if (typeResult != 0) {
                        return typeResult;
                    }
                    return Integer.compare(o2.level().ordinal(), o1.level().ordinal());
                })
                .collect(Collectors.toList());

        Minecraft.getMinecraft().addScheduledTask(() -> {
            GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;
            if (!(currentScreen instanceof GuiEStorageController controllerGUI)) {
                return;
            }
            controllerGUI.setCellDataList(sorted);
            controllerGUI.setEnergyData(energyData);
            controllerGUI.onDataReceived();
        });
    }

}
