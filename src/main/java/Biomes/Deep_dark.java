package Biomes;

import Custom.CustomItem;
import org.blog.test.MyPlugin; // MyPlugin import 추가
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World; // World import 추가
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Deep_dark implements Listener {

    // final 키워드 제거
    private final Location itemLocation; // 아이템 감지 위치 (생성자에서 초기화)
    private final JavaPlugin plugin; // 플러그인 인스턴스

    /**
     * Deep_dark 리스너 생성자
     * @param plugin 플러그인 인스턴스
     */
    public Deep_dark(JavaPlugin plugin) {
        this.plugin = plugin; // 플러그인 인스턴스 저장
        World gameWorld = Bukkit.getWorld(MyPlugin.GAME_WORLD_NAME); // MyPlugin의 상수 사용

        // 월드 로드 확인 및 itemLocation 초기화
        if (gameWorld != null) {
            // final이 아니므로 여기서 할당 가능
            this.itemLocation = new Location(gameWorld, -246, 42, -142);
            // itemLocation이 유효할 때만 이벤트 리스너 등록
            Bukkit.getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[Deep_dark] 리스너 활성화 및 itemLocation 설정 완료.");
        } else {
            // final이 아니므로 여기서 null 할당 가능 (선택적)
            this.itemLocation = null;
            plugin.getLogger().severe("[Deep_dark] 월드 '" + MyPlugin.GAME_WORLD_NAME + "'를 찾을 수 없어 리스너를 활성화할 수 없습니다!");
        }
    }

    /**
     * 플레이어가 아이템을 드롭했을 때 호출되는 이벤트 핸들러
     * @param event PlayerDropItemEvent
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        // itemLocation이 유효하지 않으면 아무 작업도 하지 않음
        if (itemLocation == null) return;

        Item droppedItem = event.getItemDrop();
        // 아이템 추적 시작
        startItemTracking(droppedItem);
    }

    /**
     * 드롭된 아이템을 추적하고 특정 위치에 도달하면 처리하는 메서드
     * @param droppedItem 추적할 드롭된 아이템 엔티티
     */
    private void startItemTracking(Item droppedItem) {
        // itemLocation이 null 이면 추적 로직 실행 안 함 (안전 장치)
        if (itemLocation == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                // 아이템 또는 월드가 유효하지 않으면 태스크 취소
                if (!droppedItem.isValid() || droppedItem.isDead() || droppedItem.getWorld() != itemLocation.getWorld()) {
                    cancel();
                    return;
                }

                Location currentItemLocation = droppedItem.getLocation();  // 아이템의 현재 위치

                // 아이템이 특정 위치 근처에 도달했는지 확인 (거리 제곱 사용)
                if (currentItemLocation.distanceSquared(itemLocation) < 1) { // 1 블록 반경 내

                    ItemStack itemStack = droppedItem.getItemStack();

                    // 드롭된 아이템이 스컬크인지 확인
                    if (itemStack.isSimilar(CustomItem.SCULK)) {
                        Player player = getDroppingPlayer(droppedItem);
                        if (player != null) {
                            int amount = itemStack.getAmount();
                            player.getInventory().addItem(new ItemStack(Material.SALMON, amount)); // 연어 지급
                            player.sendMessage("Sculk 이 흡수되었고, 당신은 연어 " + amount + "개를 받았습니다!");
                        }
                        droppedItem.remove(); // 스컬크 아이템 제거
                    }
                    // 스컬크가 아닌 다른 아이템인 경우
                    else {
                        Player player = getDroppingPlayer(droppedItem);
                        if (player != null) {
                            player.getInventory().addItem(itemStack); // 아이템 반환
                            player.sendMessage("스컬크 아이템이 아니어서 아이템이 반환되었습니다.");
                        }
                        droppedItem.remove(); // 드롭된 아이템 제거
                    }
                    cancel(); // 작업 완료 후 태스크 취소
                }
            }
            // 0.8초(16틱) 후에 시작하여 0.25초(5틱) 간격으로 반복 확인
        }.runTaskTimer(plugin, 16L, 5L);
    }

    /**
     * 아이템을 드롭한 플레이어를 반환하는 헬퍼 메서드
     * @param item 아이템 엔티티
     * @return 드롭한 플레이어 (없거나 오프라인이면 null)
     */
    private Player getDroppingPlayer(Item item) {
        if (item.getThrower() != null) {
            return Bukkit.getPlayer(item.getThrower());
        }
        return null;
    }
}