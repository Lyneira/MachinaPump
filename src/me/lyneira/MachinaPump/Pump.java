package me.lyneira.MachinaPump;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.lyneira.MachinaCraft.BlockLocation;
import me.lyneira.MachinaCraft.BlockRotation;
import me.lyneira.MachinaCraft.BlockVector;
import me.lyneira.MachinaCraft.EventSimulator;
import me.lyneira.MachinaCraft.Fuel;
import me.lyneira.MachinaCraft.HeartBeatEvent;
import me.lyneira.MachinaCraft.Machina;

/**
 * A machina that drains or fills an area with water.
 * 
 * @author Lyneira
 */
final class Pump implements Machina {
    private static final int maxLength = 9;
    private static final int maxDepth = 8;
    private static final int delay = 10;
    private static final BlockVector down = new BlockVector(0, -1, 0);

    private final Player player;
    private final BlockLocation anchor;
    private final BlockFace leverFace;
    private final BlockFace cauldronFace;
    private final BlockVector forward;
    private final BlockFace backward;
    private final BlockVector left;
    private final BlockVector right;
    private final Material tubeMaterial;
    private final Material liquidMaterial;
    private final Material stationaryLiquidMaterial;
    private final Material filledBucketMaterial;

    private final List<BlockLocation> tube = new ArrayList<BlockLocation>(maxLength);

    Pump(BlockRotation yaw, Player player, BlockLocation anchor, BlockFace leverFace, BlockFace cauldronFace, boolean lavaMode) {
        this.player = player;
        this.anchor = anchor;
        this.leverFace = leverFace;
        this.cauldronFace = cauldronFace;
        forward = new BlockVector(yaw.getYawFace());
        left = new BlockVector(yaw.getLeft().getYawFace());
        backward = yaw.getOpposite().getYawFace();
        right = new BlockVector(yaw.getRight().getYawFace());
        if (lavaMode) {
            tubeMaterial = Material.IRON_BLOCK;
            liquidMaterial = Material.LAVA;
            stationaryLiquidMaterial = Material.STATIONARY_LAVA;
            filledBucketMaterial = Material.LAVA_BUCKET;
        } else {
            tubeMaterial = Material.WOOD;
            liquidMaterial = Material.WATER;
            stationaryLiquidMaterial = Material.STATIONARY_WATER;
            filledBucketMaterial = Material.WATER_BUCKET;
        }

        setFurnace(anchor, true);
    }

    @Override
    public boolean verify(BlockLocation anchor) {
        if (!(anchor.checkType(Blueprint.anchorMaterial) && anchor.getRelative(leverFace).checkType(Material.LEVER) && anchor.getRelative(backward).checkType(Material.BURNING_FURNACE) && anchor
                .getRelative(cauldronFace).checkType(Material.CAULDRON)))
            return false;
        for (BlockLocation i : tube) {
            if (!i.checkType(tubeMaterial))
                return false;
        }
        return true;
    }

    @Override
    public HeartBeatEvent heartBeat(BlockLocation anchor) {
        stage = stage.run();
        if (stage == null)
            return null;

        return new HeartBeatEvent(delay, anchor);
    }

    @Override
    public boolean onLever(BlockLocation anchor, Player player, ItemStack itemInHand) {
        if ((this.player == player && player.hasPermission("machinapump.deactivate-own")) || player.hasPermission("machinapump.deactivate-all")) {
            if (!(stage instanceof Retract)) {
                stage = new Retract();
            }
        }
        return true;
    }

    @Override
    public void onDeActivate(BlockLocation anchor) {
        setCauldron((byte) 0);
        setFurnace(anchor, false);
    }

    /**
     * Sets the furnace to the given state and set correct direction.
     * 
     * @param anchor
     *            The anchor
     * @param burning
     *            Whether the furnace should be burning.
     */
    void setFurnace(BlockLocation anchor, boolean burning) {
        Block furnace = anchor.getRelative(backward).getBlock();
        Fuel.setFurnace(furnace, backward, burning);
    }

    /**
     * Sets the state of the cauldron.
     * 
     * @param data
     */
    void setCauldron(byte data) {
        Block cauldron = anchor.getRelative(cauldronFace).getBlock();
        if (cauldron.getType() == Material.CAULDRON) {
            cauldron.setData(data);
        }
    }

