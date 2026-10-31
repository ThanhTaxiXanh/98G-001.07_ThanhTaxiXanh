# M5.3N — Android offline GPX quality report

- Phone dashboard: **Phân tích tệp GPX ngoại tuyến**.
- Android SAF picker reads at most 8 MB, parses bounded GPX track points and timestamps, estimates travel bearing only for consecutive points with at least 30 m displacement and at most 30 seconds gap.
- Returns point counts: valid, rejected, inferred heading, uncertain. Does not upload track data to a server or persist raw GPS fixes.
- Inferred heading is not a measured compass heading; GPS accuracy remains unknown. The summary is not a road-match confidence score.
- GPX-to-road matching and topology continuity are not yet connected to this UI; no speed-limit or traffic-sign warnings are enabled.

CI for M5.3M failed because Android's XMLConstants does not expose two JAXP properties. Commit `5de96abb` replaced these constants with the corresponding property URI strings; new CI is required to verify.
