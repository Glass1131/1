package test1.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import test1.MyPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BiomeNotifier extends BukkitRunnable {

    private final MyPlugin plugin;
    private final Map<UUID, String> playerInBiome = new HashMap<>();
    private final Map<UUID, Map<String, Long>> suppressionCooldowns = new HashMap<>();

    // [수정] 쿨타임을 1분 -> 3초로 단축 (재진입 시 메시지 출력 원활화)
    private static final long COOLDOWN_DURATION_MILLIS = TimeUnit.SECONDS.toMillis(3);

    public BiomeNotifier(MyPlugin plugin) {
        this.plugin = plugin;
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerBiome(player);
        }
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerBiome(player);
        }
    }

    private void checkPlayerBiome(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        UUID playerId = player.getUniqueId();
        Location loc = player.getLocation();

        // 청크가 로드되지 않았으면 계산하지 않음
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        Biome currentBiome = loc.getWorld().getBiome(loc);
        String currentBiomeKey = currentBiome.getKey().getKey().toLowerCase();

        String previousBiomeKey = playerInBiome.getOrDefault(playerId, "default");

        // 바이옴이 변경되었을 때만 로직 수행
        if (!currentBiomeKey.equals(previousBiomeKey)) {
            Map<String, List<Integer>> biomeCoordsMap = plugin.getConfigBiomeSpawnCoords();
            long currentTime = System.currentTimeMillis();

            // 이전 바이옴 퇴장 처리
            if (!previousBiomeKey.equals("default")) {
                handleBiomeExit(player, previousBiomeKey, currentTime, biomeCoordsMap);
            }

            // 새 바이옴 진입 처리
            handleBiomeEntry(player, currentBiomeKey, currentTime, biomeCoordsMap);

            playerInBiome.put(playerId, currentBiomeKey);
        }
    }

    public void removePlayer(UUID uuid) {
        playerInBiome.remove(uuid);
        suppressionCooldowns.remove(uuid);
    }

    private void handleBiomeEntry(Player player, String biomeKey, long currentTime, Map<String, List<Integer>> biomeCoordsMap) {
        String eventType = biomeKey + "_enter";

        boolean suppressed = isSuppressed(player.getUniqueId(), eventType, currentTime);

        if (!suppressed) {
            Runnable entryAction = switch (biomeKey) {
                case "deep_dark" -> () -> {
                    player.sendMessage(Component.text("Deep Dark 바이옴 진입! 어둠이 찾아옵니다.", NamedTextColor.DARK_GRAY));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));
                };
                case "desert" -> () -> player.sendMessage(Component.text("사막에 진입했습니다. 갈증이 빨라집니다.", NamedTextColor.YELLOW));
                case "swamp" -> () -> player.sendMessage(Component.text("늪지대에 진입했습니다.", NamedTextColor.DARK_GREEN));
                default -> null;
            };

            if (entryAction != null) {
                entryAction.run();
                setSuppression(player.getUniqueId(), eventType, currentTime + COOLDOWN_DURATION_MILLIS);
            }
        }

        List<Integer> coords = biomeCoordsMap.get(biomeKey);
        if (coords != null && coords.size() == 4) {
            plugin.setZombieSpawnCoordinates(coords.get(0), coords.get(1), coords.get(2), coords.get(3));
        }
    }

    private void handleBiomeExit(Player player, String biomeKey, long currentTime, Map<String, List<Integer>> biomeCoordsMap) {
        String eventType = biomeKey + "_exit";
        boolean suppressed = isSuppressed(player.getUniqueId(), eventType, currentTime);

        if (!suppressed) {
            boolean messageSent = false;

            if (biomeKey.equals("deep_dark")) {
                player.removePotionEffect(PotionEffectType.DARKNESS);
                player.sendMessage(Component.text("Deep Dark를 벗어났습니다.", NamedTextColor.GRAY));
                messageSent = true;
            }
            else if (biomeKey.equals("swamp")) {
                player.sendMessage(Component.text("늪지대를 벗어났습니다.", NamedTextColor.GREEN));
                messageSent = true;
            }

            if (messageSent) {
                setSuppression(player.getUniqueId(), eventType, currentTime + COOLDOWN_DURATION_MILLIS);
            }
        }

        List<Integer> defaultCoords = biomeCoordsMap.get("default");
        if (defaultCoords != null) {
            plugin.setZombieSpawnCoordinates(defaultCoords.get(0), defaultCoords.get(1), defaultCoords.get(2), defaultCoords.get(3));
        }
    }

    private boolean isSuppressed(UUID playerId, String eventType, long currentTime) {
        Map<String, Long> cooldowns = suppressionCooldowns.get(playerId);
        return cooldowns != null && cooldowns.getOrDefault(eventType, 0L) > currentTime;
    }

    private void setSuppression(UUID playerId, String eventType, long endTime) {
        suppressionCooldowns.computeIfAbsent(playerId, k -> new HashMap<>()).put(eventType, endTime);
    }
}