    /**
     * Sets the state of the cauldron.
     * 
     * @param progress
     * @param total
     */
    void setCauldron(int progress, int total) {
        int divisor = total / 4;
        if (divisor == 0)
            divisor = 1;

        anchor.getRelative(cauldronFace).setData((byte) (progress / divisor));
    }

    /**
     * Adds a drain block to the furnace's smelt slot for the deconstruction of
     * a drain.
     * 
     * @return True if a drain block item could be added to the furnace smelt
     *         slot.
     */
    boolean putDrainItem() {
        Inventory inventory = ((Furnace) anchor.getRelative(backward).getBlock().getState()).getInventory();
        ItemStack item = inventory.getItem(Fuel.smeltSlot);
        Material type = item.getType();
        if (type == Material.AIR) {
            item.setType(tubeMaterial);
            inventory.setItem(Fuel.smeltSlot, item);
            return true;
        } else if (type == tubeMaterial) {
            int amount = item.getAmount();
            if (amount < tubeMaterial.getMaxStackSize()) {
                item.setAmount(amount + 1);
                inventory.setItem(Fuel.smeltSlot, item);
                return true;
            }
        }
        return false;
    }

    /**
     * Represents a stage in the operation of the Pump
     */
    private interface Stage {
        Stage run();
    }

    private Stage stage = new Expand();

    /**
     * In this stage, the Pump builds a tube forward.
     */
    private class Expand implements Stage {
        public Stage run() {
            int size = tube.size();
            if (size == maxLength)
                return stop();

            BlockLocation target = anchor.getRelative(forward, size + 1);

            if (!target.isEmpty())
                return stop();

            // Try to take a drain block from the furnace.
            Inventory inventory = ((Furnace) anchor.getRelative(backward).getBlock().getState()).getInventory();
            ItemStack item = inventory.getItem(Fuel.smeltSlot);
            if (item.getType() == tubeMaterial) {
                // Before taking, we have to simulate whether we can actually
                // place the block.
                if (!EventSimulator.blockPlace(target, tubeMaterial.getId(), target.getRelative(backward, size), player))
                    return stop();

                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                    inventory.setItem(Fuel.smeltSlot, item);
                } else {
                    inventory.clear(Fuel.smeltSlot);
                }
                target.setType(tubeMaterial);
                tube.add(target);
                return this;
            }
            return stop();
        }

