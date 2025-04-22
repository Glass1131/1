package Biomes; // 패키지 선언

import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;


public class Swamp implements Listener {

    @EventHandler
    public void onSlimeAttackPlayer(EntityDamageByEntityEvent event) {
        // 공격자가 슬라임인지, 피해자가 플레이어인지 확인 (Java 14+ instanceof 패턴 매칭 사용)
        if (!(event.getDamager() instanceof Slime slime) ||
                !(event.getEntity()  instanceof Player player)) {
            return; // 조건 불만족 시 즉시 종료
        }
        int slimeSize = slime.getSize();

        int durationTicks;
        int amplifier = 10;

        durationTicks = switch (slimeSize) {
            case 3 -> 20 * 10; // 큰 슬라임 (크기 3) -> 10초
            case 2 -> 20 * 5;  // 중간 슬라임 (크기 2) -> 5초
            default -> 0;      // 그 외 크기는 효과 없음
        };

        // 지속 시간이 0보다 큰 경우에만 효과 적용
        if (durationTicks > 0) {
            // Apply Potion Effect (SLOW_SWING)
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, amplifier));
        }
        slime.remove();


    }
}