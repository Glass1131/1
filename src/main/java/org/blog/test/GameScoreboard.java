package org.blog.test;

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

    /**
     * 스코어보드의 특정 항목 점수를 업데이트합니다.
     * @param key 업데이트할 항목 키 (예: "라운드", "남은 몹", "준비 시간")
     * @param value 새로운 점수 값
     */
    public void updateScore(String key, int value) {
        // 이전에 표시된 항목을 정확히 찾아 제거
        String previousEntry = currentEntries.get(key);
        if (previousEntry != null) {
            board.resetScores(previousEntry);
        }

        // *** 수정: 헬퍼 메서드를 호출하여 formattedComponent 생성 ***
        Component formattedComponent = createFormattedComponent(key, value);

        // 레거시 문자열 변환 및 길이 제한 처리
        String scoreText = LegacyComponentSerializer.legacySection().serialize(formattedComponent);
        if (scoreText.length() > 40) {
            scoreText = scoreText.substring(0, 40);
        }

        // 고유한 점수 값 설정 (순서 정렬용)
        int scoreValue = getScoreValueForKey(key); // 점수 값 설정을 위한 헬퍼 메서드 호출 (선택적 개선)
        objective.getScore(scoreText).setScore(scoreValue);

        // 새로 표시된 항목 텍스트를 맵에 저장
        currentEntries.put(key, scoreText);
    }

    /**
     * 주어진 키와 값에 따라 스코어보드에 표시될 텍스트 Component를 생성합니다.
     * @param key 항목 키
     * @param value 항목 값
     * @return 생성된 Component
     */
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

    /**
     * 주어진 키에 해당하는 스코어보드 점수 값을 반환합니다 (항목 순서 제어용).
     * @param key 항목 키
     * @return 스코어보드 점수 값
     */
    private int getScoreValueForKey(String key) {
        return switch (key) {
            case "준비 시간" -> 3;
            case "라운드" -> 2;
            case "남은 몹" -> 1;
            default -> 0;
        };
    }

    /**
     * 이 게임 스코어보드의 Scoreboard 객체를 반환합니다.
     * @return Scoreboard 객체
     */
    public Scoreboard getBoard() {
        return board;
    }
}