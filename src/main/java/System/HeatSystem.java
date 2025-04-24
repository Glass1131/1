package System;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class HeatSystem {
    // 온도 기본값: 사막 바이옴이 아닐 때 시작 온도 (0: 정상)
    private static final int DEFAULT_TEMPERATURE = 0;
    // 사막 바이옴 기본 온도 (1: 더움)
    private static final int DESERT_TEMPERATURE = 1;
    // 주변 블록에 의한 온도 변화량
    private static final int ICE_TEMPERATURE_CHANGE = -1;
    private static final int LAVA_TEMPERATURE_CHANGE = 2;
    private static final int FURNACE_CAMPFIRE_TEMPERATURE_CHANGE = 1;
    private static final int SOUL_CAMPFIRE_TEMPERATURE_CHANGE = 2;

    // 포션 효과 지속 시간 (틱)
    private static final int POTION_EFFECT_DURATION_TICKS = 50;
    private static final int TEMPERATURE_VERY_COLD_THRESHOLD = -2;
    private static final int TEMPERATURE_HOT = 1;
    private static final int TEMPERATURE_VERY_HOT_THRESHOLD = 2;

    // 주변 블록 탐색 반경
    private static final int NEARBY_BLOCK_RADIUS = 4;
    // 온도 업데이트 작업 간격 (틱)
    private static final long TEMPERATURE_TASK_INTERVAL_TICKS = 30L; // 1초 (20틱)

    private final JavaPlugin plugin;
    private final Map<Player, Integer> temperatureLevels = new HashMap<>();
    // 0: 매우 추움 (온도 <= -2)
    // 1: 추움     (온도 -1)
    // 2: 정상     (온도 0)
    // 3: 더움     (온도 1)
    // 4: 매우 더움 (온도 >= 2)
    private final String[] temperatureStates = {"매우 추움", "추움", "정상", "더움", "매우 더움"};


    public HeatSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        startTemperatureTask();
    }

    private void startTemperatureTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 관전자 모드인 플레이어는 온도 업데이트 로직을 건너뛰도록 합니다.
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        // 관전자가 되면 온도 레벨을 기본값으로 리셋 (선택 사항)
                        temperatureLevels.put(player, DEFAULT_TEMPERATURE);
                        continue; // 관전자는 온도 업데이트를 하지 않음
                    }
                    updateTemperature(player);
                }
            }
        }.runTaskTimer(plugin, 0L, TEMPERATURE_TASK_INTERVAL_TICKS); // 1초마다 실행
    }

    private void updateTemperature(Player player) {
        int temp = DEFAULT_TEMPERATURE; // 기본값: 0 (정상)
        Location loc = player.getLocation();

        // 사막 바이옴이면 기본값을 더움(1)으로 설정
        if (player.getWorld().getBiome(loc) == Biome.DESERT) {
            temp = DESERT_TEMPERATURE;
        }

        // 온도 변화량 계산
        int tempChange = calculateTemperatureChange(loc);

        // 물에 들어갔을 경우 온도 한 단계 낮추기
        if (player.isInWater()) {
            tempChange -= 1;
        }

        temp = temp + tempChange; // 최종 온도 계산

        temperatureLevels.put(player, temp);
        applyEffects(player, temp);
    }

    // 특정 블록의 영향을 계산하는 메서드 (각 타입별 온도 변화량 한 번만 합산)
    private int calculateTemperatureChange(Location loc) {
        // 발견된 온도 변화를 일으키는 블록 타입을 저장할 Set
        Set<Material> foundTemperatureMaterials = new HashSet<>();
        World world = loc.getWorld(); // 월드를 한 번만 가져옵니다.
        int baseX = loc.getBlockX(); // 플레이어의 정수 좌표를 미리 계산합니다.
        int baseY = loc.getBlockY();
        int baseZ = loc.getBlockZ();

        // 주변 NEARBY_BLOCK_RADIUS 범위 내의 블록 타입 확인 및 Set 에 추가
        for (int x = -NEARBY_BLOCK_RADIUS; x <= NEARBY_BLOCK_RADIUS; x++) {
            for (int y = -NEARBY_BLOCK_RADIUS; y <= NEARBY_BLOCK_RADIUS; y++) {
                for (int z = -NEARBY_BLOCK_RADIUS; z <= NEARBY_BLOCK_RADIUS; z++) {
                    // Location 객체 생성 대신 world.getBlockAt 사용
                    Material type = world.getBlockAt(baseX + x, baseY + y, baseZ + z).getType();
                    // 온도 변화를 일으키는 블록 타입인지 확인하고 Set 에 추가
                    if (type == Material.ICE ||
                            type == Material.LAVA ||
                            type == Material.FURNACE ||
                            type == Material.CAMPFIRE ||
                            type == Material.SOUL_CAMPFIRE) {
                        foundTemperatureMaterials.add(type);
                    }
                }
            }
        }
        return getTempChange(foundTemperatureMaterials);
    }

    private int getTempChange(Set<Material> foundTemperatureMaterials) {
        int tempChange = 0;
        // 발견된 온도 변화 블록 타입 Set 을 기반으로 최종 온도 변화량 계산
        // Set 에는 중복된 Material 타입이 없습니다.
        // 각 Material 타입별로 해당 온도 변화량을 더합니다.
        for (Material type : foundTemperatureMaterials) {
            if (type == Material.ICE) {
                tempChange += ICE_TEMPERATURE_CHANGE;
            } else if (type == Material.LAVA) {
                tempChange += LAVA_TEMPERATURE_CHANGE;
            } else if (type == Material.FURNACE) { // FURNACE 가 Set 에 있다면 추가
                tempChange += FURNACE_CAMPFIRE_TEMPERATURE_CHANGE;
            } else if (type == Material.CAMPFIRE) { // CAMPFIRE 가 Set 에 있다면 추가
                tempChange += FURNACE_CAMPFIRE_TEMPERATURE_CHANGE; // FURNACE 와 같은 상수지만, 별도로 더함
            } else if (type == Material.SOUL_CAMPFIRE) { // 영혼 모닥불이 Set 에 있다면 추가
                tempChange += SOUL_CAMPFIRE_TEMPERATURE_CHANGE;
            }
        }
        return tempChange;
    }

    private void applyEffects(Player player, int temp) {

        // "더움" 효과 부여 (온도 == TEMPERATURE_HOT 즉 1일 때)
        if (temp == TEMPERATURE_HOT) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, POTION_EFFECT_DURATION_TICKS,0, false, false)); // 구속 I (레벨 0)
        }

        // "매우 더움" 효과 부여 (온도 >= TEMPERATURE_VERY_HOT_THRESHOLD 즉 2 이상일 때)
        if (temp >= TEMPERATURE_VERY_HOT_THRESHOLD) {
            // 매우 더움 기준 온도(2)를 기준으로 레벨 계산
            // temp 2 -> effectLevel 0 -> 레벨 0 (I)
            // temp 3 -> effectLevel 1 -> 레벨 1 (II)
            int effectLevel = temp - TEMPERATURE_VERY_HOT_THRESHOLD;

            // 구속 효과 강화
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, POTION_EFFECT_DURATION_TICKS, effectLevel, false, false));

            // 약화 효과 강화
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, POTION_EFFECT_DURATION_TICKS, effectLevel, false, false));
        }

        // "매우 추움" 효과 부여 (온도 <= TEMPERATURE_VERY_COLD_THRESHOLD 즉 -2 이하일 때)
        if (temp <= TEMPERATURE_VERY_COLD_THRESHOLD) {
            // 매우 추움 기준 온도(-2)를 기준으로 레벨 계산
            // temp -2 -> effectLevel 0 -> 레벨 0 (I)
            // temp -3 -> effectLevel 1 -> 레벨 1 (II)
            int effectLevel = TEMPERATURE_VERY_COLD_THRESHOLD - temp;

            // 느려짐 효과
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, POTION_EFFECT_DURATION_TICKS, effectLevel, false, false));
            // 약화 효과
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, POTION_EFFECT_DURATION_TICKS, effectLevel, false, false));
        }
    }


    public String getTemperatureState(Player player) {
        int temp = temperatureLevels.getOrDefault(player, DEFAULT_TEMPERATURE);

        // 온도가 TEMPERATURE_VERY_HOT_THRESHOLD (2)보다 큰 경우 "매우 더움" 뒤에 "!" 추가
        if (temp > TEMPERATURE_VERY_HOT_THRESHOLD) {
            return "매우 더움" + "!".repeat(temp - TEMPERATURE_VERY_HOT_THRESHOLD); // 3 이상일 경우 '!' 추가
        }
        // 온도가 TEMPERATURE_VERY_COLD_THRESHOLD (-2) 이하인 경우 "매우 추움" 뒤에 "!" 추가
        else if (temp < TEMPERATURE_VERY_COLD_THRESHOLD) {
            return "매우 추움" + "!".repeat(TEMPERATURE_VERY_COLD_THRESHOLD - temp); // -3 이하일 경우 '!' 추가
        }
        else {
            // 이 else 블록에 들어오는 temp 값의 범위는 -2 <= temp <= 2 입니다.
            // 온도 레벨과 temperatureStates 배열의 인덱스를 매핑합니다.
            // 배열 인덱스: 0:매우 추움, 1:추움, 2:정상, 3:더움, 4:매우 더움
            // 온도 레벨:   -2,        -1,    0,     1,     2
            // 매핑 규칙: index = temp + 2
            int stateIndex = temp + 2;

            // 계산된 인덱스가 배열 범위를 벗어나지 않도록 안전장치를 추가합니다.
            // 이 else 블록 범위 (-2 ~ 2) 와 배열 길이 (5, 인덱스 0 ~ 4)를 고려한 안전 장치
            stateIndex = Math.max(0, Math.min(temperatureStates.length - 1, stateIndex));

            return temperatureStates[stateIndex]; // 계산된 stateIndex 를 사용하여 배열 접근
        }
    }
}