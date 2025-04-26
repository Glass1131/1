package org.blog.test;

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

// 사용 안 함 (제거 가능)
import java.util.Map;
import java.util.Random;
import java.util.Objects;

/**
 * 게임 내 몹 스폰을 관리하는 클래스.
 * 스폰 타이머, 위치, 타입, 속성 설정을 담당합니다.
 */
public class MobSpawner {

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final Random random;

    // 스폰 관련 설정값
    private final int mobsPerRoundBase;
    private final long spawnIntervalTicks;
    private final Map<String, Map<String, Double>> biomeSpawnProbabilities;
    // *** 사용되지 않는 biomeSpawnCoords 필드 제거 ***
    // private final Map<String, List<Integer>> biomeSpawnCoords;

    // 스폰 작업 관리
    private BukkitTask spawnTask = null;

    /**
     * MobSpawner 생성자.
     * @param plugin 플러그인 인스턴스
     * @param gameManager 게임 매니저 인스턴스
     */
    public MobSpawner(JavaPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.random = new Random();

        // 설정값 로드
        this.mobsPerRoundBase = plugin.getConfig().getInt("spawn.zombies-per-round", 20);
        this.spawnIntervalTicks = plugin.getConfig().getLong("spawn.interval-ticks", 10L);
        // MyPlugin의 getter를 통해 설정값 가져오기
        if (plugin instanceof MyPlugin myPluginInstance) {
            this.biomeSpawnProbabilities = myPluginInstance.getConfigBiomeSpawnProbabilities();
            // *** biomeSpawnCoords 할당 제거 ***
            // this.biomeSpawnCoords = myPluginInstance.getConfigBiomeSpawnCoords();
        } else {
            this.biomeSpawnProbabilities = Map.of();
            // this.biomeSpawnCoords = Map.of(); // 제거됨
            plugin.getLogger().severe("MobSpawner could not be initialized correctly because the provided plugin instance is not MyPlugin!");
        }
    }

