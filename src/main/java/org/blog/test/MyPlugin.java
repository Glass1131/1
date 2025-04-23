package org.blog.test;

import Custom.CustomCommand;
import Custom.CustomItem;
import Custom.CustomItemRecipe;
import Biomes.Deep_dark;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import System.ThirstSystem;
import System.HeatSystem;

import java.util.*;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Since 2025 ~
 * 제작자: Glass1131, Gemini, GPT
 * 목적: ???
 */

public class MyPlugin extends JavaPlugin implements Listener { // TabCompleter 제거
    private static final int ZOMBIES_PER_ROUND = 20;
    private static final int PREPARATION_TIME_SECONDS = 30;
    private static final long ROUND_END_DELAY_TICKS = 40L;
    private static final long ZOMBIE_SPAWN_INTERVAL_TICKS = 10L;
    private static final long ZOMBIE_COUNT_UPDATE_INTERVAL_TICKS = 20L;
    private static final long ZOMBIE_CHASE_INTERVAL_TICKS = 200L;
    private static final long BIOME_CHECK_INTERVAL_TICKS = 5L;
    private static final long ACTIONBAR_UPDATE_INTERVAL_TICKS = 20L;
    private static final int ZOMBIE_HEALTH_INCREASE_PER_10_ROUNDS = 3;
    private static final int HEALTH_INCREASE_EVERY_X_ROUNDS = 10;
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
        // ZombieDropListener 인스턴스 생성 시 MyPlugin 인스턴스(this) 전달
        getServer().getPluginManager().registerEvents(new ZombieDropListener(), this);

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
        // 바이옴 감지 스케줄러 시작
        new BiomeNotifier(this).runTaskTimer(this, 0L, BIOME_CHECK_INTERVAL_TICKS);
        // 커스텀 아이템 레시피 등록
        new CustomItemRecipe(this).registerRecipes();
        // 딥 다크 관련 초기화 (이벤트 리스너 등)
        new Deep_dark(this);
        // 게임 스코어보드 초기화 및 적용
        gameScoreboard = new GameScoreboard();
        gameScoreboard.applyToAllPlayers();
        // 좀비 디스폰 방지 매니저 실행
        new ZombiePersistenceManager(this);
        // 갈증 및 온도 시스템 초기화
        this.thirstSystem = new ThirstSystem(this);
        this.heatSystem = new HeatSystem(this);
        getLogger().info("💧 갈증 및 🔥 온도 시스템 초기화 완료!");
        // 플레이어 접속 시 레시피 발견 처리를 위한 이벤트 리스너는 onPlayerJoin 메서드에 있음
        // 액션바 스케줄러 시작
        startActionBarScheduler();
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
        // 플러그인 비활성화 시 모든 좀비 제거
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
    }

    // startGame 메서드는 CustomCommand 에서 호출될 수 있도록 유지
    public void startGame() {
        gameInProgress = true;
        gameScoreboard.applyToAllPlayers();
        startPreparationPhase();
    }

    // stopGame 메서드는 CustomCommand 에서 호출될 수 있도록 유지
    public void stopGame() {
        gameInProgress = false;
        currentRound = 1; // 라운드 초기화

        // 모든 좀비 삭제
        removeGameEntities();
        // 게임 관련 반복 작업 취소 (BiomeNotifier, ZombieCount, ZombieChase, Prepare 타이머 등)
        Bukkit.getScheduler().cancelTasks(this);

        // 스코어보드 초기화 (플레이어들에게 새로운 빈 스코어보드 적용)
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            // 플레이어에게 적용된 모든 효과 제거
            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
        }

        // BiomeNotifier 리셋 및 재시작
        Bukkit.getScheduler().runTask(this, () -> new BiomeNotifier(this).runTaskTimer(this, 0L, BIOME_CHECK_INTERVAL_TICKS));
        // 갈증 시스템과 온도 시스템 재시작
        restartThirstAndHeatSystems();
        // 액션바 스케줄러 재시작
        startActionBarScheduler();

        Bukkit.broadcast(Component.text(GAME_FORCED_STOPPED).color(NamedTextColor.RED));
    }

    private void endRound() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!gameInProgress) return;
            Bukkit.broadcast(Component.text("라운드 " + currentRound + " 종료! 다음 라운드를 준비하세요.").color(NamedTextColor.RED));
            currentRound++;
            startPreparationPhase();
        }, ROUND_END_DELAY_TICKS);
    }

    // 액션바 스케줄러 (갈증, 온도, 바이옴 상태 표시)
    private void startActionBarScheduler() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Location playerLoc = player.getLocation();

                int thirstLevel = thirstSystem.getThirstLevel(player);
                String temperature = heatSystem != null ? heatSystem.getTemperatureState(player) : "N/A";

                // 온도 상태 문자열에 따른 색상 가져오기
                NamedTextColor temperatureColor = getTemperatureColorFromStateString(temperature);

                // 현재 바이옴 정보 가져오기
                Biome currentBiome = playerLoc.getBlock().getBiome();
                String formattedBiome = formatBiomeName(currentBiome);

                NamedTextColor biomeColor = getBiomeColor(currentBiome);

                NamedTextColor thirstColor = getThirstColor(thirstLevel);

                // 액션바에 표시 - 각 요소를 별도의 Component로 분리하고 색상 적용
                player.sendActionBar(
                        Component.text("💧 갈증: " + thirstLevel + "%", thirstColor) // 갈증 정보 (색상: 갈증 레벨에 따라 다름)
                                .append(Component.text(" | ", NamedTextColor.WHITE)) // 구분자 '|' (색상: 하얀색)
                                .append(Component.text("🌡 " + temperature, temperatureColor)) // 온도 정보 (색상: 온도 상태에 따라 다름)
                                .append(Component.text(" | ", NamedTextColor.WHITE)) // 구분자 '|' (색상: 하얀색)
                                .append(Component.text("🌳 " + formattedBiome, biomeColor)) // 바이옴 정보 (색상: 바이옴에 따라 다름)
                );
            }
        }, 0L, ACTIONBAR_UPDATE_INTERVAL_TICKS);
    }

    // Biome Enum 이름을 보기 좋게 형식화하는 헬퍼 메서드
    private String formatBiomeName(Biome biome) {
        if (biome == null) return "Unknown Biome";
        String keyString = biome.getKey().asString();
        // 네임스페이스 제거 (예: "minecraft:deep_dark" -> "deep_dark")
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
    } //switch 문으로 바꾸면 ㅈㄹ남 ㅅㅂ

    private void restartThirstAndHeatSystems() {
        // 갈증 시스템 초기화 및 재시작
        this.thirstSystem = new ThirstSystem(this);
        // 온도 시스템 초기화 및 재시작
        this.heatSystem = new HeatSystem(this);
    }

    private void startPreparationPhase() {
        new BukkitRunnable() {
            int timeLeft = PREPARATION_TIME_SECONDS;

            @Override
            public void run() {
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
        // 라운드 스코어보드 업데이트는 startGame 또는 round 명령어 처리 시 먼저 이루어질 수 있으나,
        // 라운드 시작 시점에 다시 한번 확실하게 업데이트
        if (gameScoreboard != null) {
            gameScoreboard.updateScore("라운드", currentRound);
        } else {
            getLogger().warning("gameScoreboard is null at the start of startGameRound.");
        }

        Bukkit.broadcast(Component.text("게임 시작! 라운드 " + currentRound).color(NamedTextColor.GREEN));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.text("게임 시작!").color(NamedTextColor.GREEN));
        }

        // 라운드에 따라 추가 체력 계산 (10 라운드마다 체력 3 증가)
        int extraHealth = (currentRound / HEALTH_INCREASE_EVERY_X_ROUNDS) * ZOMBIE_HEALTH_INCREASE_PER_10_ROUNDS;

        // 좀비 스폰 및 수 업데이트 스케줄러 시작
        spawnZombies(extraHealth);
        updateZombieCount();
    }

    private void updateZombieCount() {
        new BukkitRunnable() {
            @Override
            public void run() {
                //좀비, 보그드, 슬라임의 총 개수 세기 (다양한 엔티티 타입 포함)
                long totalGameEntities = Bukkit.getWorlds().stream()
                        .flatMap(world -> world.getEntitiesByClass(LivingEntity.class).stream()) // 모든 LivingEntity 가져오기
                        .filter(entity -> !entity.isDead()) // 사망하지 않은 엔티티만 필터링
                        // 👇 우리가 게임 엔티티 (좀비, 보그드, 슬라임)로 간주하는 타입들만 필터링하여 개수 계산
                        .filter(entity -> entity instanceof Zombie || // 좀비 하위 클래스 (좀비, 허스크, 좀비 주민)
                                entity instanceof Bogged || // 보그드
                                entity instanceof Slime)   // 슬라임
                        .count(); // 필터링된 엔티티들의 총 개수

                // 스코어보드에 총 개수 업데이트
                gameScoreboard.updateScore("남은 좀비", (int) totalGameEntities);

                // 총 엔티티 수가 0이고 게임이 진행 중일 때 다음 라운드로 전환
                if (totalGameEntities == 0 && gameInProgress) {
                    cancel(); // 현재 엔티티 수 업데이트 작업 취소
                    endRound(); // 라운드 종료 처리 (다음 라운드 준비 단계 시작)
                }
                // 게임이 중단되면 이 작업도 취소
                if (!gameInProgress) {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, ZOMBIE_COUNT_UPDATE_INTERVAL_TICKS); // 주기적으로 엔티티 수 업데이트
    }

    // 좀비 스폰 로직
    // 좀비를 주기적으로 소환하는 메서드
    private void spawnZombies(int extraHealth) {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            getLogger().warning(WORLD_NOT_FOUND_WARNING);
            return;
        }

        new BukkitRunnable() {
            int spawnedCount = 0; // 소환된 엔티티 총 개수
            // Random 필드는 MyPlugin 클래스에 있습니다. (outer class field)
            // Accessible via outer class scope
            final int entitiesToSpawn = ZOMBIES_PER_ROUND * currentRound; // 이번 라운드에 소환될 총 엔티티 수

            // 현재 설정된 스폰 좌표 범위 사용 (BiomeNotifier 등에서 변경 가능)
            final int currentMinX = minX; // outer class field
            final int currentMaxX = maxX; // outer class field
            final int currentMinZ = minZ; // outer class field
            final int currentMaxZ = maxZ; // outer class field


            @Override
            public void run() {
                if (spawnedCount >= entitiesToSpawn) {
                    cancel(); // 필요한 엔티티 수를 모두 스폰했으면 작업 취소
                    return;
                }

                // 정의된 범위 내에서 무작위 X, Z 좌표 생성
                double spawnX = (random.nextDouble() * (currentMaxX - currentMinX)) + currentMinX;
                double spawnZ = (random.nextDouble() * (currentMaxZ - currentMinZ)) + currentMinZ;

                // 생성된 X, Z 좌표에서 지면 위 안전한 Y 좌표 찾기 (가장 높은 블록 위 +1)
                int safeY = world.getHighestBlockYAt((int) spawnX, (int) spawnZ) + 1; // 블록 바로 위에 소환 (+1)

                // 잠재적인 스폰 위치 생성 (바이옴 체크를 위해 X, Z, safeY 사용)
                Location spawnLocation = new Location(world, spawnX, safeY, spawnZ);

                // 소환될 엔티티 타입 결정 (스폰 위치의 바이옴에 따라)
                Biome spawnBiome = world.getBiome(spawnLocation);
                EntityType typeToSpawn = EntityType.ZOMBIE;

                if (spawnBiome == Biome.DESERT) {
                    double desertChance = random.nextDouble();
                    if (desertChance < 0.85) typeToSpawn = EntityType.HUSK;
                    else typeToSpawn = EntityType.ZOMBIE_VILLAGER;
                } else if (spawnBiome == Biome.SWAMP) {
                    //늪지대 바이옴: 마녀(0.01%), 좀비(35%), 좀비 주민(35%), 보그드(20%), 슬라임(10%)
                    double swampChance = random.nextDouble(); // 0.0부터 1.0까지의 랜덤 값

                    if (swampChance < 0.0001) { //0.01% 확률로 마녀 소환 (0.01 / 100 = 0.0001)
                        typeToSpawn = EntityType.WITCH;
                    } else if (swampChance < 0.0001 + 0.35) { // 0.0001 이상 0.3501 미만 (35%)
                        typeToSpawn = EntityType.ZOMBIE;
                    } else if (swampChance < 0.0001 + 0.35 + 0.35) { // 0.3501 이상 0.7001 미만 (35%)
                        typeToSpawn = EntityType.ZOMBIE_VILLAGER;
                    } else if (swampChance < 0.0001 + 0.35 + 0.35 + 0.20) { // 0.7001 이상 0.9001 미만 (20%)
                        typeToSpawn = EntityType.BOGGED;
                    } else { // 0.9001 이상 1.0 미만 (10%)
                        typeToSpawn = EntityType.SLIME;
                    }
                }
                // 다른 바이옴: 기본값 EntityType.ZOMBIE (이미 설정됨)

                // 엔티티 소환 시에는 블록 중앙에 위치시키기 위해 X, Z에 0.5를 더한 Location 사용
                Location finalSpawnLocation = new Location(world, spawnX + 0.5, safeY, spawnZ + 0.5);
                LivingEntity spawnedEntity; // 소환된 엔티티를 받을 변수 (Zombie 뿐만 아니라 LivingEntity)

                try {
                    // 👇 직접 엔티티 소환
                    spawnedEntity = (LivingEntity) world.spawnEntity(finalSpawnLocation, typeToSpawn);
                } catch (Exception e) {
                    // 소환 중 예외 발생 시 로그 기록
                    getLogger().severe("엔티티 소환 오류: " + typeToSpawn + " at " + finalSpawnLocation + ": " + e.getMessage());
                    getLogger().severe("Stack Trace:");
                    // 소환 실패했으므로 이번 스폰 시도는 실패한 것으로 간주 (spawnedCount는 증가시키지 않음)
                    return; // 이번 스폰 시도는 실패했으므로 run() 메서드 나머지 부분 실행 건너뛰기
                }


                // 👇 엔티티 타입별 추가 설정 및 체력 적용
                switch (spawnedEntity) {
                    case Zombie zombie -> {
                        // 좀비, 허스크, 좀비 주민 공통 체력 설정 방식
                        if (zombie instanceof Husk) {
                            // 허스크: 기본 10 + extraHealth/3 만큼 체력 증가
                            double baseHealth = Objects.requireNonNull(zombie.getAttribute(Attribute.MAX_HEALTH)).getBaseValue();
                            double huskTotalHealth = baseHealth + (double) extraHealth / 3;
                            Objects.requireNonNull(zombie.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(huskTotalHealth);
                            zombie.setHealth(huskTotalHealth);
                        } else {
                            // 좀비/좀비 주민: 기본 20 + extraHealth 만큼 체력 증가
                            double baseHealth = Objects.requireNonNull(zombie.getAttribute(Attribute.MAX_HEALTH)).getBaseValue();
                            double zombieTotalHealth = baseHealth + extraHealth;
                            Objects.requireNonNull(zombie.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(zombieTotalHealth);
                            zombie.setHealth(zombieTotalHealth);
                        }
                    }
                    case Bogged bogged -> {
                        // 보그드: 기본 16 + extraHealth/2 만큼 체력 증가
                        double baseHealth = Objects.requireNonNull(bogged.getAttribute(Attribute.MAX_HEALTH)).getBaseValue();
                        double boggedTotalHealth = baseHealth + (double) extraHealth / 2;
                        Objects.requireNonNull(bogged.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(boggedTotalHealth);
                        bogged.setHealth(boggedTotalHealth);
                    }
                    case Slime slime -> {
                        if (typeToSpawn == EntityType.SLIME) {
                            int slimeSize = random.nextInt(2) + 2;
                            slime.setSize(slimeSize);
                        }
                    }
                    default -> {
                    }
                }

                spawnedEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
                spawnedCount++; // 성공적으로 소환 시 카운트 증가
            }
        }.runTaskTimer(this, 0L, ZOMBIE_SPAWN_INTERVAL_TICKS); // 주기적으로 엔티티 스폰 작업 실행
        makeZombiesChasePlayers();
    }


    private void makeZombiesChasePlayers() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameInProgress) {
                    cancel(); // 게임이 진행 중이 아니면 작업 취소
                    return;
                }

                for (World world : Bukkit.getWorlds()) {
                    for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                        LivingEntity currentTarget = zombie.getTarget();
                        if (currentTarget == null ||
                                (currentTarget instanceof Player playerTarget && (playerTarget.getGameMode() == GameMode.SPECTATOR || playerTarget.isDead()))) {
                            Player nearest = getNearestPlayer(zombie);
                            if (nearest != null) {
                                zombie.setTarget(nearest); // 새로운 타겟 설정
                            }
                        }
                    }

                    for (Bogged bogged : world.getEntitiesByClass(Bogged.class)) {
                        LivingEntity currentTarget = bogged.getTarget();
                        if (currentTarget == null || (currentTarget instanceof Player playerTarget && (playerTarget.getGameMode() == GameMode.SPECTATOR || playerTarget.isDead()))) {
                            Player nearest = getNearestPlayer(bogged);
                            if (nearest != null) {
                                bogged.setTarget(nearest);
                            }
                        }
                    }

                    // 슬라임 타입 타겟 설정
                    for (Slime slime : world.getEntitiesByClass(Slime.class)) {
                        LivingEntity currentTarget = slime.getTarget();
                        if (currentTarget == null || (currentTarget instanceof Player playerTarget && (playerTarget.getGameMode() == GameMode.SPECTATOR || playerTarget.isDead()))) {
                            Player nearest = getNearestPlayer(slime);
                            if (nearest != null) {
                                slime.setTarget(nearest);
                            }
                        }
                    }

                    for (Witch witch : world.getEntitiesByClass(Witch.class)) {
                        // 타겟 설정 로직 (좀비와 유사)
                        LivingEntity currentTarget = witch.getTarget();
                        if (currentTarget == null || (currentTarget instanceof Player playerTarget && (playerTarget.getGameMode() == GameMode.SPECTATOR || playerTarget.isDead()))) {
                            Player nearest = getNearestPlayer(witch); // LivingEntity (Witch) 전달
                            if (nearest != null) {
                                witch.setTarget(nearest);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, ZOMBIE_CHASE_INTERVAL_TICKS); // 주기적으로 엔티티 타겟 업데이트 (이름은 그대로 둠)
    }

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

    // 엔티티 제거
    private void removeGameEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                zombie.remove();
            }
            for (Bogged bogged : world.getEntitiesByClass(Bogged.class)) {
                bogged.remove();
            }
            for (Slime slime : world.getEntitiesByClass(Slime.class)) {
                slime.remove();
            }
            for (Witch witch : world.getEntitiesByClass(Witch.class)) {
                witch.remove();
            }
        }
    }

    private int minX = DEFAULT_MIN_X, maxX = DEFAULT_MAX_X, minZ = DEFAULT_MIN_Z, maxZ = DEFAULT_MAX_Z; // 기본 좌표 범위

    // 좀비 소환 좌표 변경 메서드
    public void setZombieSpawnCoordinates(int minX, int maxX, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }
}