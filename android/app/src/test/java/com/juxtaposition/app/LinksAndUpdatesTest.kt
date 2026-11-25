package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class LinksAndUpdatesTest {
    private fun place(id: String = "osm:node/123", name: String = "Meghana Foods", type: String = "restaurant", cuisine: String? = "biryani, andhra",
                      lat: Double = 12.9352, lng: Double = 77.6245, dir: String = "NE", km: Double = 0.18,
                      address: String? = null, hours: String? = null, phone: String? = null, website: String? = null) =
        Place(id, name, type, cuisine, lat, lng, km, 40, dir, address, hours, phone, website, null)

    // ---- directions ----
    @Test fun directionsLinks() {
        val p = place(lat = 12.93521234, lng = 77.62459)
        assertEquals("google.navigation:q=12.935212,77.624590&mode=w", PlaceLinks.walkingNavigation(p))
        assertEquals("geo:12.935212,77.624590?q=12.935212,77.624590(Meghana%20Foods)", PlaceLinks.mapPin(p))
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=12.935212,77.624590&travelmode=walking", PlaceLinks.mapsWebDirections(p))
    }

    @Test fun coordinatesNeverUseScientificNotationOrLocaleCommas() {
        val tiny = place(lat = 0.00001, lng = -0.000002)
        assertEquals("google.navigation:q=0.000010,-0.000002&mode=w", PlaceLinks.walkingNavigation(tiny))
        val prev = java.util.Locale.getDefault()
        try { java.util.Locale.setDefault(java.util.Locale.GERMANY); assertTrue(PlaceLinks.walkingNavigation(place()).contains("12.935200,77.624500")) }
        finally { java.util.Locale.setDefault(prev) }
    }

    @Test fun namesWithSpecialCharactersAreEncoded() {
        val p = place(name = "Dominos & Co (Koramangala) / 24x7?")
        val pin = PlaceLinks.mapPin(p)
        assertFalse(pin.contains(" ")); assertTrue(pin.contains("Dominos%20%26%20Co"))
        assertFalse(pin.substringAfter("(").substringBeforeLast(")").contains("&"))
    }

    // ---- share ----
    @Test fun shareTextHasThePlaceButNotTheSendersLocation() {
        val p = place(address = "St. Marks Road, Bengaluru", hours = "Mo-Su 10:00-23:00", phone = "+91 80 1234", website = "https://example.com")
        val t = PlaceLinks.shareText(p)
        assertEquals(
            "Meghana Foods\nRestaurant • biryani, andhra\nSt. Marks Road, Bengaluru\nHours: Mo-Su 10:00-23:00\nPhone: +91 80 1234\nWebsite: https://example.com\n" +
                "https://www.google.com/maps/search/?api=1&query=12.935200,77.624500", t)
        assertFalse(t.contains("away")); assertFalse(t.contains("NE")); assertFalse(t.contains("180"))   // nothing about where the sender is
    }

    @Test fun shareTextSkipsMissingFields() {
        val t = PlaceLinks.shareText(place(cuisine = null))
        assertEquals("Meghana Foods\nRestaurant\nhttps://www.google.com/maps/search/?api=1&query=12.935200,77.624500", t)
    }

    // ---- OpenStreetMap ----
    @Test fun osmLinks() {
        assertEquals("https://www.openstreetmap.org/node/123", PlaceLinks.osmPage(place("osm:node/123")))
        assertEquals("https://www.openstreetmap.org/edit?way=9876543210", PlaceLinks.osmEdit(place("osm:way/9876543210")))
        assertEquals("https://www.openstreetmap.org/edit?relation=5", PlaceLinks.osmEdit(place("osm:relation/5")))
        assertNull(PlaceLinks.osmEdit(place("something-else"))); assertNull(PlaceLinks.osmPage(place("osm:node/abc"))); assertNull(PlaceLinks.osmEdit(place("osm:area/1")))
    }

    // ---- screen reader summary ----
    @Test fun radarSummaryIsSpokenNaturally() {
        val ps = listOf(
            place("a", "Truffles", dir = "NE", km = 0.18), place("b", "Third Wave Coffee", dir = "NW", km = 0.31),
            place("c", "Meghana Foods", dir = "E", km = 0.52), place("d", "McDonald's", dir = "SE", km = 0.74),
            place("e", "Chai Point", dir = "N", km = 0.95), place("f", "Far Place", dir = "S", km = 1.4),
        )
        assertEquals(
            "Radar view. 5 places within 1 kilometre. Nearest: Truffles, 180 metres north-east. Third Wave Coffee, 310 metres north-west. " +
                "Meghana Foods, 520 metres east. McDonald's, 740 metres south-east. And 1 more. Switch to the list view to browse every place.",
            RadarSummary.describe(ps, 1000.0))
        assertTrue(RadarSummary.describe(ps, 400.0).startsWith("Radar view. 2 places within 400 metres."))   // zoomed in: only what is visible
        assertTrue(RadarSummary.describe(ps, 200.0).startsWith("Radar view. 1 place within 200 metres. Nearest: Truffles"))
        assertEquals("Radar view. No places within 500 metres. Switch to the list view for more options.", RadarSummary.describe(emptyList(), 500.0))
        assertEquals("1.5 kilometres", RadarSummary.distance(1.5)); assertEquals("2 kilometres", RadarSummary.distance(2.0)); assertEquals("10 metres", RadarSummary.distance(0.004))
    }

    // ---- versions and updates ----
    @Test fun versionComparison() {
        assertTrue(Versions.isNewer("v1.9.0", "1.8.0")); assertTrue(Versions.isNewer("1.10.0", "1.9.0"))   // numeric, not alphabetical
        assertTrue(Versions.isNewer("2.0", "1.9.9")); assertTrue(Versions.isNewer("1.8.1", "1.8"))
        assertFalse(Versions.isNewer("1.8.0", "1.8.0")); assertFalse(Versions.isNewer("v1.8", "1.8.0")); assertFalse(Versions.isNewer("1.7.9", "1.8.0"))
        assertFalse(Versions.isNewer("garbage", "1.8.0")); assertFalse(Versions.isNewer("1.9.0-beta", "1.8.0")); assertFalse(Versions.isNewer("", "1.8.0"))
        assertEquals(listOf(1, 8, 0), Versions.parse("v1.8.0")); assertNull(Versions.parse("1..2"))
    }

    private val release = """{"tag_name":"v1.9.0","draft":false,"prerelease":false,"html_url":"https://github.com/x/y/releases/tag/v1.9.0",
        "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},{"name":"NearbyEats.apk","browser_download_url":"https://x/NearbyEats.apk"}]}"""

    @Test fun parsesANewerRelease() {
        val u = UpdateChecker.parse(release, "1.8.0")!!
        assertEquals("1.9.0", u.version); assertEquals("https://x/NearbyEats.apk", u.downloadUrl); assertEquals("https://github.com/x/y/releases/tag/v1.9.0", u.pageUrl)
    }

    @Test fun ignoresOldDraftPrereleaseAndBrokenData() {
        assertNull(UpdateChecker.parse(release, "1.9.0")); assertNull(UpdateChecker.parse(release, "2.0.0"))
        assertNull(UpdateChecker.parse(release.replace("\"draft\":false", "\"draft\":true"), "1.8.0"))
        assertNull(UpdateChecker.parse(release.replace("\"prerelease\":false", "\"prerelease\":true"), "1.8.0"))
        assertNull(UpdateChecker.parse("not json", "1.8.0")); assertNull(UpdateChecker.parse("{}", "1.8.0"))
    }

    @Test fun fallsBackToTheReleasePageWithoutAnApk() {
        val u = UpdateChecker.parse("""{"tag_name":"v1.9.0","html_url":"https://github.com/x/y/releases/tag/v1.9.0","assets":[]}""", "1.8.0")!!
        assertEquals("https://github.com/x/y/releases/tag/v1.9.0", u.downloadUrl)
    }

    @Test fun checksAtMostOnceADay() {
        val day = 24 * 60 * 60 * 1000L
        assertTrue(UpdateChecker.shouldCheck(0, day)); assertTrue(UpdateChecker.shouldCheck(1_000, 1_000 + day))
        assertFalse(UpdateChecker.shouldCheck(1_000, 1_000 + day - 1)); assertFalse(UpdateChecker.shouldCheck(5_000, 5_000))
    }
}

