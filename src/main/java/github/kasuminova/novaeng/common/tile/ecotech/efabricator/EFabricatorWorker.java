package github.kasuminova.novaeng.common.tile.ecotech.efabricator;

import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;
import github.kasuminova.novaeng.NovaEngineeringCore;
import github.kasuminova.novaeng.common.block.ecotech.efabricator.prop.WorkerStatus;
import github.kasuminova.novaeng.common.network.PktEFabricatorWorkerStatusUpdate;
import hellfirepvp.modularmachinery.common.util.ItemUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import java.util.*;

public class EFabricatorWorker extends EFabricatorPart {

    public static final int MAX_ENERGY_CACHE = 500_000;
    public static final int MAX_QUEUE_DEPTH = 32;

    public static final int ENERGY_USAGE = 100;
    public static final int COOLANT_USAGE = 5;

    protected final CraftingQueue queue = new CraftingQueue();

    protected WorkerStatus status = WorkerStatus.OFF;
    protected int queueDepth = MAX_QUEUE_DEPTH;
    protected int energyCache = 0;

    protected long lastUpdateTick = 0L;

    public EFabricatorWorker() {
    }

    public int doWork() {
        EFabricatorController controller = partController;
        int coolantCache = controller.getCoolantCache();
        int energyUsage;
        if (controller.isOverclocked() && !controller.isActiveCooling()) {
            energyUsage = controller.getLevel().applyOverclockEnergyUsage(ENERGY_USAGE);
        } else {
            energyUsage = ENERGY_USAGE;
        }
        int parallelism = Math.min(Math.max(controller.getAvailableParallelism(), 1), energyCache / energyUsage);
        if (controller.isActiveCooling()) {
            parallelism = Math.min(parallelism, coolantCache / COOLANT_USAGE);
        }

        int completed = 0;
        ItemList outputBuffer = controller.getOutputBuffer();
        CraftWork craftWork;
        synchronized (outputBuffer) {
            while ((parallelism > completed) && (craftWork = queue.poll()) != null) {
                for (ItemStack remain : craftWork.getRemaining()) {
                    if (!remain.isEmpty()) {
                        outputBuffer.add(AEItemStack.fromItemStack(remain));
                    }
                }
                outputBuffer.add(AEItemStack.fromItemStack(craftWork.getOutput()));
                completed++;
            }
        }

        if (completed > 0) {
            energyCache -= energyUsage * completed;
            if (controller.isActiveCooling()) {
                controller.consumeCoolant(COOLANT_USAGE * completed);
            }
        }
        return completed;
    }

    public void supplyEnergy(int energy) {
        energyCache += energy;
    }

    public int getEnergyCache() {
        return energyCache;
    }

    public int getMaxEnergyCache() {
        return MAX_ENERGY_CACHE;
    }

    public int getQueueDepth() {
        EFabricatorController controller = getController();
        if (controller != null && controller.isOverclocked()) {
            return controller.getLevel().applyOverclockQueueDepth(queueDepth);
        }
        return queueDepth;
    }

    public void offerWork(final CraftWork craftWork) {
        queue.add(craftWork);
    }

    public boolean hasWork() {
        return !queue.isEmpty();
    }

    public boolean isFull() {
        return queue.size() >= getQueueDepth();
    }

    public CraftingQueue getQueue() {
        return queue;
    }

    @Override
    public void onAssembled() {
        updateStatus(true);
        super.onAssembled();
    }

    @Override
    public void onDisassembled() {
        updateStatus(true);
        super.onDisassembled();
    }

    public void updateStatus(final boolean force) {
        long prevUpdateTick = lastUpdateTick;
        long updateTick = getWorld().getTotalWorldTime();

        if (!force && status == WorkerStatus.RUN && prevUpdateTick + 20 >= updateTick) {
            if (hasWork()) {
                lastUpdateTick = updateTick;
            }
            return;
        }

        if (getController() == null) {
            if (status != WorkerStatus.OFF) {
                setStatus(WorkerStatus.OFF);
            }
        } else if (hasWork()) {
            if (status != WorkerStatus.RUN) {
                setStatus(WorkerStatus.RUN);
            }
        } else {
            if (status != WorkerStatus.ON) {
                setStatus(WorkerStatus.ON);
            }
        }
        lastUpdateTick = updateTick;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public void setStatus(final WorkerStatus status) {
        this.status = status;
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            markNoUpdateSync();
        }
    }

