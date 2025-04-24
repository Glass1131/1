package org.blog.test;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BiomeNotifier extends BukkitRunnable {
    private final MyPlugin plugin;

    private final Map<Player, String> playerInBiome = new HashMap<>(); // 플레이어의 바이옴 상태 추적 (config 키 문자열 사용)

    // 3번 이상 반복되는 메시지 쿨다운 관련 필드 (유지)
    private final Map<Player, Map<String, TriggerInfo>> playerTriggerInfo = new HashMap<>();
    private final Map<Player, Map<String, Long>> suppressionCooldowns = new HashMap<>();
    private static final long TRIGGER_WINDOW_MILLIS = 5000; // 메시지 반복 감지 시간 창 (5초 = 5000 밀리초)
    private static final int TRIGGER_THRESHOLD = 3; // 메시지 반복 횟수 임계값 (3번)
    private static final long COOLDOWN_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(1); // 메시지 억제 지속 시간 (1분 = 60000 밀리초)

    // 메시지 트리거 정보를 저장하는 내부 클래스 (유지)
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

        // 👇 MyPlugin 에서 config 로드된 바이옴 좌표 맵을 가져옴 (getter 사용)
        Map<String, List<Integer>> biomeCoordsMap = plugin.getConfigBiomeSpawnCoords();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 관전 모드 플레이어는 무시
            if (player.getGameMode() == GameMode.SPECTATOR) continue;

            World world = player.getWorld();
            Location playerLoc = player.getLocation(); // 플레이어 위치 가져오기
            Biome playerBiome = world.getBiome(playerLoc); // 플레이어 현재 위치의 바이옴
            long currentTime = System.currentTimeMillis(); // 현재 시스템 시간 (밀리초)

            // 현재 플레이어 바이옴의 config 키 문자열을 가져옴 (예: "deep_dark")
            String currentPlayerBiomeKey = playerBiome.getKey().getKey().toLowerCase();


            // Biome enum 대신 config 키 문자열("deep_dark", "desert", "swamp")과 config 맵을 handleBiomeChange 에 전달
            // handleBiomeChange 메서드 내부에서 config 맵을 이용해 좌표를 조회하고 설정합니다.

            // Deep Dark 바이옴 처리 (config 키 "deep_dark")
            handleBiomeChange(player, currentPlayerBiomeKey, "deep_dark", currentTime,
                    () -> { // Deep Dark 진입 메시지
                        player.sendMessage(Component.text("현재 ")
                                .append(Component.text("Deep Dark").color(NamedTextColor.DARK_GRAY))
                                .append(Component.text(" 바이옴에 있으므로"))
                                .append(Component.text(" 어둠 효과").color(NamedTextColor.DARK_GRAY))
                                .append(Component.text("가 적용됩니다.")));
                        // Deep Dark 진입 액션 (어둠 효과 부여) - 좌표 설정은 handleBiomeChange 안에서 공통 처리
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));
                    },
                    () -> { // Deep Dark 이탈 메시지
                        player.sendMessage(Component.text("     ")
                                .append(Component.text("Deep Dark").color(NamedTextColor.DARK_GRAY))
                                .append(Component.text(" 바이옴을 벗어납니다.")));
                        // Deep Dark 이탈 액션 (어둠 효과 제거) - 좌표 설정은 handleBiomeChange 안에서 공통 처리
                        player.removePotionEffect(PotionEffectType.DARKNESS);
                    },
                    biomeCoordsMap // config 좌표 맵 전달
            );

            // 사막(Desert) 바이옴 처리 (config 키 "desert")
            handleBiomeChange(player, currentPlayerBiomeKey, "desert", currentTime,
                    () -> { // Desert 진입 메시지
                        player.sendMessage(Component.text("현재 ")
                                .append(Component.text("Desert").color(NamedTextColor.YELLOW))
                                .append(Component.text(" 바이옴에 있으므로"))
                                .append(Component.text(" 갈증 속도와 온도가 올라갑니다.")));
                        // Desert 진입 액션은 메시지 외에 없음 - 좌표 설정은 handleBiomeChange 안에서 공통 처리
                    },
                    () -> { // Desert 이탈 메시지
                        player.sendMessage(Component.text("     ")
                                .append(Component.text("Desert").color(NamedTextColor.YELLOW))
                                .append(Component.text(" 바이옴을 벗어납니다.")));
                        // Desert 이탈 액션은 메시지 외에 없음 - 좌표 설정은 handleBiomeChange 안에서 공통 처리
                    },
                    biomeCoordsMap // config 좌표 맵 전달
            );

            // 늪지대(Swamp) 바이옴 처리 (config 키 "swamp")
            handleBiomeChange(player, currentPlayerBiomeKey, "swamp", currentTime,
                    () -> { // Swamp 진입 메시지
                        player.sendMessage(Component.text("현재 ")
                                .append(Component.text("Swamp").color(NamedTextColor.DARK_GREEN))
                                .append(Component.text(" 바이옴에 있습니다.")));
                        // Swamp 진입 액션은 메시지 외에 없음 - 좌표 설정은 handleBiomeChange 안에서 공통 처리
                    },
                    () -> { // Swamp 이탈 메시지
                        player.sendMessage(Component.text("     ")
                                .append(Component.text("Swamp").color(NamedTextColor.DARK_GREEN))
                                .append(Component.text(" 바이옴을 벗어납니다.")));
                        // Swamp 이탈 액션은 메시지 외에 없음 - 좌표 설정은 handleBiomeChange 안에서 공통 처리
                    },
                    biomeCoordsMap // config 좌표 맵 전달
            );

            // TODO: 다른 바이옴 추가 시 여기에 handleBiomeChange 호출 추가

            // 플레이어가 위에 명시된 특정 바이옴들(Deep Dark, Desert, Swamp 등)이 아닌
            // 다른 모든 바이옴("default" 상태)에 있을 때의 좌표 설정 로직도 필요합니다.
            // handleBiomeChange 에서 해당 바이옴이 아닌 경우 자동으로 "default" 상태로 간주하고,
            // 이탈 시 "default" 좌표를 설정하는 방식으로 처리됩니다.
            // Deep Dark, Desert, Swamp 바이옴에서 이탈 시 "default" 좌표로 설정됩니다.
            // 처음 접속 시 플레이어가 이 3가지 바이옴 외의 다른 곳에 있다면 초기 상태가 "default" 이고,
            // 명시적으로 setZombieSpawnCoordinates(default_coords) 를 호출하는 로직이 필요할 수 있습니다.
            // 현재 run() 메서드는 주기적으로 돌면서 handleBiomeChange 를 호출하고, handleBiomeChange 는
            // 이전 상태와 현재 상태를 비교하여 진입/이탈을 감지합니다.
            // 플레이어가 처음 서버에 접속했거나 상태가 "default"이고 현재 바이옴이 명시된 바이옴이 아닐 때
            // "default" 좌표를 설정하는 로직이 부족할 수 있습니다.
            // 이 부분은 BiomeNotifier 의 초기화 또는 플레이어 접속 이벤트 등에서
            // 현재 바이옴에 맞는 초기 좌표를 설정하는 로직을 추가하는 것으로 개선할 수 있습니다.
            // (현재는 명시된 바이옴에서 이탈할 때만 default 좌표가 설정됨)

            // 일단 현재 구조를 최대한 유지하며 config 적용에 집중합니다.
            // handleBiomeChange 의 이탈 로직에서 default 좌표를 설정하므로,
            // 명시된 바이옴이 아닌 곳에서는 자연스럽게 default 좌표가 유지될 것입니다.


        } // for Player loop
    } // run method


    private void handleBiomeChange(Player player, String currentPlayerBiomeKey, String targetBiomeKey, long currentTime,
                                   Runnable entryMessage, Runnable exitMessage,
                                   Map<String, List<Integer>> biomeCoordsMap) {

        String previousState = playerInBiome.getOrDefault(player, "default"); // 이전 바이옴 상태 가져오기

        // 진입 감지: 현재 바이옴이 타겟 바이옴이고, 이전 상태가 타겟 바이옴 상태가 아니었던 경우
        // currentPlayerBiomeKey 와 targetBiomeKey 문자열 비교
        if (currentPlayerBiomeKey.equals(targetBiomeKey) && !previousState.equals(targetBiomeKey)) {
            String eventType = targetBiomeKey + "_enter"; // 진입 이벤트 타입 문자열 생성
            processMessageAndCooldown(player, eventType, currentTime, entryMessage); // 메시지 및 쿨다운 처리

            // 👇 진입 시 액션: config 에서 해당 바이옴 좌표를 찾아 설정
            List<Integer> coords = biomeCoordsMap.get(targetBiomeKey); // 타겟 바이옴(진입한 바이옴)의 좌표 조회
            if (coords != null && coords.size() == 4) {
                plugin.setZombieSpawnCoordinates(coords.get(0), coords.get(1), coords.get(2), coords.get(3));
                plugin.getLogger().fine(player.getName() + " entered biome " + targetBiomeKey + ". Set spawn coords to " + coords);
            } else {
                // 타겟 바이옴의 좌표 설정이 config에 없는 경우, 기본 좌표("default") 사용 시도
                List<Integer> defaultCoords = biomeCoordsMap.get("default");
                if (defaultCoords != null && defaultCoords.size() == 4) {
                    plugin.setZombieSpawnCoordinates(defaultCoords.get(0), defaultCoords.get(1), defaultCoords.get(2), defaultCoords.get(3));
                    plugin.getLogger().warning("Config missing spawn-coords for biome '" + targetBiomeKey + "'. Using default coords: " + defaultCoords);
                } else {
                    plugin.getLogger().severe("Config missing spawn-coords for biome '" + targetBiomeKey + "' and 'default'. Cannot set spawn coordinates!");
                    // 심각한 오류이므로 로그를 남기고 좌표 변경을 건너뜁니다.
                }
            }

            playerInBiome.put(player, targetBiomeKey); // 플레이어 바이옴 상태 업데이트

        }
        // 이탈 감지: 이전 상태가 타겟 바이옴 상태였고, 현재 바이옴이 타겟 바이옴이 아닌 경우
        // previousState 와 targetBiomeKey 문자열 비교
        else if (previousState.equals(targetBiomeKey) && !currentPlayerBiomeKey.equals(targetBiomeKey)) {
            String eventType = targetBiomeKey + "_exit"; // 이탈 이벤트 타입 문자열 생성
            processMessageAndCooldown(player, eventType, currentTime, exitMessage); // 메시지 및 쿨다운 처리

            // 👇 이탈 시 액션: 기본 좌표("default")로 설정
            List<Integer> defaultCoords = biomeCoordsMap.get("default");
            if (defaultCoords != null && defaultCoords.size() == 4) {
                plugin.setZombieSpawnCoordinates(defaultCoords.get(0), defaultCoords.get(1), defaultCoords.get(2), defaultCoords.get(3));
                plugin.getLogger().fine(player.getName() + " exited biome " + targetBiomeKey + ". Set spawn coords to default: " + defaultCoords);
            } else {
                plugin.getLogger().severe("Config missing spawn-coords for 'default' biome. Cannot reset spawn coordinates on exit from " + targetBiomeKey + "!");
                // 심각한 오류이므로 로그를 남기고 좌표 변경을 건너뜁니다.
            }

            // 이탈 시 상태를 기본값("default")으로 업데이트
            playerInBiome.put(player, "default");
        }
        // 참고: 플레이어가 targetBiomeKey 이외의 다른 바이옴에 있고, previousState도 targetBiomeKey가 아닌 경우
        // (예: Plains -> Forest 이동) 이 handleBiomeChange 호출은 아무 작업도 수행하지 않습니다.
        // 여러 바이옴에 대한 handleBiomeChange 호출이 run 메서드에서 이루어지므로,
        // 해당 플레이어의 현재 바이옴에 대한 handleBiomeChange 호출만 진입 로직을 실행하고,
        // 이전 바이옴에 대한 handleBiomeChange 호출은 이탈 로직을 실행하게 됩니다.
    }


    //메시지 발송 및 쿨다운 처리를 위한 헬퍼 메서드 (유지)

    private void processMessageAndCooldown(Player player, String eventType, long currentTime, Runnable messageSender) {
        // 👇 수정: 메시지 억제 쿨다운 상태인지 가장 먼저 확인하고, 억제 중이면 즉시 종료
        if (isSuppressed(player, eventType, currentTime)) {
            // 억제 중이라면 아무 메시지도 보내지 않고 로직을 더 이상 진행하지 않음
            // getLogger().fine("Message suppressed for player " + player.getName() + " event " + eventType); // 디버그용 로그 (선택 사항)
            return; // 억제 중이므로 여기서 메서드 실행 종료
        }

        // 👇 억제 상태가 아니라면 정상적으로 트리거 정보 업데이트 및 메시지 발송 로직 진행

        TriggerInfo info = getTriggerInfo(player, eventType);

        // 마지막 트리거 이후 시간 간격이 설정된 시간 창보다 크면 새로운 반복으로 간주
        if (currentTime - info.lastTriggerTime > TRIGGER_WINDOW_MILLIS) {
            info.count = 1; // 새로운 반복 시작, 카운트 1로 설정
        } else {
            info.count++; // 시간 창 내에서 반복 중, 카운트 증가
        }
        info.lastTriggerTime = currentTime; // 마지막 트리거 시간 업데이트

        // 반복 횟수가 임계값에 도달했는지 확인
        if (info.count >= TRIGGER_THRESHOLD) {
            // 반복 임계값 도달: 메시지 발송, 억제 쿨다운 설정, 반복 카운트 리셋

            // 👇 추가: 메시지 억제 시작 알림 메시지 발송 (이전 답변에서 추가했던 코드)
            long durationMinutes = TimeUnit.MILLISECONDS.toMinutes(COOLDOWN_DURATION_MILLIS); // 밀리초를 분으로 변환

            // 플레이어에게 메시지 억제가 시작됨을 알림
            player.sendMessage(Component.text("반복되는 메세지로 인해, " + durationMinutes + "분간 해당 메시지가 표시되지 않습니다.")
                    .color(NamedTextColor.GRAY)); // 회색 등 눈에 덜 띄는 색상 사용

            // 일반 메시지 발송 (람다 함수 실행)
            messageSender.run();

            // 억제 쿨다운 설정 (메시지 발송 이후)
            setSuppression(player, eventType, currentTime + COOLDOWN_DURATION_MILLIS);

            // 다음 억제 후 새로운 반복을 위해 카운트 리셋
            info.count = 0;

        } else {
            // 반복 횟수가 임계값에 도달하지 않은 경우: 일반 메시지만 발송
            messageSender.run(); // 메시지 발송 (람다 함수 실행)
        }
        // 참고: isSuppressed 체크가 통과되면 (억제 상태가 아니면) 이 블록 끝까지 실행됩니다.
    }
    // 메시지 억제 상태인지 확인하는 헬퍼 메서드 (유지)
    private boolean isSuppressed(Player player, String eventType, long currentTime) {
        Map<String, Long> playerCooldowns = suppressionCooldowns.get(player);
        if (playerCooldowns != null) {
            Long endTime = playerCooldowns.get(eventType);
            return endTime != null && currentTime < endTime; // 현재 시간이 억제 종료 시간 이전이면 억제 중
        }
        // 맵이 없거나, 이벤트 타입 쿨다운 정보가 없거나, 쿨다운 시간이 지났으면 억제 아님
        return false;
    }

    // 메시지 트리거 정보를 가져오거나 플레이어/이벤트 타입이 처음인 경우 새로 생성하는 헬퍼 메서드
    private TriggerInfo getTriggerInfo(Player player, String eventType) {
        // 해당 플레이어의 트리거 정보 맵이 없으면 (playerTriggerInfo에) 새로 생성하여 반환
        playerTriggerInfo.computeIfAbsent(player, k -> new HashMap<>());
        // 해당 플레이어의 맵에서 해당 이벤트 타입의 트리거 정보가 없으면 새로 생성하여 반환
        return playerTriggerInfo.get(player).computeIfAbsent(eventType, k -> new TriggerInfo());
    }

    // 메시지 억제 쿨다운을 설정하는 헬퍼 메서드 (유지)
    private void setSuppression(Player player, String eventType, long endTime) {
        // 해당 플레이어의 억제 쿨다운 맵이 없으면 (suppressionCooldowns에) 새로 생성하여 반환
        suppressionCooldowns.computeIfAbsent(player, k -> new HashMap<>());
        // 해당 플레이어의 맵에 해당 이벤트 타입의 억제 종료 시간 설정/업데이트
        suppressionCooldowns.get(player).put(eventType, endTime);
    }

    // Biome enum을 config 키 문자열로 변환하는 헬퍼 메서드 (MyPlugin 에서 가져와 여기에 추가)
    // BiomeNotifier 에서 직접 사용할 필요는 없지만, 필요하다면 추가할 수 있습니다.
    // 현재는 run() 메서드에서 Biome 객체의 키를 직접 사용합니다.

    // MyPlugin 에서 setZombieSpawnCoordinates 메서드를 호출할 수 있도록 BiomeNotifier는 MyPlugin 인스턴스를 가집니다.
    // MyPlugin 에서 configBiomeSpawnCoords 맵을 public getter 메서드로 제공하므로 BiomeNotifier는 이를 가져와 사용합니다.
}