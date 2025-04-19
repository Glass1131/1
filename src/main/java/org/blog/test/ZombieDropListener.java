package org.blog.test;

import Custom.CustomItem;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.World;

import java.util.Random;

public class ZombieDropListener implements Listener {
    private final Random random = new Random();

    @EventHandler
    public void onZombieDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.ZOMBIE) return; // 좀비가 아니라면 무시

        Zombie zombie = (Zombie) event.getEntity();
        World world = zombie.getWorld();
        Biome biome = world.getBiome(zombie.getLocation()); // 좀비가 죽은 위치의 바이옴 확인

        double chance = random.nextDouble(); // 0.0 ~ 1.0 사이 랜덤 값

        // 딥다크 바이옴에서만 아이템 드롭 변경
        if (biome == Biome.DEEP_DARK) {
            // 딥다크 바이옴에서 드롭되는 아이템 (예시로 다이아몬드와 금 조각)
            if (chance < 0.3) { // 30%
                event.getDrops().add(new ItemStack(CustomItem.SCULK_VEIN));
            } else if (chance < 0.39) { // 9%
                event.getDrops().add(new ItemStack(CustomItem.AMETHYST_SHARD));
            } else {
                event.getDrops().add(new ItemStack(CustomItem.SCULK));
            }
        } else {
            // 딥다크 바이옴이 아닌 경우 기존의 아이템 드롭
            if (chance < 0.2) { // 20% 확률로 철 주괴
                event.getDrops().add(new ItemStack(Material.IRON_INGOT, 1));
            } else if (chance < 0.4) { // 20% 확률로 금 조각
                event.getDrops().add(new ItemStack(Material.GOLD_NUGGET, 3));
            } else if (chance < 0.6) { // 20% 확률로 다이아
                event.getDrops().add(new ItemStack(Material.DIAMOND, 1));
            } else if (chance < 0.65) { // 5% 확률로 ZOMBIE_POWER
                event.getDrops().add(new ItemStack(CustomItem.ZOMBIE_POWER));
            }
        }
    }
}