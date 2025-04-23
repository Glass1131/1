package Biomes;

import Custom.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

public class Deep_dark implements Listener {

    private final Location itemLocation = new Location(Bukkit.getWorld("world"), -246, 42, -142); // 이 좌표에서 아이템 감지

    public Deep_dark(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Item droppedItem = event.getItemDrop();

        // 아이템이 드롭된 후 0.8초 뒤에 위치 추적 시작
        startItemTracking(droppedItem);
    }

    // 아이템을 추적하는 메서드
    private void startItemTracking(Item droppedItem) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Location currentItemLocation = droppedItem.getLocation();  // 아이템의 현재 위치

                // 아이템이 특정 위치에 도달했을 경우
                if (currentItemLocation.distance(itemLocation) < 1) {
                    // 아이템이 감지되었을 때
                    ItemStack itemStack = droppedItem.getItemStack();

                    if (itemStack.isSimilar(CustomItem.SCULK)) {
                        // 스컬크 아이템이라면 연어 지급 후 아이템 삭제
                        Player player = getDroppingPlayer(droppedItem);
                        if (player != null) {
                            int amount = droppedItem.getItemStack().getAmount();
                            player.getInventory().addItem(new ItemStack(Material.SALMON, amount));
                            player.sendMessage("§aSculk 이 흡수되었고, 당신은 연어 " + amount + "개를 받았습니다!");
                        }

                        // 아이템 삭제
                        droppedItem.remove();
                    } else {
                        // 스컬크 아이템이 아니라면, 플레이어에게 아이템 반환
                        Player player = getDroppingPlayer(droppedItem);
                        if (player != null) {
                            player.getInventory().addItem(droppedItem.getItemStack());
                            player.sendMessage("§c스컬크 아이템이 아니어서 아이템이 반환되었습니다.");
                        }

                        // 아이템 삭제 (반환되었기 때문에 실제로는 삭제하지 않지만, 중복 방지를 위해)
                        droppedItem.remove();
                    }

                    // 아이템 추적을 멈추고 반복을 종료
                    cancel();
                }
            }
        }.runTaskLater(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("test")), 16L); // 16L = 0.8초 (0.8초 후에 한 번 체크)
    }

    private Player getDroppingPlayer(Item item) {
        return item.getThrower() != null ? Bukkit.getPlayer(item.getThrower()) : null;
    }
}

