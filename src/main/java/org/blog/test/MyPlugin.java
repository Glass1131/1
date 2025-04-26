package org.blog.test;

// 필요한 import 문들
import Custom.CustomCommand;
import Custom.CustomItem;
import Custom.CustomItemRecipe;
import Biomes.Deep_dark;
import Biomes.Swamp;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import System.ThirstSystem;
import System.HeatSystem;
import java.util.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scoreboard.Scoreboard;


/**
 * 좀비 서바이벌 메인 플러그인 클래스.
 * 플러그인 활성화/비활성화, 설정 로드, 시스템 및 리스너 초기화를 담당합니다.
 */
public class MyPlugin extends JavaPlugin implements Listener {
    // --- 상수 정의 ---
    public static final String GAME_WORLD_NAME = "world";
    private static final int DEFAULT_MIN_X = -258;
    private static final int DEFAULT_MAX_X = -341;
    private static final int DEFAULT_MIN_Z = 80;
    private static final int DEFAULT_MAX_Z = 14;
    public static final String GAME_FORCED_STOPPED = "⚠ 게임이 강제 종료되었습니다! 남아있던 몹들이 사라졌습니다.";
    public static final String WORLD_NOT_FOUND_WARNING = "월드 '" + GAME_WORLD_NAME + "'를 찾을 수 없습니다! 몹을 소환할 수 없습니다.";

    // --- 시스템 및 매니저 인스턴스 ---
    private GameManager gameManager;
    private GameScoreboard gameScoreboard;
    private ThirstSystem thirstSystem;
    private HeatSystem heatSystem;

    // --- 설정 값 (다른 클래스에서 접근 필요 시 getter 제공) ---
    private final Map<String, List<Integer>> configBiomeSpawnCoords = new HashMap<>();
    private final Map<String, Map<String, Double>> configBiomeSpawnProbabilities = new HashMap<>();
    // 현재 스폰 좌표 (BiomeNotifier 에서 설정, MobSpawner 에서 사용)
    public int minX = DEFAULT_MIN_X, maxX = DEFAULT_MAX_X, minZ = DEFAULT_MIN_Z, maxZ = DEFAULT_MAX_Z;
    // 액션바 업데이트 간격
    long configActionBarUpdateIntervalTicks;

    // *** 액션바 색상 상수 추가 ***
    private static final NamedTextColor THIRST_COLOR_100 = NamedTextColor.BLACK;
    private static final NamedTextColor THIRST_90_PLUS = NamedTextColor.DARK_RED;
    private static final NamedTextColor THIRST_COLOR_80_PLUS = NamedTextColor.RED;
    private static final NamedTextColor THIRST_COLOR_50_PLUS = NamedTextColor.GOLD;
    private static final NamedTextColor THIRST_COLOR_20_PLUS = NamedTextColor.YELLOW;
    private static final NamedTextColor THIRST_COLOR_BELOW_20 = NamedTextColor.GREEN;
    private static final NamedTextColor VERY_COLD_COLOR = NamedTextColor.BLUE;
    private static final NamedTextColor COLD_COLOR = NamedTextColor.AQUA;
    private static final NamedTextColor NORMAL_TEMPERATURE_COLOR = NamedTextColor.GRAY;
    private static final NamedTextColor HOT_COLOR = NamedTextColor.GOLD;
    private static final NamedTextColor VERY_HOT_COLOR = NamedTextColor.RED;


    @Override
    public void onEnable() {
        getLogger().info("✅ 플러그인이 활성화되었습니다!");
        saveDefaultConfig();
        loadConfigValues();
        initializeSystems();
        registerListeners();
        CustomItem.initializeItems();
        new CustomItemRecipe(this).registerRecipes();
        registerCommands();
        startActionBarScheduler(this.configActionBarUpdateIntervalTicks);
    }

