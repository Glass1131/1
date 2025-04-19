package System;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ThirstSystem implements Listener {
    // 상수 추가 (이전 답변에서 제안된 상수들을 포함)
    private static final int DESERT_THIRST_INCREASE_INTERVAL_SECONDS = 30;
    private static final int NORMAL_THIRST_INCREASE_INTERVAL_SECONDS = 60;
    private static final long DESERT_THIRST_INCREASE_INTERVAL_TICKS = DESERT_THIRST_INCREASE_INTERVAL_SECONDS * 20L;
    private static final long NORMAL_THIRST_INCREASE_INTERVAL_TICKS = NORMAL_THIRST_INCREASE_INTERVAL_SECONDS * 20L;
    private static final int THIRST_INCREASE_AMOUNT = 1;
    private static final int MAX_THIRST_LEVEL = 100;
    private static final int INITIAL_THIRST_LEVEL = 0;
    private static final long THIRST_TASK_INTERVAL_TICKS = 20L; // 1초 (20틱) 간격

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> thirstLevels = new HashMap<>();
    // 플레이어별로 경과 시간을 저장 (틱 단위)
    private final Map<UUID, Integer> thirstTimers = new HashMap<>();
    // damageTimers 는 즉사 로직으로 변경됨에 따라 더 이상 필요 없습니다.

    public ThirstSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startThirstTask();
    }

    private void startThirstTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    // 관전자 모드인 플레이어는 갈증 업데이트 로직을 건너뜁니다.
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        // 관전자가 되면 갈증과 타이머를 초기화 (즉사 시 처리되지만, 혹시 모를 경우를 대비)
                        thirstLevels.put(player.getUniqueId(), INITIAL_THIRST_LEVEL);
                        thirstTimers.put(player.getUniqueId(), 0);
                        continue; // 관전자는 갈증 업데이트를 하지 않음
                    }

                    UUID playerId = player.getUniqueId();
                    // 타이머를 task 간격만큼 증가시킴
                    int timer = thirstTimers.getOrDefault(playerId, 0) + (int) THIRST_TASK_INTERVAL_TICKS;

                    // 현재 플레이어의 위치가 사막 바이옴인지 체크
                    Biome currentBiome = player.getLocation().getBlock().getBiome();
                    long threshold = (currentBiome == Biome.DESERT) ? DESERT_THIRST_INCREASE_INTERVAL_TICKS : NORMAL_THIRST_INCREASE_INTERVAL_TICKS;

                    if (timer >= threshold) {
                        updateThirst(player);
                        timer = 0;
                    }
                    thirstTimers.put(playerId, timer);

                    // 갈증이 100%일 때 즉사시키는 로직
                    if (getThirstLevel(player) >= MAX_THIRST_LEVEL) { // >= 100으로 변경하여 안전성 높임
                        player.setHealth(0); // 플레이어를 즉사시킴
                        player.sendMessage(Component.text("💀 극심한 갈증으로 사망했습니다!", NamedTextColor.RED)); // 메시지 변경

                        // 사망 후 관전자로 만들고 갈증 리셋
                        // Bukkit 의 사망 처리는 다음 틱에 이루어지므로, 약간의 딜레이 후 처리하는 것이 안전할 수 있습니다.
                        // 여기서는 간단하게 다음 틱에 처리하도록 합니다.
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) { // 플레이어가 아직 서버에 접속해 있다면
                                    player.setGameMode(GameMode.SPECTATOR);
                                    thirstLevels.put(playerId, INITIAL_THIRST_LEVEL); // 갈증 레벨 리셋
                                    thirstTimers.put(playerId, 0); // 갈증 타이머 리셋
                                }
                            }
                        }.runTaskLater(plugin, 1L); // 다음 틱에 실행

                        // 즉사했으므로 더 이상의 갈증 업데이트는 필요 없습니다. continue 또는 return 사용 가능
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, THIRST_TASK_INTERVAL_TICKS); // 1초(20틱)마다 실행
    }

    private void updateThirst(Player player) {
        UUID playerId = player.getUniqueId();
        int thirst = thirstLevels.getOrDefault(playerId, INITIAL_THIRST_LEVEL);
        thirst = Math.min(MAX_THIRST_LEVEL, thirst + THIRST_INCREASE_AMOUNT);  // 갈증을 1%씩 증가시킴 (최대 100%)

        // 갈증 100% 메시지는 즉사 로직에서 처리하므로 여기서는 제거합니다.
        thirstLevels.put(playerId, thirst);
    }

    @EventHandler
    public void onPlayerDrinkWater(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        // 현재는 POTION 아이템을 물로 가정합니다. 필요하다면 특정 커스텀 아이템으로 변경하세요.
        if (event.getItem().getType() == Material.POTION) {
            UUID playerId = player.getUniqueId();
            thirstLevels.put(playerId, INITIAL_THIRST_LEVEL); // 물을 마시면 갈증이 0%로 초기화
            thirstTimers.put(playerId, 0); // 타이머 초기화
            player.sendMessage(Component.text("💧 물을 마셔 갈증이 해소되었습니다!", NamedTextColor.AQUA));
        }
    }

    public int getThirstLevel(Player player) {
        return thirstLevels.getOrDefault(player.getUniqueId(), INITIAL_THIRST_LEVEL); // 초기값을 0%로 설정
    }
}