    /**
     * 특정 라운드의 몹 스폰을 시작합니다.
     * @param round 현재 라운드
     * @param extraHealth 라운드별 추가 체력
     */
    public void startSpawning(int round, int extraHealth) {
        stopSpawning(); // 기존 스폰 작업 중지

        World world = Bukkit.getWorld(MyPlugin.GAME_WORLD_NAME);
        if (world == null) {
            plugin.getLogger().warning(MyPlugin.WORLD_NOT_FOUND_WARNING + " (from MobSpawner)");
            return;
        }

        final int entitiesToSpawn = this.mobsPerRoundBase * round;
        if (entitiesToSpawn <= 0) return;

        // MyPlugin의 현재 스폰 좌표를 가져옴
        final int currentMinX, currentMaxX, currentMinZ, currentMaxZ;
        if (plugin instanceof MyPlugin myPluginInstance) {
            currentMinX = myPluginInstance.minX;
            currentMaxX = myPluginInstance.maxX;
            currentMinZ = myPluginInstance.minZ;
            currentMaxZ = myPluginInstance.maxZ;
        } else {
            plugin.getLogger().severe("Cannot get current spawn coordinates from MyPlugin instance!");
            return;
        }


        spawnTask = new BukkitRunnable() {
            int spawnedCount = 0;

            @Override
            public void run() {
                if (!gameManager.isGameInProgress()) {
                    cancel();
                    spawnTask = null;
                    return;
                }
                if (spawnedCount >= entitiesToSpawn) {
                    cancel();
                    spawnTask = null;
                    return;
                }

                // 현재 설정된 좌표 범위 내에서 스폰 위치 찾기
                Location spawnLocation = getRandomSpawnLocation(world, currentMinX, currentMaxX, currentMinZ, currentMaxZ);
                if (spawnLocation == null) {
                    plugin.getLogger().warning("Failed to find a valid spawn location in the specified range.");
                    return; // 다음 틱에 다시 시도
                }

                // 스폰 위치 바이옴 확인 및 스폰할 엔티티 타입 결정
                Biome spawnBiome = world.getBiome(spawnLocation);
                String biomeConfigKey = spawnBiome.getKey().getKey().toLowerCase();
                EntityType typeToSpawn = determineEntityType(biomeConfigKey);

                // 엔티티 스폰 및 설정
                try {
                    LivingEntity spawnedEntity = (LivingEntity) world.spawnEntity(spawnLocation, typeToSpawn);
                    configureSpawnedMonster(spawnedEntity, extraHealth);
                    gameManager.addGameMonster(spawnedEntity);
                    spawnedCount++;
                } catch (Exception e) {
                    plugin.getLogger().severe("몹 스폰 오류 in MobSpawner (" + typeToSpawn + " at " + spawnLocation + "): " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 0L, this.spawnIntervalTicks);
    }

    /**
     * 현재 진행 중인 몹 스폰 작업을 중지합니다.
     */
    public void stopSpawning() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            spawnTask.cancel();
            spawnTask = null;
            plugin.getLogger().info("Mob spawning task stopped.");
        }
    }

    /**
     * 지정된 범위 내에서 랜덤하고 안전한 스폰 위치를 찾습니다.
     * @param world 스폰할 월드
     * @param minX 최소 X
     * @param maxX 최대 X
     * @param minZ 최소 Z
     * @param maxZ 최대 Z
     * @return 안전한 스폰 Location 객체 (못 찾으면 null)
     */
    private Location getRandomSpawnLocation(World world, int minX, int maxX, int minZ, int maxZ) {
        int actualMinX = Math.min(minX, maxX);
        int actualMaxX = Math.max(minX, maxX);
        int actualMinZ = Math.min(minZ, maxZ);
        int actualMaxZ = Math.max(minZ, maxZ);

        if (actualMaxX <= actualMinX || actualMaxZ <= actualMinZ) {
            plugin.getLogger().warning("Invalid spawn coordinate range provided to MobSpawner.");
            return null;
        }

        // 최대 10번 시도 (루프 경고는 무시해도 괜찮음)
        for (int i = 0; i < 10; i++) {
            double spawnX = random.nextDouble(actualMinX, actualMaxX + 1);
            double spawnZ = random.nextDouble(actualMinZ, actualMaxZ + 1);
            int blockX = (int) spawnX;
            int blockZ = (int) spawnZ;

            int safeY = world.getHighestBlockYAt(blockX, blockZ) + 1;
            Location potentialLocation = new Location(world, spawnX + 0.5, safeY, spawnZ + 0.5);

            return potentialLocation; // 첫 시도 위치 반환
        }
        return null; // 10번 시도 후 실패
    }


    /**
     * 주어진 바이옴 키에 따라 스폰할 엔티티 타입을 결정합니다.
     * @param biomeConfigKey 바이옴 설정 키 (소문자)
     * @return 스폰할 EntityType (기본값: ZOMBIE)
     */
    private EntityType determineEntityType(String biomeConfigKey) {
        Map<String, Double> biomeProbabilities = this.biomeSpawnProbabilities.get(biomeConfigKey);

        if (biomeProbabilities == null || biomeProbabilities.isEmpty()) {
            return EntityType.ZOMBIE;
        }

        double totalProbability = biomeProbabilities.values().stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        if (totalProbability <= 0) {
            plugin.getLogger().warning("MobSpawner: Zero or negative total probability for biome " + biomeConfigKey + ". Defaulting to ZOMBIE.");
            return EntityType.ZOMBIE;
        }
        double randomChance = random.nextDouble() * totalProbability;

        double cumulativeProbability = 0.0;
        for (Map.Entry<String, Double> entry : biomeProbabilities.entrySet()) {
            String entityTypeKey = entry.getKey();
            Double probability = entry.getValue();

            if (probability == null || probability <= 0) continue;

            cumulativeProbability += probability;
            if (randomChance <= cumulativeProbability) {
                try {
                    EntityType determinedType = EntityType.valueOf(entityTypeKey);
                    if (determinedType.getEntityClass() != null && LivingEntity.class.isAssignableFrom(determinedType.getEntityClass())) {
                        return determinedType;
                    } else {
                        plugin.getLogger().warning("MobSpawner: Config error - Entity type '" + entityTypeKey + "' in biome '" + biomeConfigKey + "' is not a LivingEntity. Defaulting to ZOMBIE.");
                        return EntityType.ZOMBIE;
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("MobSpawner: Config error - Invalid entity type name '" + entityTypeKey + "' in biome '" + biomeConfigKey + "'. Defaulting to ZOMBIE.");
                    return EntityType.ZOMBIE;
                }
            }
        }
        plugin.getLogger().warning("MobSpawner: Could not determine entity type for biome " + biomeConfigKey + ". Defaulting to ZOMBIE.");
        return EntityType.ZOMBIE;
    }

    /**
     * 스폰된 몹의 초기 설정을 적용합니다 (체력, 효과 등).
     * @param entity 설정할 LivingEntity 몹
     * @param extraHealth 라운드에 따른 추가 체력
     */
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
        } else {
            plugin.getLogger().warning("MobSpawner: Entity type " + entity.getType() + " does not have MAX_HEALTH attribute.");
        }
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
    }

    /**
     * 엔티티 타입, 기본 체력, 추가 체력을 기반으로 최종 체력을 계산합니다.
     * @param type 엔티티 타입
     * @param baseHealth 기본 최대 체력
     * @param extraHealth 라운드별 추가 체력
     * @return 계산된 최종 체력
     */
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
                calculatedHealth += (double) extraHealth / 4.0;
                break;
            default:
                break;
        }
        return Math.max(1.0, calculatedHealth);
    }
}