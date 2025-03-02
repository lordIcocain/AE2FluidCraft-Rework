package com.glodblock.github.client.gui.container.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.glodblock.github.FluidCraft;
import com.glodblock.github.client.gui.container.ContainerItemMonitor;
import com.glodblock.github.common.item.ItemFluidDrop;
import com.glodblock.github.common.item.ItemFluidEncodedPattern;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.glodblock.github.inventory.item.IItemPatternTerminal;
import com.glodblock.github.loader.ItemAndBlockHolder;
import com.glodblock.github.network.SPacketUpdateAESlot;
import com.glodblock.github.util.FluidPatternDetails;
import com.glodblock.github.util.Util;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.definitions.IDefinitions;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.IContainerCraftingPacket;
import appeng.helpers.InventoryAction;
import appeng.items.misc.ItemEncodedPattern;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;

public abstract class FCContainerEncodeTerminal extends ContainerItemMonitor
        implements IAEAppEngInventory, IOptionalSlotHost, IContainerCraftingPacket {

    public static final int MULTIPLE_OF_BUTTON_CLICK = 2;
    public static final int MULTIPLE_OF_BUTTON_CLICK_ON_SHIFT = 8;
    protected final IItemPatternTerminal patternTerminal;
    protected final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);
    protected final IInventory crafting;
    protected final SlotRestrictedInput patternSlotIN;
    protected final SlotRestrictedInput patternSlotOUT;
    protected SlotFake[] craftingSlots;
    protected OptionalSlotFake[] outputSlots;
    protected SlotPatternTerm craftSlot;

    @GuiSync(97)
    public boolean craftingMode = true;

    @GuiSync(96)
    public boolean substitute = false;

    @GuiSync(95)
    public boolean combine = false;

    @GuiSync(94)
    public boolean beSubstitute = false;

    @GuiSync(93)
    public boolean inverted;

    @GuiSync(92)
    public int activePage = 0;

    @GuiSync(91)
    public boolean prioritize = false;

    @GuiSync(90)
    public boolean autoFillPattern = false;

    public FCContainerEncodeTerminal(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable);
        this.patternTerminal = (IItemPatternTerminal) monitorable;
        this.inverted = patternTerminal.isInverted();
        final IInventory patternInv = this.patternTerminal.getInventoryByName("pattern");
        this.crafting = this.patternTerminal.getInventoryByName("crafting");
        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                        patternInv,
                        0,
                        147,
                        -72 - 9,
                        this.getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        patternInv,
                        1,
                        147,
                        -72 + 34,
                        this.getInventoryPlayer()));
        this.patternSlotOUT.setStackLimit(1);
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slotId, long id) {
        if (this.isCraftingMode()) {
            super.doAction(player, action, slotId, id);
            return;
        }
        if (slotId < 0 || slotId >= this.inventorySlots.size()) {
            super.doAction(player, action, slotId, id);
            return;
        }
        if (action == InventoryAction.MOVE_REGION) {
            super.doAction(player, InventoryAction.MOVE_REGION, slotId, id);
            return;
        }
        if (action == InventoryAction.PICKUP_SINGLE) {
            super.doAction(player, InventoryAction.PICKUP_OR_SET_DOWN, slotId, id);
            return;
        }
        Slot slot = getSlot(slotId);
        ItemStack stack = player.inventory.getItemStack();
        IAEFluidStack fluid = Util.getAEFluidFromItem(stack);

        if (fluid == null || fluid.getStackSize() <= 0) {
            super.doAction(player, action, slotId, id);
            return;
        }

        if (validPatternSlot(slot)) {

            switch (action) {
                case PICKUP_OR_SET_DOWN -> {
                    slot.putStack(ItemFluidPacket.newStack(fluid));
                    return;
                }
                case SPLIT_OR_PLACE_SINGLE -> {
                    fluid = Util.getAEFluidFromItem(Util.copyStackWithSize(stack, 1));
                    IAEFluidStack origin = ItemFluidPacket.getAEFluidStack(slot.getStack());
                    if (fluid != null && fluid.equals(origin)) {
                        fluid.add(origin);
                        if (fluid.getStackSize() <= 0) fluid = null;
                    }
                    slot.putStack(ItemFluidPacket.newStack(fluid));
                    return;
                }
                default -> {}
            }

        }
        super.doAction(player, action, slotId, id);
    }

    protected abstract boolean validPatternSlot(Slot slot);

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int idx) {
        Slot clickSlot = (Slot) this.inventorySlots.get(idx);
        ItemStack is = clickSlot.getStack();
        if (is != null && !patternSlotOUT.getHasStack()
                && is.stackSize == 1
                && (is.getItem() instanceof ItemFluidEncodedPattern || is.getItem() instanceof ItemEncodedPattern)) {
            ItemStack output = is.copy();
            patternSlotOUT.putStack(output);
            p.inventory.setInventorySlotContents(clickSlot.getSlotIndex(), null);
            this.detectAndSendChanges();
            return null;
        } else {
            return super.transferStackInSlot(p, idx);
        }
    }

    public IItemPatternTerminal getPatternTerminal() {
        return this.patternTerminal;
    }

    protected static boolean containsItem(SlotFake[] slots) {
        List<SlotFake> enabledSlots = Arrays.stream(slots).filter(SlotFake::isEnabled).collect(Collectors.toList());
        long item = enabledSlots.stream().filter(s -> s.getStack() != null && !Util.isFluidPacket(s.getStack()))
                .count();
        return item > 0;
    }

    protected static boolean containsFluid(SlotFake[] slots) {
        List<SlotFake> enabledSlots = Arrays.stream(slots).filter(SlotFake::isEnabled).collect(Collectors.toList());
        long fluid = enabledSlots.stream().filter(s -> Util.isFluidPacket(s.getStack())).count();
        return fluid > 0;
    }

    protected static boolean nonNullSlot(SlotFake[] slots) {
        List<SlotFake> enabledSlots = Arrays.stream(slots).filter(SlotFake::isEnabled).collect(Collectors.toList());
        long object = enabledSlots.stream().filter(s -> s.getStack() != null).count();
        return object > 0;
    }

    protected boolean checkHasFluidPattern() {
        if (this.craftingMode) {
            return false;
        }
        boolean hasFluid = containsFluid(this.craftingSlots);
        boolean search = nonNullSlot(this.craftingSlots);
        if (!search) { // search=false -> inputs were empty
            return false;
        }
        hasFluid |= containsFluid(this.outputSlots);
        search = nonNullSlot(this.outputSlots);
        return hasFluid && search; // search=false -> outputs were empty
    }

    protected ItemStack stampAuthor(ItemStack patternStack) {
        if (patternStack.stackTagCompound == null) {
            patternStack.stackTagCompound = new NBTTagCompound();
        }
        patternStack.stackTagCompound.setString("author", getPlayerInv().player.getCommandSenderName());
        return patternStack;
    }

    protected void encodeFluidPattern() {
        ItemStack patternStack = new ItemStack(ItemAndBlockHolder.PATTERN);
        FluidPatternDetails pattern = new FluidPatternDetails(patternStack);
        pattern.setInputs(collectInventory(this.craftingSlots));
        pattern.setOutputs(collectInventory(this.outputSlots));
        pattern.setCanBeSubstitute(this.beSubstitute ? 1 : 0);
        patternSlotOUT.putStack(stampAuthor(pattern.writeToStack()));
    }

    protected static IAEItemStack[] collectInventory(Slot[] slots) {
        IAEItemStack[] stacks = new IAEItemStack[slots.length];
        for (int i = 0; i < stacks.length; i++) {
            IAEItemStack stack = ((SlotFake) slots[i]).getAEStack();
            if (stack != null) {
                if (stack.getItem() instanceof ItemFluidPacket) {
                    IAEItemStack dropStack = ItemFluidDrop.newAeStack(ItemFluidPacket.getAEFluidStack(stack));
                    if (dropStack != null) {
                        stacks[i] = dropStack;
                        continue;
                    }
                }
            }
            stacks[i] = stack;
        }
        return stacks;
    }

    @Override
    public void saveChanges() {
        // NO-OP
    }

    public void clear() {
        for (final Slot s : this.craftingSlots) {
            s.putStack(null);
        }
        for (final Slot s : this.outputSlots) {
            s.putStack(null);
        }
        this.detectAndSendChanges();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        // NO-OP
    }

    public void encodeAndMoveToInventory() {
        encode();
        ItemStack output = this.patternSlotOUT.getStack();
        if (output != null) {
            if (!getPlayerInv().addItemStackToInventory(output)) {
                getPlayerInv().player.entityDropItem(output, 0);
            }
            this.patternSlotOUT.putStack(null);
        }
        fillPattern();
    }

    public void encodeAllItemAndMoveToInventory() {
        encode();
        ItemStack output = this.patternSlotOUT.getStack();
        if (output != null) {
            if (this.patternSlotIN.getStack() != null) output.stackSize += this.patternSlotIN.getStack().stackSize;
            if (!getPlayerInv().addItemStackToInventory(output)) {
                getPlayerInv().player.entityDropItem(output, 0);
            }
            this.patternSlotOUT.putStack(null);
            this.patternSlotIN.putStack(null);
        }
        fillPattern();
    }

    private void fillPattern() {
        if (this.autoFillPattern && this.getHost().getItemInventory() != null) {
            // try to use me network item to fill pattern input slot
            final IDefinitions definitions = AEApi.instance().definitions();
            int fillStackSize = this.patternSlotIN.getHasStack() ? 64 - this.patternSlotIN.getStack().stackSize : 64;
            if (fillStackSize == 0) return;
            for (ItemStack blankPattern : definitions.materials().blankPattern().maybeStack(fillStackSize).asSet()) {
                IAEItemStack iBlankPattern = AEApi.instance().storage().createItemStack(blankPattern);
                if (this.patternSlotIN.getHasStack() && !iBlankPattern.isSameType(this.patternSlotIN.getStack()))
                    continue;
                IAEItemStack out = this.getHost().getItemInventory()
                        .extractItems(iBlankPattern, Actionable.MODULATE, this.getActionSource());
                if (out != null) {
                    ItemStack outPattern;
                    if (this.patternSlotIN.getHasStack()) {
                        outPattern = this.patternSlotIN.getStack().copy();
                        outPattern.stackSize += out.getItemStack().stackSize;
                    } else {
                        outPattern = out.getItemStack();
                    }
                    this.patternSlotIN.putStack(outPattern);
                    return;
                }
            }
        }
    }

    public void encode() {
        fillPattern();
        if (!checkHasFluidPattern()) {
            encodeItemPattern();
            return;
        }
        ItemStack stack = this.patternSlotOUT.getStack();
        if (stack == null) {
            stack = this.patternSlotIN.getStack();
            if (notPattern(stack)) {
                return;
            }
            if (stack.stackSize == 1) {
                this.patternSlotIN.putStack(null);
            } else {
                stack.stackSize--;
            }
            encodeFluidPattern();
        } else if (!notPattern(stack)) {
            encodeFluidPattern();
        }
    }

    public void encodeItemPattern() {
        ItemStack output = this.patternSlotOUT.getStack();
        final IAEItemStack[] in = this.getInputs();
        final IAEItemStack[] out = this.getOutputs();

        // if there is no input, this would be silly.
        if (in == null || out == null) {
            return;
        }
        // first check the output slots, should either be null, or a pattern
        if (output != null && this.notPattern(output)) {
            return;
        } // if nothing is there we should snag a new pattern.
        else if (output == null) {
            output = this.patternSlotIN.getStack();
            if (this.notPattern(output)) {
                return; // no blanks.
            }

            // remove one, and clear the input slot.
            output.stackSize--;
            if (output.stackSize == 0) {
                this.patternSlotIN.putStack(null);
            }

            // add a new encoded pattern.
            for (final ItemStack encodedPatternStack : AEApi.instance().definitions().items().encodedPattern()
                    .maybeStack(1).asSet()) {
                output = encodedPatternStack;
            }
        } else if (output.getItem() instanceof ItemFluidEncodedPattern) {
            for (final ItemStack encodedPatternStack : AEApi.instance().definitions().items().encodedPattern()
                    .maybeStack(1).asSet()) {
                output = encodedPatternStack;
            }
        }

        // encode the slot.
        final NBTTagCompound encodedValue = new NBTTagCompound();

        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final IAEItemStack i : in) {
            tagIn.appendTag(this.createItemTag(i));
        }

        for (final IAEItemStack i : out) {
            tagOut.appendTag(this.createItemTag(i));
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.craftingMode);
        encodedValue.setBoolean("substitute", this.substitute);
        encodedValue.setBoolean("beSubstitute", this.beSubstitute);
        encodedValue.setBoolean("prioritize", this.prioritize);
        output.setTagCompound(encodedValue);
        stampAuthor(output);
        this.patternSlotOUT.putStack(output);
    }

    protected IAEItemStack[] getInputs() {
        final ArrayList<IAEItemStack> input = new ArrayList<>();
        for (SlotFake craftingSlot : this.craftingSlots) {
            input.add(craftingSlot.getAEStack());
        }
        if (input.stream().anyMatch(Objects::nonNull)) {
            return input.toArray(new IAEItemStack[0]);
        }
        return null;
    }

    protected IAEItemStack[] getOutputs() {
        final ArrayList<IAEItemStack> output = new ArrayList<>();
        for (final SlotFake outputSlot : this.outputSlots) {
            output.add(outputSlot.getAEStack());
        }
        if (output.stream().anyMatch(Objects::nonNull)) {
            return output.toArray(new IAEItemStack[0]);
        }
        return null;
    }

    protected boolean notPattern(final ItemStack output) {
        if (output == null) {
            return true;
        }
        if (output.getItem() instanceof ItemFluidEncodedPattern) {
            return false;
        }
        final IDefinitions definitions = AEApi.instance().definitions();

        boolean isPattern = definitions.items().encodedPattern().isSameAs(output);
        isPattern |= definitions.materials().blankPattern().isSameAs(output);

        return !isPattern;
    }

    protected NBTBase createItemTag(final IAEItemStack i) {
        final NBTTagCompound c = new NBTTagCompound();
        if (i != null) {
            i.writeToNBT(c);
        }
        return c;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (Platform.isServer()) {
            this.substitute = this.patternTerminal.isSubstitution();
            this.combine = this.patternTerminal.shouldCombine();
            this.beSubstitute = this.patternTerminal.canBeSubstitute();
            this.prioritize = this.patternTerminal.isPrioritize();
            this.autoFillPattern = this.patternTerminal.isAutoFillPattern();
        }
    }

    @Override
    public void addCraftingToCrafters(ICrafting c) {
        super.addCraftingToCrafters(c);
        updateSlots();
    }

    @Override
    public void onSlotChange(final Slot s) {
        if (Platform.isServer()) {
            if (s == this.patternSlotOUT) {
                for (final Object crafter : this.crafters) {
                    final ICrafting icrafting = (ICrafting) crafter;

                    for (final Object g : this.inventorySlots) {
                        if (g instanceof OptionalSlotFake || g instanceof SlotFakeCraftingMatrix) {
                            final Slot sri = (Slot) g;
                            icrafting.sendSlotContents(this, sri.slotNumber, sri.getStack());
                        }
                    }
                    onCraftMatrixChanged();
                    ((EntityPlayerMP) icrafting).isChangingQuantityOnly = false;
                }
                this.detectAndSendChanges();
                if (s.getHasStack()) updateSlotsOnPatternInject();
            } else if (s instanceof SlotFake sf) {
                for (final Object crafter : this.crafters) {
                    final EntityPlayerMP emp = (EntityPlayerMP) crafter;
                    if (sf.getHasStack()) {
                        FluidCraft.proxy.netHandler
                                .sendTo(new SPacketUpdateAESlot(sf.slotNumber, sf.getAEStack()), emp);
                    }
                }
            }
        }
    }

    public void updateSlotsOnPatternInject() {
        for (final Object crafter : this.crafters) {
            final EntityPlayerMP emp = (EntityPlayerMP) crafter;
            for (final SlotFake sf : this.craftingSlots) {
                if (sf.getHasStack()) {
                    AppEngInternalAEInventory inv = (AppEngInternalAEInventory) this.patternTerminal
                            .getInventoryByName("crafting");
                    sf.putAEStack(inv.getAEStackInSlot(sf.getSlotIndex()));
                    FluidCraft.proxy.netHandler.sendTo(new SPacketUpdateAESlot(sf.slotNumber, sf.getAEStack()), emp);
                }
            }

            for (final OptionalSlotFake osf : this.outputSlots) {
                if (osf.getHasStack()) {
                    AppEngInternalAEInventory inv = (AppEngInternalAEInventory) this.patternTerminal
                            .getInventoryByName("output");
                    osf.putAEStack(inv.getAEStackInSlot(osf.getSlotIndex()));
                    FluidCraft.proxy.netHandler.sendTo(new SPacketUpdateAESlot(osf.slotNumber, osf.getAEStack()), emp);
                }
            }
        }
    }

    public void updateSlots() {
        for (final Object crafter : this.crafters) {
            final EntityPlayerMP emp = (EntityPlayerMP) crafter;

            for (final SlotFake sf : this.craftingSlots) {
                if (sf.getHasStack()) {
                    FluidCraft.proxy.netHandler.sendTo(new SPacketUpdateAESlot(sf.slotNumber, sf.getAEStack()), emp);
                }
            }

            for (final OptionalSlotFake osf : this.outputSlots) {
                if (osf.getHasStack()) {
                    FluidCraft.proxy.netHandler.sendTo(new SPacketUpdateAESlot(osf.slotNumber, osf.getAEStack()), emp);
                }
            }
        }
    }

    @Override
    public void onCraftMatrixChanged(IInventory p_75130_1_) {
        super.onCraftMatrixChanged(p_75130_1_);
        if (Platform.isServer()) {
            p_75130_1_.markDirty();
        }
    }

    public void onCraftMatrixChanged() {
        onCraftMatrixChanged(this.patternTerminal.getInventoryByName("crafting"));
        onCraftMatrixChanged(this.patternTerminal.getInventoryByName("output"));
    }

    public void setPatternValue(int index, long amount) {
        SlotFake sf = (SlotFake) this.inventorySlots.get(index);
        ItemStack stack = sf.getStack();

        if (Util.isFluidPacket(stack)) {
            ItemFluidPacket.setFluidAmount(stack, amount);
            sf.putStack(stack);
        } else {
            sf.getAEStack().setStackSize(amount);
            this.inventoryItemStacks.set(index, stack);
        }

        onCraftMatrixChanged(sf.inventory);
        onSlotChange(sf);
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("player")) {
            return this.getInventoryPlayer();
        }
        return this.patternTerminal.getInventoryByName(name);
    }

    @Override
    public boolean useRealItems() {
        return false;
    }

    public void doubleStacks(int val) {
        multiplyOrDivideStacks(
                ((val & 1) != 0 ? MULTIPLE_OF_BUTTON_CLICK_ON_SHIFT : MULTIPLE_OF_BUTTON_CLICK)
                        * ((val & 2) != 0 ? -1 : 1));
    }

    static boolean canMultiplyOrDivide(SlotFake[] slots, int mult) {
        if (mult > 0) {
            for (SlotFake s : slots) {
                ItemStack st = s.getStack();
                if (st == null) continue;
                final long count;
                if (st.getItem() instanceof ItemFluidPacket) {
                    count = ItemFluidPacket.getFluidAmount(st);
                } else {
                    count = s.getAEStack().getStackSize();
                }
                double result = (double) count * mult;
                if (result > Long.MAX_VALUE) {
                    return false;
                }
            }
            return true;
        } else if (mult < 0) {
            mult = Math.abs(mult);
            for (SlotFake s : slots) {
                ItemStack st = s.getStack();
                if (st == null) continue;
                final long count;
                if (st.getItem() instanceof ItemFluidPacket) {
                    count = ItemFluidPacket.getFluidAmount(st);
                } else {
                    count = s.getAEStack().getStackSize();
                }
                if (count % mult != 0) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    static void multiplyOrDivideStacksInternal(SlotFake[] slots, int mult) {
        List<SlotFake> enabledSlots = Arrays.stream(slots).filter(SlotFake::isEnabled).collect(Collectors.toList());
        if (mult > 0) {
            for (final SlotFake s : enabledSlots) {
                ItemStack st = s.getStack();
                if (st != null) {
                    if (st.getItem() instanceof ItemFluidPacket) {
                        ItemFluidPacket.setFluidAmount(st, ItemFluidPacket.getFluidAmount(st) * mult);
                        s.putStack(st);
                    } else {
                        s.getAEStack().setStackSize(s.getAEStack().getStackSize() * mult);
                    }
                }
            }
        } else if (mult < 0) {
            mult = Math.abs(mult);
            for (final SlotFake s : enabledSlots) {
                ItemStack st = s.getStack();
                if (st != null) {
                    if (st.getItem() instanceof ItemFluidPacket) {
                        ItemFluidPacket.setFluidAmount(st, ItemFluidPacket.getFluidAmount(st) / mult);
                        s.putStack(st);
                    } else {
                        s.getAEStack().setStackSize(s.getAEStack().getStackSize() / mult);
                    }
                }
            }
        }
    }

    /**
     * Multiply or divide a number
     *
     * @param multi Positive numbers are multiplied and negative numbers are divided
     */
    public void multiplyOrDivideStacks(int multi) {
        if (!isCraftingMode()) {
            if (canMultiplyOrDivide(this.craftingSlots, multi) && canMultiplyOrDivide(this.outputSlots, multi)) {
                multiplyOrDivideStacksInternal(this.craftingSlots, multi);
                multiplyOrDivideStacksInternal(this.outputSlots, multi);
                this.updateSlots();
            }
            onCraftMatrixChanged();
            this.detectAndSendChanges();
        }
    }

    public boolean isCraftingMode() {
        return this.craftingMode;
    }
}
