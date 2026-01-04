package test1.biomes;

import test1.item.CustomItem;
import test1.MyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public class DeepDark {

    private Location targetLocation;

    public DeepDark(JavaPlugin plugin) {
        World world = Bukkit.getWorld(MyPlugin.GAME_WORLD_NAME);
        if (world != null) {
            this.targetLocation = new Location(world, -246, 42, -142);
            startScanning(plugin);
        } else {
            this.targetLocation = null;
        }
    }

    public void setTargetLocation(Location location) {
        this.targetLocation = location;
    }

    private void startScanning(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // targetLocation이 없거나 월드가 없는 경우 스킵
                if (targetLocation == null || targetLocation.getWorld() == null) return;

                // [최적화 & 안전] 해당 좌표의 청크가 로드되어 있는지 확인
                // getChunk()를 호출하면 청크가 강제 로드될 수 있으므로, World의 메서드를 사용하여 확인
                if (!targetLocation.getWorld().isChunkLoaded(targetLocation.getBlockX() >> 4, targetLocation.getBlockZ() >> 4)) {
                    return;
                }

                // [최적화] getNearbyEntitiesByType는 Paper에서 이미 최적화되어 있지만,
                // 불필요한 호출을 줄이기 위해 로드 여부를 먼저 체크했습니다.
                Collection<Item> nearbyItems = targetLocation.getWorld().getNearbyEntitiesByType(Item.class, targetLocation, 1.5);

                for (Item itemEntity : nearbyItems) {
                    processItem(itemEntity);
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private void processItem(Item itemEntity) {
        if (itemEntity.isDead()) return;

        ItemStack stack = itemEntity.getItemStack();
        Player thrower = null;

        if (itemEntity.getThrower() != null) {
            thrower = Bukkit.getPlayer(itemEntity.getThrower());
        }

        if (stack.isSimilar(CustomItem.SCULK)) {
            int amount = stack.getAmount();
            itemEntity.remove(); // 아이템 제거

            if (thrower != null && thrower.isOnline()) {
                thrower.getInventory().addItem(new ItemStack(Material.SALMON, amount));
                thrower.sendMessage("§b[Deep Dark] §f스컬크를 바치고 연어 " + amount + "개를 얻었습니다!");
            }
        }
        else {
            // 다른 아이템이면 반환
            if (thrower != null && thrower.isOnline()) {
                if (thrower.getInventory().firstEmpty() != -1) {
                    thrower.getInventory().addItem(stack);
                    thrower.sendMessage("§c[Deep Dark] §f이 제단은 스컬크만 받습니다.");
                    itemEntity.remove();
                }
            }
        }
    }
}