package test1.system;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class HeatSystem {
    private static final int DEFAULT_TEMPERATURE = 0;
    private static final int DESERT_TEMPERATURE = 1;
    private static final int NEARBY_BLOCK_RADIUS = 3;

    private static final long TEMPERATURE_TASK_INTERVAL_TICKS = 20L;

    private static final int ICE_TEMP = -1;
    private static final int LAVA_TEMP = 2;
    private static final int HOT_BLOCK_TEMP = 1;
    private static final int SOUL_FIRE_TEMP = 2;

    private final JavaPlugin plugin;
    private final Map<Player, Integer> temperatureLevels = new HashMap<>();
    private final String[] temperatureStates = {"매우 추움", "추움", "정상", "더움", "매우 더움"};

    public HeatSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        startTemperatureTask();
    }

    private void startTemperatureTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        temperatureLevels.remove(player);
                        continue;
                    }
                    updateTemperature(player);
                }
            }
        }.runTaskTimer(plugin, 0L, TEMPERATURE_TASK_INTERVAL_TICKS);
    }

    private void updateTemperature(Player player) {
        Location loc = player.getLocation();

        // [수정] 매개변수 radius 제거
        if (!isAreaLoaded(loc.getWorld(), loc.getBlockX(), loc.getBlockZ())) {
            return;
        }

        int temp = DEFAULT_TEMPERATURE;

        if (loc.getWorld().getBiome(loc) == Biome.DESERT) {
            temp = DESERT_TEMPERATURE;
        }

        temp += calculateBlockTemperatureChange(loc);

        if (player.isInWater()) {
            temp -= 1;
        }

        temperatureLevels.put(player, temp);
        applyEffects(player, temp);
    }

    // [수정] 매개변수 radius 제거 (상수 사용)
    private boolean isAreaLoaded(World world, int centerX, int centerZ) {
        int minChunkX = (centerX - NEARBY_BLOCK_RADIUS) >> 4;
        int maxChunkX = (centerX + NEARBY_BLOCK_RADIUS) >> 4;
        int minChunkZ = (centerZ - NEARBY_BLOCK_RADIUS) >> 4;
        int maxChunkZ = (centerZ + NEARBY_BLOCK_RADIUS) >> 4;

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!world.isChunkLoaded(x, z)) return false;
            }
        }
        return true;
    }

    private int calculateBlockTemperatureChange(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        boolean foundIce = false;
        boolean foundLava = false;
        boolean foundHeat = false;
        boolean foundSoul = false;

        for (int x = -NEARBY_BLOCK_RADIUS; x <= NEARBY_BLOCK_RADIUS; x++) {
            for (int y = -NEARBY_BLOCK_RADIUS; y <= NEARBY_BLOCK_RADIUS; y++) {
                for (int z = -NEARBY_BLOCK_RADIUS; z <= NEARBY_BLOCK_RADIUS; z++) {

                    if (foundIce && foundLava && foundHeat && foundSoul) {
                        return calculateTotalChange(true, true, true, true);
                    }

                    Material type = world.getBlockAt(cx + x, cy + y, cz + z).getType();

                    if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) continue;

                    if (type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE) foundIce = true;
                    else if (type == Material.LAVA) foundLava = true;
                    else if (type == Material.FURNACE || type == Material.CAMPFIRE || type == Material.MAGMA_BLOCK) foundHeat = true;
                    else if (type == Material.SOUL_CAMPFIRE || type == Material.SOUL_FIRE) foundSoul = true;
                }
            }
        }
        return calculateTotalChange(foundIce, foundLava, foundHeat, foundSoul);
    }

    private int calculateTotalChange(boolean ice, boolean lava, boolean heat, boolean soul) {
        int change = 0;
        if (ice) change += ICE_TEMP;
        if (lava) change += LAVA_TEMP;
        if (heat) change += HOT_BLOCK_TEMP;
        if (soul) change += SOUL_FIRE_TEMP;
        return change;
    }

    private void applyEffects(Player player, int temp) {
        if (temp == 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, false, false));
        } else if (temp >= 2) {
            int amplifier = temp - 2;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, amplifier, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, amplifier, false, false));
        } else if (temp <= -2) {
            int amplifier = (-2) - temp;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, amplifier, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, amplifier, false, false));
        }
    }

    public String getTemperatureState(Player player) {
        int temp = temperatureLevels.getOrDefault(player, DEFAULT_TEMPERATURE);
        if (temp > 2) return "매우 더움" + "!".repeat(Math.min(3, temp - 2));
        if (temp < -2) return "매우 추움" + "!".repeat(Math.min(3, (-2) - temp));

        // [수정] 불필요한 idx >= 0 검사 단순화
        int idx = temp + 2;
        if (idx >= 0 && idx < temperatureStates.length) {
            return temperatureStates[idx];
        }
        return "정상";
    }
}