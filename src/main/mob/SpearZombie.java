package test1.mob;

import test1.MyPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class SpearZombie implements Listener {

    private final MyPlugin plugin;
    private final Random random = new Random();

    private final NamespacedKey keyIsSpearZombie;
    private final NamespacedKey keyChargeCount;
    private final NamespacedKey keyLastChargeTime;

    private static final double SPAWN_CHANCE = 0.01;
    private static final int MAX_CHARGES = 3;
    private static final long CHARGE_INTERVAL_MS = 2000;
    private static final long CYCLE_COOLDOWN_MS = 15000;

    private final Set<UUID> activeSpearZombies = new HashSet<>();

    public SpearZombie(MyPlugin plugin) {
        this.plugin = plugin;
        this.keyIsSpearZombie = new NamespacedKey(plugin, "is_spear_zombie");
        this.keyChargeCount = new NamespacedKey(plugin, "charge_count");
        this.keyLastChargeTime = new NamespacedKey(plugin, "last_charge_time");

        scanAlreadyLoadedEntities();
        startAiTask();
    }

    private void scanAlreadyLoadedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                if (isSpearZombie(zombie)) {
                    activeSpearZombies.add(zombie.getUniqueId());
                }
            }
        }
    }

    private boolean isSpearZombie(Entity entity) {
        return entity.getPersistentDataContainer().has(keyIsSpearZombie, PersistentDataType.BOOLEAN);
    }

    public void spawn(Location location) {
        Zombie zombie = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        makeSpearZombie(zombie);
    }

    private void makeSpearZombie(Zombie zombie) {
        zombie.getPersistentDataContainer().set(keyIsSpearZombie, PersistentDataType.BOOLEAN, true);
        zombie.getPersistentDataContainer().set(keyChargeCount, PersistentDataType.INTEGER, 0);

        ItemStack spear = new ItemStack(Material.IRON_SPEAR);
        zombie.getEquipment().setItemInMainHand(spear);
        zombie.getEquipment().setItemInMainHandDropChance(0.05f);

        zombie.customName(Component.text("§c⚔ 창 좀비"));
        zombie.setCustomNameVisible(true);

        activeSpearZombies.add(zombie.getUniqueId());
    }

    @EventHandler
    public void onZombieSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ZOMBIE) return;

        boolean shouldSpawn = plugin.isProbabilityTestMode() || random.nextDouble() < SPAWN_CHANCE;
        if (shouldSpawn) {
            makeSpearZombie((Zombie) event.getEntity());
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Zombie && isSpearZombie(entity)) {
                activeSpearZombies.add(entity.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Zombie) {
            activeSpearZombies.remove(event.getEntity().getUniqueId());
        }
    }

    private void startAiTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Iterator를 사용하여 안전하게 순회 및 삭제
                Iterator<UUID> iterator = activeSpearZombies.iterator();
                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Entity entity = Bukkit.getEntity(uuid);

                    // 엔티티가 로드되지 않았거나(null), 죽었거나(!isValid) 하면 리스트에서 제거
                    if (entity == null || !entity.isValid() || !(entity instanceof Zombie zombie)) {
                        iterator.remove();
                        continue;
                    }

                    if (zombie.getTarget() instanceof Player target) {
                        handleChargeLogic(zombie, target);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 5L);
    }

    private void handleChargeLogic(Zombie zombie, Player target) {
        PersistentDataContainer pdc = zombie.getPersistentDataContainer();

        int count = pdc.getOrDefault(keyChargeCount, PersistentDataType.INTEGER, 0);
        long lastTime = pdc.getOrDefault(keyLastChargeTime, PersistentDataType.LONG, 0L);
        long now = System.currentTimeMillis();

        long requiredCooldown = (count >= MAX_CHARGES) ? CYCLE_COOLDOWN_MS : CHARGE_INTERVAL_MS;

        if (now - lastTime < requiredCooldown) return;

        double distanceSq = zombie.getLocation().distanceSquared(target.getLocation());
        if (distanceSq < 16 || distanceSq > 100) return;

        if (count >= MAX_CHARGES) {
            count = 0;
        }

        zombie.swingMainHand();
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.5f);
        zombie.getWorld().spawnParticle(Particle.CLOUD, zombie.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!zombie.isValid() || zombie.isDead() || !target.isValid()) {
                    this.cancel();
                    return;
                }
                if (tick >= 10) {
                    this.cancel();
                    return;
                }

                Vector dir = target.getLocation().toVector().subtract(zombie.getLocation().toVector());
                zombie.setVelocity(dir.normalize().multiply(1.5).setY(0.2));

                if (zombie.getLocation().distanceSquared(target.getLocation()) < 2.25) {
                    target.damage(8.0, zombie);
                    target.setVelocity(dir.normalize().multiply(0.5).setY(0.3));
                    this.cancel();
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        pdc.set(keyChargeCount, PersistentDataType.INTEGER, count + 1);
        pdc.set(keyLastChargeTime, PersistentDataType.LONG, now);
    }
}