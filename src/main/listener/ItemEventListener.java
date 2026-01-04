package test1.listener;

import test1.MyPlugin;
import test1.item.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemEventListener implements Listener {

    private final Random random = new Random();

    public ItemEventListener(MyPlugin plugin) {
    }

    // 우클릭 섭취 (먹을 수 없는 아이템)
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) return;

        // 영생하는 자 (네더의 별)
        if (item.isSimilar(CustomItem.IMMORTAL_ONE)) {
            consumeItem(player, item);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0));
            player.sendMessage(Component.text("영생하는 자를 섭취하여 무한한 힘을 얻었습니다!", NamedTextColor.AQUA));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
            return;
        }

        // 좀비 토금 (금 조각)
        if (item.isSimilar(CustomItem.ZOMBIE_GOLD_NUGGET)) {
            consumeItem(player, item);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
            return;
        }

        // 좀비의 흔적 (자수정 조각)
        if (item.isSimilar(CustomItem.ZOMBIE_TRACE)) {
            consumeItem(player, item);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
        }
    }

    // 섭취 이벤트 (먹을 수 있는 아이템)
    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // 좀비 사과
        if (item.isSimilar(CustomItem.ZOMBIE_APPLE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20, 1));

            // [신규] 0.0015% 확률로 감염 (사망 처리로 트리거)
            if (random.nextDouble() < 0.000015) {
                player.setHealth(0);
                player.sendMessage(Component.text("☣ 오염된 사과를 먹고 감염되었습니다!", NamedTextColor.DARK_GREEN));
            }
        }
        else if (item.isSimilar(CustomItem.GOLDEN_APPLE_CUSTOM)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 1));
        }
    }

    // 타격 패시브 (영생하는 자)
    @EventHandler
    public void onPlayerAttackZombie(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Zombie)) {
            return;
        }

        if (!attacker.getInventory().containsAtLeast(CustomItem.IMMORTAL_ONE, 1)) {
            return;
        }

        if (random.nextDouble() > 0.15) return;

        List<Player> nearbyPlayers = new ArrayList<>();
        nearbyPlayers.add(attacker);
        for (Entity e : attacker.getNearbyEntities(3, 3, 3)) {
            if (e instanceof Player p && !p.equals(attacker)) {
                nearbyPlayers.add(p);
            }
        }

        int holderCount = 0;
        for (Player p : nearbyPlayers) {
            if (p.getInventory().containsAtLeast(CustomItem.IMMORTAL_ONE, 1)) {
                holderCount++;
            }
        }
        boolean shareEffect = (holderCount >= 2);

        PotionEffectType[] effects = {
                PotionEffectType.INSTANT_HEALTH,
                PotionEffectType.INSTANT_DAMAGE,
                PotionEffectType.REGENERATION,
                PotionEffectType.SPEED
        };
        PotionEffectType selectedType = effects[random.nextInt(effects.length)];

        int baseDuration = 20 * 10;
        int baseAmp = 0;

        if (selectedType == PotionEffectType.INSTANT_HEALTH) {
            baseDuration = 1;
            baseAmp = 1;
        } else if (selectedType == PotionEffectType.INSTANT_DAMAGE) {
            baseDuration = 20 * 105;
        } else if (selectedType == PotionEffectType.REGENERATION) {
            baseDuration = 20 * 60;
        } else if (selectedType == PotionEffectType.SPEED) {
            baseDuration = 20 * 120;
        }

        for (Player target : nearbyPlayers) {
            boolean isHolder = target.getInventory().containsAtLeast(CustomItem.IMMORTAL_ONE, 1);

            if (!shareEffect && !isHolder) continue;

            int finalAmp = baseAmp;
            if (isHolder && selectedType != PotionEffectType.INSTANT_HEALTH) {
                PotionEffect existing = target.getPotionEffect(selectedType);
                if (existing != null) {
                    finalAmp = Math.min(4, existing.getAmplifier() + 1);
                }
            }

            target.addPotionEffect(new PotionEffect(selectedType, baseDuration, finalAmp, false, true));
        }

        attacker.getWorld().spawnParticle(org.bukkit.Particle.COMPOSTER, attacker.getLocation(), 10, 0.5, 1, 0.5);
    }

    private void consumeItem(Player player, ItemStack item) {
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }
}