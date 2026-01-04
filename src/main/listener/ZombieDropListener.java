package test1.listener;

import test1.MyPlugin;
import test1.item.CustomItem;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Bogged;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.World;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Random;

public class ZombieDropListener implements Listener {
    private final Random random = new Random();
    private final MyPlugin plugin;

    public ZombieDropListener(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        World world = entity.getWorld();
        Biome biome = world.getBiome(entity.getLocation());
        double chance = random.nextDouble();

        // [신규] 슬라임 드롭
        if (entity instanceof Slime) {
            event.getDrops().add(CustomItem.STICKY_SLIME.clone());
        }

        // [신규] 보그드 사망 시 독 구름 생성
        if (entity instanceof Bogged) {
            AreaEffectCloud cloud = (AreaEffectCloud) world.spawnEntity(entity.getLocation(), EntityType.AREA_EFFECT_CLOUD);
            cloud.setRadius(1.0f);
            cloud.setDuration(40); // 2초 (40틱)
            cloud.setParticle(org.bukkit.Particle.ENTITY_EFFECT);
            cloud.setColor(Color.GREEN);
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 40, 0), true);
        }

        // 보스 마녀 드롭 업데이트
        if (entity instanceof Witch witch) {
            Component nameComponent = witch.customName();
            if (nameComponent != null) {
                String name = PlainTextComponentSerializer.plainText().serialize(nameComponent);
                if (name.contains("늪지대의 마녀")) {
                    event.getDrops().clear();
                    event.getDrops().add(new ItemStack(Material.NETHER_STAR));
                    event.getDrops().add(new ItemStack(Material.DIAMOND, 5));
                    event.getDrops().add(new ItemStack(CustomItem.SCULK_SENSOR));

                    // 마녀의 눈, 수상한 물약 추가
                    event.getDrops().add(CustomItem.WITCH_EYE.clone());
                    event.getDrops().add(CustomItem.SUSPICIOUS_POTION.clone());

                    // 영생하는 자 드랍 확률 (예: 20%)
                    if (random.nextDouble() < 0.2) {
                        event.getDrops().add(CustomItem.IMMORTAL_ONE.clone());
                    }
                    return;
                }
            }
        }

        // 좀비 드롭 업데이트
        if (entity instanceof Zombie) {
            if (plugin.isProbabilityTestMode()) {
                if (biome == Biome.DEEP_DARK) chance = 0.0;
                else chance = 0.62;
            }

            // 좀비의 흔적 (8%)
            if (random.nextDouble() < 0.08) {
                event.getDrops().add(CustomItem.ZOMBIE_TRACE.clone());
            }

            // 좀비 사과 (5%)
            if (random.nextDouble() < 0.05) {
                event.getDrops().add(CustomItem.ZOMBIE_APPLE.clone());
            }

            // 좀비 토금 (10%)
            if (random.nextDouble() < 0.10) {
                event.getDrops().add(CustomItem.ZOMBIE_GOLD_NUGGET.clone());
            }

            // 기존 드롭 로직 유지
            if (biome == Biome.DEEP_DARK) {
                if (chance < 0.3) {
                    event.getDrops().add(new ItemStack(CustomItem.SCULK_VEIN));
                } else if (chance < 0.39) {
                    event.getDrops().add(new ItemStack(CustomItem.AMETHYST_SHARD));
                } else {
                    event.getDrops().add(new ItemStack(CustomItem.SCULK));
                }
            } else {
                if (chance < 0.2) {
                    event.getDrops().add(new ItemStack(Material.IRON_INGOT, 1));
                } else if (chance < 0.4) {
                    event.getDrops().add(new ItemStack(Material.GOLD_NUGGET, 3));
                } else if (chance < 0.6) {
                    event.getDrops().add(new ItemStack(Material.LEATHER, 2));
                }
            }
        }
    }
}