class ApkInstallerTest {
    @org.junit.Test fun onlyGithubHttpsApkLinksAreDownloadedInApp() {
        org.junit.Assert.assertTrue(ApkInstaller.isTrustedUrl("https://github.com/VivekMuralee07/Juxtaposition/releases/download/v1.12.0/NearbyEats.apk"))
        org.junit.Assert.assertFalse(ApkInstaller.isTrustedUrl("http://github.com/x/y/releases/download/v1/NearbyEats.apk"))
        org.junit.Assert.assertFalse(ApkInstaller.isTrustedUrl("https://evil.example.com/NearbyEats.apk"))
        org.junit.Assert.assertFalse(ApkInstaller.isTrustedUrl("https://github.com/VivekMuralee07/Juxtaposition/releases/tag/v1.12.0"))
        org.junit.Assert.assertFalse(ApkInstaller.isTrustedUrl("not a url"))
    }
}

class GeocoderTest {
    @org.junit.Test fun parsesResultsAndShortensTheName() {
        val json = """[{"lat":"12.9784","lon":"77.6408","display_name":"Indiranagar, East Bengaluru, Bengaluru, Karnataka, India"},
            {"lat":"abc","lon":"1","display_name":"Bad"},{"lat":"95","lon":"1","display_name":"Off the map"},{"lat":"1","lon":"2","display_name":""}]"""
        val r = Geocoder.parse(json)
        org.junit.Assert.assertEquals(1, r.size)
        org.junit.Assert.assertEquals("Indiranagar, East Bengaluru, Bengaluru", r[0].label)
        org.junit.Assert.assertEquals(12.9784, r[0].lat, 1e-9)
        org.junit.Assert.assertEquals(77.6408, r[0].lng, 1e-9)
    }

    @org.junit.Test fun garbageGivesNoResults() {
        org.junit.Assert.assertTrue(Geocoder.parse("not json").isEmpty())
        org.junit.Assert.assertTrue(Geocoder.parse("[]").isEmpty())
    }

    @org.junit.Test fun queryIsEncodedAndSentOnlyAsText() {
        org.junit.Assert.assertTrue(Geocoder.url(" MG Road & Brigade ").endsWith("&q=MG+Road+%26+Brigade"))
    }
}
