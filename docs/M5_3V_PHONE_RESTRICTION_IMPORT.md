# M5.3V — Import OSM restriction relations from an Android phone

The Flutter phone dashboard now offers **Nhập quan hệ cấm rẽ OSM (GeoJSON)**. Android Storage Access Framework lets users select a local file without a computer or broad storage permission.

Input is a normalized GeoJSON FeatureCollection as defined in `docs/M5_3T_OSM_RESTRICTION_IMPORT_FOUNDATION.md`, **not** a raw Overpass/PBF file. Max file size: 8 MB. The parser requires explicit OSM relation/from-way/to-way/via-node identifiers and a `no_*` or `only_*` restriction kind. Any rejected feature or an empty file causes import to abort without overwriting the existing SQLite snapshot. A valid file atomically replaces the snapshot. The phone shows the stored relation count.

The imported snapshot is not yet cross-checked against the road topology extract, and `inspectOffline` does not yet apply it. Imported `only_*` restrictions remain unresolved by the resolver. No traffic/speed alerts are activated. Status: `UNVERIFIED_REVIEW_ONLY`.
