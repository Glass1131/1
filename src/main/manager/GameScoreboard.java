package test1.manager;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

public class GameScoreboard {
    private final Scoreboard board;
    private final Objective objective;
    private static final String OBJECTIVE_NAME = "zombiegame_obj";
    private final Map<String, String> currentEntries = new HashMap<>();

    public GameScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Objective existingObjective = manager.getMainScoreboard().getObjective(OBJECTIVE_NAME);
        if (existingObjective != null) {
            existingObjective.unregister();
        }
        board = manager.getNewScoreboard();
        objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.text("게임 진행").decorate(TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        updateScore("라운드", 1);
        updateScore("남은 몹", 0);
    }

    public void applyToAllPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
    }

    public void updateScore(String key, int value) {
        String previousEntry = currentEntries.get(key);
        if (previousEntry != null) {
            board.resetScores(previousEntry);
        }

        Component formattedComponent = createFormattedComponent(key, value);

        String scoreText = LegacyComponentSerializer.legacySection().serialize(formattedComponent);
        if (scoreText.length() > 40) {
            scoreText = scoreText.substring(0, 40);
        }

        int scoreValue = getScoreValueForKey(key);
        objective.getScore(scoreText).setScore(scoreValue);

        currentEntries.put(key, scoreText);
    }

    private Component createFormattedComponent(String key, int value) {
        String scoreIdentifier = key + ": ";
        return switch (key) {
            case "준비 시간" -> Component.text(scoreIdentifier, NamedTextColor.GREEN)
                    .append(Component.text(value + " 초", NamedTextColor.WHITE));
            case "라운드" -> Component.text(scoreIdentifier, NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .append(Component.text(value, NamedTextColor.WHITE));
            case "남은 몹" -> Component.text(scoreIdentifier, NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(value + " 마리", NamedTextColor.WHITE));
            default -> Component.text(key + ": " + value);
        };
    }

    private int getScoreValueForKey(String key) {
        return switch (key) {
            case "준비 시간" -> 3;
            case "라운드" -> 2;
            case "남은 몹" -> 1;
            default -> 0;
        };
    }

    public Scoreboard getBoard() {
        return board;
    }
}