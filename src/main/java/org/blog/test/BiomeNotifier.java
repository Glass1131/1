package org.blog.test;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID; // UUID 임포트 추가
import java.util.concurrent.TimeUnit;

// BukkitRunnable 대신 Listener 구현
public class BiomeNotifier implements Listener {
    private final MyPlugin plugin;
    // 플레이어 UUID를 키로 사용하도록 변경 (Player 객체는 메모리 누수 위험)
    private final Map<UUID, String> playerInBiome = new HashMap<>();
    private final Map<UUID, Map<String, TriggerInfo>> playerTriggerInfo = new HashMap<>();
    private final Map<UUID, Map<String, Long>> suppressionCooldowns = new HashMap<>();

    // 쿨다운 관련 상수 (변경 없음)
    private static final long TRIGGER_WINDOW_MILLIS = 5000;
    private static final int TRIGGER_THRESHOLD = 3;
    private static final long COOLDOWN_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(1);

    // TriggerInfo 내부 클래스 (변경 없음)
    private static class TriggerInfo {
        int count = 0;
        long lastTriggerTime = 0;
    }

    public BiomeNotifier(MyPlugin plugin) {
        this.plugin = plugin;
    }

    // run() 메서드 제거

    /**
     * 플레이어가 서버에 접속했을 때 초기 바이옴 상태를 설정합니다.
     * @param event PlayerJoinEvent
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return; // 월드가 null 이면 처리 중단

        Biome currentBiome = world.getBiome(loc);
        String currentBiomeKey = currentBiome.getKey().getKey().toLowerCase();

        // 초기 상태 설정 (기본은 "default")
        String initialState = "default";
        Map<String, List<Integer>> biomeCoordsMap = plugin.getConfigBiomeSpawnCoords();

        // 접속한 바이옴이 특별히 관리되는 바이옴 중 하나인지 확인
        if (biomeCoordsMap.containsKey(currentBiomeKey)) {
            // Deep Dark, Desert, Swamp 등 관리 대상 바이옴에 직접 접속한 경우
            initialState = currentBiomeKey; // 초기 상태를 해당 바이옴 키로 설정

            // 해당 바이옴 진입 로직 즉시 실행 (메시지, 효과 등)
            handleBiomeEntry(player, currentBiomeKey, System.currentTimeMillis(), biomeCoordsMap);

        } else {
            // 관리 대상이 아닌 바이옴("default" 상태)에 접속한 경우
            // 기본 스폰 좌표 설정
            List<Integer> defaultCoords = biomeCoordsMap.get("default");
            if (defaultCoords != null && defaultCoords.size() == 4) {
                plugin.setZombieSpawnCoordinates(defaultCoords.get(0), defaultCoords.get(1), defaultCoords.get(2), defaultCoords.get(3));
                plugin.getLogger().fine(player.getName() + " joined in default biome. Set spawn coords to default: " + defaultCoords);
            } else {
                plugin.getLogger().severe("Config missing spawn-coords for 'default' biome. Cannot set initial spawn coordinates for " + player.getName() + "!");
            }
        }
        // 플레이어의 초기 바이옴 상태 저장
        playerInBiome.put(playerId, initialState);
    }


    /**
     * 플레이어가 서버에서 나갔을 때 관련 데이터를 정리합니다.
     * @param event PlayerQuitEvent
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        // 플레이어 퇴장 시 모든 관련 맵에서 데이터 제거
        playerInBiome.remove(playerId);
        playerTriggerInfo.remove(playerId);
        suppressionCooldowns.remove(playerId);
    }

    /**
     * 플레이어가 움직였을 때 호출되는 이벤트 핸들러.
     * 다른 블록으로 이동했을 때만 바이옴 변경을 감지하고 처리합니다.
     * @param event PlayerMoveEvent
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 플레이어가 실제로 다른 블록으로 이동했는지 확인
        // (getFrom()과 getTo()의 블록 좌표 비교)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // 같은 블록 내에서의 움직임(예: 고개 돌리기)은 무시
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 관전 모드 플레이어는 무시
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        Location to = event.getTo(); // 이동한 위치
        World world = to.getWorld();
        if (world == null) return; // 월드 null 체크

        Biome currentBiome = world.getBiome(to); // 현재 위치의 바이옴
        String currentBiomeKey = currentBiome.getKey().getKey().toLowerCase(); // 바이옴 키 (소문자)
        long currentTime = System.currentTimeMillis(); // 현재 시간

        // 이전 바이옴 상태 가져오기 (없으면 "default")
        String previousBiomeKey = playerInBiome.getOrDefault(playerId, "default");

        // 바이옴이 변경되었는지 확인
        if (!currentBiomeKey.equals(previousBiomeKey)) {
            // 바이옴 변경 감지됨!

            Map<String, List<Integer>> biomeCoordsMap = plugin.getConfigBiomeSpawnCoords();

            // 1. 이전 바이옴에서 나가는 로직 처리 (이전 상태가 "default"가 아니었다면)
            if (!previousBiomeKey.equals("default")) {
                handleBiomeExit(player, previousBiomeKey, currentTime, biomeCoordsMap);
            }

            // 2. 새로운 바이옴으로 들어가는 로직 처리 (현재 상태가 "default"가 아니라면)
            if (!currentBiomeKey.equals("default")) {
                handleBiomeEntry(player, currentBiomeKey, currentTime, biomeCoordsMap);
            }
            // 현재 바이옴이 "default" 라면 (즉, 특별 관리 바이옴에서 일반 바이옴으로 이동)
            // handleBiomeExit 에서 이미 기본 좌표로 설정했으므로 추가 작업 필요 없음.

            // 플레이어의 현재 바이옴 상태 업데이트
            playerInBiome.put(playerId, currentBiomeKey);
        }
    }

    /**
     * 특정 바이옴에 진입했을 때의 로직을 처리합니다 (메시지, 효과, 좌표 설정).
     * @param player 플레이어
     * @param biomeKey 진입한 바이옴의 키 (config 키)
     * @param currentTime 현재 시간 (밀리초)
     * @param biomeCoordsMap 바이옴 좌표 설정 맵
     */
    private void handleBiomeEntry(Player player, String biomeKey, long currentTime, Map<String, List<Integer>> biomeCoordsMap) {
        String eventType = biomeKey + "_enter"; // 이벤트 타입 문자열

        // 바이옴별 진입 메시지 및 액션 정의
        Runnable entryAction = switch (biomeKey) {
            case "deep_dark" -> () -> {
                player.sendMessage(Component.text("현재 ")
                        .append(Component.text("Deep Dark").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(" 바이옴에 있으므로"))
                        .append(Component.text(" 어둠 효과").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text("가 적용됩니다.")));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));
            };
            case "desert" -> () -> player.sendMessage(Component.text("현재 ")
                    .append(Component.text("Desert").color(NamedTextColor.YELLOW))
                    .append(Component.text(" 바이옴에 있으므로"))
                    .append(Component.text(" 갈증 속도와 온도가 올라갑니다.")));
            case "swamp" -> () -> player.sendMessage(Component.text("현재 ")
                    .append(Component.text("Swamp").color(NamedTextColor.DARK_GREEN))
                    .append(Component.text(" 바이옴에 있습니다.")));
            default -> null;
            // 다른 관리 대상 바이옴 추가 시 여기에 case 추가
        };

        // 해당 바이옴에 대한 진입 액션이 정의되어 있다면 실행 (메시지 및 쿨다운 처리 포함)
        if (entryAction != null) {
            processMessageAndCooldown(player, eventType, currentTime, entryAction);
        }

        // 진입한 바이옴의 스폰 좌표 설정
        List<Integer> coords = biomeCoordsMap.get(biomeKey);
        if (coords != null && coords.size() == 4) {
            plugin.setZombieSpawnCoordinates(coords.get(0), coords.get(1), coords.get(2), coords.get(3));
            plugin.getLogger().fine(player.getName() + " entered biome " + biomeKey + ". Set spawn coords to " + coords);
        } else {
            // 진입한 바이옴 좌표가 없으면 기본 좌표 사용 시도
            List<Integer> defaultCoords = biomeCoordsMap.get("default");
            if (defaultCoords != null && defaultCoords.size() == 4) {
                plugin.setZombieSpawnCoordinates(defaultCoords.get(0), defaultCoords.get(1), defaultCoords.get(2), defaultCoords.get(3));
                plugin.getLogger().warning("Config missing spawn-coords for biome '" + biomeKey + "'. Using default coords: " + defaultCoords);
            } else {
                plugin.getLogger().severe("Config missing spawn-coords for biome '" + biomeKey + "' and 'default'. Cannot set spawn coordinates!");
            }
        }
    }

    /**
     * 특정 바이옴에서 이탈했을 때의 로직을 처리합니다 (메시지, 효과 제거, 기본 좌표 설정).
     * @param player 플레이어
     * @param biomeKey 이탈한 바이옴의 키 (config 키)
     * @param currentTime 현재 시간 (밀리초)
     * @param biomeCoordsMap 바이옴 좌표 설정 맵
     */
    private void handleBiomeExit(Player player, String biomeKey, long currentTime, Map<String, List<Integer>> biomeCoordsMap) {
        String eventType = biomeKey + "_exit"; // 이벤트 타입 문자열

        // 바이옴별 이탈 메시지 및 액션 정의
        Runnable exitAction = switch (biomeKey) {
            case "deep_dark" -> () -> {
                player.sendMessage(Component.text("     ")
                        .append(Component.text("Deep Dark").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(" 바이옴을 벗어납니다.")));
                player.removePotionEffect(PotionEffectType.DARKNESS); // 어둠 효과 제거
            };
            case "desert" -> () -> player.sendMessage(Component.text("     ")
                    .append(Component.text("Desert").color(NamedTextColor.YELLOW))
                    .append(Component.text(" 바이옴을 벗어납니다.")));
            case "swamp" -> () -> player.sendMessage(Component.text("     ")
                    .append(Component.text("Swamp").color(NamedTextColor.DARK_GREEN))
                    .append(Component.text(" 바이옴을 벗어납니다.")));
            default -> null;
            // 다른 관리 대상 바이옴 추가 시 여기에 case 추가
        };

        // 해당 바이옴에 대한 이탈 액션이 정의되어 있다면 실행 (메시지 및 쿨다운 처리 포함)
        if (exitAction != null) {
            processMessageAndCooldown(player, eventType, currentTime, exitAction);
        }

        // 이탈 시 항상 기본("default") 스폰 좌표로 설정
        List<Integer> defaultCoords = biomeCoordsMap.get("default");
        if (defaultCoords != null && defaultCoords.size() == 4) {
            plugin.setZombieSpawnCoordinates(defaultCoords.get(0), defaultCoords.get(1), defaultCoords.get(2), defaultCoords.get(3));
            plugin.getLogger().fine(player.getName() + " exited biome " + biomeKey + ". Set spawn coords to default: " + defaultCoords);
        } else {
            plugin.getLogger().severe("Config missing spawn-coords for 'default' biome. Cannot reset spawn coordinates on exit from " + biomeKey + "!");
        }
    }


    // handleBiomeChange 메서드는 handleBiomeEntry 와 handleBiomeExit 로 분리되었으므로 제거합니다.
    // private void handleBiomeChange(...) { ... }


    /**
     * 메시지 발송 및 쿨다운 처리를 위한 헬퍼 메서드.
     * 반복적인 메시지 발송을 감지하고 일정 시간 동안 억제합니다.
     * @param player 메시지를 받을 플레이어
     * @param eventType 이벤트 타입 문자열 (예: "deep_dark_enter")
     * @param currentTime 현재 시간 (밀리초)
     * @param messageSender 메시지를 보내는 로직 (Runnable)
     */
    private void processMessageAndCooldown(Player player, String eventType, long currentTime, Runnable messageSender) {
        UUID playerId = player.getUniqueId(); // UUID 사용

        // 메시지 억제 쿨다운 상태인지 확인
        if (isSuppressed(playerId, eventType, currentTime)) {
            return; // 억제 중이면 메시지 보내지 않음
        }

        // 플레이어의 해당 이벤트 타입에 대한 트리거 정보 가져오기
        TriggerInfo info = getTriggerInfo(playerId, eventType);

        // 마지막 트리거 이후 시간 간격 확인
        if (currentTime - info.lastTriggerTime > TRIGGER_WINDOW_MILLIS) {
            info.count = 1; // 시간 창이 지났으면 새로운 반복 시작
        } else {
            info.count++; // 시간 창 내 반복이면 카운트 증가
        }
        info.lastTriggerTime = currentTime; // 마지막 트리거 시간 업데이트

        // 반복 횟수가 임계값에 도달했는지 확인
        if (info.count >= TRIGGER_THRESHOLD) {
            // 임계값 도달: 억제 시작 알림 메시지 발송, 실제 메시지 발송, 쿨다운 설정, 카운트 리셋

            long durationMinutes = TimeUnit.MILLISECONDS.toMinutes(COOLDOWN_DURATION_MILLIS);
            // 억제 시작 알림 (회색으로 표시)
            player.sendMessage(Component.text("반복되는 메세지로 인해, " + durationMinutes + "분간 해당 메시지가 표시되지 않습니다.")
                    .color(NamedTextColor.GRAY));

            messageSender.run(); // 실제 메시지 또는 액션 실행
            setSuppression(playerId, eventType, currentTime + COOLDOWN_DURATION_MILLIS); // 억제 쿨다운 설정
            info.count = 0; // 다음 억제 해제 후 새로운 반복을 위해 카운트 리셋

        } else {
            // 임계값 미만: 일반 메시지/액션 실행
            messageSender.run();
        }
    }

    /**
     * 특정 플레이어의 특정 이벤트 타입에 대한 메시지가 현재 억제 상태인지 확인합니다.
     * @param playerId 플레이어 UUID
     * @param eventType 이벤트 타입 문자열
     * @param currentTime 현재 시간 (밀리초)
     * @return 억제 상태이면 true, 아니면 false
     */
    private boolean isSuppressed(UUID playerId, String eventType, long currentTime) {
        Map<String, Long> playerCooldowns = suppressionCooldowns.get(playerId);
        if (playerCooldowns != null) {
            Long endTime = playerCooldowns.get(eventType);
            // 억제 종료 시간이 존재하고 현재 시간보다 미래이면 억제 중
            return endTime != null && currentTime < endTime;
        }
        return false; // 쿨다운 정보가 없으면 억제 아님
    }

    /**
     * 특정 플레이어의 특정 이벤트 타입에 대한 TriggerInfo 객체를 가져오거나 새로 생성합니다.
     * @param playerId 플레이어 UUID
     * @param eventType 이벤트 타입 문자열
     * @return TriggerInfo 객체
     */
    private TriggerInfo getTriggerInfo(UUID playerId, String eventType) {
        // 플레이어의 트리거 정보 맵이 없으면 새로 생성
        playerTriggerInfo.computeIfAbsent(playerId, k -> new HashMap<>());
        // 해당 이벤트 타입의 정보가 없으면 새로 생성
        return playerTriggerInfo.get(playerId).computeIfAbsent(eventType, k -> new TriggerInfo());
    }

    /**
     * 특정 플레이어의 특정 이벤트 타입에 대한 메시지 억제 쿨다운을 설정합니다.
     * @param playerId 플레이어 UUID
     * @param eventType 이벤트 타입 문자열
     * @param endTime 억제 종료 시간 (밀리초)
     */
    private void setSuppression(UUID playerId, String eventType, long endTime) {
        // 플레이어의 억제 쿨다운 맵이 없으면 새로 생성
        suppressionCooldowns.computeIfAbsent(playerId, k -> new HashMap<>());
        // 해당 이벤트 타입의 억제 종료 시간 설정
        suppressionCooldowns.get(playerId).put(eventType, endTime);
    }
}