package ru.posts.antispam.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import ru.posts.antispam.model.AntispamSection;

/** Maps enabled sections to inbound subscription event types. */
public final class AntispamEventTypes {

    private AntispamEventTypes() {}

    public static List<String> forNetworkAndSections(String network, List<String> sections) {
        String net = network == null ? "" : network.toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        List<String> secs = sections == null ? List.of() : sections;
        return switch (net) {
            case "vk" -> {
                for (String s : secs) {
                    AntispamSection section = AntispamSection.fromWire(s).orElse(null);
                    if (section == null) {
                        continue;
                    }
                    switch (section) {
                        case WALL_COMMENT -> {
                            out.add("wall_reply_new");
                            out.add("wall_reply_edit");
                        }
                        case PHOTO_COMMENT -> out.add("photo_comment_new");
                        case VIDEO_COMMENT -> out.add("video_comment_new");
                        case BOARD_COMMENT -> out.add("board_post_new");
                        case MARKET_COMMENT -> out.add("market_comment_new");
                        case WALL_POST, SUGGESTED_POST -> out.add("wall_post_new");
                        default -> {
                            // ignore non-VK
                        }
                    }
                }
                if (out.isEmpty()) {
                    out.addAll(List.of(
                            "wall_reply_new",
                            "wall_reply_edit",
                            "photo_comment_new",
                            "video_comment_new",
                            "board_post_new",
                            "market_comment_new",
                            "wall_post_new"));
                }
                yield List.copyOf(out);
            }
            case "tg", "telegram" -> List.of("message", "edited_message");
            case "max" -> {
                for (String s : secs) {
                    AntispamSection section = AntispamSection.fromWire(s).orElse(null);
                    if (section == AntispamSection.MAX_COMMENT) {
                        out.add("comment_created");
                        out.add("comment_edited");
                    } else if (section == AntispamSection.MAX_CHAT) {
                        out.add("message_created");
                    }
                }
                if (out.isEmpty()) {
                    out.addAll(List.of("comment_created", "comment_edited", "message_created"));
                }
                yield List.copyOf(out);
            }
            default -> List.of();
        };
    }

    public static List<String> merge(List<String> a, List<String> b) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        return new ArrayList<>(out);
    }
}
