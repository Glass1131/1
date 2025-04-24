package org.blog.test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;

public class Weaponability implements Listener {
    // 상수 정의
    private static final double SPECIAL_ATTACK_PROBABILITY = 0.035; // 특수 공격 확률 (3.5%)
    private static final long SPECIAL_ATTACK_COOLDOWN_MILLISECONDS = 1000; // 특수 공격 쿨다운 (1초)
    private static final int SONIC_WAVE_RANGE = 5; // 일반 공격 음파 범위
    private static final double SONIC_WAVE_DAMAGE = 9; // 일반 공격 음파 피해량
    private static final double SPECIAL_ATTACK_RANGE = 5.0; // 특수 공격 음파 범위
    private static final double[] SPECIAL_ATTACK_ANGLES = {72, 144, 216, 288, 360}; // 특수 공격 방향 각도
    private static final double SPECIAL_ATTACK_START_OFFSET = 2; // 특수 공격 시작 오프셋
    private static final int SPECIAL_ATTACK_PARTICLE_COUNT = 10; // 특수 공격 파티클 수
    private static final double SPECIAL_ATTACK_DAMAGE = 20; // 특수 공격 피해량

    // 필드 정의
    private final Random random = new Random(); // 랜덤 객체
    private final Map<UUID, Long> cooldowns = new HashMap<>(); // 플레이어별 쿨다운 저장 맵

    /**
     * 엔티티가 다른 엔티티로부터 피해를 입었을 때 호출되는 이벤트 핸들러
     * @param event EntityDamageByEntityEvent
     */
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        // 공격자가 플레이어가 아니거나 피해자가 LivingEntity가 아니면 리턴
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        // 플레이어가 들고 있는 아이템이 커스텀 에코 샤드인지 확인
        if (!isCustomEchoShard(player.getInventory().getItemInMainHand())) return;

        // 일반 공격 효과 실행 (100% 확률)
        createSonicWave(player);

        // 특수 공격 발동 조건 확인 (확률 및 쿨다운)
        if (random.nextDouble() < SPECIAL_ATTACK_PROBABILITY && canUseSpecialAttack(player)) {
            performSpecialAttack(player); // 특수 공격 실행
            startCooldown(player); // 쿨다운 시작
        }
    }

    /**
     * 주어진 아이템이 커스텀 "비명들의 힘" 에코 샤드인지 확인
     * @param item 확인할 아이템 스택
     * @return 커스텀 아이템이면 true, 아니면 false
     */
    private boolean isCustomEchoShard(ItemStack item) {
        // 아이템 타입 및 메타데이터 유효성 검사
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        // 이름 유효성 검사
        if (!meta.hasDisplayName()) return false;

        // 이름 비교 (Component 사용)
        return Objects.requireNonNull(meta.displayName()).equals(Component.text("비명들의 힘", NamedTextColor.DARK_AQUA));
    }

    /**
     * 일반 공격: 플레이어 전방으로 음파 생성 및 피해 적용
     * @param player 공격을 실행한 플레이어
     */
    private void createSonicWave(Player player) {
        World world = player.getWorld();
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize();
        // 루프 밖에서 Location 객체 클론 (객체 생성 최소화)
        Location particleLoc = startLoc.clone();
        // 루프 밖에서 Vector 객체 클론 (이동 단계를 저장하기 위해)
        Vector step = direction.clone(); // 이동 방향 및 거리 벡터

        // 지정된 범위까지 음파 생성
        for (int i = 1; i <= SONIC_WAVE_RANGE; i++) {
            // 이전 위치에 step 벡터를 더해 다음 위치 계산
            particleLoc.add(step);
            world.spawnParticle(Particle.SONIC_BOOM, particleLoc, 5, 0.1, 0.1, 0.1, 0.1); // 파티클 생성

            // 경로 상의 엔티티에게 피해 적용
            for (Entity entity : world.getNearbyEntities(particleLoc, 0.8, 0.8, 0.8)) {
                if (entity instanceof LivingEntity target && !entity.equals(player)) {
                    target.damage(SONIC_WAVE_DAMAGE); // 피해 적용
                }
            }
        }
        // 효과음 재생
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);
    }

    /**
     * 특수 공격: 플레이어 주변 5방향으로 강력한 음파 생성 및 피해 적용
     * @param player 공격을 실행한 플레이어
     */
    private void performSpecialAttack(Player player) {
        World world = player.getWorld();
        Location playerEyeLoc = player.getEyeLocation(); // 플레이어 눈 위치 미리 가져오기
        double playerY = playerEyeLoc.getY(); // Y 좌표 미리 가져오기

        // 플레이어에게 특수 공격 발동 메시지 전송
        player.sendMessage(Component.text("❗ 특수 공격 발동! 주변 적을 강력한 음파로 공격합니다!", NamedTextColor.AQUA));

        // 지정된 각도로 음파 생성
        for (double angle : SPECIAL_ATTACK_ANGLES) {
            double rad = Math.toRadians(angle);
            // Vector 생성은 바깥 루프에서 한 번만 수행
            Vector direction = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize();
            // 시작 위치 계산 (Vector 곱셈은 여기서 한 번)
            Location startLoc = playerEyeLoc.clone().add(direction.clone().multiply(SPECIAL_ATTACK_START_OFFSET));
            // 안쪽 루프용 Location 객체 클론
            Location particleLoc = startLoc.clone();
            // 안쪽 루프용 이동 벡터
            Vector step = direction.clone();

            // 지정된 범위까지 음파 생성
            for (int i = 1; i <= SPECIAL_ATTACK_RANGE; i++) {
                // 이전 위치에 step 벡터를 더해 다음 위치 계산
                particleLoc.add(step);
                particleLoc.setY(playerY);  // Y 좌표를 플레이어 눈 높이로 유지
                world.spawnParticle(Particle.SONIC_BOOM, particleLoc, SPECIAL_ATTACK_PARTICLE_COUNT, 0.3, 0.3, 0.3, 0.2); // 파티클 생성
                world.playSound(particleLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f); // 효과음 재생

                // 경로 상의 엔티티에게 피해 적용
                for (Entity entity : world.getNearbyEntities(particleLoc, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity livingEntity && !entity.equals(player)) {
                        livingEntity.damage(SPECIAL_ATTACK_DAMAGE); // 피해 적용
                    }
                }
            }
        }
    }

    /**
     * 플레이어가 특수 공격을 사용할 수 있는지 (쿨다운 확인)
     * @param player 확인할 플레이어
     * @return 사용 가능하면 true, 아니면 false
     */
    private boolean canUseSpecialAttack(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // 쿨다운 맵에 플레이어 정보가 없거나, 마지막 사용 시간 + 쿨다운 시간 <= 현재 시간이면 사용 가능
        return !cooldowns.containsKey(playerId) || (cooldowns.get(playerId) + SPECIAL_ATTACK_COOLDOWN_MILLISECONDS) <= currentTime;
    }

    /**
     * 플레이어의 특수 공격 쿨다운 시작 (현재 시간 기록)
     * @param player 쿨다운을 시작할 플레이어
     */
    private void startCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
}