package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

public class StreamingAppsTest {

    private static final String PEACOCK = "com.peacock.peacockfiretv";
    private static final String APPLE_TV = "com.apple.atve.amazon.appletv";

    private static Offer offer(int id, Offer.Mode mode) {
        return new Offer(id, "provider " + id, mode, "/logo" + id + ".jpg");
    }

    @Test
    public void mapsEveryPeacockPlanToThePeacockApp() {
        assertEquals("Peacock", StreamingApps.forProvider(386).name);
        assertEquals("Peacock", StreamingApps.forProvider(387).name);
    }

    @Test
    public void mapsTheAppleStoreAndAppleTvToTheAppleTvApp() {
        assertEquals("Apple TV", StreamingApps.forProvider(350).name);
        assertEquals("Apple TV", StreamingApps.forProvider(2).name);
    }

    @Test
    public void returnsNullForAServiceWithNoAppOnFireTv() {
        // 3 is Google Play Movies. 1825 is an Amazon Channel, which plays
        // inside Prime Video and is not an app of its own.
        assertNull(StreamingApps.forProvider(3));
        assertNull(StreamingApps.forProvider(1825));
    }

    @Test
    public void leavesOutPrimeVideoBecauseAtriumCannotOpenIt() {
        // On Fire TV, Prime Video closes again when another app opens it.
        assertNull(StreamingApps.forProvider(9));
        assertNull(StreamingApps.forProvider(10));
    }

    @Test
    public void groupsOffersByAppAndListsInstalledFreeOptionsFirst() {
        List<Offer> offers = List.of(
                offer(8, Offer.Mode.INCLUDED),        // Netflix, not installed
                offer(386, Offer.Mode.INCLUDED),      // Peacock, installed
                offer(387, Offer.Mode.INCLUDED),      // Peacock again
                offer(2, Offer.Mode.RENT),            // Apple TV, installed
                offer(2, Offer.Mode.BUY),
                offer(3, Offer.Mode.RENT));           // Google Play, no app

        List<WatchOption> options = StreamingApps.whereToWatch(offers, Set.of(PEACOCK, APPLE_TV));

        assertEquals(3, options.size());
        assertEquals("Peacock", options.get(0).app.name);
        assertTrue(options.get(0).installed);
        assertEquals(List.of(Offer.Mode.INCLUDED), options.get(0).modes);
        assertEquals("/logo386.jpg", options.get(0).logo);
        assertEquals("Apple TV", options.get(1).app.name);
        assertEquals(List.of(Offer.Mode.RENT, Offer.Mode.BUY), options.get(1).modes);
        assertEquals("Netflix", options.get(2).app.name);
        assertFalse(options.get(2).installed);
    }

    @Test
    public void listsNothingWhenNoOfferHasAnApp() {
        assertTrue(StreamingApps.whereToWatch(List.of(offer(3, Offer.Mode.RENT)), Set.of()).isEmpty());
        assertTrue(StreamingApps.whereToWatch(List.of(), Set.of(PEACOCK)).isEmpty());
    }

    @Test
    public void watchHereIsAnInstalledAppWhereTheTitleIsIncludedOrFree() {
        List<Offer> offers = List.of(offer(8, Offer.Mode.INCLUDED), offer(386, Offer.Mode.INCLUDED));

        WatchOption here = StreamingApps.watchHere(offers, Set.of(PEACOCK));

        assertEquals("Peacock", here.app.name);
    }

    @Test
    public void watchHereIsNullWhenTheTitleIsOnlyForRentOrOnAppsYouDoNotHave() {
        assertNull(StreamingApps.watchHere(List.of(offer(2, Offer.Mode.RENT)), Set.of(APPLE_TV)));
        assertNull(StreamingApps.watchHere(List.of(offer(8, Offer.Mode.INCLUDED)), Set.of(PEACOCK)));
    }

    @Test
    public void includedServicesAreOnlyThoseOfInstalledApps() {
        // Apple TV Store (2) sells titles. It is not a service you subscribe to,
        // so it does not belong in a row of titles on your apps.
        assertEquals(Set.of(386, 387, 350), StreamingApps.includedServices(Set.of(PEACOCK, APPLE_TV, "com.other")));
    }

    @Test
    public void subscriptionAppsAreInstalledAppsWithServicesToWatchInOrder() {
        // YouTube only rents and sells titles here, so it is not one of them.
        List<TvApp> apps = StreamingApps.subscriptionApps(
                Set.of(APPLE_TV, "com.amazon.firetv.youtube", PEACOCK));

        assertEquals(2, apps.size());
        assertEquals("Peacock", apps.get(0).name);
        assertEquals("Apple TV", apps.get(1).name);
    }

    @Test
    public void installedPackageIsTheNameThisTvHasForTheApp() {
        TvApp hulu = StreamingApps.forProvider(15);

        assertEquals("com.hulu.plus", hulu.installedPackage(Set.of("com.hulu.plus")));
        assertNull(hulu.installedPackage(Set.of(PEACOCK)));
    }

    @Test
    public void everyAppHasTheAppstorePackageThatWasCheckedOnTheTv() {
        assertEquals("com.tubitv.ott", StreamingApps.forProvider(73).appstorePackage());
        assertEquals("com.hbo.hbonow", StreamingApps.forProvider(1899).appstorePackage());
        for (TvApp app : StreamingApps.ALL) {
            assertTrue(app.name, !app.appstorePackage().isEmpty());
        }
    }

    @Test
    public void leavesOutAppsWithNoAppstorePageOnThisTv() {
        // Starz (43) and MGM+ (34): no Fire TV package name could be confirmed.
        assertNull(StreamingApps.forProvider(43));
        assertNull(StreamingApps.forProvider(34));
    }

    @Test
    public void subscriptionAppsIsEmptyWithNoneInstalled() {
        assertTrue(StreamingApps.subscriptionApps(Set.of("com.esaba.downloader")).isEmpty());
    }

    @Test
    public void includedServicesIsEmptyWhenNoStreamingAppIsInstalled() {
        assertTrue(StreamingApps.includedServices(Set.of("com.esaba.downloader")).isEmpty());
    }
}
