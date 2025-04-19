package org.blog.test;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public class GameScoreboard {
    private final Scoreboard board;
    private final Objective objective;

    public GameScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();
        objective = board.registerNewObjective("game", Criteria.DUMMY, Component.text("게임 진행").decorate(TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        updateScore("준비 시간", 30);
        updateScore("라운드", 1);
        updateScore("남은 좀비", 0);
    }

    public void applyToAllPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
    }

    public void updateScore(String key, int value) {
        for (String entry : board.getEntries()) {
            if (entry.contains(key)) {
                board.resetScores(entry);
            }
        }

        Component formattedKey = switch (key) {
            case "준비 시간" -> Component.text("준비 시간: ", NamedTextColor.GREEN)
                    .append(Component.text(value + " 초", NamedTextColor.WHITE));
            case "라운드" -> Component.text("라운드: ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(value, NamedTextColor.WHITE));
            case "남은 좀비" -> Component.text("남은 좀비: ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(value + " 마리", NamedTextColor.WHITE));
            default -> Component.text(key + " " + value);
        };

        String scoreText = LegacyComponentSerializer.legacySection().serialize(formattedKey);
        objective.getScore(scoreText).setScore(1);
    }
}
