package test1.listener;

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
import test1.MyPlugin;

import java.util.*;

public class Weaponability implements Listener {

    // MyPlugin 의존성 추가 (테스트 모드 확인용)
    private final MyPlugin plugin;

    private static final Component TARGET_ITEM_NAME = Component.text("비명들의 힘", NamedTextColor.DARK_AQUA);

    private static final double SPECIAL_ATTACK_PROBABILITY = 0.035;
    private static final long SPECIAL_ATTACK_COOLDOWN_MS = 1000;

    private static final int SONIC_WAVE_RANGE = 5;
    private static final double SONIC_WAVE_DAMAGE = 9;
    private static final double SPECIAL_ATTACK_DAMAGE = 20;

    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    // 무한 루프 방지 잠금
    private final Set<UUID> damageLock = new HashSet<>();

    // [수정] 생성자에서 plugin 주입
    public Weaponability(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        if (damageLock.contains(player.getUniqueId())) return;

        if (!isCustomEchoShard(player.getInventory().getItemInMainHand())) return;

        damageLock.add(player.getUniqueId());
        try {
            createSonicWave(player);

            // [수정] 테스트 모드일 경우 확률 체크 패스 (|| plugin.isProbabilityTestMode())
            boolean shouldTrigger = plugin.isProbabilityTestMode() || (random.nextDouble() < SPECIAL_ATTACK_PROBABILITY);

            if (shouldTrigger && canUseSpecialAttack(player)) {
                performSpecialAttack(player);
                startCooldown(player);
            }
        } finally {
            damageLock.remove(player.getUniqueId());
        }
    }

    private boolean isCustomEchoShard(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && TARGET_ITEM_NAME.equals(meta.displayName());
    }

    private void createSonicWave(Player player) {
        World world = player.getWorld();
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize();

        Location particleLoc = startLoc.clone();
        Vector step = direction.clone().multiply(0.5);
        for (int i = 0; i < SONIC_WAVE_RANGE * 2; i++) {
            particleLoc.add(step);
            world.spawnParticle(Particle.SONIC_BOOM, particleLoc, 1, 0, 0, 0, 0);
        }
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);

        for (Entity entity : world.getNearbyEntities(startLoc, SONIC_WAVE_RANGE, SONIC_WAVE_RANGE, SONIC_WAVE_RANGE)) {
            if (entity instanceof LivingEntity target && !entity.equals(player)) {
                Location targetLoc = target.getLocation().add(0, target.getHeight() / 2.0, 0);
                Vector toTarget = targetLoc.toVector().subtract(startLoc.toVector());

                if (toTarget.lengthSquared() > SONIC_WAVE_RANGE * SONIC_WAVE_RANGE) continue;

                double dot = toTarget.dot(direction);
                if (dot < 0) continue;

                Vector closestPoint = direction.clone().multiply(dot);
                if (toTarget.distance(closestPoint) <= 1.2) {
                    target.damage(SONIC_WAVE_DAMAGE, player);
                }
            }
        }
    }

    private void performSpecialAttack(Player player) {
        World world = player.getWorld();
        // 테스트 모드일 때 메시지를 다르게 보여줄 수도 있음
        if (plugin.isProbabilityTestMode()) {
            player.sendMessage(Component.text("⚡ [TEST] 특수 공격 100% 발동!", NamedTextColor.GOLD));
        } else {
            player.sendMessage(Component.text("❗ 특수 공격 발동!", NamedTextColor.AQUA));
        }
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        double[] angles = {72, 144, 216, 288, 360};

        for (double angle : angles) {
            double rad = Math.toRadians(angle);
            Vector dir = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize();

            Location pLoc = player.getEyeLocation().add(dir.clone().multiply(2));
            Vector pStep = dir.clone();

            for(int i=0; i<5; i++) {
                pLoc.add(pStep);
                pLoc.setY(player.getLocation().getY() + 1);
                world.spawnParticle(Particle.SONIC_BOOM, pLoc, 5, 0.2, 0.2, 0.2, 0.1);
            }
        }

        double searchRadius = 7.0;
        for (Entity entity : world.getNearbyEntities(player.getLocation(), searchRadius, 3, searchRadius)) {
            if (entity instanceof LivingEntity target && !entity.equals(player)) {
                Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector());
                toTarget.setY(0);
                double dist = toTarget.length();

                if (dist > 7 || dist < 2) continue;

                target.damage(SPECIAL_ATTACK_DAMAGE, player);
            }
        }
    }

    private boolean canUseSpecialAttack(Player player) {
        long now = System.currentTimeMillis();
        return !cooldowns.containsKey(player.getUniqueId()) || (now - cooldowns.get(player.getUniqueId()) >= SPECIAL_ATTACK_COOLDOWN_MS);
    }

    private void startCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
}