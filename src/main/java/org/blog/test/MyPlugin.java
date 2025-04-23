package org.blog.test;

import Custom.CustomCommand;
import Custom.CustomItem;
import Custom.CustomItemRecipe;
import Biomes.Deep_dark;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
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
import org.bukkit.entity.Creature; // Creature import 추가

import java.util.*;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityDeathEvent; // EntityDeathEvent를 추가합니다.

/**
 * Since 2025 ~
 * 제작자: Glass1131, Gemini, GPT
 * 목적: ???
 */

public class MyPlugin extends JavaPlugin implements Listener { // TabCompleter 제거
    private static final long ROUND_END_DELAY_TICKS = 40L;
    private static final int DEFAULT_MIN_X = -258;
    private static final int DEFAULT_MAX_X = -341;
    private static final int DEFAULT_MIN_Z = 80;
    private static final int DEFAULT_MAX_Z = 14;
    public static final String GAME_ALREADY_IN_PROGRESS = "게임이 이미 진행 중입니다.";
    public static final String NO_GAME_IN_PROGRESS = "진행 중인 게임이 없습니다.";
    public static final String GAME_FORCED_STOPPED = "⚠ 게임이 강제 종료되었습니다! 남아있던 좀비들이 사라졌습니다.";
    public static final String WORLD_NOT_FOUND_WARNING = "월드 'world'를 찾을 수 없습니다! 좀비를 소환할 수 없습니다.";



    // CustomCommand 에서 접근할 수 있도록 public 으로 변경 (또는 getter 추가)
    public boolean gameInProgress = false;
    public int currentRound = 1;
    // CustomCommand 에서 스케줄러 owner로 사용되므로 MyPlugin 인스턴스 참조는 유지
    private final JavaPlugin plugin = this;
    // CustomCommand 에서 접근할 수 있도록 public 으로 변경 (또는 getter 추가)
    public GameScoreboard gameScoreboard;
    private ThirstSystem thirstSystem;
    private HeatSystem heatSystem;
    private final Random random = new Random();

    // 게임에 의해 소환된 몬스터들을 관리하는 컬렉션 추가
    private final Set<LivingEntity> gameMonsters = new HashSet<>();

    // 바이옴별 설정 (스폰 좌표 및 확률)
// 좌표는 [minX, maxX, minZ, maxZ] 형태의 List<Integer>로 저장
// 확률은 EntityType의 String 이름과 확률(Double)의 Map 으로 저장
// Map의 키는 config.yml에 정의된 바이옴 키 (예: "default", "deep_dark", "desert", "swamp")
    private final Map<String, List<Integer>> configBiomeSpawnCoords = new HashMap<>(); // final로 선언
    private final Map<String, Map<String, Double>> configBiomeSpawnProbabilities = new HashMap<>(); // final로 선언



    // 갈증 레벨별 색상 상수 (startActionBarScheduler 에서 사용)
    private static final NamedTextColor THIRST_COLOR_100 = NamedTextColor.BLACK;
    private static final NamedTextColor THIRST_90_PLUS = NamedTextColor.DARK_RED;
    private static final NamedTextColor THIRST_COLOR_80_PLUS = NamedTextColor.RED;
    private static final NamedTextColor THIRST_COLOR_50_PLUS = NamedTextColor.GOLD;
    private static final NamedTextColor THIRST_COLOR_20_PLUS = NamedTextColor.YELLOW;
    private static final NamedTextColor THIRST_COLOR_BELOW_20 = NamedTextColor.GREEN;

    // 온도 상태별 색상 상수 (이거 없어도 색깔 적용됐는데 찾아줘봐 없으면 말고)
    private static final NamedTextColor VERY_COLD_COLOR = NamedTextColor.BLUE;
    private static final NamedTextColor COLD_COLOR = NamedTextColor.AQUA;
    private static final NamedTextColor NORMAL_TEMPERATURE_COLOR = NamedTextColor.GRAY;
    private static final NamedTextColor HOT_COLOR = NamedTextColor.GOLD;
    private static final NamedTextColor VERY_HOT_COLOR = NamedTextColor.RED;