        private Stage stop() {
            if (tube.size() == 0)
                return null;
            // Check which mode the pump should operate in.
            Inventory inventory = ((Furnace) anchor.getRelative(backward).getBlock().getState()).getInventory();
            ItemStack item = inventory.getItem(Fuel.fuelSlot);
            if (item.getType() == filledBucketMaterial) {
                if (filledBucketMaterial == Material.WATER_BUCKET && anchor.getWorld().getEnvironment() == World.Environment.NETHER && !player.hasPermission("machinapump.nether-water")) {
                    player.sendMessage("You do not have permission to pour water with a pump in the nether.");
                    return new Retract();
                } else if (filledBucketMaterial == Material.LAVA_BUCKET && !player.hasPermission("machinapump.lava.fill")) {
                    player.sendMessage("You do not have permission to pour lava with a pump.");
                    return new Retract();
                }
                return new Fill();
            }
            if (liquidMaterial == Material.LAVA && !player.hasPermission("machinapump.lava.drain")) {
                player.sendMessage("You do not have permission to drain lava with a pump.");
                return new Retract();
            }
            return new Drain();
        }
    }

    /**
     * Generalized middle stage of the Pump, which is to drain or fill and show progress.
     */
    private abstract class Process implements Stage {
        final int width;
        final int maxTargets;
        int progress = 0;
        int total = 0;
        List<BlockLocation> targets;

        Process() {
            int length = tube.size();
            width = length / 2;
            maxTargets = (width * 2) * length;
            initialize();
        }

        public Stage run() {
            if (targets.size() == 0)
                return new Retract();

            progress++;

            if (progress >= total) {
                total = 0;
                progress = 0;
                for (BlockLocation target : targets) {
                    apply(target);
                }

                targets = scan();
            }

            setCauldron(progress, total);
            return this;
        }

        /**
         * Function called during Process constructor. Executed before the
         * subclass constructor, so member variables must be set during this
         * function rather than inline or in the subclass constructor.
         */
        abstract void initialize();

        /**
         * Applies the process operation on a target.
         * 
         * @param target
         */
        abstract void apply(BlockLocation target);

        /**
         * Scans for and returns a list of targets to be processed when progress
         * reaches total.
         * 
         * @return A list of target {@link BlockLocation}s.
         */
        abstract List<BlockLocation> scan();
    }

    /**
     * Drains visible water from a (roughly) square area below the tube. 
     */
    private class Drain extends Process {
        private int depth;

        void initialize() {
            targets = new ArrayList<BlockLocation>(maxTargets);
            depth = 0;
            for (BlockLocation t : tube) {
                addTarget(targets, t.getRelative(down));
                for (int i = 1; i <= width; i++) {
                    addTarget(targets, t.getRelative(down.add(left, i)));
                    addTarget(targets, t.getRelative(down.add(right, i)));
                }
            }
        }

        void apply(BlockLocation target) {
            if (target.checkTypes(stationaryLiquidMaterial, liquidMaterial) && EventSimulator.blockBreak(target, player)) {
                target.setEmpty();
            }
        }

        void addTarget(List<BlockLocation> targetArray, BlockLocation target) {
            if (target.checkTypes(stationaryLiquidMaterial, liquidMaterial)) {
                targetArray.add(target);
                total++;
            } else if (target.isEmpty()) {
                targetArray.add(target);
            }
        }

        List<BlockLocation> scan() {
            depth++;
            if (depth >= maxDepth) {
                targets.clear();
                return targets;
            }
            List<BlockLocation> newTargets = new ArrayList<BlockLocation>(targets.size());
            for (BlockLocation i : targets) {
                addTarget(newTargets, i.getRelative(down));
            }
            return newTargets;
        }
    }

    /**
     * Fills a (roughly) square area below the tube with water. 
     */
    private class Fill extends Process {
        private List<BlockLocation> topLevel;
        private int depthLimit;

        void initialize() {
            topLevel = new ArrayList<BlockLocation>(maxTargets);
            depthLimit = maxDepth;
            for (BlockLocation t : tube) {
                addTopLevel(t.getRelative(down));
                for (int i = 1; i <= width; i++) {
                    addTopLevel(t.getRelative(down.add(left, i)));
                    addTopLevel(t.getRelative(down.add(right, i)));
                }
            }
            targets = scan();
        }

        void apply(BlockLocation target) {
            if ((target.checkTypes(Material.AIR, liquidMaterial, stationaryLiquidMaterial)) && EventSimulator.blockPlace(target, stationaryLiquidMaterial.getId(), target.getRelative(down), player)) {
                target.getBlock().setTypeIdAndData(stationaryLiquidMaterial.getId(), (byte) 0, true);
            }
        }

        void addTopLevel(BlockLocation target) {
            if (target.checkTypes(Material.AIR, liquidMaterial, stationaryLiquidMaterial)) {
                topLevel.add(target);
            }
        }

        List<BlockLocation> scan() {
            int depth = 0;
            int topLevelSize = topLevel.size();
            List<BlockLocation> visible = new ArrayList<BlockLocation>(topLevelSize);
            visible.addAll(topLevel);
            List<BlockLocation> newTargets = new ArrayList<BlockLocation>(topLevelSize);
            for (int i = 0; i < depthLimit; i++) {
                for (Iterator<BlockLocation> it = visible.iterator(); it.hasNext();) {
                    BlockLocation target = it.next().getRelative(down, i);
                    Block targetBlock = target.getBlock();
                    Material type = targetBlock.getType();
                    byte data = targetBlock.getData();
                    if (type == Material.AIR || type == liquidMaterial || (type == stationaryLiquidMaterial && data != 0)) {
                        if (i == depth) {
                            newTargets.add(target);
                        } else {
                            depth = i;
                            newTargets.clear();
                            newTargets.add(target);
                        }
                    } else if (!(type == stationaryLiquidMaterial && data == 0)) {
                        it.remove();
                    }
                }
            }
            total = newTargets.size();
            depthLimit = depth;
            return newTargets;
        }
    }

    /**
     * In this stage, the Pump retracts the tube it built in the Expand phase.
     */
    private class Retract implements Stage {
        public Stage run() {
            int size = tube.size();
            if (size == 0)
                return null;
            
            BlockLocation target = tube.remove(size - 1);
            if (!EventSimulator.blockBreak(target, player))
                return null;

            if (!putDrainItem())
                return null;

            target.setEmpty();
            return this;
        }
    }
}
