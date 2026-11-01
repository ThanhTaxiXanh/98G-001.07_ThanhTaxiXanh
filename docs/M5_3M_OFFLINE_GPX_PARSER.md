# M5.3M — Bounded offline GPX parser

`GpxTraceParser.parse(xml)` reads GPX trackpoints (`trkpt`) with WGS84 latitude/longitude and ISO-8601 timestamps. It rejects non-GPX XML, out-of-range coordinates, missing timestamps, and non-increasing timestamps. Maximum 8 MB and 5,000 trackpoints. XML DTD/entity expansion and external references are disabled. Returns `GpsTraceRoadReview.Fix` records and a rejected-point count.

**Critical limitations:** Standard GPX does not contain GPS accuracy or travel heading. The parser stores `accuracyM=25.0` as an **unknown-quality placeholder**, not a measured accuracy, and `heading=null`. The existing conservative geometry matcher therefore marks these points uncertain; the parser alone cannot validate map matching. GPX waypoint and route-point elements are intentionally ignored. No Android document picker or GPX-to-graph integration yet. No in-car speed warnings.

The M5.3L baseline passed GitHub Actions run 41196083723. New parser/tests need separate CI verification.
