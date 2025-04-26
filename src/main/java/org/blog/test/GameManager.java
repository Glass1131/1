package org.blog.test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

/**
 * 좀비 서바이벌 게임의 핵심 로직 및 상태를 관리하는 클래스.
 */
public class GameManager {

    // --- 상수 ---
    public static final String GAME_ALREADY_IN_PROGRESS = "게임이 이미 진행 중입니다.";
    public static final String NO_GAME_IN_PROGRESS = "진행 중인 게임이 없습니다.";

    // --- 의존성 ---
    private final MyPlugin plugin;
    private final GameScoreboard gameScoreboard;
    private final MobSpawner mobSpawner;
    // private final PlayerStatusManager playerStatusManager;

    // --- 게임 상태 ---
    private boolean gameInProgress = false;
    private int currentRound = 1;
    private final Set<LivingEntity> gameMonsters = new HashSet<>();

    // --- 설정 값 ---
    private final int preparationTimeSeconds;
    private final long roundEndDelayTicks;
    private final long zombieCountUpdateIntervalTicks;
    private final int healthIncreaseAmount;
    private final int healthIncreasePerXRounds;

    // --- 스케줄러 작업 ID 관리 ---
    private BukkitTask preparationTask = null;
    private BukkitTask roundEndTask = null;
    private BukkitTask entityCountUpdateTask = null;

    /**
     * GameManager 생성자.
     * @param plugin MyPlugin 인스턴스
     * @param gameScoreboard GameScoreboard 인스턴스
     */
    public GameManager(MyPlugin plugin, GameScoreboard gameScoreboard) {
        this.plugin = plugin;
        this.gameScoreboard = gameScoreboard;
        this.mobSpawner = new MobSpawner(plugin, this);

        // 설정값 로드
        this.preparationTimeSeconds = plugin.getConfig().getInt("round.preparation-time-seconds", 30);
        this.roundEndDelayTicks = plugin.getConfig().getLong("round.end-delay-ticks", 40L);
        this.zombieCountUpdateIntervalTicks = plugin.getConfig().getLong("intervals.zombie-count-update-ticks", 20L);
        this.healthIncreaseAmount = plugin.getConfig().getInt("spawn.health-increase.per-10-rounds", 3);
        this.healthIncreasePerXRounds = plugin.getConfig().getInt("spawn.health-increase.every-x-rounds", 10);
    }

    /**
     * 게임을 시작합니다.
     */
    public void startGame() {
        if (gameInProgress) {
            plugin.getLogger().warning(GAME_ALREADY_IN_PROGRESS);
            return;
        }
        gameInProgress = true;
        currentRound = 1;
        gameMonsters.clear();

        if (gameScoreboard != null) {
            gameScoreboard.applyToAllPlayers();
            gameScoreboard.updateScore("라운드", currentRound);
        } else {
            plugin.getLogger().severe("GameScoreboard is null! Cannot start game correctly.");
            gameInProgress = false;
            return;
        }
        startPreparationPhase();
        plugin.getLogger().info("Game started!");
    }

    /**
     * 게임을 중지합니다.
     */
    public void stopGame() {
        if (!gameInProgress) {
            plugin.getLogger().warning(NO_GAME_IN_PROGRESS);
            return;
        }
        gameInProgress = false;
        currentRound = 1;

        cancelAllGameTasks();
        removeGameEntities();
        resetPlayerStates();

        plugin.getLogger().info("Game stopped!");
    }

    /**
     * 게임 관련 모든 BukkitRunnable 작업을 취소합니다.
     */
    private void cancelAllGameTasks() {
        if (preparationTask != null && !preparationTask.isCancelled()) {
            preparationTask.cancel();
            preparationTask = null;
        }
        if (roundEndTask != null && !roundEndTask.isCancelled()) {
            roundEndTask.cancel();
            roundEndTask = null;
        }
        if (mobSpawner != null) {
            mobSpawner.stopSpawning();
        }
        if (entityCountUpdateTask != null && !entityCountUpdateTask.isCancelled()) {
            entityCountUpdateTask.cancel();
            entityCountUpdateTask = null;
        }
    }

