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
    private static final double SPECIAL_ATTACK_PROBABILITY = 0.035;
    private static final long SPECIAL_ATTACK_COOLDOWN_MILLISECONDS = 1000;
    private static final int SONIC_WAVE_RANGE = 5;
    private static final double SONIC_WAVE_DAMAGE = 9;
    private static final double SPECIAL_ATTACK_RANGE = 5.0;
    private static final double[] SPECIAL_ATTACK_ANGLES = {72, 144, 216, 288, 360};
    private static final double SPECIAL_ATTACK_START_OFFSET = 2;
    private static final int SPECIAL_ATTACK_PARTICLE_COUNT = 10;
    private static final double SPECIAL_ATTACK_DAMAGE = 20;

    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        // 공격한 아이템 확인 (커스텀 이름 검사)
        if (!isCustomEchoShard(player.getInventory().getItemInMainHand())) return;

        // 100% 확률 기본 공격 효과 (바라보는 방향으로 5블록까지 음파 생성)
        createSonicWave(player);

        // 3.5% 확률로 특수 공격 발동 (쿨다운 체크)
        if (random.nextDouble() < SPECIAL_ATTACK_PROBABILITY && canUseSpecialAttack(player)) {
            performSpecialAttack(player);
            startCooldown(player);
        }
    }

    //커스텀 아이템 감지(이름으로 감지)
    private boolean isCustomEchoShard(ItemStack item) {
        if (item.getType() != Material.ECHO_SHARD) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return false;

        return Objects.requireNonNull(meta.displayName()).equals(Component.text("비명들의 힘", NamedTextColor.DARK_AQUA));
    }

    //일반 공격
    private void createSonicWave(Player player) {
        World world = player.getWorld();
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize();

        for (int i = 1; i <= SONIC_WAVE_RANGE; i++) {
            Location particleLoc = startLoc.clone().add(direction.clone().multiply(i));
            world.spawnParticle(Particle.SONIC_BOOM, particleLoc, 5, 0.1, 0.1, 0.1, 0.1);

            // 경로를 따라 피해 적용
            for (Entity entity : world.getNearbyEntities(particleLoc, 0.8, 0.8, 0.8)) {
                if (entity instanceof LivingEntity target && !entity.equals(player)) {
                    target.damage(SONIC_WAVE_DAMAGE);
                }
            }
        }

        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);
    }

    //특수 공격
    private void performSpecialAttack(Player player) {
        World world = player.getWorld();
        // 5방향

        player.sendMessage(Component.text("❗ 특수 공격 발동! 주변 적을 강력한 음파로 공격합니다!", NamedTextColor.AQUA));

        for (double angle : SPECIAL_ATTACK_ANGLES) {
            double rad = Math.toRadians(angle);
            Vector direction = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize();
            Location startLoc = player.getEyeLocation().clone().add(direction.multiply(SPECIAL_ATTACK_START_OFFSET)); // 시작점 설정

            for (int i = 1; i <= SPECIAL_ATTACK_RANGE; i++) {
                Location particleLoc = startLoc.clone().add(direction.clone().multiply(i));
                particleLoc.setY(player.getEyeLocation().getY());  // Y 좌표를 상체 높이로 유지
                world.spawnParticle(Particle.SONIC_BOOM, particleLoc, SPECIAL_ATTACK_PARTICLE_COUNT, 0.3, 0.3, 0.3, 0.2);
                world.playSound(particleLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

                // 경로를 따라 피해 적용
                for (Entity entity : world.getNearbyEntities(particleLoc, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity livingEntity && !entity.equals(player)) {
                        livingEntity.damage(SPECIAL_ATTACK_DAMAGE);
                    }
                }
            }
        }
    }

    private boolean canUseSpecialAttack(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // 1초 (밀리초)
        return !cooldowns.containsKey(playerId) || (currentTime - cooldowns.get(playerId)) >= SPECIAL_ATTACK_COOLDOWN_MILLISECONDS;
    }

    private void startCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
}