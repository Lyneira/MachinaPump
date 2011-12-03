package me.lyneira.MachinaPump;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.lyneira.MachinaCraft.BlockLocation;
import me.lyneira.MachinaCraft.BlockRotation;
import me.lyneira.MachinaCraft.Machina;
import me.lyneira.MachinaCraft.MachinaBlueprint;

/**
 * MachinaBlueprint representing a Pump blueprint
 * 
 * @author Lyneira
 */
final class Blueprint implements MachinaBlueprint {
    final static Blueprint instance = new Blueprint();
    
    final static Material anchorMaterial = Material.GOLD_BLOCK;
    final static Material drainMaterial = Material.WOOD;
    
    
    private Blueprint() {
        // Singleton
    }
    
    public Machina detect(Player player, final BlockLocation anchor, final BlockFace leverFace, ItemStack itemInHand) {
        if (!player.hasPermission("machinapump.activate"))
            return null;
        
        if (!anchor.checkType(anchorMaterial))
            return null;

        BlockRotation leverRotation;
        try {
            leverRotation = BlockRotation.yawFromBlockFace(leverFace);
        } catch (Exception e) {
            return null;
        }
        
        BlockRotation[] possibleDirections = {leverRotation.getLeft(), leverRotation.getRight()};
        for (BlockRotation i : possibleDirections) {
            if (anchor.getRelative(i.getYawFace()).checkType(Material.FURNACE)) {
                return new Pump(i.getOpposite(), player, anchor, leverFace);
            }
        }
        
        return null;
    }
}
