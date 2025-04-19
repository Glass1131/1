package Custom;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomItemCommand implements CommandExecutor, TabCompleter {

    private final Map<String, ItemStack> itemMap = new HashMap<>();

    public CustomItemCommand() {
        // **✅ 명령어와 아이템을 연결**

        itemMap.put("zombie_power", CustomItem.ZOMBIE_POWER);
        itemMap.put("d_sword", CustomItem.D_SWORD);
        itemMap.put("d_helmet", CustomItem.D_HELMET);
        itemMap.put("d_chestplate", CustomItem.D_CHESTPLATE);
        itemMap.put("d_leggings", CustomItem.D_LEGGINGS);
        itemMap.put("d_boots", CustomItem.D_BOOTS);
        itemMap.put("dark_shrieker", CustomItem.DARK_CORE);
        itemMap.put("dark_weapon", CustomItem.DARK_WEAPON);
        itemMap.put("sculk", CustomItem.SCULK);
        itemMap.put("sculk_vein", CustomItem.SCULK_VEIN);
        itemMap.put("sculk_sensor", CustomItem.SCULK_SENSOR);
        itemMap.put("silence_template", CustomItem.SILENCE_TEMPLATE);
        itemMap.put("amethyst_shard", CustomItem.AMETHYST_SHARD);
        itemMap.put("calibrated_sculk_sensor", CustomItem.CALIBRATED_SCULK_SENSOR);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[!] 플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        // /get-item all 입력 시 모든 아이템 지급
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            for (ItemStack item : itemMap.values()) {
                item.setAmount(1); // 모든 아이템의 수량을 1로 설정
                player.getInventory().addItem(item);
            }
            player.sendMessage("§a[!] 모든 커스텀 아이템을 지급받았습니다!");
            return true;
        }

        // /get-item <아이템 이름> <수량> 입력 시
        if (args.length == 2) {
            String itemName = args[0].toLowerCase();
            int quantity;

            try {
                quantity = Integer.parseInt(args[1]);
                if (quantity <= 0) {
                    player.sendMessage("§c[!] 수량은 1 이상이어야 합니다.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c[!] 유효하지 않은 수량입니다. 숫자를 입력해주세요.");
                return true;
            }

            ItemStack item = itemMap.get(itemName);

            if (item == null) {
                player.sendMessage("§c[!] 존재하지 않는 아이템입니다! 사용 가능한 목록: " + String.join(", ", itemMap.keySet()));
                return true;
            }

            // 아이템을 수량만큼 지급
            item.setAmount(quantity);
            player.getInventory().addItem(item);
            player.sendMessage("§a[!] " + itemName + " " + quantity + "개(을)를 지급받았습니다!");
            return true;
        }

        // /get-item <아이템 이름> 입력 시
        if (args.length == 1) {
            String itemName = args[0].toLowerCase();
            ItemStack item = itemMap.get(itemName);

            if (item == null) {
                player.sendMessage("§c[!] 존재하지 않는 아이템입니다! 사용 가능한 목록: " + String.join(", ", itemMap.keySet()));
                return true;
            }

            // 기본 수량 1로 아이템 지급
            item.setAmount(1);
            player.getInventory().addItem(item);
            player.sendMessage("§a[!] " + itemName + " 1개(을)를 지급받았습니다!");
            return true;
        }

        player.sendMessage("§ 사용법: /get-item <아이템이름> <수량> | /get-item all");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // 첫 번째 인자에서 'all' 옵션 추가
        if (args.length == 1) {
            completions.add("all"); // 모든 아이템 지급 옵션
            completions.addAll(itemMap.keySet()); // 등록된 아이템 자동완성
        }
        return completions;
    }

}