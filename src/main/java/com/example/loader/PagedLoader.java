package com.example.loader;

import com.example.model.LogEntry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Common interface for all paged log loaders (local and remote)
 */
public interface PagedLoader extends AutoCloseable {

    /**
     * Load the next page of log entries
     * @return List of log entries
     * @throws IOException if loading fails
     */
    List<LogEntry> loadNextPage() throws IOException;

    /**
     * Check if there are more pages to load
     * @return true if more pages available
     */
    boolean hasMore();

    /**
     * Reset the loader to initial state
     * @throws IOException if reset fails
     */
    void reset() throws IOException;

    /**
     * Load next page asynchronously
     * @param onSuccess callback for successful load
     * @param onError callback for errors
     */
    default void loadNextPageAsync(Consumer<List<LogEntry>> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return loadNextPage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(onSuccess)
          .exceptionally(throwable -> {
              onError.accept(throwable);
              return null;
          });
    }

    /**
     * Close the loader and release resources
     */
    @Override
    void close() throws IOException;
}

