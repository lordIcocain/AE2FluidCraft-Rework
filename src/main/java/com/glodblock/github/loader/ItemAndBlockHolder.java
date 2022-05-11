package com.glodblock.github.loader;

import com.glodblock.github.common.block.BlockFluidDiscretizer;
import com.glodblock.github.common.block.BlockFluidInterface;
import com.glodblock.github.common.block.BlockFluidPacketDecoder;
import com.glodblock.github.common.block.BlockFluidPatternEncoder;
import com.glodblock.github.common.item.*;
import com.glodblock.github.common.storage.CellType;

public class ItemAndBlockHolder {

    public static ItemFluidDrop DROP = new ItemFluidDrop().register();
    public static ItemFluidEncodedPattern PATTERN = new ItemFluidEncodedPattern().register();
    public static ItemPartFluidPatternTerminal FLUID_TERMINAL = new ItemPartFluidPatternTerminal().register();
    public static ItemPartFluidInterface FLUID_INTERFACE = new ItemPartFluidInterface().register();
    public static ItemFluidPacket PACKET = new ItemFluidPacket().register();
    public static ItemBasicFluidStorageCell CELL1K = new ItemBasicFluidStorageCell(CellType.Cell1kPart, 1).register();
    public static ItemBasicFluidStorageCell CELL4K = new ItemBasicFluidStorageCell(CellType.Cell4kPart, 4).register();
    public static ItemBasicFluidStorageCell CELL16K = new ItemBasicFluidStorageCell(CellType.Cell16kPart, 16).register();
    public static ItemBasicFluidStorageCell CELL64K = new ItemBasicFluidStorageCell(CellType.Cell64kPart, 64).register();
    public static ItemBasicFluidStorageCell CELL256K = new ItemBasicFluidStorageCell(CellType.Cell256kPart, 256).register();
    public static ItemBasicFluidStorageCell CELL1024K = new ItemBasicFluidStorageCell(CellType.Cell1024kPart, 1024).register();
    public static ItemBasicFluidStorageCell CELL4096K = new ItemBasicFluidStorageCell(CellType.Cell4096kPart, 4096).register();
    public static ItemBasicFluidStoragePart CELL_PART = new ItemBasicFluidStoragePart().register();

    public static BlockFluidDiscretizer DISCRETIZER = new BlockFluidDiscretizer().register();
    public static BlockFluidPatternEncoder ENCODER = new BlockFluidPatternEncoder().register();
    public static BlockFluidPacketDecoder DECODER = new BlockFluidPacketDecoder().register();
    public static BlockFluidInterface INTERFACE = new BlockFluidInterface().register();

    public static void loadSetting() {
        CellType.Cell1kPart.setItemInstance(CELL_PART);
        CellType.Cell4kPart.setItemInstance(CELL_PART);
        CellType.Cell16kPart.setItemInstance(CELL_PART);
        CellType.Cell64kPart.setItemInstance(CELL_PART);
        CellType.Cell256kPart.setItemInstance(CELL_PART);
        CellType.Cell1024kPart.setItemInstance(CELL_PART);
        CellType.Cell4096kPart.setItemInstance(CELL_PART);
    }

}
