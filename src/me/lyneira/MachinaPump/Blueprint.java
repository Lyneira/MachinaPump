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
    
    
    private Blueprint() {
        // Singleton
    }
    
    public Machina detect(Player player, final BlockLocation anchor, final BlockFace leverFace, ItemStack itemInHand) {
        if (!player.hasPermission("machinapump.activate"))
            return null;
        
        if (!anchor.checkType(anchorMaterial))
            return null;

        BlockRotation yaw = null;
        BlockFace cauldron = null;
        for (BlockRotation i : BlockRotation.values()) {
            BlockFace face = i.getYawFace();
            BlockLocation location = anchor.getRelative(face);
            if (location.checkType(Material.FURNACE)) {
                yaw = i.getOpposite();
            } else if (location.checkType(Material.CAULDRON)) {
                cauldron = face;
            }
        }
        if (anchor.getRelative(BlockFace.UP).checkType(Material.CAULDRON)) {
            cauldron = BlockFace.UP;
        }
        
        if (yaw != null && cauldron != null) {
            return new Pump(yaw, player, anchor, leverFace, cauldron);
        }
        return null;
    }
}