    @Override
    public void onEnable() {
        getLogger().info("✅ 플러그인이 활성화되었습니다!");

        // config.yml 파일 로드 및 기본값 저장 (플러그인 리소스에서 복사)
        saveDefaultConfig();

        // 라운드 설정
        int configPreparationTimeSeconds = getConfig().getInt("round.preparation-time-seconds", 30);
        long configRoundEndDelayTicks = getConfig().getLong("round.end-delay-ticks", 40L);

        // 스폰 설정 로드
        // 스폰 설정
        int configZombiesPerRound = getConfig().getInt("spawn.zombies-per-round", 20);
        long configSpawnIntervalTicks = getConfig().getLong("spawn.interval-ticks", 10L);
        int configHealthIncreasePer10Rounds = getConfig().getInt("spawn.health-increase.per-10-rounds", 3);
        int configHealthIncreaseEveryXRounds = getConfig().getInt("spawn.health-increase.every-x-rounds", 10);

        // 스케줄러 간격 설정 로드
        // 스케줄러 간격 설정
        long configZombieCountUpdateIntervalTicks = getConfig().getLong("intervals.zombie-count-update-ticks", 20L);
        long configZombieChaseIntervalTicks = getConfig().getLong("intervals.zombie-chase-ticks", 200L); // 사용자 기존 값 반영
        long configBiomeCheckIntervalTicks = getConfig().getLong("intervals.biome-check-ticks", 5L);
        long configActionBarUpdateIntervalTicks = getConfig().getLong("intervals.actionbar-update-ticks", 20L);

        // 바이옴별 설정 로드 (스폰 좌표 및 확률)
        // config.yml 에서 'biomes' 섹션을 가져옴
        if (getConfig().isConfigurationSection("biomes")) {
            ConfigurationSection biomesSection = getConfig().getConfigurationSection("biomes");
            if (biomesSection != null) {
                // 'biomes' 섹션 아래의 각 바이옴 키(예: "default", "deep_dark", "desert", "swamp")를 순회
                for (String biomeKey : biomesSection.getKeys(false)) {
                    // 해당 바이옴의 스폰 좌표 로드 ([minX, maxX, minZ, maxZ] 형태의 리스트)
                    List<Integer> coords = biomesSection.getIntegerList(biomeKey + ".spawn-coords");
                    if (coords.size() == 4) {
                        configBiomeSpawnCoords.put(biomeKey, coords);
                    } else {
                        getLogger().warning("config.yml: Biome '" + biomeKey + "' has invalid or missing spawn-coords. Skipping.");
                    }

                    // 해당 바이옴의 스폰 확률 로드 (EntityType 문자열과 확률(double)의 맵)
                    ConfigurationSection probSection = biomesSection.getConfigurationSection(biomeKey + ".spawn-probabilities");
                    if (probSection != null) {
                        Map<String, Double> probabilities = new HashMap<>();
                        for(String entityTypeKey : probSection.getKeys(false)) {
                            // 값이 double 인지 확인 (확률은 double로 설정)
                            if (probSection.isDouble(entityTypeKey)) {
                                // EntityType 이름은 대문자로 저장하여 나중에 EntityType.valueOf와 일치하도록 함
                                probabilities.put(entityTypeKey.toUpperCase(), probSection.getDouble(entityTypeKey));
                            } else {
                                getLogger().warning("config.yml: Biome '" + biomeKey + "' spawn-probabilities for '" + entityTypeKey + "' is not a double. Skipping.");
                            }
                        }
                        configBiomeSpawnProbabilities.put(biomeKey, probabilities);
                    }
                }
            }
        } else {
            getLogger().warning("config.yml: 'biomes' section is missing. Biome-specific settings will not be loaded.");
        }

        // config 에서 로드한 기본 스폰 좌표("default")로 minX, maxX, minZ, maxZ 필드 초기화
        List<Integer> defaultCoords = configBiomeSpawnCoords.get("default");
        if (defaultCoords != null && defaultCoords.size() == 4) { // default 좌표 설정이 있는지 확인
            this.minX = defaultCoords.get(0);
            this.maxX = defaultCoords.get(1);
            this.minZ = defaultCoords.get(2);
            this.maxZ = defaultCoords.get(3);
        } else {
            // config에 default 좌표가 없거나 형식이 잘못된 경우 경고 로깅 및 기존 하드코딩 값으로 초기화
            getLogger().warning("config.yml: Default biome spawn-coords missing or invalid. Using hardcoded defaults.");
            this.minX = DEFAULT_MIN_X; // 기존 하드코딩 DEFAULT_MIN_X 값 사용
            this.maxX = DEFAULT_MAX_X; // 기존 하드코딩 DEFAULT_MAX_X 값 사용
            this.minZ = DEFAULT_MIN_Z;  // 기존 하드코딩 DEFAULT_MIN_Z 값 사용
            this.maxZ = DEFAULT_MAX_Z;  // 기존 하드코딩 DEFAULT_MAX_Z 값 사용
        }

        // 콘솔에 로드된 값 확인 (디버깅에 유용)
        getLogger().info("--- Config Loaded ---");
        getLogger().info("Round Preparation Time: " + configPreparationTimeSeconds + "s");
        getLogger().info("Round End Delay: " + configRoundEndDelayTicks + " ticks");
        getLogger().info("Mobs Per Round: " + configZombiesPerRound);
        getLogger().info("Spawn Interval: " + configSpawnIntervalTicks + " ticks");
        getLogger().info("Health Increase (per " + configHealthIncreaseEveryXRounds + " rounds): +" + configHealthIncreasePer10Rounds);
        getLogger().info("Update Interval (Mob Count): " + configZombieCountUpdateIntervalTicks + " ticks");
        getLogger().info("Update Interval (Chase): " + configZombieChaseIntervalTicks + " ticks");
        getLogger().info("Update Interval (Biome Check): " + configBiomeCheckIntervalTicks + " ticks");
        getLogger().info("Update Interval (Actionbar): " + configActionBarUpdateIntervalTicks + " ticks");
        getLogger().info("Loaded Biome Spawn Coords for: " + configBiomeSpawnCoords.keySet());
        getLogger().info("Loaded Biome Spawn Probabilities for: " + configBiomeSpawnProbabilities.keySet());
        getLogger().info("Initial Spawn Coords: [" + minX + ", " + maxX + ", " + minZ + ", " + maxZ + "]");
        getLogger().info("---------------------");


        // ... (나머지 onEnable 코드) ...
        // 이벤트 리스너 등록, 시스템 초기화 등
        // 이 부분에서 BiomeNotifier 시작 시 configBiomeCheckIntervalTicks 사용하도록 수정 예정 (다음 단계)
        // 액션바 스케줄러 시작 시 configActionBarUpdateIntervalTicks 사용하도록 수정 예정 (다음 단계)

        // ZombieDropListener 인스턴스 생성 시 MyPlugin 인스턴스(this) 전달
        getServer().getPluginManager().registerEvents(new ZombieDropListener(), this);
        getServer().getPluginManager().registerEvents(this, this); // MyPlugin 에서 EntityDeathEvent를 받기 위해 리스너 등록

        // 🔹 커스텀 아이템 초기화
        CustomItem.initializeItems();

        // 🔹 커스텀 명령어 핸들러 초기화 및 등록 (모든 명령어 처리)
        // CustomCommand 인스턴스 생성 시 MyPlugin 인스턴스(this) 전달
        CustomCommand customCommandHandler = new CustomCommand(this);

        // 모든 명령어를 customCommandHandler 인스턴스에 등록
        Objects.requireNonNull(this.getCommand("get-item")).setExecutor(customCommandHandler);
        Objects.requireNonNull(this.getCommand("get-item")).setTabCompleter(customCommandHandler);
        Objects.requireNonNull(getCommand("게임시작")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("게임취소")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("round")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("round")).setTabCompleter(customCommandHandler);


        getServer().getPluginManager().registerEvents(new Biomes.Swamp(), this);

        getServer().getPluginManager().registerEvents(new Weaponability(), this);
        // 바이옴 감지 스케줄러 시작 - config 값 사용
        // BiomeNotifier 인스턴스 생성 시 config 값 전달
        new BiomeNotifier(this).runTaskTimer(this, 0L, getConfig().getLong("intervals.biome-check-ticks", 5L)); // config 값 사용

        // 커스텀 아이템 레시피 등록
        new CustomItemRecipe(this).registerRecipes();
        // 딥 다크 관련 초기화 (이벤트 리스너 등)
        new Deep_dark(this);
        // 게임 스코어보드 초기화 및 적용
        gameScoreboard = new GameScoreboard();
        gameScoreboard.applyToAllPlayers();

        // 갈증 및 온도 시스템 초기화
        this.thirstSystem = new ThirstSystem(this);
        this.heatSystem = new HeatSystem(this);
        getLogger().info("💧 갈증 및 🔥 온도 시스템 초기화 완료!");

        // 플레이어 접속 시 레시피 발견 처리를 위한 이벤트 리스너는 onPlayerJoin 메서드에 있음
        // 액션바 스케줄러 시작
        startActionBarScheduler(getConfig().getLong("intervals.actionbar-update-ticks", 20L)); // config 값 사용
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof Slime dyingSlime) {

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!dyingSlime.getWorld().isChunkLoaded(dyingSlime.getLocation().getChunk())) {
                        // 딜레이 중에 청크 언로드 시 처리 중단
                        return;
                    }
                    int dyingSlimeSize = dyingSlime.getSize();
                    for (Entity nearbyEntity : dyingSlime.getNearbyEntities(2, 1, 2)) { // 죽은 슬라임 주변 2블록 범위 탐색
                        if (nearbyEntity instanceof Slime smallerSlime) {

                            if (smallerSlime.getSize() < dyingSlimeSize && !gameMonsters.contains(smallerSlime)) {
                                gameMonsters.add(smallerSlime);
                                smallerSlime.setRemoveWhenFarAway(false);
                                smallerSlime.setPersistent(true);
                                getLogger().fine("Added smaller Slime (" + smallerSlime.getSize() + ") to gameMonsters.");
                            }
                        }
                    }
                }
                // 슬라임 분열 후 작은 슬라임들이 생성되는 데 걸리는 약간의 시간만큼 딜레이 (예: 1틱)
            }.runTaskLater(plugin, 1L); // 1틱 딜레이 후 실행
        }
    }

    public NamedTextColor getThirstColor(int thirstLevel) {
        if (thirstLevel == 100) {
            return THIRST_COLOR_100; // 100%는 검은색
        } else if (thirstLevel >= 90) {
            return THIRST_90_PLUS; // 90% 이상은 다크 레드
        } else if (thirstLevel >= 80) {
            return THIRST_COLOR_80_PLUS; // 80% 이상은 빨간색
        } else if (thirstLevel >= 50) {
            return THIRST_COLOR_50_PLUS; // 50% 이상 80% 미만은 금색
        } else if (thirstLevel >= 20) {
            return THIRST_COLOR_20_PLUS; // 20% 이상 50% 미만은 노란색
        } else {
            return THIRST_COLOR_BELOW_20; // 20% 미만은 초록색
        }
    }

    // 온도 상태 문자열에 따른 색상 반환
    private NamedTextColor getTemperatureColorFromStateString(String temperatureState) {
        if (temperatureState == null) {
            return NamedTextColor.GRAY; // 기본 색상
        }
        // '!'를 제거하고 상태 문자열만 비교
        String cleanState = temperatureState.replace("!", "");

        return switch (cleanState) {
            case "매우 추움" -> VERY_COLD_COLOR;
            case "추움" -> COLD_COLOR;
            case "정상" -> NORMAL_TEMPERATURE_COLOR;
            case "더움" -> HOT_COLOR;
            case "매우 더움" -> VERY_HOT_COLOR;
            default -> NamedTextColor.GRAY; // 기본 색상
        };
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ 모든 플러그인이 비활성화되었습니다!");
        // 플러그인 비활성화 시 모든 게임 몬스터 제거
        removeGameEntities();
        // 모든 스케줄러 작업 취소
        Bukkit.getScheduler().cancelTasks(this);
        // 스코어보드 초기화 (선택 사항)
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 특정 레시피 자동으로 발견 처리
        player.discoverRecipe(new NamespacedKey(plugin, "CALIBRATED_SCULK_SENSOR"));
        player.discoverRecipe(new NamespacedKey(plugin, "DARK_WEAPON"));
        // 플레이어 접속 시 스코어보드 적용
        if (gameScoreboard != null) {
            gameScoreboard.applyToAllPlayers(); // 모든 플레이어에게 적용하는 메서드 재사용
        }
    }

    // startGame 메서드는 CustomCommand 에서 호출될 수 있도록 유지
    public void startGame() {
        if (gameInProgress) return; // 이미 게임 중이면 시작하지 않음
        gameInProgress = true;
        gameMonsters.clear(); // 새 게임 시작 시 기존 게임 몬스터 목록 초기화
        gameScoreboard.applyToAllPlayers();
        startPreparationPhase();
    }

    // stopGame 메서드는 CustomCommand 에서 호출될 수 있도록 유지
    public void stopGame() {
        if (!gameInProgress) return; // 게임 중이 아니면 중지할 필요 없음
        gameInProgress = false;
        currentRound = 1; // 라운드 초기화

        // 모든 게임 몬스터 삭제 - 성능 최적화 (엔티티 목록 관리)
        removeGameEntities();
        // 게임 관련 반복 작업 취소 (BiomeNotifier, ZombieCount, ZombieChase, Prepare 타이머 등)
        Bukkit.getScheduler().cancelTasks(this); // MyPlugin의 모든 스케줄러 작업 취소

        // 스코어보드 초기화 (플레이어들에게 새로운 빈 스코어보드 적용)
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            // 플레이어에게 적용된 모든 효과 제거
            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
        }

        // BiomeNotifier 리셋 및 재시작 (새로운 스케줄러 인스턴스 생성)
        // 기존 스케줄러는 cancelTasks(this)로 취소되었으므로 새로운 인스턴스 생성 및 실행
        new BiomeNotifier(this).runTaskTimer(this, 0L, getConfig().getLong("intervals.biome-check-ticks", 5L));

        // 갈증 시스템과 온도 시스템 재시작 (새로운 인스턴스 생성)
        restartThirstAndHeatSystems();

        // 액션바 스케줄러 재시작 (새로운 스케줄러 인스턴스 생성)
        startActionBarScheduler(getConfig().getLong("intervals.actionbar-update-ticks", 20L));

        Bukkit.broadcast(Component.text(GAME_FORCED_STOPPED).color(NamedTextColor.RED));
    }

    private void endRound() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // 라운드 종료 시점에 gameInProgress 상태를 다시 확인 (stopGame 으로 취소되었을 경우 대비)
            if (!gameInProgress) {
                getLogger().info("Game was stopped during round end delay.");
                return;
            }
            Bukkit.broadcast(Component.text("라운드 " + currentRound + " 종료! 다음 라운드를 준비하세요.").color(NamedTextColor.RED));
            currentRound++;
            startPreparationPhase();
        }, ROUND_END_DELAY_TICKS);
    }

    // 액션바 스케줄러 (갈증, 온도, 바이옴 상태 표시)
    // 스케줄러 간격 값을 파라미터로 받도록 수정
    private void startActionBarScheduler(long intervalTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 게임이 진행 중이 아니거나 플레이어가 없으면 스케줄러 중지 (선택 사항: 성능 개선)
                if (!gameInProgress && Bukkit.getOnlinePlayers().isEmpty()) {
                    cancel();
                    getLogger().info("Actionbar scheduler stopped due to no active game or players.");
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 관전 모드 플레이어는 액션바 업데이트를 건너뜊니다.
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        player.sendActionBar(Component.empty()); // 관전자에게는 빈 액션바 표시
                        continue;
                    }

                    Location playerLoc = player.getLocation();

                    int thirstLevel = thirstSystem != null ? thirstSystem.getThirstLevel(player) : 0; // thirstSystem이 null일 경우 처리
                    String temperature = heatSystem != null ? heatSystem.getTemperatureState(player) : "N/A"; // heatSystem이 null일 경우 처리

                    // 온도 상태 문자열에 따른 색상 가져오기
                    NamedTextColor temperatureColor = getTemperatureColorFromStateString(temperature);

                    // 현재 바이옴 정보 가져오기
                    Biome currentBiome = playerLoc.getBlock().getBiome();
                    String formattedBiome = formatBiomeName(currentBiome);

                    NamedTextColor biomeColor = getBiomeColor(currentBiome);

                    NamedTextColor thirstColor = getThirstColor(thirstLevel);

                    player.sendActionBar(
                            Component.text("💧 갈증: " + thirstLevel + "%", thirstColor)
                                    .append(Component.text(" | ", NamedTextColor.WHITE))
                                    .append(Component.text("🌡 " + temperature, temperatureColor))
                                    .append(Component.text(" | ", NamedTextColor.WHITE))
                                    .append(Component.text("🌳 " + formattedBiome, biomeColor))
                    );
                }
            }
        }.runTaskTimer(this, 0L, intervalTicks);
    }

    // Biome Enum 이름을 보기 좋게 형식화하는 헬퍼 메서드
    public String formatBiomeName(Biome biome) {
        if (biome == null) return "Unknown Biome";
        String keyString = biome.getKey().asString();
        String name = keyString.contains(":") ? keyString.split(":")[1] : keyString;
        name = name.toLowerCase().replace("_", " "); // 소문자로 바꾸고 밑줄을 공백으로

        StringBuilder formattedName = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                formattedName.append(c);
            } else if (capitalizeNext) {
                formattedName.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formattedName.append(c);
            }
        }
        return formattedName.toString();
    }

    private NamedTextColor getBiomeColor(Biome biome) {
        if (biome == null) {
            return NamedTextColor.GRAY;
        } else if (biome == Biome.DEEP_DARK) {
            return NamedTextColor.DARK_GRAY;
        } else if (biome == Biome.DESERT) {
            return NamedTextColor.YELLOW;
        } else if (biome == Biome.SWAMP) {
            return NamedTextColor.DARK_GREEN;
        } else if (biome == Biome.PLAINS){
            return  NamedTextColor.GREEN;
        } else {
            return NamedTextColor.WHITE;
        }
    } //switch 문으로 바꾸면 ㅈㄹ남 ㅅㅂ -> switch 문으로 변경 가능하며, 오히려 더 깔끔해질 수 있습니다. 이 부분은 나중에 코드 구조 개선 단계에서 다룰 수 있습니다.

    private void restartThirstAndHeatSystems() {
        // 기존 스케줄러가 cancelTasks(this)로 취소되었으므로 새로운 인스턴스 생성 및 등록
        this.thirstSystem = new ThirstSystem(this);
        this.heatSystem = new HeatSystem(this);
    }

    private void startPreparationPhase() {
        new BukkitRunnable() {
            int timeLeft = getConfig().getInt("round.preparation-time-seconds", 30); // config 값 사용

            @Override
            public void run() {
                if (!gameInProgress) { // 게임이 중단되었으면 타이머 취소
                    cancel();
                    return;
                }
                if (timeLeft >= 1) {
                    gameScoreboard.updateScore("준비 시간", timeLeft);
                    timeLeft--;
                } else {
                    cancel(); // 타이머 작업 취소
                    startGameRound(); // 준비 시간 종료 후 게임 라운드 시작
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 타이머는 20틱/초 기준으로 작동
    }

    //startGameRound 메서드는 CustomCommand 에서 호출될 수 있도록 public 으로 변경
    public void startGameRound() {
        if (!gameInProgress) return; // 게임 중이 아니면 라운드 시작하지 않음

        // 라운드 스코어보드 업데이트는 startGame 또는 round 명령어 처리 시 먼저 이루어질 수 있으나,
        // 라운드 시작 시점에 다시 한번 확실하게 업데이트
        if (gameScoreboard != null) {
            gameScoreboard.updateScore("라운드", currentRound);
            gameScoreboard.updateScore("준비 시간", 0); // 준비 시간 0으로 초기화
        } else {
            getLogger().warning("gameScoreboard is null at the start of startGameRound.");
        }

        Bukkit.broadcast(Component.text("게임 시작! 라운드 " + currentRound).color(NamedTextColor.GREEN));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.text("게임 시작!").color(NamedTextColor.GREEN));
        }

        // 라운드에 따라 추가 체력 계산 (10 라운드마다 체력 3 증가)
        int extraHealth = (currentRound / getConfig().getInt("spawn.health-increase.every-x-rounds", 10)) * getConfig().getInt("spawn.health-increase.per-10-rounds", 3); // config 값 사용

        // 게임 몬스터 컬렉션 비우기 (새로운 라운드 시작 전)
        gameMonsters.clear();

        // 좀비 스폰 스케줄러 시작
        spawnZombies(extraHealth);
        // 좀비 수 업데이트 및 추적 스케줄러 시작
        updateZombieCount();


        // ZombiePersistenceManager 로직이 필요하다면 여기에 통합 또는 별도 관리
        // 현재는 주석 처리된 상태이므로, 만약 필요하다면 게임 몬스터 컬렉션의 엔티티에 대해
        // setRemoveWhenFarAway(false) 및 setPersistent(true)를 적용하는 로직을 추가해야 합니다.
        // 예를 들어, spawnZombies 에서 엔티티를 스폰한 직후 해당 엔티티에 대해 호출.
    }

    // 좀비 스폰 로직
    // 좀비를 주기적으로 소환하는 메서드
    private void spawnZombies(int extraHealth) {
        World world = Bukkit.getWorld("world"); // TODO: config 에서 월드 이름 가져오도록 변경 (나중에)
        if (world == null) {
            getLogger().warning(WORLD_NOT_FOUND_WARNING);
            return;
        }

        new BukkitRunnable() {
            int spawnedCount = 0; // 소환된 엔티티 총 개수
            final int entitiesToSpawn = getConfig().getInt("spawn.zombies-per-round", 20) * currentRound; // config 값 사용

            // 현재 설정된 스폰 좌표 범위 사용 (BiomeNotifier 등에서 변경 가능)
            // 이 좌표들은 BiomeNotifier에 의해 플레이어 바이옴에 따라 업데이트됩니다.
            final int currentMinX = minX;
            final int currentMaxX = maxX;
            final int currentMinZ = minZ;
            final int currentMaxZ = maxZ;


            @Override
            public void run() {
                if (!gameInProgress) { // 게임이 중단되었으면 스폰 중단
                    cancel();
                    return;
                }
                if (spawnedCount >= entitiesToSpawn) {
                    cancel(); // 필요한 엔티티 수를 모두 스폰했으면 작업 취소
                    return;
                }

                // 정의된 범위 내에서 무작위 X, Z 좌표 생성
                double spawnX = (random.nextDouble() * (currentMaxX - currentMinX)) + currentMinX;
                double spawnZ = (random.nextDouble() * (currentMaxZ - currentMinZ)) + currentMinZ; // Using currentMinZ here based on common pattern, double-check if maxZ < minZ is intentional in config

                // 생성된 X, Z 좌표에서 지면 위 안전한 Y 좌표 찾기 (가장 높은 블록 위 +1)
                int safeY = world.getHighestBlockYAt((int) spawnX, (int) spawnZ) + 1; // 블록 바로 위에 소환 (+1)

                // 잠재적인 스폰 위치 생성 및 바이옴 체크
                Biome spawnBiome = world.getBiome(new Location(world, spawnX, safeY, spawnZ));

                // **👇 바이옴별 스폰 확률을 config 에서 로드한 데이터로 결정 👇**
                EntityType typeToSpawn = EntityType.ZOMBIE; // 기본값: 좀비

                // 현재 스폰 위치의 바이옴 키를 가져옴 (예: "deep_dark", "desert", "swamp" 등)
                String biomeKey = spawnBiome.getKey().getKey(); // Biome enum 에서 키 문자열을 가져옴 (예: "deep_dark")

                // config 에서 해당 바이옴의 스폰 확률 맵을 가져옴
                Map<String, Double> probabilities = configBiomeSpawnProbabilities.get(biomeKey);

                // 해당 바이옴에 대한 확률 설정이 config에 존재하는 경우
                if (probabilities != null && !probabilities.isEmpty()) {
                    double totalProbability = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
                    double randomValue = random.nextDouble() * totalProbability; // 총 확률 합계 내에서 랜덤 값 선택

                    double cumulativeProbability = 0.0;
                    // 확률에 따라 엔티티 타입 결정
                    for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
                        cumulativeProbability += entry.getValue();
                        if (randomValue <= cumulativeProbability) {
                            try {
                                // EntityType.valueOf()는 대문자 이름을 사용해야 합니다.
                                EntityType configuredType = EntityType.valueOf(entry.getKey().toUpperCase());

                                // EntityType.getEntityClass()가 null이 아닌지 먼저 확인
                                Class<?> entityClass = configuredType.getEntityClass();
                                if (entityClass != null && LivingEntity.class.isAssignableFrom(entityClass)) {
                                    typeToSpawn = configuredType;
                                } else {
                                    getLogger().warning("Config error: Entity type " + entry.getKey() + " in biome " + biomeKey + " is not a valid LivingEntity type. Falling back to Zombie.");
                                }
                            } catch (IllegalArgumentException e) {
                                getLogger().warning("Config error: Invalid entity type name '" + entry.getKey() + "' in biome " + biomeKey + ". Falling back to Zombie.");
                            }
                            break; // 타입이 결정되면 반복 중단
                        }
                    }
                } else {
                    // 해당 바이옴에 대한 확률 설정이 config에 없거나 비어있는 경우
                    // 기본값인 EntityType.ZOMBIE가 사용됩니다.
                    getLogger().fine("No specific spawn probabilities for biome " + biomeKey + ". Using default Zombie spawn."); // 디버그용 메시지
                }
                // **👆 바이옴별 스폰 확률 결정 로직 끝 👆**


                // 엔티티 소환 시에는 블록 중앙에 위치시키기 위해 X, Z에 0.5를 더한 Location 사용
                Location finalSpawnLocation = new Location(world, spawnX + 0.5, safeY, spawnZ + 0.5);
                LivingEntity spawnedEntity; // 소환된 엔티티를 받을 변수 (Zombie 뿐만 아니라 LivingEntity)

                try {
                    // 👇 직접 엔티티 소환
                    spawnedEntity = (LivingEntity) world.spawnEntity(finalSpawnLocation, typeToSpawn);

                    // 새로 소환된 엔티티를 게임 몬스터 컬렉션에 추가
                    gameMonsters.add(spawnedEntity);

                    // 게임 몬스터는 멀리 있어도 유지, 월드 리로드 후에도 유지되도록 설정
                    spawnedEntity.setRemoveWhenFarAway(false);
                    spawnedEntity.setPersistent(true);


                } catch (Exception e) {
                    // 소환 중 예외 발생 시 로그 기록
                    getLogger().severe("엔티티 소환 오류: " + typeToSpawn + " at " + finalSpawnLocation + ": " + e.getMessage());
                    getLogger().severe("Stack Trace:");
                    // 소환 실패했으므로 이번 스폰 시도는 실패한 것으로 간주 (spawnedCount는 증가시키지 않음)
                    return; // 이번 스폰 시도는 실패했으므로 run() 메서드 나머지 부분 실행 건너뛰기
                }


                // **👇 엔티티 타입별 초기 설정 및 체력 적용 (헬퍼 메서드 추출 및 switch 사용) 👇**

                // Slime은 체력 설정 방식이 다르므로 먼저 처리
                if (spawnedEntity instanceof Slime slime) {
                    // Slime 전용 설정 (크기 설정 등)
                    int slimeSize = random.nextInt(2) + 2; // 크기 2 또는 3
                    slime.setSize(slimeSize);
                    // 슬라임은 속도 효과를 받으면 문제가 될 수 있으므로, 효과 부여는 Slime이 아닐 경우에만 적용
                } else {
                    // Slime이 아닌 LivingEntity (Zombie, Husk, Bogged, Witch 등) 체력 설정

                    // MAX_HEALTH 속성이 있는지 확인
                    if (spawnedEntity.getAttribute(Attribute.MAX_HEALTH) != null) {
                        // 헬퍼 메서드를 사용하여 최종 체력 계산 및 적용
                        double totalHealth = calculateEntityTotalHealth(spawnedEntity, extraHealth);

                        Objects.requireNonNull(spawnedEntity.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(totalHealth);
                        spawnedEntity.setHealth(totalHealth); // 현재 체력도 최대로 설정
                    }


                    // Slime이 아닌 경우에만 속도 효과 부여
                    spawnedEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
                }
                // **👆 엔티티 타입별 초기 설정 및 체력 적용 끝 👆**


                spawnedCount++; // 성공적으로 소환 시 카운트 증가
            }
        }.runTaskTimer(this, 0L, getConfig().getLong("spawn.interval-ticks", 10L)); // config 값 사용
    }

    // **👇 엔티티 타입에 따라 추가 체력을 계산하는 헬퍼 메서드 👇**
    private double calculateEntityTotalHealth(LivingEntity entity, int extraHealth) {
        // MAX_HEALTH 속성이 없다면 기본값 0을 반환하거나 예외 처리
        if (entity.getAttribute(Attribute.MAX_HEALTH) == null) {
            getLogger().warning("Attempted to calculate health for entity without MAX_HEALTH attribute: " + entity.getType());
            return 0.0; // 또는 기본 체력 등 적절한 값 반환
        }

        double baseHealth = Objects.requireNonNull(entity.getAttribute(Attribute.MAX_HEALTH)).getBaseValue();
        double totalHealth = baseHealth; // 기본 체력으로 초기화 (변수 이미 대입 경고 해결)

        // 엔티티 타입에 따라 추가 체력 계산
        switch (entity.getType()) { // EntityType을 기준으로 switch
            case HUSK:
                totalHealth = baseHealth + (double) extraHealth / 3;
                break;
            case ZOMBIE:
            case ZOMBIE_VILLAGER: // 좀비와 좀비 주민은 같은 로직
                totalHealth = baseHealth + extraHealth;
                break;
            case BOGGED, WITCH:
                totalHealth = baseHealth + (double) extraHealth / 2;
                break;
            // Slime은 이 메서드 밖에서 별도 처리되므로 여기에 포함하지 않습니다.
            // 다른 LivingEntity 타입이 있다면 여기서 case를 추가할 수 있습니다.
            default:
                // 위에서 명시적으로 처리되지 않은 다른 LivingEntity 타입
                // 이 경우 totalHealth는 baseHealth로 유지됩니다.
                // 또는 필요에 따라 다른 기본 추가 체력 계산 로직을 넣을 수 있습니다.
                // 현재는 빈 default 블록이 아니므로 경고 해결됩니다.
                break; // 빈 default 블록이 되지 않도록 break 추가
        }

        return totalHealth; // 계산된 최종 체력 반환
    }    // 좀비 수를 업데이트하고 라운드 종료를 감지하며 좀비를 추격하는 스케줄러 수정
    private void updateZombieCount() {
        // 기존 스케줄러가 실행 중이라면 취소 (중복 실행 방지)
        // 이 메서드가 startGameRound 에서 호출될 때마다 새로운 스케줄러가 생성되므로,
        // 이전 라운드에서 실행 중이던 스케줄러를 취소해야 합니다.
        // 하지만 BukkitRunnable 인스턴스를 필드에 저장하여 관리하지 않고 있어서,
        // Bukkit.getScheduler().cancelTasks(this) 로 MyPlugin의 모든 작업을 취소하는 방식이 필요합니다.
        // 현재 구조에서는 stopGame 에서 전체 취소를 하고 startGameRound 에서 새로 시작하므로 괜찮습니다.

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameInProgress) {
                    cancel(); // 게임이 진행 중이 아니면 작업 취소
                    return;
                }

                // ** 게임 몬스터 컬렉션을 순회하며 살아있는 엔티티 수 계산 및 죽은 엔티티 제거 **
                // Iterator를 사용하여 순회 중에 안전하게 요소를 제거합니다.
                Iterator<LivingEntity> iterator = gameMonsters.iterator();
                int aliveMonsterCount = 0;
                while (iterator.hasNext()) {
                    LivingEntity monster = iterator.next();
                    // 엔티티가 죽었거나 유효하지 않으면 컬렉션에서 제거
                    if (monster.isDead() || !monster.isValid()) {
                        iterator.remove();
                        continue; // 제거했으면 다음 요소로 넘어갑니다.
                    } else {
                        // 살아있는 유효한 엔티티만 카운트
                        aliveMonsterCount++;
                    }

                    // ** 좀비 추격 로직을 이 스케줄러에 통합 (LivingEntity를 Creature로 캐스팅) **
                    // Creature 인터페이스를 구현하는 몬스터만 타겟을 가질 수 있습니다.
                    if (monster instanceof Creature creature) {
                        // 이미 타겟이 있거나 타겟이 플레이 가능한 상태인 경우 업데이트 건너뛰기
                        LivingEntity currentTarget = creature.getTarget();
                        if (currentTarget instanceof Player playerTarget && playerTarget.getGameMode() != GameMode.SPECTATOR && !playerTarget.isDead()) {
                            continue;
                        }

                        Player nearest = getNearestPlayer(creature); // Creature 객체를 getNearestPlayer에 전달 가능
                        // 추격할 플레이어가 없으면 타겟 해제 (선택 사항)
                        creature.setTarget(nearest); // 새로운 타겟 설정
                    }
                    // Slime은 Creature를 구현하지 않으므로 이 로직을 건너뜊니다.
                }

                for (World world : Bukkit.getWorlds()) {
                    for (Slime slime : world.getEntitiesByClass(Slime.class)) {
                        if (!gameMonsters.contains(slime) && !slime.isDead() && slime.isValid()) {
                            gameMonsters.add(slime);
                            slime.setRemoveWhenFarAway(false);
                            slime.setPersistent(true);
                        }
                    }
                }


                // 스코어보드에 총 개수 업데이트
                gameScoreboard.updateScore("남은 좀비", aliveMonsterCount);

                // 총 엔티티 수가 0이고 게임이 진행 중일 때 다음 라운드로 전환
                if (aliveMonsterCount == 0 && gameInProgress) {
                    cancel(); // 현재 엔티티 수 업데이트 작업 취소
                    endRound(); // 라운드 종료 처리 (다음 라운드 준비 단계 시작)
                }

                // 게임이 중단되면 이 작업도 취소 (이미 스케줄러 시작 부분에서 체크하지만, 한번 더 확인)
                if (!gameInProgress) {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, getConfig().getLong("intervals.zombie-count-update-ticks", 20L)); // config 값 사용
    }


    // 이 메서드는 이제 LivingEntity를 받도록 유지하지만, 내부에서는 Creature만 getTarget/setTarget 로직을 수행하도록 updateZombieCount 에서 처리
    private Player getNearestPlayer(LivingEntity entity) {
        double closestDistance = Double.MAX_VALUE;
        Player closestPlayer = null;
        Location entityLocation = entity.getLocation(); // 입력 엔티티의 위치 사용

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 플레이 가능한 상태의 플레이어만 고려 (관전자, 사망자 제외)
            if (player.getGameMode() != GameMode.SPECTATOR && !player.isDead()) {
                double distance = player.getLocation().distance(entityLocation);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = player;
                }
            }
        }
        return closestPlayer; // 가장 가까운 플레이어 반환 (없으면 null)
    }

    // 게임 몬스터 컬렉션의 모든 엔티티를 제거하는 메서드
    private void removeGameEntities() {
        // Iterator를 사용하여 순회하면서 안전하게 제거
        Iterator<LivingEntity> iterator = gameMonsters.iterator();
        while(iterator.hasNext()){
            LivingEntity monster = iterator.next();
            monster.remove(); // 월드에서 엔티티 제거
            iterator.remove(); // 컬렉션에서 제거
        }
        // 컬렉션을 명확하게 비우는 것을 보장
        gameMonsters.clear();
        getLogger().info("모든 게임 몬스터가 제거되었습니다.");
    }

    private int minX = DEFAULT_MIN_X, maxX = DEFAULT_MAX_X, minZ = DEFAULT_MIN_Z, maxZ = DEFAULT_MAX_Z; // 기본 좌표 범위

    // 좀비 소환 좌표 변경 메서드
    public void setZombieSpawnCoordinates(int minX, int maxX, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        getLogger().info("스폰 좌표가 [" + minX + ", " + maxX + ", " + minZ + ", " + maxZ + "] 로 변경되었습니다.");
    }
}