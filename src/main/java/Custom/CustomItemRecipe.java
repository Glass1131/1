package Custom;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomItemRecipe {

    private final JavaPlugin plugin;

    public CustomItemRecipe(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerRecipes() {

        // "소리 조절기"
        NamespacedKey Calibrated_Sculk_Sensor_Key = new NamespacedKey(plugin, "CALIBRATED_SCULK_SENSOR");
        ShapedRecipe Calibrated_Sculk_Sensor_Recipe = new ShapedRecipe(Calibrated_Sculk_Sensor_Key, CustomItem.CALIBRATED_SCULK_SENSOR);
        Calibrated_Sculk_Sensor_Recipe.shape(
                " A ",
                "ASA",
                "   "
        );

        Calibrated_Sculk_Sensor_Recipe.setIngredient('S', CustomItem.SCULK_SENSOR);
        Calibrated_Sculk_Sensor_Recipe.setIngredient('A', CustomItem.AMETHYST_SHARD);

        Bukkit.addRecipe(Calibrated_Sculk_Sensor_Recipe);

        // "비명 조절기"
        NamespacedKey Dark_Weapon_Key = new NamespacedKey(plugin, "DARK_WEAPON");
        ShapedRecipe Dark_Weapon_Recipe = new ShapedRecipe(Dark_Weapon_Key, CustomItem.DARK_WEAPON);
        Dark_Weapon_Recipe.shape(
                "QWQ",
                "QCQ",
                "VOV"
        );

        Dark_Weapon_Recipe.setIngredient('Q', CustomItem.SCULK);
        Dark_Weapon_Recipe.setIngredient('W', CustomItem.SILENCE_TEMPLATE);
        Dark_Weapon_Recipe.setIngredient('V', CustomItem.SCULK_VEIN);
        Dark_Weapon_Recipe.setIngredient('O', CustomItem.DARK_CORE);
        Dark_Weapon_Recipe.setIngredient('C', CustomItem.CALIBRATED_SCULK_SENSOR);

        Bukkit.addRecipe(Dark_Weapon_Recipe);

    }
}