    /**
     * config.yml 에서 설정값들을 로드합니다.
     */
    private void loadConfigValues() {
        this.configActionBarUpdateIntervalTicks = getConfig().getLong("intervals.actionbar-update-ticks", 20L);

        // 바이옴별 설정 로드
        configBiomeSpawnCoords.clear();
        configBiomeSpawnProbabilities.clear();
        ConfigurationSection biomesSection = getConfig().getConfigurationSection("biomes");
        if (biomesSection != null) {
            for (String biomeKey : biomesSection.getKeys(false)) {
                List<Integer> coords = biomesSection.getIntegerList(biomeKey + ".spawn-coords");
                if (coords.size() == 4) {
                    this.configBiomeSpawnCoords.put(biomeKey.toLowerCase(), coords);
                } else {
                    getLogger().warning("config.yml: Biome '" + biomeKey + "' has invalid or missing spawn-coords. Skipping.");
                }
                ConfigurationSection probSection = biomesSection.getConfigurationSection(biomeKey + ".spawn-probabilities");
                if (probSection != null) {
                    Map<String, Double> probabilities = new HashMap<>();
                    for(String entityTypeKey : probSection.getKeys(false)) {
                        if (probSection.isDouble(entityTypeKey)) {
                            probabilities.put(entityTypeKey.toUpperCase(), probSection.getDouble(entityTypeKey));
                        } else {
                            getLogger().warning("config.yml: Biome '" + biomeKey + "' spawn-probabilities for '" + entityTypeKey + "' is not a double. Skipping.");
                        }
                    }
                    if (!probabilities.isEmpty()) {
                        this.configBiomeSpawnProbabilities.put(biomeKey.toLowerCase(), probabilities);
                    }
                }
            }
        } else {
            getLogger().warning("config.yml: 'biomes' section is missing. Biome-specific settings will not be loaded.");
        }

        // 기본 스폰 좌표 설정
        List<Integer> defaultCoords = configBiomeSpawnCoords.get("default");
        if (defaultCoords != null && defaultCoords.size() == 4) {
            this.minX = defaultCoords.get(0);
            this.maxX = defaultCoords.get(1);
            this.minZ = defaultCoords.get(2);
            this.maxZ = defaultCoords.get(3);
        } else {
            getLogger().warning("config.yml: Default biome spawn-coords missing or invalid. Using hardcoded defaults.");
            this.minX = DEFAULT_MIN_X;
            this.maxX = DEFAULT_MAX_X;
            this.minZ = DEFAULT_MIN_Z;
            this.maxZ = DEFAULT_MAX_Z;
        }
        getLogger().info("Initial Spawn Coords set: [" + this.minX + ", " + this.maxX + ", " + this.minZ + ", " + this.maxZ + "]");
        getLogger().info("Actionbar Interval: " + this.configActionBarUpdateIntervalTicks + " ticks");
        getLogger().info("Loaded Biome Coords Keys: " + this.configBiomeSpawnCoords.keySet());
        getLogger().info("Loaded Biome Probabilities Keys: " + this.configBiomeSpawnProbabilities.keySet());
    }

    /**
     * 게임 시스템 및 매니저들을 초기화합니다.
     */
    private void initializeSystems() {
        gameScoreboard = new GameScoreboard();
        gameManager = new GameManager(this, gameScoreboard);
        thirstSystem = new ThirstSystem(this);
        heatSystem = new HeatSystem(this);
        getLogger().info("💧 갈증, 🔥 온도, 🎮 게임 관리 시스템 초기화 완료!");
    }

