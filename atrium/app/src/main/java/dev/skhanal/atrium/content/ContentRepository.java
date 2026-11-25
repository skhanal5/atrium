package dev.skhanal.atrium.content;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Executor;
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

    /** What one load was asked for. */
    private static final class Request {
        final Set<String> installed;
        final Set<Genre> genres;
        final Listener listener;

        Request(Set<String> installed, Set<Genre> genres, Listener listener) {
            this.installed = installed;
            this.genres = genres;
            this.listener = listener;
        }

        boolean asksForTheSameAs(Request o) {
            return installed.equals(o.installed) && genres.equals(o.genres);
        }
    }

    /** The load in progress, or null. Guarded by this. */
    private Request running;
    /** A load asked for while another ran, with other apps or genres. Guarded by this. */
    private Request queued;

    /**
     * Load the feed. A call made while an earlier load is still running, for
     * the same apps and genres, does nothing, so coming back to the home
     * screen twice never fetches twice. A call with other apps or genres runs
     * when the earlier one ends, so a genre picked mid-fetch is not lost.
     *
     * @param installed package names of the apps on this TV
     * @param genres    the genres chosen for Your genres
     */
    public void load(Set<String> installed, Set<Genre> genres, Listener listener) {
        Request request = new Request(installed, genres, listener);
        synchronized (this) {
            if (running != null) {
                if (!running.asksForTheSameAs(request)) {
                    queued = request;
                }
                return;
            }
            running = request;
        }
        // One task runs the queued loads too, so they never overlap.
        background.execute(() -> {
            Request r = request;
            try {
                while (r != null) {
                    run(r);
                    r = next();
                }
            } finally {
                // An error ends the task. Without this, no load would run again.
                if (r != null) {
                    synchronized (this) {
                        running = null;
                        queued = null;
                    }
                }
            }
        });
    }

    /** The queued load, now running, or null when there is none. */
    private synchronized Request next() {
        running = queued;
        queued = null;
        return running;
    }

    private void run(Request r) {
        HomeFeed saved = store.load();
        if (saved != null) {
            deliver.execute(() -> r.listener.onFeed(saved));
            // A recent feed is kept, unless a streaming app was
            // installed or removed since, which changes "On your apps",
            // or other genres were chosen, which changes "Your genres".
            if (saved.isFresh(clock.getAsLong())
                    && saved.services.equals(StreamingApps.includedServices(r.installed))
                    && saved.genres.equals(r.genres)) {
                return;
            }
        }
        HomeFeed fetched;
        try {
            fetched = fetcher.fetch(r.installed, r.genres, clock.getAsLong());
        } catch (IOException e) {
            // Offline or TMDB is down. The saved feed, if any, stays on screen.
            return;
        }
        try {
            store.save(fetched);
        } catch (IOException e) {
            // The disk is full. Show the new feed anyway. It is fetched again next time.
        }
        deliver.execute(() -> r.listener.onFeed(fetched));
    }
}
