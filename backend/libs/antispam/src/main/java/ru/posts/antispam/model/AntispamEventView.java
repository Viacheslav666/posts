package ru.posts.antispam.model;

import java.util.List;
import java.util.Set;

/** Engine-facing event projection (pure data, no Spring). */
public record AntispamEventView(
        String network,
        AntispamSection section,
        String authorId,
        boolean authorCommunity,
        boolean authorBot,
        String text,
        Set<String> attachmentTypes,
        boolean hasText,
        boolean isAutomaticForward,
        boolean fromSenderChat) {

    public static AntispamEventView of(
            String network,
            AntispamSection section,
            String authorId,
            boolean authorCommunity,
            boolean authorBot,
            String text,
            List<String> attachmentTypes,
            boolean isAutomaticForward,
            boolean fromSenderChat) {
        Set<String> types = attachmentTypes == null ? Set.of() : Set.copyOf(attachmentTypes);
        boolean hasText = text != null && !text.isBlank();
        return new AntispamEventView(
                network,
                section,
                authorId,
                authorCommunity,
                authorBot,
                text == null ? "" : text,
                types,
                hasText,
                isAutomaticForward,
                fromSenderChat);
    }
}
