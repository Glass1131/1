package org.blog.test;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

//이거 실행되는 건지 모르겠는데 일단 놔둠
public class ZombiePersistenceManager {
    private static final long ZOMBIE_PERSISTENCE_INTERVAL_TICKS = 200L;
    private final JavaPlugin plugin;

    public ZombiePersistenceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startPersistenceTask();
    }

    private void startPersistenceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                        zombie.setRemoveWhenFarAway(false); // 좀비가 멀리 가도 디스폰되지 않음
                        zombie.setPersistent(true); // 엔티티가 월드 리로드 후에도 유지됨
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, ZOMBIE_PERSISTENCE_INTERVAL_TICKS); // 10초(200틱)마다 실행하여 지속적으로 적용
    }
}