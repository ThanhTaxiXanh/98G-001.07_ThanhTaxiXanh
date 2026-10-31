package vn.g98g00107.thanhtaxixanh.driver.car

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * M5.3M: offline, bounded, XXE-disabled GPX 1.0/1.1 track parser.
 * Data is review-only and never supplies verified road speed limits.
 */
object GpxTraceParser {
    data class Parsed(
        val fixes: List<GpsTraceRoadReview.Fix>,
        val rejectedPoints: Int
    )
    const val MAX_BYTES = 8_000_000
    const val MAX_POINTS = 5_000

    fun parse(xml: String): Parsed {
        require(xml.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES) {
            "GPX size outside allowed range"
        }
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) &&
            !xml.contains("<!ENTITY", ignoreCase = true)) {
            "XML entities and DTDs are forbidden"
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.isXIncludeAware = false
        factory.setExpandEntityReferences(false)
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = document.documentElement
        require(root.localName == "gpx") { "Not a GPX document" }
        val points = root.getElementsByTagNameNS("*", "trkpt")
        require(points.length <= MAX_POINTS) { "Too many GPX points" }
        val fixes = ArrayList<GpsTraceRoadReview.Fix>()
        var rejected = 0
        var lastTime = Long.MIN_VALUE
        for (i in 0 until points.length) {
            val element = points.item(i) as? org.w3c.dom.Element
            if (element == null) { rejected++; continue }
            val lat = element.getAttribute("lat").toDoubleOrNull()
            val lon = element.getAttribute("lon").toDoubleOrNull()
            val timeNodes = element.getElementsByTagNameNS("*", "time")
            val time = if (timeNodes.length > 0) {
                try {
                    java.time.Instant.parse(timeNodes.item(0).textContent.trim()).toEpochMilli()
                } catch (_: Exception) { null }
            } else null
            // GPX does not standardize GPS accuracy or heading. Do not invent them.
            // Use a conservative accuracy placeholder ONLY for data interchange;
            // missing accuracy means this point cannot be trusted by the matcher.
            if (lat == null || lon == null || time == null ||
                !lat.isFinite() || !lon.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
                time <= lastTime) {
                rejected++
                continue
            }
            fixes.add(GpsTraceRoadReview.Fix(lat, lon, 25.0, null, time))
            lastTime = time
        }
        return Parsed(fixes, rejected)
    }
}