    /**
     * 모든 플레이어의 스코어보드를 초기화하고 포션 효과를 제거합니다.
     */
    private void resetPlayerStates() {
        Scoreboard emptyScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (gameScoreboard != null && p.getScoreboard().equals(gameScoreboard.getBoard())) {
                p.setScoreboard(emptyScoreboard);
            }
            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
        }
    }


    /**
     * 라운드 시작 전 준비 단계를 시작합니다.
     */
    private void startPreparationPhase() {
        if (preparationTask != null && !preparationTask.isCancelled()) {
            preparationTask.cancel();
        }
        preparationTask = new BukkitRunnable() {
            int timeLeft = preparationTimeSeconds;
            @Override
            public void run() {
                if (!gameInProgress) {
                    cancel();
                    preparationTask = null;
                    return;
                }
                if (timeLeft >= 1) {
                    if (gameScoreboard != null) {
                        gameScoreboard.updateScore("준비 시간", timeLeft);
                    }
                    timeLeft--;
                } else {
                    cancel();
                    preparationTask = null;
                    startGameRound();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * 실제 게임 라운드를 시작합니다.
     */
    private void startGameRound() {
        if (!gameInProgress) return;

        if (gameScoreboard != null) {
            gameScoreboard.updateScore("라운드", currentRound);
            gameScoreboard.updateScore("준비 시간", 0);
        } else {
            plugin.getLogger().severe("GameScoreboard is null! Cannot start round correctly.");
            stopGame();
            return;
        }

        Bukkit.broadcast(Component.text("게임 시작! 라운드 " + currentRound).color(NamedTextColor.GREEN));

        int extraHealth = 0;
        if (healthIncreasePerXRounds > 0) {
            extraHealth = (currentRound / healthIncreasePerXRounds) * healthIncreaseAmount;
        }

        gameMonsters.clear();

        if (mobSpawner != null) {
            mobSpawner.startSpawning(currentRound, extraHealth);
        } else {
            plugin.getLogger().severe("MobSpawner is null! Cannot spawn mobs.");
            stopGame();
            return;
        }

        startEntityCountUpdateTask();
    }

    /**
     * 현재 라운드를 종료하고 다음 라운드 준비 단계를 시작합니다.
     */
    private void endRound() {
        if (roundEndTask != null && !roundEndTask.isCancelled()) {
            roundEndTask.cancel();
        }
        roundEndTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!gameInProgress) {
                plugin.getLogger().info("Game was stopped during round end delay (GameManager).");
                roundEndTask = null;
                return;
            }
            Bukkit.broadcast(Component.text("라운드 " + currentRound + " 종료! 다음 라운드를 준비하세요.").color(NamedTextColor.YELLOW));
            currentRound++;
            roundEndTask = null;
            startPreparationPhase();
        }, this.roundEndDelayTicks);
    }

    /**
     * 주기적으로 남은 게임 몹 수를 업데이트하는 작업을 시작합니다.
     */
    private void startEntityCountUpdateTask() {
        if (entityCountUpdateTask != null && !entityCountUpdateTask.isCancelled()) {
            entityCountUpdateTask.cancel();
        }
        entityCountUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameInProgress) {
                    cancel();
                    entityCountUpdateTask = null;
                    return;
                }

                Iterator<LivingEntity> iterator = gameMonsters.iterator();
                int aliveMonsterCount = 0;
                while (iterator.hasNext()) {
                    LivingEntity monster = iterator.next();
                    if (monster.isDead() || !monster.isValid()) {
                        iterator.remove();
                    } else {
                        aliveMonsterCount++;
                        if (monster instanceof Creature creature) {
                            updateMonsterTarget(creature);
                        }
                    }
                }

                if (gameScoreboard != null) {
                    gameScoreboard.updateScore("남은 몹", aliveMonsterCount);
                }

                if (aliveMonsterCount == 0 && gameInProgress) {
                    cancel();
                    entityCountUpdateTask = null;
                    endRound();
                }
            }
        }.runTaskTimer(plugin, 0L, this.zombieCountUpdateIntervalTicks);
    }

    /**
     * Creature 타입 몹의 추적 대상을 업데이트합니다.
     * @param creature 대상을 업데이트할 Creature 몹
     */
    private void updateMonsterTarget(Creature creature) {
        LivingEntity currentTarget = creature.getTarget();
        if (currentTarget instanceof Player playerTarget && !playerTarget.isDead() && playerTarget.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        Player nearestPlayer = getNearestPlayer(creature);
        creature.setTarget(nearestPlayer);
    }

    /**
     * 주어진 엔티티로부터 가장 가까운 플레이어(관전자 제외)를 찾습니다.
     * @param entity 기준 엔티티
     * @return 가장 가까운 플레이어 (없으면 null)
     */
    private Player getNearestPlayer(LivingEntity entity) {
        Player closestPlayer = null;
        double closestDistanceSq = Double.MAX_VALUE;
        Location entityLocation = entity.getLocation();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) && !player.isDead()) {
                if (player.getWorld().equals(entityLocation.getWorld())) {
                    double distanceSq = player.getLocation().distanceSquared(entityLocation);
                    if (distanceSq < closestDistanceSq) {
                        closestDistanceSq = distanceSq;
                        closestPlayer = player;
                    }
                }
            }
        }
        return closestPlayer;
    }


    /**
     * 게임 몹 목록에 몹을 추가합니다. (MobSpawner 에서 호출)
     * @param monster 추가할 LivingEntity
     */
    public void addGameMonster(LivingEntity monster) {
        if (monster == null || !monster.isValid()) return;
        gameMonsters.add(monster);
        monster.setRemoveWhenFarAway(false);
        monster.setPersistent(true);
    }

    /**
     * 게임 몹 목록에서 몹을 제거합니다. (EntityDeathEvent 등에서 호출)
     * @param monster 제거할 LivingEntity
     */
    public void removeGameMonster(LivingEntity monster) {
        if (monster == null) return;
        gameMonsters.remove(monster);
    }

    /**
     * 게임 몹 목록에 있는 모든 몹을 월드에서 제거하고 목록을 비웁니다. (stopGame 에서 호출)
     */
    public void removeGameEntities() {
        for (LivingEntity monster : new HashSet<>(gameMonsters)) {
            if (monster != null && monster.isValid()) {
                monster.remove();
            }
        }
        gameMonsters.clear();
        plugin.getLogger().info("All game monsters removed by GameManager.");
    }

    // --- Getter 및 Setter ---

    public boolean isGameInProgress() {
        return gameInProgress;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = Math.max(1, currentRound);
    }

    public Set<LivingEntity> getGameMonsters() {
        return Collections.unmodifiableSet(gameMonsters);
    }
}