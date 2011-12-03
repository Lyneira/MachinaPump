package me.lyneira.MachinaPump;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
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

    private static final int maxLength = 8;
    private static final int maxDepth = 6;
    private static final int maxWidth = 4;
    private static final int delay = 10;

    private final Player player;
    private final BlockLocation anchor;
    private final BlockFace leverFace;
    private final BlockVector forward;
    private final BlockFace backward;
    private final BlockVector left;
    private final BlockVector right;

    private final List<BlockLocation> drain = new ArrayList<BlockLocation>(maxLength);

    Pump(BlockRotation yaw, Player player, BlockLocation anchor, BlockFace leverFace) {
        this.player = player;
        this.anchor = anchor;
        this.leverFace = leverFace;
        forward = new BlockVector(yaw.getYawFace());
        left = new BlockVector(yaw.getLeft().getYawFace());
        backward = yaw.getOpposite().getYawFace();
        right = new BlockVector(yaw.getRight().getYawFace());

        setFurnace(anchor, true);
    }

    @Override
    public boolean verify(BlockLocation anchor) {
        if (!(anchor.checkType(Blueprint.anchorMaterial) && anchor.getRelative(leverFace).checkType(Material.LEVER) && anchor.getRelative(backward).checkType(Material.BURNING_FURNACE)))
            return false;
        for (BlockLocation i : drain) {
            if (!i.checkType(Blueprint.drainMaterial))
                return false;
        }
        return true;
    }

    @Override
    public HeartBeatEvent heartBeat(BlockLocation anchor) {
        phase = phase.run();
        if (phase == null)
            return null;

        return new HeartBeatEvent(delay, anchor);
    }

    @Override
    public boolean onLever(BlockLocation anchor, Player player, ItemStack itemInHand) {
        if ((this.player == player && player.hasPermission("machinapump.deactivate-own")) || player.hasPermission("machinapump.deactivate-all")) {
            if (!(phase instanceof Retract)) {
                phase = new Retract();
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
        Inventory inventory = ((Furnace) furnace.getState()).getInventory();
        Fuel.setFurnace(furnace, backward, burning, inventory);
    }

    /**
     * Sets the state of the cauldron.
     * 
     * @param data
     */
    void setCauldron(byte data) {
        anchor.getRelative(BlockFace.UP).setData(data);
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

        anchor.getRelative(BlockFace.UP).setData((byte) (progress / divisor));
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
            item.setType(Blueprint.drainMaterial);
            inventory.setItem(Fuel.smeltSlot, item);
            return true;
        } else if (type == Blueprint.drainMaterial) {
            int amount = item.getAmount();
            if (amount < Blueprint.drainMaterial.getMaxStackSize()) {
                item.setAmount(amount + 1);
                inventory.setItem(Fuel.smeltSlot, item);
                return true;
            }
        }
        return false;
    }

    private interface Phase {
        Phase run();
    }

    private Phase phase = new Expand();

    private class Expand implements Phase {
        /**
         * Expands the pump's drain forward.
         */
        public Phase run() {
            int size = drain.size();
            if (size == maxLength)
                return stop();

            BlockLocation target = anchor.getRelative(forward, size + 1);

            if (!target.isEmpty())
                return stop();

            // Try to take a drain block from the furnace.
            Inventory inventory = ((Furnace) anchor.getRelative(backward).getBlock().getState()).getInventory();
            ItemStack item = inventory.getItem(Fuel.smeltSlot);
            if (item.getType() == Blueprint.drainMaterial) {
                // Before taking, we have to simulate whether we can actually
                // place the block.
                if (!EventSimulator.blockPlace(target, Blueprint.drainMaterial.getId(), anchor.getRelative(forward, size), player))
                    return stop();

                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                    inventory.setItem(Fuel.smeltSlot, item);
                } else {
                    inventory.clear(Fuel.smeltSlot);
                }
                target.setType(Blueprint.drainMaterial);
                drain.add(target);
                return this;
            }
            return stop();
        }

        private Phase stop() {
            if (drain.size() == 0)
                return null;
            return new Drain();
        }
    }

    private class Drain implements Phase {
        private int depth = 1;
        private int progress;
        private int total;

        private List<BlockLocation> targets = new ArrayList<BlockLocation>(maxLength * maxWidth);

        Drain() {
            determineTargets();
        }

        public Phase run() {
            if (total == 0)
                return new Retract();

            progress++;

            if (progress == total) {
                for (BlockLocation target : targets) {
                    if (target.checkType(Material.STATIONARY_WATER) || target.checkType(Material.WATER) && EventSimulator.blockBreak(target, player)) {
                        target.setEmpty();
                    }
                }
                targets.clear();

                depth++;
                if (depth > maxDepth)
                    return new Retract();

                determineTargets();
            }

            setCauldron(progress, total);
            return this;
        }

        private void determineTargets() {
            total = 0;
            progress = 0;
            for (BlockLocation d : drain) {
                BlockVector depthVector = new BlockVector(0, -1 * depth, 0);
                addTarget(d.getRelative(depthVector));
                for (int i = 1; i <= maxWidth; i++) {
                    addTarget(d.getRelative(depthVector.add(left, i)));
                    addTarget(d.getRelative(depthVector.add(right, i)));
                }
            }
        }

        private void addTarget(BlockLocation target) {
            if (target.checkType(Material.STATIONARY_WATER) || target.checkType(Material.WATER)) {
                targets.add(target);
                total++;
            }
        }
    }

    private class Retract implements Phase {
        /**
         * Removes the pump's existing drain.
         */
        public Phase run() {
            int size = drain.size();
            if (size == 0)
                return null;

            if (!putDrainItem())
                return null;

            BlockLocation target = drain.remove(size - 1);
            target.setEmpty();
            return this;
        }
    }
}
