package test1.biomes;

import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Swamp implements Listener {

    @EventHandler
    public void onSlimeAttackPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Slime slime) ||
                !(event.getEntity()  instanceof Player player)) {
            return;
        }
        int slimeSize = slime.getSize();

        int durationTicks = switch (slimeSize) {
            case 4 -> 20 * 8;  // 초대형: 8초
            case 3 -> 20 * 5;  // 대형: 5초
            case 2 -> 20 * 3;  // 중형: 3초
            default -> 0;      // 소형(1)은 효과 없음
        };

        if (durationTicks > 0) {
            // [수정] 나약함(WEAKNESS) 레벨을 255(Max)로 설정하여 데미지를 0으로 만듦 (공격 불가)
            // Amplifier: 255 (Effect Level 256)
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 255, false, false));
            player.sendMessage(Component.text("끈적한 점액이 묻어 공격할 수 없습니다!", NamedTextColor.GREEN));

            // 슬라임 즉시 소멸 (분열 방지)
            slime.remove();
        }
    }
}