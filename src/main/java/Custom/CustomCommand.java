package Custom;

import org.blog.test.MyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CustomCommand implements CommandExecutor, TabCompleter {

    private final MyPlugin plugin; // MyPlugin 인스턴스를 저장할 필드
    private final Map<String, ItemStack> itemMap = new HashMap<>(); // 아이템 맵 필드
    // round 명령어의 서브커맨드 목록 상수화
    private static final List<String> ROUND_SUBCOMMANDS = Arrays.asList("add", "remove", "reset", "set");

    public CustomCommand(MyPlugin plugin) {
        this.plugin = plugin; // 전달받은 MyPlugin 인스턴스 저장

        // ✅ 명령어와 아이템을 연결
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
        // 플레이어만 사용할 수 있는 명령어인지 확인 (필요하다면 명령어별로 분기)
        // 현재는 모든 명령어가 플레이어 전용이라고 가정하고 이 체크를 유지합니다.
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[!] 플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        // 명령어 이름에 따라 처리 로직 분기
        if (command.getName().equalsIgnoreCase("get-item")) {
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

            player.sendMessage("§b 사용법: /get-item <아이템이름> <수량> | /get-item all");
            return true;
        }

        // 게임 시작 명령어
        if (command.getName().equalsIgnoreCase("게임시작")) {
            // Player check already done at the top
            if (plugin.gameInProgress) {
                player.sendMessage("§c" + MyPlugin.GAME_ALREADY_IN_PROGRESS);
                return true;
            }
            plugin.startGame();
            return true;
        }

        // 게임 취소 명령어
        if (command.getName().equalsIgnoreCase("게임취소")) {
            if (!plugin.gameInProgress) {
                player.sendMessage("§c" + MyPlugin.NO_GAME_IN_PROGRESS);
                return true;
            }
            plugin.stopGame(); // MyPlugin 인스턴스 통해 메서드 호출
            return true;
        }

        // 라운드 관리 명령어 (/round)
        if (command.getName().equalsIgnoreCase("round")) {
            if (!sender.hasPermission("plugin.round")) {
                player.sendMessage("§c 관리자 권한이 없어 실행되지 않습니다.");
                return true;
            }

            // 게임 진행 중에만 사용 가능
            if (!plugin.gameInProgress) {
                player.sendMessage("§c이 명령어는 게임 진행 중에만 사용할 수 있습니다.");
                return true;
            }

            // 최소 인자 개수 확인 (서브커맨드)
            if (args.length < 1) {
                player.sendMessage("§e 사용법: /round [add|remove|reset|set] <값>");
                return true;
            }

            String subCommand = args[0].toLowerCase(); // 서브커맨드를 소문자로 변환
            int targetRound; // 최종 설정될 라운드 값
            int value; // add, remove, set 에 사용될 값 (초기화 불필요)

            switch (subCommand) {
                case "set":
                case "add":
                case "remove":
                    // 값 인자가 필요한 서브커맨드: 인자 개수 및 숫자 유효성 검사
                    if (args.length < 2) {
                        player.sendMessage("§e 사용법: /round " + subCommand + " <값>");
                        return true;
                    }
                    try {
                        value = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c 유효한 숫자를 입력해주세요.");
                        return true;
                    }

                    // add, remove, set 에 따라 targetRound 계산
                    if ("set".equals(subCommand)) {
                        targetRound = value;
                    } else if ("add".equals(subCommand)) {
                        targetRound = plugin.currentRound + value;
                    } else { // remove
                        targetRound = plugin.currentRound - value;
                    }
                    break;

                case "reset":
                    // reset은 인자가 필요 없음: 인자 개수 검사
                    if (args.length > 1) {
                        player.sendMessage("§e 사용법: /round reset");
                        return true;
                    }
                    targetRound = 1;
                    break;

                default:
                    player.sendMessage("§c알 수 없는 명령어입니다. [add|remove|reset|set]");
                    return true; // 명령어 처리 중단
            }

            // 최종 라운드 값이 1 미만일 경우 1로 설정
            if (targetRound < 1) {
                targetRound = 1;
            }

            // 라운드 변경 및 게임 상태 초기화 로직 실행
            player.sendMessage("§e게임 상태를 초기화하고 라운드를 " + targetRound + "로 변경합니다.");

            // 현재 게임을 중단하고 새 라운드로 재시작하는 로직을 다음 틱에 실행
            int finalTargetRound = targetRound;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.stopGame();
                plugin.currentRound = finalTargetRound;

                // 스코어보드에 새로운 라운드 번호 즉시 반영
                if (plugin.gameScoreboard != null) {
                    plugin.gameScoreboard.updateScore("라운드", plugin.currentRound);
                } else {
                    plugin.getLogger().warning("gameScoreboard is null when trying to update round score via /round command.");
                }
                plugin.startGame();
            });

            return true;
        }

        return false; // CustomCommand 에서 처리하지 않은 다른 명령어 (plugin.yml에 등록되지 않은 명령어 등)
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {

        // '/get-item' 명령어 탭 자동 완성
        if (command.getName().equalsIgnoreCase("get-item")) {
            return getGetItemCompletions(args); // Delegate to helper method
        }

        // '/round' 명령어 탭 자동 완성
        if (command.getName().equalsIgnoreCase("round")) {
            return getRoundCompletions(sender, args); // Delegate to helper method
        }

        // CustomCommand 에서 처리하지 않는 다른 명령어는 기본 Bukkit 자동 완성 사용
        return null; // null을 반환하면 Bukkit의 기본 자동 완성 기능이 동작합니다 (예: 플레이어 이름 완성)
    }

    //get-item 명령어 탭 자동 완성 로직을 분리한 헬퍼 메서드
    private List<String> getGetItemCompletions(String[] args) {
        List<String> completions = new ArrayList<>();

        // 첫 번째 인자에서 'all' 옵션 추가 및 아이템 이름 제안
        if (args.length == 1) {
            String partialArg = args[0].toLowerCase();
            if ("all".startsWith(partialArg)) {
                completions.add("all");
            }
            // 등록된 아이템 이름 자동완성
            for (String itemName : itemMap.keySet()) {
                if (itemName.startsWith(partialArg)) {
                    completions.add(itemName);
                }
            }
        }
        return completions;
    }

    //round 명령어 탭 자동 완성 로직을 분리한 헬퍼 메서드
    private List<String> getRoundCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("plugin.round")) {
            return completions;
        }

        if (args.length == 1) {
            String partialCommand = args[0].toLowerCase();
            for (String sub : ROUND_SUBCOMMANDS) {
                if (sub.startsWith(partialCommand)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if ("add".equals(subCommand) || "remove".equals(subCommand) || "set".equals(subCommand)) {
                return completions;
            }
        }
        return completions;
    }
}