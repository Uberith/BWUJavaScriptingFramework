package com.botwithus.bot.cli.log;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LogLineClassifier {

    private static final Pattern CONSOLE_LOG_PATTERN =
            Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+(TRACE|DEBUG|INFO|WARN|ERROR)\\b");
    private static final Pattern LOGBACK_STATUS_PATTERN =
            Pattern.compile("\\|-([A-Z]+)\\b");
    private static final Pattern COMMON_FRAMES_OMITTED =
            Pattern.compile("^\\.\\.\\. \\d+ common frames omitted$");
    private static final Set<String> LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    private LogLineClassifier() {
    }

    static boolean isStructuredConsoleLog(String line) {
        return CONSOLE_LOG_PATTERN.matcher(line).find();
    }

    static String classify(String defaultLevel, String previousLevel, String line) {
        String explicitLevel = explicitLevel(line);
        if (explicitLevel != null) {
            return explicitLevel;
        }
        if (isThrowableContinuation(line)) {
            return previousLevel != null ? previousLevel : defaultLevel;
        }
        return defaultLevel;
    }

    private static String explicitLevel(String line) {
        Matcher consoleMatcher = CONSOLE_LOG_PATTERN.matcher(line);
        if (consoleMatcher.find()) {
            return consoleMatcher.group(1);
        }

        Matcher statusMatcher = LOGBACK_STATUS_PATTERN.matcher(line);
        if (statusMatcher.find()) {
            String level = statusMatcher.group(1);
            return LEVELS.contains(level) ? level : null;
        }
        return null;
    }

    private static boolean isThrowableContinuation(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("at ")
                || trimmed.startsWith("Caused by:")
                || trimmed.startsWith("Suppressed:")
                || COMMON_FRAMES_OMITTED.matcher(trimmed).matches();
    }
}
