package test1.mob;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.*;

public class BossListener implements Listener {

    private final JavaPlugin plugin;

    // --- [Witch] 설정 및 변수 ---
    private PotionType witchPotionType = PotionType.HARMING;
    private int witchPotionAmplifier = 1;
    private long witchThrowCooldownMillis = 0;
    private boolean witchPlayerOnlyMode = false;
    private boolean witchPreventVanillaThrow = false;

    private final Map<UUID, Long> witchLastThrowTime = new HashMap<>();
    private final Set<UUID> activeWitches = new HashSet<>();
    private final NamespacedKey customWitchPotionKey;
    private boolean isWitchForcedShot = false;

    public BossListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.customWitchPotionKey = new NamespacedKey(plugin, "custom_witch_potion");

        // 마녀 시스템 초기화
        scanExistingWitches();
        startWitchAiTask();
    }

    // ========================================================================
    // [1] 보그드(Bogged) 보스 로직
    // ========================================================================

    @EventHandler
    public void onBossShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)) return;

        if (boss.customName() == null) return;
        String name = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(boss.customName()));

        // 늪지대 보스(진흙에 빠진 사수) 능력: 유도 독화살 2발 추가 발사
        if (name.contains("진흙에 빠진 사수") && boss instanceof Bogged) {
            Player target = getNearestSurvivalPlayer(boss);
            if (target != null) {
                new BukkitRunnable() {
                    int count = 0;
                    @Override
                    public void run() {
                        if (count >= 2 || boss.isDead() || !boss.isValid()) {
                            this.cancel();
                            return;
                        }
                        shootHomingArrow(boss, target);
                        count++;
                    }
                }.runTaskTimer(plugin, 5L, 10L);
            }
        }
    }

    private void shootHomingArrow(LivingEntity shooter, Player target) {
        Location startLoc = shooter.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(startLoc.toVector()).normalize();

        Arrow arrow = shooter.launchProjectile(Arrow.class, direction.multiply(1.5));
        arrow.setShooter(shooter);
        arrow.setCritical(true);
        arrow.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 0), true);
        arrow.setColor(org.bukkit.Color.GREEN);

        shooter.getWorld().playSound(startLoc, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround() || !target.isValid() || target.isDead()) {
                    this.cancel();
                    return;
                }
                if (tick > 60) {
                    this.cancel();
                    return;
                }

                Vector targetDir = target.getEyeLocation().toVector().subtract(arrow.getLocation().toVector()).normalize();
                Vector currentDir = arrow.getVelocity().normalize();
                Vector newDir = currentDir.add(targetDir.multiply(0.2)).normalize();

                arrow.setVelocity(newDir.multiply(arrow.getVelocity().length()));
                arrow.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, arrow.getLocation(), 1, 0, 0, 0, 0);

                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Player getNearestSurvivalPlayer(LivingEntity entity) {
        Player closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                if (p.getWorld().equals(entity.getWorld())) {
                    double dist = p.getLocation().distanceSquared(entity.getLocation());
                    if (dist < minDistance && dist < 2500) {
                        minDistance = dist;
                        closest = p;
                    }
                }
            }
        }
        return closest;
    }

    // ========================================================================
    // [2] 마녀(Witch) 보스/AI 로직 (기존 Witch.java 통합)
    // ========================================================================

    private void scanExistingWitches() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Witch witch : world.getEntitiesByClass(org.bukkit.entity.Witch.class)) {
                activeWitches.add(witch.getUniqueId());
            }
        }
    }

    public void addWitch(UUID uuid) {
        activeWitches.add(uuid);
    }

    private void startWitchAiTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (witchThrowCooldownMillis <= 0 && !witchPreventVanillaThrow) return;
                if (activeWitches.isEmpty()) return;

                Iterator<UUID> iterator = activeWitches.iterator();
                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Entity entity = Bukkit.getEntity(uuid);

                    if (entity == null || !entity.isValid() || !(entity instanceof org.bukkit.entity.Witch witch)) {
                        iterator.remove();
                        witchLastThrowTime.remove(uuid);
                        continue;
                    }

                    if (witch.getTarget() != null) {
                        handleWitchAttack(witch);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getType() == EntityType.WITCH) {
                activeWitches.add(entity.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.WITCH) {
            activeWitches.add(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.WITCH) {
            activeWitches.remove(event.getEntity().getUniqueId());
            witchLastThrowTime.remove(event.getEntity().getUniqueId());
        }
    }

    private void handleWitchAttack(org.bukkit.entity.Witch witch) {
        LivingEntity target = witch.getTarget();
        if (target == null || (witchPlayerOnlyMode && !(target instanceof Player))) return;

        double distSq = witch.getLocation().distanceSquared(target.getLocation());
        if (distSq > 256) return;

        if (!witch.hasLineOfSight(target)) return;

        UUID witchId = witch.getUniqueId();
        long now = System.currentTimeMillis();
        long lastThrow = witchLastThrowTime.getOrDefault(witchId, 0L);

        if (now - lastThrow >= witchThrowCooldownMillis) {
            if (witchThrowCooldownMillis > 0) {
                launchWitchPotion(witch, target);
                witchLastThrowTime.put(witchId, now);
            }
        }
    }

    private void launchWitchPotion(org.bukkit.entity.Witch witch, LivingEntity target) {
        Location from = witch.getEyeLocation();
        Location to = target.getEyeLocation().subtract(0, 0.2, 0);
        Vector direction = to.toVector().subtract(from.toVector());

        double distance = from.distance(to);
        double speed = 1.0 + (distance * 0.03);

        isWitchForcedShot = true;
        try {
            witch.getWorld().playSound(witch.getLocation(), Sound.ENTITY_WITCH_THROW, 1.0f, 1.0f);
            ThrownPotion potion = witch.launchProjectile(ThrownPotion.class, direction.normalize().multiply(speed));
            potion.getPersistentDataContainer().set(customWitchPotionKey, PersistentDataType.BOOLEAN, true);
        } finally {
            isWitchForcedShot = false;
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof org.bukkit.entity.Witch && event.getEntityType() == EntityType.SPLASH_POTION) {
            if (witchPreventVanillaThrow && !isWitchForcedShot) {
                event.setCancelled(true);
                return;
            }
            ThrownPotion potion = (ThrownPotion) event.getEntity();
            applyWitchPotionStats(potion);
        }
    }

    private void applyWitchPotionStats(ThrownPotion potion) {
        ItemStack item = potion.getItem();
        PotionMeta meta = (PotionMeta) item.getItemMeta();

        if (meta != null) {
            meta.setBasePotionType(witchPotionType);
            meta.clearCustomEffects();

            PotionEffectType effectType = getWitchEffectType(witchPotionType);
            if (effectType != null) {
                int duration = effectType.isInstant() ? 1 : 20 * 10;
                int amplifier = Math.max(0, witchPotionAmplifier - 1);
                meta.addCustomEffect(new PotionEffect(effectType, duration, amplifier), true);
            }

            item.setItemMeta(meta);
            potion.setItem(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!witchPlayerOnlyMode) return;
        if (event.getEntity().getShooter() instanceof org.bukkit.entity.Witch) {
            event.getAffectedEntities().removeIf(entity -> !(entity instanceof Player));
        }
    }

    private PotionEffectType getWitchEffectType(PotionType type) {
        if (type == PotionType.HARMING) return PotionEffectType.INSTANT_DAMAGE;
        if (type == PotionType.POISON) return PotionEffectType.POISON;
        if (type == PotionType.SLOWNESS) return PotionEffectType.SLOWNESS;
        if (type == PotionType.WEAKNESS) return PotionEffectType.WEAKNESS;
        if (type == PotionType.TURTLE_MASTER) return PotionEffectType.SLOWNESS;
        if (type == PotionType.HEALING) return PotionEffectType.INSTANT_HEALTH;
        if (type == PotionType.REGENERATION) return PotionEffectType.REGENERATION;
        if (type == PotionType.STRENGTH) return PotionEffectType.STRENGTH;
        return null;
    }

    // --- 마녀 설정 Getter/Setter ---
    public void setWitchPotionType(PotionType type) { this.witchPotionType = type; }
    public PotionType getWitchPotionType() { return witchPotionType; }
    public void setWitchAmplifier(int level) { this.witchPotionAmplifier = level; }
    public int getWitchAmplifier() { return witchPotionAmplifier; }
    public void setWitchCooldownSeconds(double seconds) { this.witchThrowCooldownMillis = (long) (seconds * 1000.0); }
    public double getWitchCooldownSeconds() { return witchThrowCooldownMillis / 1000.0; }
    public void setWitchPlayerOnlyMode(boolean enable) { this.witchPlayerOnlyMode = enable; }
    public boolean isWitchPlayerOnlyMode() { return witchPlayerOnlyMode; }
    public void setWitchPreventVanillaThrow(boolean prevent) { this.witchPreventVanillaThrow = prevent; }
    public boolean isWitchPreventVanillaThrow() { return witchPreventVanillaThrow; }
}