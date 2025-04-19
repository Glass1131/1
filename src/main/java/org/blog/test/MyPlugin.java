package org.blog.test;

import Custom.CustomCommand;
import Custom.CustomItem;
import Custom.CustomItemRecipe;
import Biomes.Deep_dark;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
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
 * 제작자: Glass1131, Gemini 2.5 pro, Gemini 2.5 flash, GPT4o, GPT4 mini, 추후 추가될 사람: Barity_
 * 목적: 좀비 웨이브 게임 및 기타 시스템 구현
 */

public class MyPlugin extends JavaPlugin implements Listener { // TabCompleter 제거
    private static final int ZOMBIES_PER_ROUND = 20;
    private static final int PREPARATION_TIME_SECONDS = 30;
    private static final long ROUND_END_DELAY_TICKS = 40L;
    private static final long ZOMBIE_SPAWN_INTERVAL_TICKS = 10L;
    private static final long ZOMBIE_COUNT_UPDATE_INTERVAL_TICKS = 20L;
    private static final long ZOMBIE_CHASE_INTERVAL_TICKS = 20L;
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
    public static final String INVALID_ENTITY_TYPE_WARNING = "유효하지 않은 엔티티 타입을 소환하려 했습니다: ";
    public static final String SPAWN_ERROR_SEVERE = "엔티티 소환 오류";
    // ROUND_SUBCOMMANDS 상수는 CustomCommand로 이동됨


    // CustomCommand 에서 접근할 수 있도록 public 으로 변경 (또는 getter 추가)
    public boolean gameInProgress = false;
    public int currentRound = 1;
    // CustomCommand 에서 스케줄러 owner로 사용되므로 MyPlugin 인스턴스 참조는 유지
    private final JavaPlugin plugin = this;
    // CustomCommand 에서 접근할 수 있도록 public 으로 변경 (또는 getter 추가)
    public GameScoreboard gameScoreboard;
    private ThirstSystem thirstSystem;
    private HeatSystem heatSystem;

    // 갈증 레벨별 색상 상수 (startActionBarScheduler 에서 사용)
    private static final NamedTextColor THIRST_COLOR_100 = NamedTextColor.BLACK;
    private static final NamedTextColor THIRST_90_PLUS = NamedTextColor.DARK_RED;
    private static final NamedTextColor THIRST_COLOR_80_PLUS = NamedTextColor.RED;
    private static final NamedTextColor THIRST_COLOR_50_PLUS = NamedTextColor.GOLD;
    private static final NamedTextColor THIRST_COLOR_20_PLUS = NamedTextColor.YELLOW;
    private static final NamedTextColor THIRST_COLOR_BELOW_20 = NamedTextColor.GREEN;

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
        Objects.requireNonNull(getCommand("게임")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("게임취소")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("round")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("round")).setTabCompleter(customCommandHandler);


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

    // onCommand 메서드는 CustomCommand가 처리하므로 MyPlugin 에서 제거되었습니다.
    // onTabComplete 메서드는 CustomCommand가 처리하므로 MyPlugin 에서 제거되었습니다.


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

