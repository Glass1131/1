package test1.manager;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import test1.MyPlugin;

import java.util.Map;
import java.util.Random;

public class MobSpawner {

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final Random random;

    private int mobsPerRoundBase;
    private long spawnIntervalTicks;
    private Map<String, Map<String, Double>> biomeSpawnProbabilities;

    private BukkitTask spawnTask = null;

    public MobSpawner(JavaPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.random = new Random();
        reloadConfig();
    }

    public void reloadConfig() {
        this.mobsPerRoundBase = plugin.getConfig().getInt("spawn.zombies-per-round", 20);
        this.spawnIntervalTicks = plugin.getConfig().getLong("spawn.interval-ticks", 10L);

        if (plugin instanceof MyPlugin myPluginInstance) {
            this.biomeSpawnProbabilities = myPluginInstance.getConfigBiomeSpawnProbabilities();
        } else {
            this.biomeSpawnProbabilities = Map.of();
            plugin.getLogger().severe("MobSpawner could not be initialized correctly.");
        }
    }

    public void startSpawning(int round, int extraHealth, boolean isBossRound) {
        stopSpawning();

        World world = Bukkit.getWorld(MyPlugin.GAME_WORLD_NAME);
        if (world == null) {
            plugin.getLogger().warning(MyPlugin.WORLD_NOT_FOUND_WARNING);
            return;
        }

        final int totalMobs = isBossRound ? (this.mobsPerRoundBase / 2) + 1 : this.mobsPerRoundBase + (round / 2);

        final int currentMinX, currentMaxX, currentMinZ, currentMaxZ;
        if (plugin instanceof MyPlugin myPluginInstance) {
            currentMinX = myPluginInstance.minX;
            currentMaxX = myPluginInstance.maxX;
            currentMinZ = myPluginInstance.minZ;
            currentMaxZ = myPluginInstance.maxZ;
        } else {
            return;
        }

        if (isBossRound) {
            Bukkit.broadcast(Component.text("☠ 보스 스테이지! 강력한 적이 나타났습니다!", NamedTextColor.DARK_PURPLE));
            world.playSound(new Location(world, (currentMinX + currentMaxX)/2.0, 80, (currentMinZ + currentMaxZ)/2.0), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }

        spawnTask = new BukkitRunnable() {
            int spawnedCount = 0;
            boolean bossSpawned = false;

            @Override
            public void run() {
                if (!gameManager.isGameInProgress()) {
                    cancel();
                    spawnTask = null;
                    return;
                }
                if (spawnedCount >= totalMobs) {
                    cancel();
                    spawnTask = null;
                    return;
                }

                Location spawnLocation = getRandomSpawnLocation(world, currentMinX, currentMaxX, currentMinZ, currentMaxZ);
                if (spawnLocation == null) return;

                Biome spawnBiome = world.getBiome(spawnLocation);
                LivingEntity spawnedEntity;

                if (isBossRound && !bossSpawned) {
                    spawnBossSkeleton(spawnLocation, spawnBiome, round);
                    bossSpawned = true;
                    spawnedCount++;
                    return;
                }

                String biomeConfigKey = spawnBiome.getKey().getKey().toLowerCase();
                EntityType typeToSpawn = determineEntityType(biomeConfigKey);

                try {
                    spawnedEntity = (LivingEntity) world.spawnEntity(spawnLocation, typeToSpawn);
                    configureSpawnedMonster(spawnedEntity, extraHealth);
                    gameManager.addGameMonster(spawnedEntity);
                    spawnedCount++;
                } catch (Exception e) {
                    plugin.getLogger().severe("몹 스폰 오류: " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 0L, this.spawnIntervalTicks);
    }

    private void spawnBossSkeleton(Location location, Biome biome, int round) {
        EntityType bossType = EntityType.SKELETON;
        String bossName = "공포에 적응하는 자";
        NamedTextColor bossColor = NamedTextColor.WHITE;

        String biomeKey = biome.getKey().getKey().toLowerCase();

        if (biomeKey.contains("swamp")) {
            bossType = EntityType.BOGGED;
            bossName = "진흙에 빠진 사수";
            bossColor = NamedTextColor.GREEN;
        } else if (biomeKey.contains("desert")) {
            bossName = "메마른 저격수";
            bossColor = NamedTextColor.YELLOW;
        } else if (biomeKey.contains("deep_dark") || biomeKey.contains("dark")) {
            bossName = "빛을 잃은 저격수";
            bossColor = NamedTextColor.BLACK;
        } else if (biomeKey.contains("nether") || biomeKey.contains("hell")) {
            bossName = "영혼을 빼앗긴 자";
            bossColor = NamedTextColor.BLUE;
        }

        LivingEntity boss = (LivingEntity) location.getWorld().spawnEntity(location, bossType);

        AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double health = 150.0 + (round * 10.0);
            maxHealth.setBaseValue(health);
            boss.setHealth(health);
        }

        boss.customName(Component.text("§l[BOSS] " + bossName + " (Lv." + round + ")", bossColor));
        boss.setCustomNameVisible(true);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));

        if (boss.getEquipment() != null) {
            boss.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.BOW));
        }

