package ru.posts.api.web.support;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import ru.posts.api.web.dto.common.CursorPage;

/** Simple opaque-cursor pagination over an already-ordered list (id DESC). */
public final class CursorPages {

    private CursorPages() {}

    public static <T> Slice<T> slice(List<T> orderedNewestFirst, String cursor, int limit, Function<T, UUID> idFn) {
        List<T> filtered = orderedNewestFirst;
        if (cursor != null && !cursor.isBlank()) {
            UUID cursorId = UUID.fromString(cursor);
            filtered = orderedNewestFirst.stream()
                    .dropWhile(item -> !idFn.apply(item).equals(cursorId))
                    .skip(1)
                    .toList();
        }
        boolean hasMore = filtered.size() > limit;
        List<T> page = hasMore ? filtered.subList(0, limit) : filtered;
        String next = hasMore && !page.isEmpty()
                ? idFn.apply(page.get(page.size() - 1)).toString()
                : null;
        return new Slice<>(page, next, hasMore);
    }

    public record Slice<T>(List<T> items, String nextCursor, boolean hasMore) {
        public CursorPage.Params unusedParams() {
            return new CursorPage.Params(nextCursor, items.size());
        }
    }
}
