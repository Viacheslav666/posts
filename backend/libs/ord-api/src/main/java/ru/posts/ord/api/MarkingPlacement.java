package ru.posts.ord.api;

import java.util.Locale;

/**
 * Six ad-marking placement modes (vposter parity / BE-25).
 *
 * <p>{@code vk_menu} and {@code vk_menu_and_text_end}: VK {@code wall.post} exposes only {@code
 * mark_as_ads=1} (badge «Реклама»), not a native menu with advertiser+erid. Callers must fall back
 * to text placement and surface {@link #VK_MENU_FALLBACK_WARNING}.
 */
public enum MarkingPlacement {
    vk_menu,
    text_start,
    text_end,
    vk_menu_and_text_end,
    click_link_start,
    click_link_end;

    /** Honest API warning when native VK menu marking with erid is unavailable. */
    public static final String VK_MENU_FALLBACK_WARNING =
            "vk_menu_unsupported: VK wall.post has no native advertiser+erid menu; "
                    + "falling back to text_end and mark_as_ads=1 when publishing to VK";

    /**
     * Resolves placement from {@code placement} enum name, legacy {@code markingType} (vposter
     * {@code type_marking} 0..5), or aliases. Defaults to {@link #text_start} for backward
     * compatibility with BE-15 prepend behaviour when both are null/blank.
     */
    public static MarkingPlacement resolve(String placement, String markingType) {
        MarkingPlacement fromPlacement = parseName(placement);
        if (fromPlacement != null) {
            return fromPlacement;
        }
        MarkingPlacement fromType = parseLegacyType(markingType);
        if (fromType != null) {
            return fromType;
        }
        return text_start;
    }

    public boolean requestsVkMenu() {
        return this == vk_menu || this == vk_menu_and_text_end;
    }

    public boolean isClickLink() {
        return this == click_link_start || this == click_link_end;
    }

    private static MarkingPlacement parseName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "vk_menu", "menu", "dropdown", "выпадающее_меню" -> vk_menu;
            case "text_start", "start", "begin", "текст_начало" -> text_start;
            case "text_end", "end", "текст_конец" -> text_end;
            case "vk_menu_and_text_end", "menu_and_text_end", "menu_text_end" -> vk_menu_and_text_end;
            case "click_link_start", "link_start", "clickable_start" -> click_link_start;
            case "click_link_end", "link_end", "clickable_end" -> click_link_end;
            default -> {
                try {
                    yield MarkingPlacement.valueOf(key);
                } catch (IllegalArgumentException ex) {
                    yield null;
                }
            }
        };
    }

    /** vposter {@code type_marking}: 0 menu, 1 start, 2 end, 3 menu+end, 4 link start, 5 link end. */
    private static MarkingPlacement parseLegacyType(String markingType) {
        if (markingType == null || markingType.isBlank()) {
            return null;
        }
        String t = markingType.trim();
        return switch (t) {
            case "0" -> vk_menu;
            case "1" -> text_start;
            case "2" -> text_end;
            case "3" -> vk_menu_and_text_end;
            case "4" -> click_link_start;
            case "5" -> click_link_end;
            default -> parseName(t);
        };
    }
}
