package com.pptagent.agent.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentPlanParser {

    public List<ParsedCheckpoint> parse(String markdown, int maxSlides) {
        List<ParsedCheckpoint> checkpoints = new ArrayList<>();
        String[] lines = markdown == null ? new String[0] : markdown.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- [ ]")) {
                addCheckpoint(checkpoints, trimmed.substring(5).trim(), maxSlides);
            } else if (trimmed.matches("\\d+\\.\\s+.*")) {
                addCheckpoint(checkpoints, trimmed.replaceFirst("\\d+\\.\\s+", "").trim(), maxSlides);
            }
        }
        if (checkpoints.isEmpty()) {
            addCheckpoint(checkpoints, "Analyze source material and define the deck narrative.", maxSlides);
            addCheckpoint(checkpoints, "Create the core slide JSON content.", maxSlides);
            addCheckpoint(checkpoints, "Review generated slide JSON and mark the job generated.", maxSlides);
        }
        return checkpoints;
    }

    private void addCheckpoint(List<ParsedCheckpoint> checkpoints, String objective, int maxSlides) {
        if (checkpoints.size() >= maxSlides || objective.isBlank()) {
            return;
        }
        String title = objective.length() <= 80 ? objective : objective.substring(0, 80);
        checkpoints.add(new ParsedCheckpoint(title, objective));
    }

    public record ParsedCheckpoint(String title, String objective) {
    }
}