    @Override
    public void markNoUpdate() {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            NovaEngineeringCore.NET_CHANNEL.sendToAllTracking(
                    new PktEFabricatorWorkerStatusUpdate(getPos(), status),
                    new NetworkRegistry.TargetPoint(
                            world.provider.getDimension(),
                            pos.getX(), pos.getY(), pos.getZ(),
                            -1)
            );
        }
        super.markNoUpdate();
    }

    @Override
    public void readCustomNBT(final NBTTagCompound compound) {
        queue.readFromNBT(compound);
        energyCache = compound.getInteger("energyCache");
        status = WorkerStatus.values()[compound.getByte("status")];
        super.readCustomNBT(compound);
    }

    @Override
    public void writeCustomNBT(final NBTTagCompound compound) {
        queue.writeToNBT(compound);
        compound.setInteger("energyCache", energyCache);
        compound.setByte("status", (byte) status.ordinal());
        super.writeCustomNBT(compound);
    }

    public static class CraftingQueue {

        private static final String QUEUE_TAG = "Q";
        private static final String STACK_SET_TAG = "SS";
        private static final String STACK_SET_TAG_ID_PREFIX = "S#";
        private static final String STACK_SET_SIZE_TAG = "SSS";
        private static final String REPEAT_TAG = "R";

        private final Deque<EFabricatorWorker.CraftWork> queue = new ArrayDeque<>();

        public int size() {
            return queue.size();
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public void add(final EFabricatorWorker.CraftWork craftWork) {
            queue.add(craftWork);
        }

        public EFabricatorWorker.CraftWork poll() {
            return queue.poll();
        }

        public EFabricatorWorker.CraftWork peek() {
            return queue.peek();
        }

        public Deque<CraftWork> getQueue() {
            return queue;
        }

        public NBTTagCompound writeToNBT(final NBTTagCompound nbt) {
            if (queue.isEmpty()) {
                return nbt;
            }

            List<ItemStack> stackSet = new ArrayList<>();

            // Queue
            NBTTagList queueTag = new NBTTagList();
            CraftWork prev = null;
            int repeat = 0;
            for (CraftWork craftWork : queue) {
                if (prev != null && prev.equals(craftWork)) {
                    repeat++;
                    continue;
                }
                if (repeat > 0) {
                    queueTag.getCompoundTagAt(queueTag.tagCount() - 1).setShort(REPEAT_TAG, (short) repeat);
                    repeat = 0;
                }
                queueTag.appendTag(craftWork.writeToNBT(stackSet));
                prev = craftWork;
            }
            if (repeat > 0) {
                queueTag.getCompoundTagAt(queueTag.tagCount() - 1).setShort(REPEAT_TAG, (short) repeat);
            }
            nbt.setTag(QUEUE_TAG, queueTag);

            // StackSet
            NBTTagCompound stackSetTag = new NBTTagCompound();
            for (int i = 0; i < stackSet.size(); i++) {
                final ItemStack stack = stackSet.get(i);
                if (!stack.isEmpty()) {
                    stackSetTag.setTag(STACK_SET_TAG_ID_PREFIX + i, stack.serializeNBT());
                }
            }
            nbt.setTag(STACK_SET_TAG, stackSetTag);
            nbt.setInteger(STACK_SET_SIZE_TAG, stackSet.size());

            return nbt;
        }

        public void readFromNBT(final NBTTagCompound nbt) {
            queue.clear();
            List<ItemStack> stackSet = new ArrayList<>();

            // StackSet
            NBTTagCompound stackSetTag = nbt.getCompoundTag(STACK_SET_TAG);
            for (int i = 0; i < nbt.getInteger(STACK_SET_SIZE_TAG); i++) {
                stackSet.add(new ItemStack(stackSetTag.getCompoundTag(STACK_SET_TAG_ID_PREFIX + i)));
            }

            // Queue
            NBTTagList queueTag = nbt.getTagList(QUEUE_TAG, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < queueTag.tagCount(); i++) {
                NBTTagCompound tagAt = queueTag.getCompoundTagAt(i);
                CraftWork work = new CraftWork(tagAt, stackSet);
                queue.add(work);
                short repeat = tagAt.getShort(REPEAT_TAG);
                for (short r = 0; r < repeat; r++) {
                    queue.add(work.copy());
                }
            }
        }

    }

    public static class CraftWork {

        private static final String REMAIN_TAG_PREFIX = "R#";
        private static final String REMAIN_SIZE_TAG = REMAIN_TAG_PREFIX + "S";
        private static final String OUTPUT_TAG = "O";

        private final ItemStack[] remaining;
        private final ItemStack output;

        public CraftWork(final ItemStack[] remaining, final ItemStack output) {
            this.remaining = remaining;
            this.output = output;
        }

        public CraftWork(final NBTTagCompound nbt, final List<ItemStack> stackSet) {
            remaining = new ItemStack[nbt.getByte(REMAIN_SIZE_TAG)];
            for (int remainIdx = 0; remainIdx < remaining.length; remainIdx++) {
                int setIdx = nbt.hasKey(REMAIN_TAG_PREFIX + remainIdx) ? nbt.getInteger(REMAIN_TAG_PREFIX + remainIdx) : -1;
                remaining[remainIdx] = setIdx == -1 ? ItemStack.EMPTY : stackSet.get(setIdx);
            }
            output = stackSet.get(nbt.getInteger(OUTPUT_TAG));
        }

        public NBTTagCompound writeToNBT(final List<ItemStack> stackSet) {
            NBTTagCompound nbt = new NBTTagCompound();

            // Input.
            nbt.setByte(REMAIN_SIZE_TAG, (byte) remaining.length);
            remain:
            for (int remainIdx = 0; remainIdx < remaining.length; remainIdx++) {
                final ItemStack remain = remaining[remainIdx];
                if (remain.isEmpty()) {
                    continue;
                }

                for (int setIdx = 0; setIdx < stackSet.size(); setIdx++) {
                    if (matchStacksStrict(remain, stackSet.get(setIdx))) {
                        nbt.setShort(REMAIN_TAG_PREFIX + remainIdx, (short) setIdx);
                        continue remain;
                    }
                }

                stackSet.add(remain);
                nbt.setShort(REMAIN_TAG_PREFIX + remainIdx, (short) (stackSet.size() - 1));
            }

            // Output
            for (int setIdx = 0; setIdx < stackSet.size(); setIdx++) {
                if (matchStacksStrict(output, stackSet.get(setIdx))) {
                    nbt.setShort(OUTPUT_TAG, (short) setIdx);
                    return nbt;
                }
            }

            stackSet.add(output);
            nbt.setShort(OUTPUT_TAG, (short) (stackSet.size() - 1));
            return nbt;
        }

        public CraftWork copy() {
            ItemStack[] remaining = Arrays.stream(this.remaining).map(ItemStack::copy).toArray(ItemStack[]::new);
            return new CraftWork(remaining, output.copy());
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof final CraftWork craftWork) {
                for (int i = 0; i < remaining.length; i++) {
                    if (!matchStacksStrict(remaining[i], craftWork.remaining[i])) {
                        return false;
                    }
                }
                return matchStacksStrict(output, craftWork.output);
            }
            return false;
        }

        private static boolean matchStacksStrict(final ItemStack stack1, final ItemStack stack2) {
            return ItemUtils.matchStacks(stack1, stack2) && stack1.getCount() == stack2.getCount();
        }

        public ItemStack[] getRemaining() {
            return remaining;
        }

        public ItemStack getOutput() {
            return output;
        }

    }
}
