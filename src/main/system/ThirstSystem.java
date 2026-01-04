package test1.system;

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

    private static final int MAX_THIRST = 100;
    private static final long DESERT_INTERVAL = 30 * 20L;
    private static final long NORMAL_INTERVAL = 60 * 20L;

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> thirstLevels = new HashMap<>();
    private final Map<UUID, Integer> thirstTimers = new HashMap<>();

    // [확인] 이 변수가 있는지 확인하세요.
    private boolean isEnabled = true;

    public ThirstSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startThirstTask();
    }

    // [확인] 이 메서드가 있는지 확인하세요.
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            thirstLevels.clear();
            thirstTimers.clear();
        }
    }

    // [확인] 이 메서드가 있는지 확인하세요.
    public boolean isEnabled() {
        return isEnabled;
    }

    private void startThirstTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEnabled) return; // 꺼져있으면 중단

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        thirstTimers.remove(player.getUniqueId());
                        continue;
                    }

                    UUID uid = player.getUniqueId();
                    int currentTimer = thirstTimers.getOrDefault(uid, 0);
                    currentTimer += 20;

                    long threshold = NORMAL_INTERVAL;
                    // 청크 로드 확인 (안전장치)
                    if (player.getWorld().isChunkLoaded(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4)) {
                        if (player.getLocation().getBlock().getBiome() == Biome.DESERT) {
                            threshold = DESERT_INTERVAL;
                        }
                    }

                    if (currentTimer >= threshold) {
                        increaseThirst(player);
                        currentTimer = 0;
                    }
                    thirstTimers.put(uid, currentTimer);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void increaseThirst(Player player) {
        UUID uid = player.getUniqueId();
        int currentThirst = thirstLevels.getOrDefault(uid, 0);

        if (currentThirst >= MAX_THIRST) {
            player.setHealth(0);
            player.sendMessage(Component.text("💀 극심한 갈증으로 사망했습니다!", NamedTextColor.RED));
            thirstLevels.put(uid, 0);
            thirstTimers.put(uid, 0);
        } else {
            thirstLevels.put(uid, currentThirst + 1);
        }
    }

    @EventHandler
    public void onDrink(PlayerItemConsumeEvent event) {
        if (!isEnabled) return; // 꺼져있으면 무시

        if (event.getItem().getType() == Material.POTION) {
            resetThirst(event.getPlayer());
            event.getPlayer().sendMessage(Component.text("💧 갈증이 해소되었습니다!", NamedTextColor.AQUA));
        }
    }

    public int getThirstLevel(Player player) {
        return thirstLevels.getOrDefault(player.getUniqueId(), 0);
    }

    public void setThirstLevel(Player player, int level) {
        thirstLevels.put(player.getUniqueId(), Math.max(0, Math.min(MAX_THIRST, level)));
    }

    private void resetThirst(Player player) {
        UUID uid = player.getUniqueId();
        thirstLevels.put(uid, 0);
        thirstTimers.put(uid, 0);
    }
}