    /**
     * 필요한 이벤트 리스너들을 등록합니다.
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ZombieDropListener(), this);
        getServer().getPluginManager().registerEvents(this, this); // MyPlugin 자체 리스너 (onEntityDeath 등)
        getServer().getPluginManager().registerEvents(new Swamp(), this);
        getServer().getPluginManager().registerEvents(new Weaponability(), this);
        getServer().getPluginManager().registerEvents(new BiomeNotifier(this), this);
        getServer().getPluginManager().registerEvents(new Deep_dark(this), this);
        getLogger().info("이벤트 리스너 등록 완료!");
    }

    /**
     * 플러그인 명령어 핸들러를 등록합니다.
     */
    private void registerCommands() {
        CustomCommand customCommandHandler = new CustomCommand(this, gameManager); // GameManager 전달
        Objects.requireNonNull(getCommand("get-item")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("get-item")).setTabCompleter(customCommandHandler);
        Objects.requireNonNull(getCommand("게임시작")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("게임취소")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("round")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("round")).setTabCompleter(customCommandHandler);
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ 플러그인이 비활성화됩니다!");
        if (gameManager != null && gameManager.isGameInProgress()) {
            gameManager.stopGame();
        }
        Bukkit.getScheduler().cancelTasks(this);

        Scoreboard emptyScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (gameScoreboard != null && p.getScoreboard().equals(gameScoreboard.getBoard())) {
                p.setScoreboard(emptyScoreboard);
            }
        }
        getLogger().info("플러그인 비활성화 완료.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        discoverRecipes(player);
    }

    private void discoverRecipes(Player player) {
        player.discoverRecipe(new NamespacedKey(this, "CALIBRATED_SCULK_SENSOR"));
        player.discoverRecipe(new NamespacedKey(this, "DARK_WEAPON"));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (gameManager != null && gameManager.getGameMonsters().contains(entity)) {
            gameManager.removeGameMonster(entity);
            handleSlimeSplit(entity);
        }
    }