        gameManager.addGameMonster(boss);
        Bukkit.broadcast(Component.text("🏹 " + bossName + "가 출현했습니다!", bossColor));
    }

    public void stopSpawning() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    private Location getRandomSpawnLocation(World world, int minX, int maxX, int minZ, int maxZ) {
        int actualMinX = Math.min(minX, maxX);
        int actualMaxX = Math.max(minX, maxX);
        int actualMinZ = Math.min(minZ, maxZ);
        int actualMaxZ = Math.max(minZ, maxZ);

        for (int i = 0; i < 10; i++) {
            double spawnX = random.nextDouble(actualMinX, actualMaxX + 1);
            double spawnZ = random.nextDouble(actualMinZ, actualMaxZ + 1);
            int blockX = (int) spawnX;
            int blockZ = (int) spawnZ;

            if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) continue;

            int safeY = world.getHighestBlockYAt(blockX, blockZ);
            if (safeY < world.getMinHeight() || safeY > world.getMaxHeight()) continue;

            return new Location(world, spawnX + 0.5, safeY + 1, spawnZ + 0.5);
        }
        return null;
    }

    private EntityType determineEntityType(String biomeConfigKey) {
        Map<String, Double> biomeProbabilities = this.biomeSpawnProbabilities.get(biomeConfigKey);

        if (biomeProbabilities == null || biomeProbabilities.isEmpty()) {
            return EntityType.ZOMBIE;
        }

        double totalProbability = 0.0;
        for (Double prob : biomeProbabilities.values()) {
            if (prob != null) totalProbability += prob;
        }

        if (totalProbability <= 0) return EntityType.ZOMBIE;

        double randomChance = random.nextDouble() * totalProbability;
        double cumulativeProbability = 0.0;

        for (Map.Entry<String, Double> entry : biomeProbabilities.entrySet()) {
            Double probability = entry.getValue();
            if (probability == null || probability <= 0) continue;

            cumulativeProbability += probability;
            if (randomChance <= cumulativeProbability) {
                try {
                    return EntityType.valueOf(entry.getKey());
                } catch (IllegalArgumentException e) {
                    return EntityType.ZOMBIE;
                }
            }
        }
        return EntityType.ZOMBIE;
    }

    private void configureSpawnedMonster(LivingEntity entity, int extraHealth) {
        if (entity instanceof Slime slime) {
            int slimeSize = random.nextInt(2) + 2;
            slime.setSize(slimeSize);
            return;
        }

        AttributeInstance maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            double baseHealth = maxHealthAttribute.getBaseValue();
            double totalHealth = calculateEntityTotalHealth(entity.getType(), baseHealth, extraHealth);
            maxHealthAttribute.setBaseValue(totalHealth);
            entity.setHealth(totalHealth);
        }
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
    }

    private double calculateEntityTotalHealth(EntityType type, double baseHealth, int extraHealth) {
        double calculatedHealth = baseHealth;
        switch (type) {
            case HUSK:
                calculatedHealth += (double) extraHealth / 3.0;
                break;
            case ZOMBIE:
            case ZOMBIE_VILLAGER:
                calculatedHealth += extraHealth;
                break;
            case BOGGED:
            case WITCH:
            case SKELETON:
                calculatedHealth += (double) extraHealth / 4.0;
                break;
            default:
                break;
        }
        return Math.max(1.0, calculatedHealth);
    }
}