package dev.skhanal.atrium.content;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Gives the home screen its rows of titles, from disk first and from TMDB
 * when the saved rows are old.
 *
 * The order is what makes it feel fast: the saved feed is drawn at once, and
 * a fresh one replaces it when it arrives. Offline, the saved feed stays.
 */
public final class ContentRepository {

    public interface Listener {
        /**
         * Called on the delivery executor, once or twice per load: first the
         * saved feed if there is one, then a new feed if one was fetched.
         */
        void onFeed(HomeFeed feed);
    }

    private final FeedStore store;
    private final FeedFetcher fetcher;
    private final Executor background;
    private final Executor deliver;
    private final LongSupplier clock;
    private final AtomicBoolean busy = new AtomicBoolean();

    /**
     * @param background runs disk and network work. Never the main thread.
     * @param deliver    runs the listener. The main thread on the TV.
     * @param clock      the current time in milliseconds
     */
    public ContentRepository(FeedStore store, FeedFetcher fetcher, Executor background,
            Executor deliver, LongSupplier clock) {
        this.store = store;
        this.fetcher = fetcher;
        this.background = background;
        this.deliver = deliver;
        this.clock = clock;
    }

    /**
     * Load the feed. A call made while an earlier load is still running does
     * nothing, so coming back to the home screen twice never fetches twice.
     *
     * @param installed package names of the apps on this TV
     */
    public void load(Set<String> installed, Listener listener) {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        background.execute(() -> {
            try {
                HomeFeed saved = store.load();
                if (saved != null) {
                    deliver.execute(() -> listener.onFeed(saved));
                    // A recent feed is kept, unless a streaming app was
                    // installed or removed since, which changes "On your apps".
                    if (saved.isFresh(clock.getAsLong())
                            && saved.services.equals(StreamingApps.includedServices(installed))) {
                        return;
                    }
                }
                HomeFeed fetched;
                try {
                    fetched = fetcher.fetch(installed, clock.getAsLong());
                } catch (IOException e) {
                    // Offline or TMDB is down. The saved feed, if any, stays on screen.
                    return;
                }
                try {
                    store.save(fetched);
                } catch (IOException e) {
                    // The disk is full. Show the new feed anyway. It is fetched again next time.
                }
                deliver.execute(() -> listener.onFeed(fetched));
            } finally {
                busy.set(false);
            }
        });
    }
}