    private void handleSlimeSplit(LivingEntity deadEntity) {
        if (!(deadEntity instanceof Slime dyingSlime)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                World world = dyingSlime.getWorld();
                if (!world.isChunkLoaded(dyingSlime.getLocation().getChunk())) {
                    return;
                }
                int dyingSlimeSize = dyingSlime.getSize();
                for (Entity nearbyEntity : dyingSlime.getNearbyEntities(2, 1, 2)) {
                    if (nearbyEntity instanceof Slime smallerSlime && !nearbyEntity.isDead()) {
                        if (gameManager != null && smallerSlime.getSize() < dyingSlimeSize && !gameManager.getGameMonsters().contains(smallerSlime)) {
                            gameManager.addGameMonster(smallerSlime);
                            getLogger().fine("Added smaller Slime (Size: " + smallerSlime.getSize() + ") via GameManager.");
                        }
                    }
                }
            }
        }.runTaskLater(this, 1L);
    }

    // --- 액션바 관련 ---
    private void startActionBarScheduler(long intervalTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        player.sendActionBar(Component.empty());
                        continue;
                    }
                    player.sendActionBar(createActionBarComponent(player));
                }
            }
        }.runTaskTimer(this, 0L, intervalTicks);
    }

    private Component createActionBarComponent(Player player) {
        int thirstLevel = (thirstSystem != null) ? thirstSystem.getThirstLevel(player) : 0;
        NamedTextColor thirstColor = getThirstColor(thirstLevel); // 이제 정상적으로 상수 사용 가능
        Component thirstComponent = Component.text("💧 갈증: " + thirstLevel + "%", thirstColor);

        String temperatureState = (heatSystem != null) ? heatSystem.getTemperatureState(player) : "N/A";
        NamedTextColor temperatureColor = getTemperatureColorFromStateString(temperatureState); // 이제 정상적으로 상수 사용 가능
        Component temperatureComponent = Component.text("🌡 " + temperatureState, temperatureColor);

        Location playerLoc = player.getLocation();
        Biome currentBiome = playerLoc.getWorld().getBiome(playerLoc);
        String formattedBiome = formatBiomeName(currentBiome);
        NamedTextColor biomeColor = getBiomeColor(currentBiome);
        Component biomeComponent = Component.text("🌳 " + formattedBiome, biomeColor);

        return thirstComponent
                .append(Component.text(" | ", NamedTextColor.WHITE))
                .append(temperatureComponent)
                .append(Component.text(" | ", NamedTextColor.WHITE))
                .append(biomeComponent);
    }

    /**
     * 갈증 레벨에 따라 적절한 색상을 반환합니다.
     * @param thirstLevel 갈증 레벨 (0-100)
     * @return NamedTextColor
     */
    public NamedTextColor getThirstColor(int thirstLevel) {
        if (thirstLevel == 100) return THIRST_COLOR_100;
        if (thirstLevel >= 90) return THIRST_90_PLUS;
        if (thirstLevel >= 80) return THIRST_COLOR_80_PLUS;
        if (thirstLevel >= 50) return THIRST_COLOR_50_PLUS;
        if (thirstLevel >= 20) return THIRST_COLOR_20_PLUS;
        return THIRST_COLOR_BELOW_20;
    }

    /**
     * 온도 상태 문자열에 따라 적절한 색상을 반환합니다.
     * @param temperatureState 온도 상태 문자열 (예: "매우 추움!!")
     * @return NamedTextColor
     */
    private NamedTextColor getTemperatureColorFromStateString(String temperatureState) {
        if (temperatureState == null) return NamedTextColor.GRAY;
        String cleanState = temperatureState.replace("!", "");
        return switch (cleanState) {
            case "매우 추움" -> VERY_COLD_COLOR;
            case "추움" -> COLD_COLOR;
            case "정상" -> NORMAL_TEMPERATURE_COLOR;
            case "더움" -> HOT_COLOR;
            case "매우 더움" -> VERY_HOT_COLOR;
            default -> NamedTextColor.GRAY;
        };
    }

    /**
     * Biome enum 이름을 보기 좋은 형식의 문자열로 변환합니다.
     * @param biome 변환할 Biome
     * @return 형식화된 바이옴 이름 문자열
     */
    public String formatBiomeName(Biome biome) {
        if (biome == null) return "Unknown Biome";
        String name = biome.getKey().getKey();
        String[] parts = name.split("_");
        StringBuilder formattedName = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                formattedName.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return formattedName.toString().trim();
    }

    /**
     * 특정 바이옴에 대한 색상을 반환합니다 (액션바 표시용).
     * @param biome 바이옴
     * @return NamedTextColor
     */
    private NamedTextColor getBiomeColor(Biome biome) {
        if (biome == null) return NamedTextColor.GRAY;
        return switch (biome.getKey().getKey()) {
            case "deep_dark" -> NamedTextColor.DARK_GRAY;
            case "desert" -> NamedTextColor.YELLOW;
            case "swamp" -> NamedTextColor.DARK_GREEN;
            case "plains" -> NamedTextColor.GREEN;
            default -> NamedTextColor.WHITE;
        };
    }

    /**
     * config.yml 에서 로드한 바이옴별 스폰 좌표 맵을 반환합니다.
     * @return 바이옴 키와 좌표 리스트 맵
     */
    public Map<String, List<Integer>> getConfigBiomeSpawnCoords() {
        return new HashMap<>(this.configBiomeSpawnCoords); // 방어적 복사본 반환
    }

    /**
     * config.yml 에서 로드한 바이옴별 스폰 확률 맵을 반환합니다.
     * @return 바이옴 키와 (엔티티 타입 키, 확률) 맵
     */
    public Map<String, Map<String, Double>> getConfigBiomeSpawnProbabilities() {
        return new HashMap<>(this.configBiomeSpawnProbabilities); // 방어적 복사본 반환
    }

    /**
     * 몹 스폰 좌표 범위를 변경합니다 (BiomeNotifier 에서 호출).
     * @param minX 최소 X 좌표
     * @param maxX 최대 X 좌표
     * @param minZ 최소 Z 좌표
     * @param maxZ 최대 Z 좌표
     */
    public void setZombieSpawnCoordinates(int minX, int maxX, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }
}