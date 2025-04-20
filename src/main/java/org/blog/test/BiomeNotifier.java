    package org.blog.test;

    import org.bukkit.Bukkit;
    import org.bukkit.GameMode;
    import org.bukkit.World;
    import org.bukkit.block.Biome;
    import org.bukkit.entity.Player;
    import org.bukkit.potion.PotionEffect;
    import org.bukkit.potion.PotionEffectType;
    import org.bukkit.scheduler.BukkitRunnable;
    import net.kyori.adventure.text.Component;
    import net.kyori.adventure.text.format.NamedTextColor;


    import java.util.HashMap;
    import java.util.Map;

    public class BiomeNotifier extends BukkitRunnable {
        private final MyPlugin plugin;
        private final Biome deepDarkBiome = Biome.DEEP_DARK; // Deep Dark 바이옴
        private final Biome desertBiome = Biome.DESERT; // 사막 바이옴
        private final Biome swampBiome = Biome.SWAMP; // 늪지대 바이옴
        private final Map<Player, String> playerInBiome = new HashMap<>(); // 플레이어의 바이옴 상태 추적

        public BiomeNotifier(MyPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void run() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR) continue;

                World world = player.getWorld();
                Biome playerBiome = world.getBiome(player.getLocation());

                // 플레이어가 Deep Dark 바이옴에 들어갔을 때
                if (playerBiome == deepDarkBiome && !"deep_dark".equals(playerInBiome.getOrDefault(player, ""))) {
                    player.sendMessage(Component.text("현재 ")
                            .append(Component.text("Deep Dark").color(NamedTextColor.DARK_GRAY))
                            .append(Component.text(" 바이옴에 있으므로"))
                            .append(Component.text(" 어둠 효과").color(NamedTextColor.DARK_GRAY))
                            .append(Component.text("가 적용됩니다.")));

                    // 어둠 효과 부여
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));

                    // 좀비 소환 좌표 변경 (Deep Dark 전용 위치)
                    plugin.setZombieSpawnCoordinates(-225, -258, -119, -153);

                    playerInBiome.put(player, "deep_dark");
                }

                // 플레이어가 Deep Dark 에서 나왔을 때
                if (playerInBiome.getOrDefault(player, "").equals("deep_dark") && playerBiome != deepDarkBiome) {
                    player.sendMessage(Component.text("     ")
                            .append(Component.text("Deep Dark").color(NamedTextColor.DARK_GRAY))
                            .append(Component.text(" 바이옴을 벗어납니다.")));

                    // 어둠 효과 제거
                    player.removePotionEffect(PotionEffectType.DARKNESS);

                    // 기본 좀비 소환 좌표로 복구
                    plugin.setZombieSpawnCoordinates(-258, -341, 80, 14);

                    playerInBiome.put(player, "default");
                }

                // 플레이어가 사막(Desert) 바이옴에 들어갔을 때
                if (playerBiome == desertBiome && !"desert".equals(playerInBiome.getOrDefault(player, ""))) {
                    player.sendMessage(Component.text("현재 ")
                            .append(Component.text("Desert").color(NamedTextColor.YELLOW))
                            .append(Component.text(" 바이옴에 있으므로"))
                            .append(Component.text(" 갈증 속도와 온도가 올라갑니다."))); //플레이어가 말하는 대사처럼 바꾸고 싶네

                    // 사막 전용 좀비 소환 위치 설정
                    plugin.setZombieSpawnCoordinates(-214, -180, -153, -119);

                    playerInBiome.put(player, "desert");
                }

                // 플레이어가 사막(Desert)에서 나왔을 때
                if (playerInBiome.getOrDefault(player, "").equals("desert") && playerBiome != desertBiome) {
                    player.sendMessage(Component.text("     ")
                            .append(Component.text("Desert").color(NamedTextColor.YELLOW))
                            .append(Component.text(" 바이옴을 벗어납니다.")));

                    // 기본 좀비 소환 좌표로 복구
                    plugin.setZombieSpawnCoordinates(-258, -341, 80, 14);

                    playerInBiome.put(player, "default");
                }

                // 플레이어가 늪지대(Swamp) 바이옴에 들어갔을 때
                if (playerBiome == swampBiome && !"swamp".equals(playerInBiome.getOrDefault(player, ""))) {
                    player.sendMessage(Component.text("현재 ")
                            .append(Component.text("Swamp").color(NamedTextColor.DARK_GREEN))
                            .append(Component.text(" 바이옴에 있습니다.")));

                    // 늪지대 전용 좀비 소환 위치 설정
                    plugin.setZombieSpawnCoordinates(-264, -299, -153, -119);

                    playerInBiome.put(player, "swamp");
                }

                // 플레이어가 늪지대(swamp)에서 나왔을 때
                if (playerInBiome.getOrDefault(player, "").equals("swamp") && playerBiome != swampBiome) {
                    player.sendMessage(Component.text("     ")
                            .append(Component.text("Swamp").color(NamedTextColor.DARK_GREEN))
                            .append(Component.text(" 바이옴을 벗어납니다.")));

                    // 기본 좀비 소환 좌표로 복구
                    plugin.setZombieSpawnCoordinates(-258, -341, 80, 14);

                    playerInBiome.put(player, "default");
                }
            }
        }
    }

