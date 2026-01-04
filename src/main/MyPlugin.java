package test1;

import test1.command.CustomCommand;
import test1.item.CustomItem;
import test1.item.CustomItemRecipe;
import test1.biomes.DeepDark;
import test1.biomes.Swamp;
import test1.listener.ItemEventListener;
import test1.listener.Weaponability;
import test1.listener.ZombieDropListener;
import test1.manager.BiomeNotifier;
import test1.manager.GameManager;
import test1.manager.GameScoreboard;
import test1.system.ThirstSystem;
import test1.system.HeatSystem;
import test1.system.OxygenSystem;
import test1.mob.SpearZombie;
import test1.mob.BossListener;
import test1.party.PartyManager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.NamespacedKey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class MyPlugin extends JavaPlugin implements Listener {
    public static final String GAME_WORLD_NAME = "world";
    private static final int DEFAULT_MIN_X = -258;
    private static final int DEFAULT_MAX_X = -341;
    private static final int DEFAULT_MIN_Z = 80;
    private static final int DEFAULT_MAX_Z = 14;
    public static final String GAME_FORCED_STOPPED = "⚠ 게임이 강제 종료되었습니다! 남아있던 몹들이 사라졌습니다.";
    public static final String WORLD_NOT_FOUND_WARNING = "월드 '" + GAME_WORLD_NAME + "'를 찾을 수 없습니다! 몹을 소환할 수 없습니다.";

    private GameManager gameManager;
    private GameScoreboard gameScoreboard;
    private PartyManager partyManager;

    private ThirstSystem thirstSystem;
    private HeatSystem heatSystem;
    private OxygenSystem oxygenSystem;
    private BossListener bossListener; // [변경] Witch 대신 BossListener 사용
    private SpearZombie spearZombie;
    private BiomeNotifier biomeNotifier;
    private DeepDark deepDark;

    private final Map<String, List<Integer>> configBiomeSpawnCoords = new HashMap<>();
    private final Map<String, Map<String, Double>> configBiomeSpawnProbabilities = new HashMap<>();

    private final Map<Biome, String> biomeNameCache = new HashMap<>();

    public int minX = DEFAULT_MIN_X, maxX = DEFAULT_MAX_X, minZ = DEFAULT_MIN_Z, maxZ = DEFAULT_MAX_Z;
    long configActionBarUpdateIntervalTicks;

    private boolean probabilityTestMode = false;

    private static final NamedTextColor THIRST_COLOR_100 = NamedTextColor.BLACK;
    private static final NamedTextColor THIRST_90_PLUS = NamedTextColor.DARK_RED;
    private static final NamedTextColor THIRST_COLOR_80_PLUS = NamedTextColor.RED;
    private static final NamedTextColor THIRST_COLOR_50_PLUS = NamedTextColor.GOLD;
    private static final NamedTextColor THIRST_COLOR_20_PLUS = NamedTextColor.YELLOW;
    private static final NamedTextColor THIRST_COLOR_BELOW_20 = NamedTextColor.GREEN;
    private static final NamedTextColor VERY_COLD_COLOR = NamedTextColor.BLUE;
    private static final NamedTextColor COLD_COLOR = NamedTextColor.AQUA;
    private static final NamedTextColor NORMAL_TEMPERATURE_COLOR = NamedTextColor.GRAY;
    private static final NamedTextColor HOT_COLOR = NamedTextColor.GOLD;
    private static final NamedTextColor VERY_HOT_COLOR = NamedTextColor.RED;

    @Override
    public void onEnable() {
        getLogger().info("✅ 플러그인이 활성화되었습니다! (Package: test1)");
        saveDefaultConfig();
        loadConfigValues();
        initializeSystems();

        CustomItem.initializeItems();
        new CustomItemRecipe(this).registerRecipes();

        registerListeners();
        registerCommands();
        startActionBarScheduler(this.configActionBarUpdateIntervalTicks);

        long biomeCheckTicks = getConfig().getLong("intervals.biome-check-ticks", 20L);
        biomeNotifier = new BiomeNotifier(this);
        biomeNotifier.runTaskTimer(this, 0L, biomeCheckTicks);

        deepDark = new DeepDark(this);
    }

    public void loadConfigValues() {
        reloadConfig();
        this.configActionBarUpdateIntervalTicks = getConfig().getLong("intervals.actionbar-update-ticks", 20L);

        configBiomeSpawnCoords.clear();
        configBiomeSpawnProbabilities.clear();
        ConfigurationSection biomesSection = getConfig().getConfigurationSection("biomes");
        if (biomesSection != null) {
            for (String biomeKey : biomesSection.getKeys(false)) {
                List<Integer> coords = biomesSection.getIntegerList(biomeKey + ".spawn-coords");
                if (coords.size() == 4) {
                    this.configBiomeSpawnCoords.put(biomeKey.toLowerCase(), coords);
                }
                ConfigurationSection probSection = biomesSection.getConfigurationSection(biomeKey + ".spawn-probabilities");
                if (probSection != null) {
                    Map<String, Double> probabilities = new HashMap<>();
                    for(String entityTypeKey : probSection.getKeys(false)) {
                        if (probSection.isDouble(entityTypeKey)) {
                            probabilities.put(entityTypeKey.toUpperCase(), probSection.getDouble(entityTypeKey));
                        }
                    }
                    if (!probabilities.isEmpty()) {
                        this.configBiomeSpawnProbabilities.put(biomeKey.toLowerCase(), probabilities);
                    }
                }
            }
        }

        List<Integer> defaultCoords = configBiomeSpawnCoords.get("default");
        if (defaultCoords != null && defaultCoords.size() == 4) {
            this.minX = defaultCoords.get(0);
            this.maxX = defaultCoords.get(1);
            this.minZ = defaultCoords.get(2);
            this.maxZ = defaultCoords.get(3);
        }

        if (gameManager != null) {
            gameManager.reloadConfig();
        }
    }

    public void saveBiomeCoordinates(String biomeKey, int x1, int z1, int x2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        getConfig().set("biomes." + biomeKey.toLowerCase() + ".spawn-coords", Arrays.asList(minX, maxX, minZ, maxZ));
        saveConfig();
        loadConfigValues();
        getLogger().info("바이옴 '" + biomeKey + "'의 스폰 좌표가 업데이트되었습니다.");
    }

    private void initializeSystems() {
        gameScoreboard = new GameScoreboard();
        gameManager = new GameManager(this, gameScoreboard);
        partyManager = new PartyManager();
        thirstSystem = new ThirstSystem(this);
        heatSystem = new HeatSystem(this);
        oxygenSystem = new OxygenSystem(this);
        bossListener = new BossListener(this); // [변경] BossListener 초기화
        spearZombie = new SpearZombie(this);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ZombieDropListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Swamp(), this);
        getServer().getPluginManager().registerEvents(new Weaponability(this), this);
        getServer().getPluginManager().registerEvents(bossListener, this); // [변경] 등록된 인스턴스 사용
        getServer().getPluginManager().registerEvents(spearZombie, this);
        getServer().getPluginManager().registerEvents(new ItemEventListener(this), this);
    }

    private void registerCommands() {
        CustomCommand customCommandHandler = new CustomCommand(this, gameManager, partyManager);

        Objects.requireNonNull(getCommand("zombie")).setExecutor(customCommandHandler);
        Objects.requireNonNull(getCommand("zombie")).setTabCompleter(customCommandHandler);

        getServer().getPluginManager().registerEvents(customCommandHandler, this);
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameInProgress()) {
            gameManager.stopGame();
        }
        Bukkit.getScheduler().cancelTasks(this);
        biomeNameCache.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        discoverRecipes(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (biomeNotifier != null) {
            biomeNotifier.removePlayer(event.getPlayer().getUniqueId());
        }
        if (partyManager != null) {
            partyManager.leaveParty(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (gameManager != null && gameManager.isGameInProgress()) {
            if (event.getEntity().getWorld().getName().equals(GAME_WORLD_NAME)) {
                gameManager.handlePlayerDeath(event.getEntity());
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!gameManager.isGameInProgress()) return;

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player p && gameManager.isZombiePlayer(p) && victim instanceof Monster) {
            event.setCancelled(true);
        }
        if (damager instanceof Monster && victim instanceof Player p && gameManager.isZombiePlayer(p)) {
            event.setCancelled(true);
        }
        if (damager instanceof Player p1 && gameManager.isZombiePlayer(p1) &&
                victim instanceof Player p2 && gameManager.isZombiePlayer(p2)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!gameManager.isGameInProgress()) return;

        if (event.getTarget() instanceof Player p && gameManager.isZombiePlayer(p)) {
            event.setCancelled(true);
        }
    }

    private void discoverRecipes(Player player) {
        player.discoverRecipe(new NamespacedKey(this, "CALIBRATED_SCULK_SENSOR"));
        player.discoverRecipe(new NamespacedKey(this, "DARK_WEAPON"));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (gameManager != null && gameManager.getGameMonsters().contains(entity)) {
            gameManager.removeGameMonster(entity);
            handleSlimeSplit(entity);
        }
    }

    private void handleSlimeSplit(LivingEntity deadEntity) {
        if (!(deadEntity instanceof Slime dyingSlime)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dyingSlime.isValid() && !dyingSlime.isDead()) return;
                if (!dyingSlime.getLocation().getChunk().isLoaded()) return;

                int dyingSlimeSize = dyingSlime.getSize();
                for (Entity nearbyEntity : dyingSlime.getNearbyEntities(2, 1, 2)) {
                    if (nearbyEntity instanceof Slime smallerSlime && !nearbyEntity.isDead()) {
                        if (gameManager != null && smallerSlime.getSize() < dyingSlimeSize && !gameManager.getGameMonsters().contains(smallerSlime)) {
                            gameManager.addGameMonster(smallerSlime);
                        }
                    }
                }
            }
        }.runTaskLater(this, 1L);
    }

    private void startActionBarScheduler(long intervalTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        player.sendActionBar(Component.empty());
                        continue;
                    }
                    player.sendActionBar(createActionBarComponent(player));
                }
            }
        }.runTaskTimer(this, 0L, intervalTicks);
    }

    private Component createActionBarComponent(Player player) {
        Component oxygenComponent = Component.empty();
        if (oxygenSystem != null && oxygenSystem.isEnabled()) {
            int oxygen = oxygenSystem.getOxygenLevel(player);
            NamedTextColor color = oxygen < 20 ? NamedTextColor.RED : NamedTextColor.AQUA;
            oxygenComponent = Component.text("🫧 산소: " + oxygen + "%", color)
                    .append(Component.text(" | ", NamedTextColor.WHITE));
        }

        int thirstLevel = (thirstSystem != null) ? thirstSystem.getThirstLevel(player) : 0;
        NamedTextColor thirstColor = getThirstColor(thirstLevel);
        Component thirstComponent = Component.text("💧 갈증: " + thirstLevel + "%", thirstColor);

        String temperatureState = (heatSystem != null) ? heatSystem.getTemperatureState(player) : "N/A";
        NamedTextColor temperatureColor = getTemperatureColorFromStateString(temperatureState);
        Component temperatureComponent = Component.text("🌡 " + temperatureState, temperatureColor);

        Location playerLoc = player.getLocation();
        String formattedBiome = "Unknown";
        NamedTextColor biomeColor = NamedTextColor.GRAY;

        if (playerLoc.getWorld().isChunkLoaded(playerLoc.getBlockX() >> 4, playerLoc.getBlockZ() >> 4)) {
            Biome currentBiome = playerLoc.getWorld().getBiome(playerLoc);
            formattedBiome = formatBiomeName(currentBiome);
            biomeColor = getBiomeColor(currentBiome);
        }

        Component biomeComponent = Component.text("🌳 " + formattedBiome, biomeColor);

        return oxygenComponent
                .append(thirstComponent)
                .append(Component.text(" | ", NamedTextColor.WHITE))
                .append(temperatureComponent)
                .append(Component.text(" | ", NamedTextColor.WHITE))
                .append(biomeComponent);
    }

    public NamedTextColor getThirstColor(int thirstLevel) {
        if (thirstLevel == 100) return THIRST_COLOR_100;
        if (thirstLevel >= 90) return THIRST_90_PLUS;
        if (thirstLevel >= 80) return THIRST_COLOR_80_PLUS;
        if (thirstLevel >= 50) return THIRST_COLOR_50_PLUS;
        if (thirstLevel >= 20) return THIRST_COLOR_20_PLUS;
        return THIRST_COLOR_BELOW_20;
    }

    private NamedTextColor getTemperatureColorFromStateString(String temperatureState) {
        if (temperatureState == null) return NamedTextColor.GRAY;
        String cleanState = temperatureState.replace("!", "");
        return switch (cleanState) {
            case "매우 추움" -> VERY_COLD_COLOR;
            case "추움" -> COLD_COLOR;
            case "정상" -> NORMAL_TEMPERATURE_COLOR;
            case "더움" -> HOT_COLOR;
            case "매우 더움" -> VERY_HOT_COLOR;
            default -> NamedTextColor.GRAY;
        };
    }

    public String formatBiomeName(Biome biome) {
        if (biome == null) return "Unknown Biome";

        return biomeNameCache.computeIfAbsent(biome, b -> {
            String name = b.getKey().getKey();
            String[] parts = name.split("_");
            StringBuilder formattedName = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    formattedName.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1).toLowerCase())
                            .append(" ");
                }
            }
            return formattedName.toString().trim();
        });
    }

    private NamedTextColor getBiomeColor(Biome biome) {
        if (biome == null) return NamedTextColor.GRAY;
        return switch (biome.getKey().getKey()) {
            case "deep_dark" -> NamedTextColor.DARK_GRAY;
            case "desert" -> NamedTextColor.YELLOW;
            case "swamp" -> NamedTextColor.DARK_GREEN;
            case "plains" -> NamedTextColor.GREEN;
            default -> NamedTextColor.WHITE;
        };
    }

    public Map<String, List<Integer>> getConfigBiomeSpawnCoords() {
        return new HashMap<>(this.configBiomeSpawnCoords);
    }

    public Map<String, Map<String, Double>> getConfigBiomeSpawnProbabilities() {
        return new HashMap<>(this.configBiomeSpawnProbabilities);
    }

    public void setZombieSpawnCoordinates(int minX, int maxX, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public boolean isProbabilityTestMode() {
        return probabilityTestMode;
    }

    public void setProbabilityTestMode(boolean probabilityTestMode) {
        this.probabilityTestMode = probabilityTestMode;
    }

    public ThirstSystem getThirstSystem() { return thirstSystem; }
    public HeatSystem getHeatSystem() { return heatSystem; }
    public OxygenSystem getOxygenSystem() { return oxygenSystem; }
    public BossListener getBossListener() { return bossListener; } // [변경] Getter 수정
    public GameScoreboard getGameScoreboard() { return gameScoreboard; }
    public DeepDark getDeepDark() { return deepDark; }
    public SpearZombie getSpearZombie() { return spearZombie; }
}