package com.litematicasearcher.client.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 轻量门面：把 {@link RedenApiClient} 包装成"按页查询"接口。
 * 旧的 GUI 代码只需要 {@link #search(String, int)}，不用关心 limit/offset。
 */
public final class RedenSearchService {

    public static final int PAGE_SIZE = 12;

    private static final RedenApiClient CLIENT = new RedenApiClient();

    private RedenSearchService() {
    }

    public static CompletableFuture<RedenPage> search(String query, int page) {
        final int normalizedPage = page < 1 ? 1 : page;
        int offset = (normalizedPage - 1) * PAGE_SIZE;
        return CLIENT.search(query == null ? "" : query, PAGE_SIZE, offset)
            .thenApply(resp -> new RedenPage(
                resp.machines(),
                normalizedPage,
                resp.estimatedTotalHits(),
                Math.max(1, (int) Math.ceil(resp.estimatedTotalHits() / (double) PAGE_SIZE))))
            .exceptionally(t -> new RedenPage(List.of(), normalizedPage, 0, 1));
    }

    public record RedenPage(List<RedenMachine> machines, int page, int totalHits, int totalPages) {
    }
}
