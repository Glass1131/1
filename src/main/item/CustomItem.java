package test1.item;

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
            CALIBRATED_SCULK_SENSOR,
            IMMORTAL_ONE,
            WITCH_EYE,
            SUSPICIOUS_POTION,
            ZOMBIE_APPLE,
            ZOMBIE_GOLD_NUGGET,
            ZOMBIE_TRACE,
            GOLDEN_APPLE_CUSTOM,
    // [신규 아이템]
    STICKY_SLIME,
            OXYGEN_FILTER;

    public static void initializeItems() {
        D_SWORD = customItem(Material.IRON_SWORD, "§e★☆☆ 보급형 검", NamedTextColor.YELLOW,
                Arrays.asList("§7날카로움 V", "", "§7간단한 보급형 검 한 자루다.", "§7성능은 그럭저럭. 더 좋은 검을 찾아보자."),
                Map.of(Enchantment.SHARPNESS, 5));

        D_HELMET = customItem(Material.IRON_HELMET, "§e★☆☆ 보급형 헬멧", NamedTextColor.YELLOW,
                List.of("§7보호 III"), Map.of(Enchantment.PROTECTION, 3));

        D_CHESTPLATE = customItem(Material.IRON_CHESTPLATE, "§e★☆☆ 보급형 갑옷", NamedTextColor.YELLOW,
                List.of("§7보호 III"), Map.of(Enchantment.PROTECTION, 3));

        D_LEGGINGS = customItem(Material.IRON_LEGGINGS, "§e★☆☆ 보급형 바지", NamedTextColor.YELLOW,
                List.of("§7보호 III"), Map.of(Enchantment.PROTECTION, 3));

        D_BOOTS = customItem(Material.IRON_BOOTS, "§e★☆☆ 보급형 부츠", NamedTextColor.YELLOW,
                List.of("§7보호 III"), Map.of(Enchantment.PROTECTION, 3));

        ZOMBIE_POWER = customItem(Material.DIAMOND_SWORD, "★★☆ 좀비의 힘", NamedTextColor.GOLD,
                List.of("§7날카로움 V", "", "§5좀비의 강력한 기운§7이 가득 담겨있다.",
                        "§7다른 좀비 관련 드랍 아이템들과 조합하면", "§5아주 강력한 §7무기를 만들 수 있을 것 같다.",
                        "", "§8모든 좀비에게서 §d1.5%§8 확률로 드랍"),
                Map.of(Enchantment.SHARPNESS, 5));

        DARK_CORE = customItem(Material.SCULK_SHRIEKER, "비명 응집체", NamedTextColor.DARK_GRAY,
                Arrays.asList("어둠 속의 지배자 유물", "???"), null);

        DARK_WEAPON = customItem(Material.ECHO_SHARD, "비명들의 힘", NamedTextColor.DARK_AQUA,
                Arrays.asList("???", "", "기본 공격 시:", " - 전방에 음파가 나갑니다.", " - 3.5% 확률로 다섯 방향으로 음파가 나갑니다."), null);


        SILENCE_TEMPLATE = customItem(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "침묵의 형상", NamedTextColor.DARK_AQUA,
                List.of("조용한 어둠의 형상이다."), null);

        SCULK_VEIN = customItem(Material.SCULK_VEIN, "스컬크 정맥", NamedTextColor.DARK_GRAY,
                List.of("꿈틀거리는 정맥이다."), null);

        SCULK_SENSOR = customItem(Material.SCULK_SENSOR, "스컬크 센서", NamedTextColor.DARK_GRAY,
                List.of("소리를 감지한다."), null);

        SCULK = customItem(Material.SCULK, "스컬크", NamedTextColor.DARK_GRAY,
                List.of("어둠의 부산물"), null);

        AMETHYST_SHARD = customItem(Material.AMETHYST_SHARD, "자수정 조각", NamedTextColor.LIGHT_PURPLE,
                List.of("영롱하게 빛난다."), null);

        CALIBRATED_SCULK_SENSOR = customItem(Material.CALIBRATED_SCULK_SENSOR, "음파 조절기", NamedTextColor.DARK_PURPLE,
                List.of("음파를 정밀하게 조절할 수 있다."), null);

        IMMORTAL_ONE = customItem(Material.NETHER_STAR, "영생하는 자", NamedTextColor.AQUA,
                Arrays.asList(
                        "§7섭취 시, 재생 2와 포화 1, 힘 1을 무한히 받습니다.",
                        "§7좀비 타격 시 주변 아군에게 이로운 효과를 부여합니다.",
                        "",
                        "§e[패시브 효과]",
                        "§7좀비 타격 시 확률적으로 주변 3칸에 버프 부여",
                        "§7(즉시 치유 II, 힘 I, 재생 I, 신속 I 중 하나)",
                        "§7동일한 아이템을 가진 플레이어는 효과가 중첩됩니다. (최대 5중첩)"
                ), null);

        WITCH_EYE = customItem(Material.FERMENTED_SPIDER_EYE, "마녀의 눈", NamedTextColor.DARK_PURPLE,
                List.of("마녀의 마력이 담긴 눈이다."), null);

        SUSPICIOUS_POTION = customItem(Material.OMINOUS_BOTTLE, "수상한 물약", NamedTextColor.DARK_RED,
                Arrays.asList(
                        "§7무언가 들어있는 병이다.",
                        "§7어쩌면 제작에 활용될 지도..?"
                ), null);

        ZOMBIE_APPLE = customItem(Material.APPLE, "좀비 사과", NamedTextColor.GREEN,
                Arrays.asList(
                        "적당히 먹을만한 사과다.",
                        "가끔씩 먹다보면 좀비로 변할 수 있을 것 같다.",
                        "",
                        "§a섭취 시: 포화 2",
                        "§c주의: 극히 낮은 확률로 감염됨"
                ), null);

        ZOMBIE_GOLD_NUGGET = customItem(Material.GOLD_NUGGET, "좀비 토금", NamedTextColor.GOLD,
                Arrays.asList(
                        "티끌 모아 태산을 실천했다.",
                        "놀랍게도 금속이지만 먹을 수 있다.",
                        "다른 음식에 합친다면 정말 별미일 것이다.",
                        "",
                        "§6우클릭 시 즉시 섭취됨",
                        "§a섭취 시: 재생 1"
                ), null);

        ZOMBIE_TRACE = customItem(Material.AMETHYST_SHARD, "좀비의 흔적", NamedTextColor.LIGHT_PURPLE,
                Arrays.asList(
                        "초코바 같은 느낌이라 먹을만하다.",
                        "모든 좀비 아이템의 진화 베이스가 된다.",
                        "",
                        "§d우클릭 시 즉시 섭취됨",
                        "§a섭취 시: 즉시 치유 2"
                ), null);

        GOLDEN_APPLE_CUSTOM = customItem(Material.GOLDEN_APPLE, "황금 사과", NamedTextColor.GOLD,
                Arrays.asList(
                        "몸에 이로운 황금으로 이루어져있다.",
                        "",
                        "§a섭취 시: 재생 2"
                ), null);

        // [신규 아이템 정의]
        STICKY_SLIME = customItem(Material.SLIME_BALL, "끈적한 점액", NamedTextColor.GREEN,
                List.of("§7쓸만 할 수도?"), null);

        OXYGEN_FILTER = customItem(Material.PAPER, "산소 필터", NamedTextColor.AQUA,
                Arrays.asList(
                        "§7숨 쉴만 해졌는데?",
                        "§b[효과] §f인벤토리에 소지 시 산소 감소량이 0.5배가 됩니다.",
                        "§7(개수 무관, 효과 중복 불가)"
                ), null);
    }

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
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}