// BiomeNotifier.java - Code without the playerBiomeToStateKey method

package org.blog.test;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class BiomeNotifier extends BukkitRunnable {
    private final MyPlugin plugin;
    private final Biome deepDarkBiome = Biome.DEEP_DARK; // Deep Dark 바이옴
    private final Biome desertBiome = Biome.DESERT; // 사막 바이옴
    private final Biome swampBiome = Biome.SWAMP; // 늪지대 바이옴
    private final Map<Player, String> playerInBiome = new HashMap<>(); // 플레이어의 바이옴 상태 추적

    // 3번 이상 반복되는 메시지 쿨다운 관련 필드
    private final Map<Player, Map<String, TriggerInfo>> playerTriggerInfo = new HashMap<>();
    private final Map<Player, Map<String, Long>> suppressionCooldowns = new HashMap<>();
    private static final long TRIGGER_WINDOW_MILLIS = 5000; // 메시지 반복 감지 시간 창 (5초 = 5000 밀리초)
    private static final int TRIGGER_THRESHOLD = 3; // 메시지 반복 횟수 임계값 (3번)
    private static final long COOLDOWN_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(1); // 메시지 억제 지속 시간 (1분 = 60000 밀리초)

    // 메시지 트리거 정보를 저장하는 내부 클래스
    private static class TriggerInfo {
        int count = 0; // 반복 횟수
        long lastTriggerTime = 0; // 마지막 트리거 시간 (밀리초)
    }


    public BiomeNotifier(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // 주기적으로 오프라인 플레이어 정보를 정리 (메모리 누수 방지)
        playerInBiome.keySet().removeIf(player -> !player.isOnline());
        playerTriggerInfo.keySet().removeIf(player -> !player.isOnline());
        suppressionCooldowns.keySet().removeIf(player -> !player.isOnline());


        for (Player player : Bukkit.getOnlinePlayers()) {
            // 관전 모드 플레이어는 무시
            if (player.getGameMode() == GameMode.SPECTATOR) continue;

            World world = player.getWorld();
            Biome playerBiome = world.getBiome(player.getLocation()); // 플레이어 현재 위치의 바이옴
            long currentTime = System.currentTimeMillis(); // 현재 시스템 시간 (밀리초)

            // Deep Dark 바이옴 진입/이탈 감지 및 메시지/액션 처리
            handleBiomeChange(player, playerBiome, deepDarkBiome, "deep_dark", currentTime,
                    () -> { // Deep Dark 진입 메시지 전송 로직
                        player.sendMessage(Component.text("현재 ")
                                .append(Component.text("Deep Dark").color(NamedTextColor.DARK_GRAY))
                                .append(Component.text(" 바이옴에 있으므로"))
                                .append(Component.text(" 어둠 효과").color(NamedTextColor.DARK_GRAY))
                                .append(Component.text("가 적용됩니다.")));
                    },
                    () -> { // Deep Dark 진입 시 실행될 액션 (메시지 발송 여부와 무관)
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));
                        plugin.setZombieSpawnCoordinates(-225, -258, -119, -153);
                    },
                    () -> { // Deep Dark 이탈 메시지 전송 로직
                        player.sendMessage(Component.text("     ")
                                .append(Component.text("Deep Dark").color(NamedTextColor.DARK_GRAY))
                                .append(Component.text(" 바이옴을 벗어납니다.")));
                    },
                    () -> { // Deep Dark 이탈 시 실행될 액션 (메시지 발송 여부와 무관)
                        player.removePotionEffect(PotionEffectType.DARKNESS);
                        plugin.setZombieSpawnCoordinates(-258, -341, 80, 14);
                    }
            );

            // 사막(Desert) 바이옴 진입/이탈 감지 및 메시지/액션 처리
            handleBiomeChange(player, playerBiome, desertBiome, "desert", currentTime,
                    () -> { // Desert 진입 메시지 전송 로직
                        player.sendMessage(Component.text("현재 ")
                                .append(Component.text("Desert").color(NamedTextColor.YELLOW))
                                .append(Component.text(" 바이옴에 있으므로"))
                                .append(Component.text(" 갈증 속도와 온도가 올라갑니다."))); //플레이어가 말하는 대사처럼 바꾸고 싶네
                    },
                    () -> { // Desert 진입 시 실행될 액션
                        plugin.setZombieSpawnCoordinates(-214, -180, -153, -119);
                    },
                    () -> { // Desert 이탈 메시지 전송 로직
                        player.sendMessage(Component.text("     ")
                                .append(Component.text("Desert").color(NamedTextColor.YELLOW))
                                .append(Component.text(" 바이옴을 벗어납니다.")));
                    },
                    () -> { // Desert 이탈 시 실행될 액션
                        plugin.setZombieSpawnCoordinates(-258, -341, 80, 14);
                    }
            );

            // 늪지대(Swamp) 바이옴 진입/이탈 감지 및 메시지/액션 처리
            handleBiomeChange(player, playerBiome, swampBiome, "swamp", currentTime,
                    () -> { // Swamp 진입 메시지 전송 로직
                        player.sendMessage(Component.text("현재 ")
                                .append(Component.text("Swamp").color(NamedTextColor.DARK_GREEN))
                                .append(Component.text(" 바이옴에 있습니다.")));
                    },
                    () -> { // Swamp 진입 시 실행될 액션
                        plugin.setZombieSpawnCoordinates(-264, -299, -153, -119); // 사용자 마지막 제공 좌표
                    },
                    () -> { // Swamp 이탈 메시지 전송 로직
                        player.sendMessage(Component.text("     ")
                                .append(Component.text("Swamp").color(NamedTextColor.DARK_GREEN))
                                .append(Component.text(" 바이옴을 벗어납니다.")));
                    },
                    () -> { // Swamp 이탈 시 실행될 액션
                        plugin.setZombieSpawnCoordinates(-258, -341, 80, 14);
                    }
            );
        }
    }

    // 👇 바이옴 진입/이탈 감지 및 메시지/액션/쿨다운 처리를 위한 헬퍼 메서드
    private void handleBiomeChange(Player player, Biome currentPlayerBiome, Biome targetBiome, String biomeStateKey, long currentTime,
                                   Runnable entryMessage, Runnable entryAction, Runnable exitMessage, Runnable exitAction) {

        String previousState = playerInBiome.getOrDefault(player, "default"); // 이전 바이옴 상태 가져오기

        // 진입 감지: 현재 바이옴이 타겟 바이옴이고, 이전 상태가 타겟 바이옴 상태가 아니었던 경우
        if (currentPlayerBiome == targetBiome && !previousState.equals(biomeStateKey)) {
            String eventType = biomeStateKey + "_enter"; // 진입 이벤트 타입 문자열 생성
            processMessageAndCooldown(player, eventType, currentTime, entryMessage); // 메시지 및 쿨다운 처리
            entryAction.run(); // 진입 시 액션 실행 (메시지 억제 여부와 무관)
            playerInBiome.put(player, biomeStateKey); // 플레이어 바이옴 상태 업데이트 (메시지 억제 여부와 무관)

        }
        // 이탈 감지: 이전 상태가 타겟 바이옴 상태였고, 현재 바이옴이 타겟 바이옴이 아닌 경우
        else if (previousState.equals(biomeStateKey) && currentPlayerBiome != targetBiome) {
            String eventType = biomeStateKey + "_exit"; // 이탈 이벤트 타입 문자열 생성
            processMessageAndCooldown(player, eventType, currentTime, exitMessage); // 메시지 및 쿨다운 처리
            exitAction.run(); // 이탈 시 액션 실행 (메시지 억제 여부와 무관)
            // 이탈 시 상태를 기본값("default")으로 업데이트
            playerInBiome.put(player, "default"); // 플레이어 바이옴 상태 업데이트 (메시지 억제 여부와 무관)
        }
    }


    // 👇 메시지 발송 및 쿨다운 처리를 위한 헬퍼 메서드
    private void processMessageAndCooldown(Player player, String eventType, long currentTime, Runnable messageSender) {
        if (!isSuppressed(player, eventType, currentTime)) {
            TriggerInfo info = getTriggerInfo(player, eventType);

            // 이전 트리거와의 시간 간격이 설정된 시간 창보다 크면 새로운 반복으로 간주하여 카운트 리셋
            if (currentTime - info.lastTriggerTime > TRIGGER_WINDOW_MILLIS) {
                info.count = 1; // 새로운 반복 시작, 카운트 1로 설정
            } else {
                info.count++; // 시간 창 내에서 반복 중, 카운트 증가
            }
            info.lastTriggerTime = currentTime; // 마지막 트리거 시간 업데이트

            // 반복 횟수가 임계값에 도달했는지 확인
            if (info.count >= TRIGGER_THRESHOLD) {
                // 반복 임계값 도달: 메시지 발송, 억제 쿨다운 설정, 반복 카운트 리셋
                messageSender.run(); // 메시지 발송 (람다 함수 실행)
                setSuppression(player, eventType, currentTime + COOLDOWN_DURATION_MILLIS); // 억제 쿨다운 설정
                info.count = 0; // 다음 억제 후 새로운 반복을 위해 카운트 리셋
            } else {
                messageSender.run();
            }
        }
    }


    // 👇 메시지 억제 상태인지 확인하는 헬퍼 메서드
    private boolean isSuppressed(Player player, String eventType, long currentTime) {
        Map<String, Long> playerCooldowns = suppressionCooldowns.get(player);
        if (playerCooldowns != null) {
            Long endTime = playerCooldowns.get(eventType);
            return endTime != null && currentTime < endTime; // 현재 시간이 억제 종료 시간 이전이면 억제 중
        }
        // 맵이 없거나, 이벤트 타입 쿨다운 정보가 없거나, 쿨다운 시간이 지났으면 억제 아님
        return false;
    }

    // 👇 메시지 트리거 정보를 가져오거나 플레이어/이벤트 타입이 처음인 경우 새로 생성하는 헬퍼 메서드
    private TriggerInfo getTriggerInfo(Player player, String eventType) {
        // 해당 플레이어의 트리거 정보 맵이 없으면 (playerTriggerInfo에) 새로 생성하여 반환
        playerTriggerInfo.computeIfAbsent(player, k -> new HashMap<>());
        // 해당 플레이어의 맵에서 해당 이벤트 타입의 트리거 정보가 없으면 새로 생성하여 반환
        return playerTriggerInfo.get(player).computeIfAbsent(eventType, k -> new TriggerInfo());
    }

    // 👇 메시지 억제 쿨다운을 설정하는 헬퍼 메서드
    private void setSuppression(Player player, String eventType, long endTime) {
        // 해당 플레이어의 억제 쿨다운 맵이 없으면 (suppressionCooldowns에) 새로 생성하여 반환
        suppressionCooldowns.computeIfAbsent(player, k -> new HashMap<>());
        // 해당 플레이어의 맵에 해당 이벤트 타입의 억제 종료 시간 설정/업데이트
        suppressionCooldowns.get(player).put(eventType, endTime);
    }
}