    @Override
    public void onDisable() {
        getLogger().info("❌ 모든 플러그인이 비활성화되었습니다!");
        // 플러그인 비활성화 시 모든 좀비 제거
        removeAllZombies();
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
        removeAllZombies();
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

    // 액션바 스케줄러 (갈증, 온도 상태 표시)
    private void startActionBarScheduler() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                int thirstLevel = thirstSystem.getThirstLevel(player);
                // HeatSystem 인스턴스에서 현재 온도 상태 문자열 가져오기
                String temperature = heatSystem != null ? heatSystem.getTemperatureState(player) : "N/A";

                NamedTextColor thirstColor = getThirstColor(thirstLevel);

                player.sendActionBar(
                        Component.text("💧 갈증: " + thirstLevel + "%", thirstColor)
                                .append(Component.text(" | 🌡 " + temperature, NamedTextColor.WHITE))
                );
            }
        }, 0L, ACTIONBAR_UPDATE_INTERVAL_TICKS); // 1초마다 실행
    }

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
                // 살아있는 좀비 (허스크 포함)만 개수 세기
                long zombieCount = Bukkit.getWorlds().stream()
                        .flatMap(world -> world.getEntitiesByClass(Zombie.class).stream())
                        .filter(z -> !z.isDead()) // 사망하지 않은 좀비만 필터링
                        .count();

                gameScoreboard.updateScore("남은 좀비", (int) zombieCount);

                // 좀비 수가 0이고 게임이 진행 중일 때 다음 라운드로 전환
                if (zombieCount == 0 && gameInProgress) {
                    cancel(); // 현재 좀비 수 업데이트 작업 취소
                    endRound(); // 라운드 종료 처리 (다음 라운드 준비 단계 시작)
                }
                // 게임이 중단되면 이 작업도 취소
                if (!gameInProgress) {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, ZOMBIE_COUNT_UPDATE_INTERVAL_TICKS); // 주기적으로 좀비 수 업데이트
    }

    // 좀비 스폰 로직
    private void spawnZombies(int extraHealth) {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            getLogger().warning(WORLD_NOT_FOUND_WARNING);
            return;
        }

        new BukkitRunnable() {
            int spawnedZombies = 0;
            final Random random = new Random();
            final int zombiesToSpawn = ZOMBIES_PER_ROUND * currentRound;
            // 현재 설정된 스폰 좌표 범위 사용
            final int currentMinX = minX;
            final int currentMaxX = maxX;
            final int currentMinZ = minZ;
            final int currentMaxZ = maxZ;


            @Override
            public void run() {
                if (spawnedZombies >= zombiesToSpawn) {
                    cancel(); // 필요한 좀비 수를 모두 스폰했으면 작업 취소
                    return;
                }

                // 정의된 범위 내에서 무작위 X, Z 좌표 생성
                double spawnX = (random.nextDouble() * (currentMaxX - currentMinX)) + currentMinX;
                double spawnZ = (random.nextDouble() * (currentMaxZ - currentMinZ)) + currentMinZ;

                // 생성된 X, Z 좌표에서 지면 위 안전한 Y 좌표 찾기 (가장 높은 블록 위 +1)
                int safeY = world.getHighestBlockYAt((int) spawnX, (int) spawnZ) + 1; // 블록 바로 위에 소환 (+1)

                // 잠재적인 스폰 위치 생성 (바이옴 체크를 위해 X, Z, safeY 사용)
                Location spawnLocation = new Location(world, spawnX, safeY, spawnZ);

                // 스폰 위치의 바이옴을 확인하여 소환할 엔티티 타입 결정
                Biome biome = world.getBiome(spawnLocation);
                EntityType entityTypeToSpawn = EntityType.ZOMBIE; // 기본값: 일반 좀비 소환

                if (biome == Biome.DESERT) {
                    entityTypeToSpawn = EntityType.HUSK; // 사막 바이옴에서는 허스크 소환
                }

                // 엔티티 소환 시에는 블록 중앙에 위치시키기 위해 X, Z에 0.5를 더한 Location 사용
                Location finalSpawnLocation = new Location(world, spawnX + 0.5, safeY, spawnZ + 0.5);

                // 결정된 엔티티 타입을 안전하게 소환
                Zombie zombie = spawnEntitySafely(finalSpawnLocation, entityTypeToSpawn);


                if (zombie != null) {
                    // 체력 계산 및 설정 로직 (엔티티 타입에 따라 다르게 적용)
                    if (zombie instanceof org.bukkit.entity.Husk) {
                        // 허스크인 경우: 기본 최대 체력 10, 추가 체력은 extraHealth/3
                        Objects.requireNonNull(zombie.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(10.0);
                        double huskBoostAmount = (double) extraHealth / 3;
                        double finalHuskHealth = 10.0 + huskBoostAmount;
                        zombie.setHealth(finalHuskHealth);

                        // 허스크 고유의 속성/효과 추가 (필요하다면 여기에 구현)
                    } else {
                        // 일반 좀비인 경우: 기본 BaseValue (20) + extraHealth 만큼 체력 증가
                        double originalBaseHealth = Objects.requireNonNull(zombie.getAttribute(Attribute.MAX_HEALTH)).getBaseValue();
                        double zombieTotalHealth = originalBaseHealth + extraHealth;
                        Objects.requireNonNull(zombie.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(zombieTotalHealth);
                        zombie.setHealth(zombieTotalHealth);
                    }

                    // PotionEffectType.SPEED 효과 무한 지속 부여 (모든 좀비/허스크 공통)
                    zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
                }
                spawnedZombies++; // 스폰 시도 횟수 증가
            }
        }.runTaskTimer(this, 0L, ZOMBIE_SPAWN_INTERVAL_TICKS); // 주기적으로 좀비 스폰 작업 실행
        makeZombiesChasePlayers(); // 좀비가 플레이어를 추적하도록 하는 작업 시작
    }

    // 특정 타입의 엔티티 (좀비/허스크)를 주어진 위치에 안전하게 소환하는 헬퍼 메서드
    private Zombie spawnEntitySafely(Location spawnLocation, EntityType type) {
        World world = spawnLocation.getWorld();
        // 월드 유효성 및 소환 가능한 엔티티 타입 확인 (Zombie 또는 Husk만 허용)
        if (world == null || (type != EntityType.ZOMBIE && type != EntityType.HUSK)) {
            getLogger().warning(INVALID_ENTITY_TYPE_WARNING + type);
            return null;
        }

        // spawnLocation 의 Y 좌표는 이미 안전한 지면 위로 가정 (getHighestBlockYAt + 1 사용)

        try {
            // 엔티티 소환 시도
            return (Zombie) world.spawnEntity(spawnLocation, type);
        } catch (Exception e) {
            // 엔티티 소환 중 예외 발생 시 오류 로깅
            getLogger().severe(SPAWN_ERROR_SEVERE + " " + type + " at " + spawnLocation + ": " + e.getMessage());
            getLogger().severe("Stack Trace:");
            return null; // 소환 실패 시 null 반환
        }
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
                    // 모든 월드의 Zombie 타입 엔티티 (허스크 포함) 반복 처리
                    for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                        LivingEntity currentTarget = zombie.getTarget(); // 현재 타겟 가져오기

                        // 현재 타겟이 없거나 유효하지 않은 플레이어인 경우 새로운 타겟 찾기
                        if (currentTarget == null ||
                                (currentTarget instanceof Player playerTarget && (playerTarget.getGameMode() == GameMode.SPECTATOR || playerTarget.isDead()))) {

                            // 가장 가까운 플레이어 찾기 (플레이 가능한 상태의 플레이어만 고려)
                            Player nearest = getNearestPlayer(zombie);
                            if (nearest != null) {
                                zombie.setTarget(nearest); // 새로운 타겟 설정
                            }
                        }
                        // else: 현재 타겟이 유효한 플레이어인 경우 타겟 유지 (별도 로직 없음)
                    }
                }
            }
        }.runTaskTimer(this, 0L, ZOMBIE_CHASE_INTERVAL_TICKS); // 주기적으로 좀비 타겟 업데이트
    }

    // 가장 가까운 플레이어 찾기 (플레이 가능한 상태의 플레이어만 고려)
    private Player getNearestPlayer(Zombie zombie) {
        double closestDistance = Double.MAX_VALUE;
        Player closestPlayer = null;
        Location zombieLocation = zombie.getLocation();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 플레이 가능한 상태의 플레이어만 고려 (관전자, 사망자 제외)
            if (player.getGameMode() != GameMode.SPECTATOR && !player.isDead()) {
                double distance = player.getLocation().distance(zombieLocation);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = player;
                }
            }
        }
        return closestPlayer; // 가장 가까운 플레이어 반환 (없으면 null)
    }

    // 모든 좀비 제거
    private void removeAllZombies() {
        for (World world : Bukkit.getWorlds()) {
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                zombie.remove();
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