package Custom;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CustomItem {

    // **✅ 아이템 ID 선언**
    public static ItemStack
            ZOMBIE_POWER,
            D_SWORD,
            D_BOOTS,
            D_LEGGINGS,
            D_CHESTPLATE,
            D_HELMET,
            DARK_CORE,
            DARK_WEAPON,
            SILENCE_TEMPLATE,
            SCULK_VEIN,
            SCULK_SENSOR,
            SCULK,
            AMETHYST_SHARD,
            CALIBRATED_SCULK_SENSOR


                    ;

    // **✅ 아이템 초기화 (플러그인 시작 시 실행)**
    public static void initializeItems() {
        D_SWORD = customItem(Material.IRON_SWORD, "★☆☆ 보급형 검", NamedTextColor.YELLOW,
                Arrays.asList("§7날카로움 V", "", "§7간단한 보급형 검 한 자루다.", "§7성능은 그럭저럭. 더 좋은 검을 찾아보자."),
                Map.of(Enchantment.SHARPNESS, 5));

        ZOMBIE_POWER = customItem(Material.DIAMOND_SWORD, "★★☆ 좀비의 힘", NamedTextColor.GOLD,
                List.of("§7날카로움 V", "", "§좀비의 강력한 기운§7이 가득 담겨있다.",
                        "§7다른 좀비 관련 드랍 아이템들과 조합하면", "§5아주 강력한 §7무기를 만들 수 있을 것 같다.",
                        "", "§8모든 좀비에게서 §d1.5%§8 확률로 드랍"),
                Map.of(Enchantment.SHARPNESS, 5));
        // 방어구류
        D_HELMET = customItem(Material.IRON_HELMET, "§e★☆☆ 보급형 헬멧", NamedTextColor.YELLOW,
                List.of("§7보호 III"), Map.of(Enchantment.PROTECTION, 3));

        D_CHESTPLATE = customItem(Material.IRON_CHESTPLATE, "§e★☆☆ 보급형 갑옷", NamedTextColor.YELLOW,
                List.of("§7보호 III"), Map.of(Enchantment.PROTECTION, 3));

        D_LEGGINGS = customItem(Material.IRON_LEGGINGS, "§e★☆☆ 보급형 바지", NamedTextColor.YELLOW,
                List.of("§7보호 III"), Map.of(Enchantment.PROTECTION, 3));

        D_BOOTS = customItem(Material.IRON_BOOTS, "§e★☆☆ 보급형 부츠", NamedTextColor.YELLOW,
                List.of("§7보호 III"), Map.of(Enchantment.PROTECTION, 3));
        //소모품류

        DARK_CORE = customItem(Material.SCULK_SHRIEKER, "비명 응집체", NamedTextColor.DARK_GRAY,
                Arrays.asList("어둠 속의 지배자 유물",
                        "???"),
                null);

        DARK_WEAPON = customItem(Material.ECHO_SHARD, "비명들의 힘", NamedTextColor.DARK_AQUA,
                Arrays.asList("???",
                        "",
                        "기본 공격 시:",
                        " - 전방에 음파가 나갑니다.",
                        " - 3.5% 확률로 다섯 방향으로 음파가 나갑니다."),
                null);

        SILENCE_TEMPLATE = customItem(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "설명", NamedTextColor.DARK_AQUA,
                Arrays.asList("설명",
                        "설명"),
                null);

        SCULK_VEIN = customItem(Material.SCULK_VEIN, "피", NamedTextColor.DARK_GRAY,
                Arrays.asList("설명",
                        "설명"),
                null);

        SCULK_SENSOR = customItem(Material.SCULK_SENSOR, "스컬크 센서", NamedTextColor.DARK_GRAY,
                List.of("설명"),
                null);

        SCULK = customItem(Material.SCULK, "스컬크", NamedTextColor.DARK_GRAY,
                List.of("설명"),
                null);

        AMETHYST_SHARD = customItem(Material.AMETHYST_SHARD, "자수정", NamedTextColor.LIGHT_PURPLE,
                List.of("설명"),
                null);

        CALIBRATED_SCULK_SENSOR = customItem(Material.CALIBRATED_SCULK_SENSOR, "음파 조절기", NamedTextColor.DARK_PURPLE,
                List.of("설명"),
                null);

    }

    // **🛠 공통 아이템 생성 메서드**
    private static ItemStack customItem(Material material, String name, NamedTextColor color, List<String> lore, Map<Enchantment, Integer> enchantments) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name, color));

            if (lore != null) {
                meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
            }

            if (enchantments != null) {
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            }

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            meta.setUnbreakable(true);
        }

        return item;
    }
}