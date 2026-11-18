package ru.posts.content;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders editor variable tags at publish / preview time in the post timezone.
 *
 * <p>Supported tags:
 *
 * <ul>
 *   <li>{@code {date}}, {@code {time}}, {@code {weekday}}, {@code {day}}, {@code {month}}, {@code
 *       {year}}
 *   <li>{@code {group_id}}, {@code {group_name}}
 *   <li>{@code {hyperlink:Текст|https://example.com}} / {@code {link:Текст|url}}
 *   <li>{@code {json:path.to.value}} — lookup in supplied JSON map
 *   <li>{@code {birthdays}} / {@code {именинники}} — pre-resolved birthday list string
 *   <li>{@code [rand]a|b[/rand]} via {@link SpintaxExpander}
 * </ul>
 */
public final class VariableTemplateRenderer {

    private static final Locale RU = Locale.forLanguageTag("ru-RU");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final Pattern SIMPLE = Pattern.compile(
            "\\{(date|time|weekday|day|month|year|group_id|group_name|birthdays|именинники)}",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HYPERLINK =
            Pattern.compile("\\{(?:hyperlink|link):([^|}]+)\\|([^}]+)}", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON = Pattern.compile("\\{json:([a-zA-Z0-9_.\\-\\[\\]]+)}", Pattern.CASE_INSENSITIVE);

    private VariableTemplateRenderer() {}

    public record Context(
            Instant at,
            ZoneId zone,
            String groupId,
            String groupName,
            String birthdays,
            Map<String, Object> json,
            boolean htmlNetwork,
            IntUnaryOperator spintaxIndex) {

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private Instant at = Instant.now();
            private ZoneId zone = ZoneId.of("Europe/Moscow");
            private String groupId = "";
            private String groupName = "";
            private String birthdays = "";
            private Map<String, Object> json = Map.of();
            private boolean htmlNetwork;
            private IntUnaryOperator spintaxIndex = bound -> 0;

            public Builder at(Instant at) {
                if (at != null) {
                    this.at = at;
                }
                return this;
            }

            public Builder zone(ZoneId zone) {
                if (zone != null) {
                    this.zone = zone;
                }
                return this;
            }

            public Builder zone(String timezone) {
                this.zone = resolveZone(timezone);
                return this;
            }

            public Builder groupId(String groupId) {
                this.groupId = groupId == null ? "" : groupId;
                return this;
            }

            public Builder groupName(String groupName) {
                this.groupName = groupName == null ? "" : groupName;
                return this;
            }

            public Builder birthdays(String birthdays) {
                this.birthdays = birthdays == null ? "" : birthdays;
                return this;
            }

            public Builder json(Map<String, Object> json) {
                this.json = json == null ? Map.of() : Map.copyOf(json);
                return this;
            }

            public Builder htmlNetwork(boolean htmlNetwork) {
                this.htmlNetwork = htmlNetwork;
                return this;
            }

            public Builder spintaxIndex(IntUnaryOperator spintaxIndex) {
                if (spintaxIndex != null) {
                    this.spintaxIndex = spintaxIndex;
                }
                return this;
            }

            public Builder spintaxSeed(String seed) {
                this.spintaxIndex = SpintaxExpander.deterministicIndex(seed);
                return this;
            }

            public Context build() {
                return new Context(at, zone, groupId, groupName, birthdays, json, htmlNetwork, spintaxIndex);
            }
        }
    }

    public static String render(String template, Context context) {
        if (template == null || template.isEmpty()) {
            return template == null ? "" : template;
        }
        Context ctx = context == null ? Context.builder().build() : context;
        String result = expandSimple(template, ctx);
        result = expandHyperlinks(result, ctx);
        result = expandJson(result, ctx);
        return SpintaxExpander.expand(result, ctx.spintaxIndex());
    }

    public static boolean needsBirthdays(String template) {
        if (template == null || template.isEmpty()) {
            return false;
        }
        String lower = template.toLowerCase(Locale.ROOT);
        return lower.contains("{birthdays}") || lower.contains("{именинники}");
    }

    public static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("Europe/Moscow");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    private static String expandSimple(String template, Context ctx) {
        ZonedDateTime zdt = ctx.at().atZone(ctx.zone());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("date", DATE.format(zdt));
        values.put("time", TIME.format(zdt));
        values.put("weekday", weekdayRu(zdt.getDayOfWeek()));
        values.put("day", String.valueOf(zdt.getDayOfMonth()));
        values.put("month", monthRu(zdt.getMonth()));
        values.put("year", String.valueOf(zdt.getYear()));
        values.put("group_id", nullToEmpty(ctx.groupId()));
        values.put("group_name", nullToEmpty(ctx.groupName()));
        values.put("birthdays", nullToEmpty(ctx.birthdays()));
        values.put("именинники", nullToEmpty(ctx.birthdays()));

        Matcher matcher = SIMPLE.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(RU);
            String replacement = values.getOrDefault(key, "");
            // Cyrillic tag is already lowercased via UNICODE_CASE; map both.
            if ("именинники".equalsIgnoreCase(matcher.group(1))) {
                replacement = nullToEmpty(ctx.birthdays());
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String expandHyperlinks(String template, Context ctx) {
        Matcher matcher = HYPERLINK.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String text = matcher.group(1).trim();
            String url = matcher.group(2).trim();
            String replacement = ctx.htmlNetwork()
                    ? "<a href=\"" + escapeAttr(url) + "\">" + text + "</a>"
                    : text + " (" + url + ")";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String expandJson(String template, Context ctx) {
        Matcher matcher = JSON.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(1);
            String replacement = lookupJson(ctx.json(), path);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String lookupJson(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return "";
        }
        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return "";
            }
        }
        return current == null ? "" : String.valueOf(current);
    }

    private static String weekdayRu(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "понедельник";
            case TUESDAY -> "вторник";
            case WEDNESDAY -> "среда";
            case THURSDAY -> "четверг";
            case FRIDAY -> "пятница";
            case SATURDAY -> "суббота";
            case SUNDAY -> "воскресенье";
        };
    }

    private static String monthRu(Month month) {
        return switch (month) {
            case JANUARY -> "января";
            case FEBRUARY -> "февраля";
            case MARCH -> "марта";
            case APRIL -> "апреля";
            case MAY -> "мая";
            case JUNE -> "июня";
            case JULY -> "июля";
            case AUGUST -> "августа";
            case SEPTEMBER -> "сентября";
            case OCTOBER -> "октября";
            case NOVEMBER -> "ноября";
            case DECEMBER -> "декабря";
        };
    }

    /** Formats birthday members for the {@code {birthdays}} tag. */
    public static String formatBirthdays(Iterable<String> displayNames) {
        if (displayNames == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : displayNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(name.trim());
        }
        return sb.toString();
    }

    public static boolean isBirthdayToday(String bdate, LocalDate today) {
        if (bdate == null || bdate.isBlank() || today == null) {
            return false;
        }
        // VK bdate: "D.M" or "D.M.YYYY"
        String[] parts = bdate.trim().split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            return today.getDayOfMonth() == day && today.getMonthValue() == month;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeAttr(String url) {
        return Objects.requireNonNullElse(url, "").replace("\"", "&quot;");
    }
}
