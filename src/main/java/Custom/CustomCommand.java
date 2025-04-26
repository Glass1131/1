package Custom;

import org.blog.test.GameManager;
import org.blog.test.MyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CustomCommand implements CommandExecutor, TabCompleter {

    private final MyPlugin plugin;
    private final GameManager gameManager;
    private final Map<String, ItemStack> itemMap = new HashMap<>();
    private static final List<String> ROUND_SUBCOMMANDS = Arrays.asList("add", "remove", "reset", "set");

    public CustomCommand(MyPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        initializeItemMap();
    }

    private void initializeItemMap() {
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
            sender.sendMessage(Component.text("[!] 플레이어만 사용할 수 있는 명령어입니다.", NamedTextColor.RED));
            return true; // 콘솔 등에서 실행 시 여전히 true 반환 (처리 완료)
        }

        String commandName = command.getName().toLowerCase();

        // 각 핸들러 메서드가 이제 false를 반환할 수 있음
        return switch (commandName) {
            case "get-item" -> handleGetItemCommand(player, args);
            case "게임시작" -> handleStartGameCommand(player);
            case "게임취소" -> handleStopGameCommand(player);
            case "round" -> handleRoundCommand(player, args);
            default -> false; // 이 플러그인이 처리하지 않는 명령어
        };
    }

    /**
     * /get-item 명령어 처리 로직
     * @param player 명령어 사용자
     * @param args 명령어 인자
     * @return 명령어 처리 성공 시 true, 실패 또는 잘못된 사용 시 false
     */
    private boolean handleGetItemCommand(Player player, String[] args) {
        // /get-item all
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            if (!player.hasPermission("customize.getitem.all")) {
                // 권한 없음 메시지 대신 false 반환 (Bukkit 기본 메시지 표시 유도)
                // player.sendMessage(Component.text("[!] 이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED));
                return false; // 수정: 권한 없을 시 false 반환
            }
            for (ItemStack item : itemMap.values()) {
                if (item != null) {
                    ItemStack newItem = item.clone();
                    newItem.setAmount(1);
                    player.getInventory().addItem(newItem);
                }
            }
            player.sendMessage(Component.text("[!] 모든 커스텀 아이템을 지급받았습니다!", NamedTextColor.GREEN));
            return true; // 성공 시 true
        }

        // /get-item <아이템이름> [수량]
        if (args.length >= 1) {
            if (!player.hasPermission("customize.getitem.single")) {
                // 권한 없음 메시지 대신 false 반환
                // player.sendMessage(Component.text("[!] 이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED));
                return false; // 수정: 권한 없을 시 false 반환
            }
            String itemName = args[0].toLowerCase();
            int quantity = 1;

            if (args.length == 2) {
                try {
                    quantity = Integer.parseInt(args[1]);
                    if (quantity <= 0) {
                        // 잘못된 수량 메시지 대신 false 반환 (plugin.yml 사용법 메시지 유도)
                        // player.sendMessage(Component.text("[!] 수량은 1 이상이어야 합니다.", NamedTextColor.RED));
                        return false; // 수정: 잘못된 사용 시 false 반환
                    }
                } catch (NumberFormatException e) {
                    // 잘못된 수량 메시지 대신 false 반환
                    // player.sendMessage(Component.text("[!] 유효하지 않은 수량입니다. 숫자를 입력해주세요.", NamedTextColor.RED));
                    return false; // 수정: 잘못된 사용 시 false 반환
                }
            }

            ItemStack item = itemMap.get(itemName);

            if (item == null) {
                // 아이템 없음 메시지 대신 false 반환
                // player.sendMessage(Component.text("[!] 존재하지 않는 아이템입니다! ...", NamedTextColor.RED));
                return false; // 수정: 잘못된 사용 시 false 반환
            }

            ItemStack newItem = item.clone();
            newItem.setAmount(quantity);
            player.getInventory().addItem(newItem);
            player.sendMessage(Component.text("[!] ", NamedTextColor.GREEN)
                    .append(Component.text(itemName, NamedTextColor.AQUA))
                    .append(Component.text(" " + quantity + "개(을)를 지급받았습니다!", NamedTextColor.GREEN)));
            return true; // 성공 시 true
        }

        // 인자가 부족한 경우 등 잘못된 사용법 -> false 반환 (plugin.yml 사용법 메시지 유도)
        // player.sendMessage(Component.text("사용법: /get-item <아이템이름> [수량] | /get-item all", NamedTextColor.YELLOW));
        return false; // 수정: 잘못된 사용 시 false 반환
    }

    /**
     * /게임시작 명령어 처리 로직
     * @param player 명령어 사용자
     * @return 명령어 처리 성공 시 true, 실패 시 false
     */
    private boolean handleStartGameCommand(Player player) {
        if (!player.hasPermission("plugin.start")) {
            // 권한 없음 메시지 대신 false 반환
            // player.sendMessage(Component.text("[!] 게임을 시작할 권한이 없습니다.", NamedTextColor.RED));
            return false; // 수정: 권한 없을 시 false 반환
        }
        // 성공 시 true
        if (gameManager.isGameInProgress()) {
            player.sendMessage(Component.text(GameManager.GAME_ALREADY_IN_PROGRESS, NamedTextColor.RED));
            // 이미 진행 중인 경우도 처리했으므로 true 반환 (명령어 자체는 유효)
        } else {
            gameManager.startGame();
            // 성공 메시지는 GameManager 내부 또는 여기서 전송 가능
        }
        return true;
    }

    /**
     * /게임취소 명령어 처리 로직
     * @param player 명령어 사용자
     * @return 명령어 처리 성공 시 true, 실패 시 false
     */
    private boolean handleStopGameCommand(Player player) {
        if (!player.hasPermission("plugin.stop")) {
            // 권한 없음 메시지 대신 false 반환
            // player.sendMessage(Component.text("[!] 게임을 중지할 권한이 없습니다.", NamedTextColor.RED));
            return false; // 수정: 권한 없을 시 false 반환
        }
        // 성공 시 true
        if (!gameManager.isGameInProgress()) {
            player.sendMessage(Component.text(GameManager.NO_GAME_IN_PROGRESS, NamedTextColor.RED));
            // 게임이 진행 중이지 않은 경우도 처리했으므로 true 반환
        } else {
            gameManager.stopGame();
            Bukkit.broadcast(Component.text(MyPlugin.GAME_FORCED_STOPPED, NamedTextColor.RED));
        }
        return true;
    }

    /**
     * /round 명령어 처리 로직
     * @param player 명령어 사용자
     * @param args 명령어 인자
     * @return 명령어 처리 성공 시 true, 실패 또는 잘못된 사용 시 false
     */
    private boolean handleRoundCommand(Player player, String[] args) {
        if (!player.hasPermission("plugin.round")) {
            // 권한 없음 메시지 대신 false 반환 (Bukkit 기본 메시지 또는 plugin.yml 메시지 유도)
            // player.sendMessage(Component.text("관리자 권한이 없어 실행되지 않습니다.", NamedTextColor.RED));
            return false; // 수정: 권한 없을 시 false 반환
        }

        if (!gameManager.isGameInProgress()) {
            player.sendMessage(Component.text("이 명령어는 게임 진행 중에만 사용할 수 있습니다.", NamedTextColor.RED));
            // 게임 진행 중 아닐 때도 처리했으므로 true 반환
            return true;
        }

        if (args.length < 1) {
            // 사용법 오류 시 false 반환 (plugin.yml 사용법 메시지 유도)
            // player.sendMessage(Component.text("사용법: /round [add|remove|reset|set] <값>", NamedTextColor.YELLOW));
            return false; // 수정: 잘못된 사용 시 false 반환
        }

        String subCommand = args[0].toLowerCase();
        int targetRound;
        int currentValue = gameManager.getCurrentRound();

        try {
            switch (subCommand) {
                case "set":
                case "add":
                case "remove":
                    if (args.length < 2) throw new IllegalArgumentException("값이 필요합니다.");
                    targetRound = parseRoundValue(subCommand, currentValue, args[1]);
                    break;
                case "reset":
                    if (args.length > 1) throw new IllegalArgumentException("값이 필요하지 않습니다.");
                    targetRound = 1;
                    break;
                default:
                    // 알 수 없는 서브커맨드 시 false 반환
                    // player.sendMessage(Component.text("알 수 없는 명령어입니다. [add|remove|reset|set]", NamedTextColor.RED));
                    return false; // 수정: 잘못된 사용 시 false 반환
            }
        } catch (NumberFormatException e) {
            // 숫자 형식 오류 시 false 반환
            // player.sendMessage(Component.text("유효한 숫자를 입력해주세요.", NamedTextColor.RED));
            return false; // 수정: 잘못된 사용 시 false 반환
        } catch (IllegalArgumentException e) {
            // 인자 개수 오류 시 false 반환
            // player.sendMessage(Component.text("사용법: /round " + subCommand + ..., NamedTextColor.YELLOW));
            return false; // 수정: 잘못된 사용 시 false 반환
        }


        targetRound = Math.max(1, targetRound);

        player.sendMessage(Component.text("게임 상태를 초기화하고 라운드를 " + targetRound + "로 변경합니다.", NamedTextColor.YELLOW));

        final int finalTargetRound = targetRound;
        Bukkit.getScheduler().runTask(plugin, () -> {
            gameManager.stopGame();
            gameManager.setCurrentRound(finalTargetRound);
            gameManager.startGame();
        });

        return true; // 성공적으로 명령 처리 시작 시 true
    }

    /**
     * /round 명령어의 값을 파싱하고 계산하는 헬퍼 메서드
     * @param subCommand 서브커맨드 (set, add, remove)
     * @param currentValue 현재 라운드 값
     * @param valueArg 파싱할 값 문자열
     * @return 계산된 목표 라운드
     * @throws NumberFormatException 값이 숫자가 아닐 경우
     */
    private int parseRoundValue(String subCommand, int currentValue, String valueArg) throws NumberFormatException {
        int value = Integer.parseInt(valueArg);
        return switch (subCommand) {
            case "set" -> value;
            case "add" -> currentValue + value;
            case "remove" -> currentValue - value;
            default -> currentValue; // 도달할 수 없음 (호출 전에 체크됨)
        };
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("get-item")) {
            return getGetItemCompletions(args);
        }

        if (commandName.equals("round")) {
            return getRoundCompletions(sender, args);
        }

        return null;
    }

    private List<String> getGetItemCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            if ("all".startsWith(currentArg)) {
                completions.add("all");
            }
            for (String itemName : itemMap.keySet()) {
                if (itemName.startsWith(currentArg)) {
                    completions.add(itemName);
                }
            }
        }
        return completions;
    }

    private List<String> getRoundCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("plugin.round")) {
            return completions;
        }

        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            for (String sub : ROUND_SUBCOMMANDS) {
                if (sub.startsWith(currentArg)) {
                    completions.add(sub);
                }
            }
        }
        return completions;
    }
}