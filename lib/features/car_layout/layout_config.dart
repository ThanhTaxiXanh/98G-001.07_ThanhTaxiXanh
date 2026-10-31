import 'dart:convert';

/// Pixel dimensions describe the *design preview*, never force Android Auto.
class CarLayoutConfig {
  const CarLayoutConfig({
    this.schemaVersion = 1,
    this.targetWidthPx = 934,
    this.targetHeightPx = 844,
    this.columnRatio = 0.60,
    this.calendarWeight = 0.25,
    this.qrWeight = 0.45,
    this.mediaWeight = 0.30,
    this.nightMode = true,
    this.showSpeedEstimates = true,
    this.signWarningMeters = 500,
    this.map3d = true,
  });

  final int schemaVersion, targetWidthPx, targetHeightPx, signWarningMeters;
  final double columnRatio, calendarWeight, qrWeight, mediaWeight;
  final bool nightMode, showSpeedEstimates, map3d;

  static const profiles = <(int, int)>[
    (934, 844), (800, 480), (1024, 600), (1280, 720),
    (1920, 720), (1280, 800), (800, 1280),
  ];

  List<double> get normalizedRows {
    final total = calendarWeight + qrWeight + mediaWeight;
    if (total <= 0) return [0.25, 0.45, 0.30];
    return [calendarWeight / total, qrWeight / total, mediaWeight / total];
  }

  CarLayoutConfig copyWith({
    int? targetWidthPx, int? targetHeightPx, int? signWarningMeters,
    double? columnRatio, double? calendarWeight, double? qrWeight,
    double? mediaWeight, bool? nightMode, bool? showSpeedEstimates,
    bool? map3d,
  }) => CarLayoutConfig(
    schemaVersion: schemaVersion,
    targetWidthPx: targetWidthPx ?? this.targetWidthPx,
    targetHeightPx: targetHeightPx ?? this.targetHeightPx,
    columnRatio: (columnRatio ?? this.columnRatio).clamp(0.50, 0.70).toDouble(),
    calendarWeight: (calendarWeight ?? this.calendarWeight).clamp(0.10, 0.75).toDouble(),
    qrWeight: (qrWeight ?? this.qrWeight).clamp(0.10, 0.75).toDouble(),
    mediaWeight: (mediaWeight ?? this.mediaWeight).clamp(0.10, 0.75).toDouble(),
    nightMode: nightMode ?? this.nightMode,
    showSpeedEstimates: showSpeedEstimates ?? this.showSpeedEstimates,
    signWarningMeters: signWarningMeters ?? this.signWarningMeters,
    map3d: map3d ?? this.map3d,
  );

  Map<String, Object> toJson() => {
    'schemaVersion': schemaVersion,
    'targetWidthPx': targetWidthPx, 'targetHeightPx': targetHeightPx,
    'columnRatio': columnRatio, 'calendarWeight': calendarWeight,
    'qrWeight': qrWeight, 'mediaWeight': mediaWeight,
    'nightMode': nightMode, 'showSpeedEstimates': showSpeedEstimates,
    'signWarningMeters': signWarningMeters, 'map3d': map3d,
  };

  String encode() => jsonEncode(toJson());

  factory CarLayoutConfig.decode(String raw) {
    final m = jsonDecode(raw) as Map<String, dynamic>;
    if (m['schemaVersion'] != 1) {
      throw const FormatException('Unsupported layout schema');
    }
    int integer(String key, int fallback) => (m[key] as num?)?.toInt() ?? fallback;
    double decimal(String key, double fallback) =>
        (m[key] as num?)?.toDouble() ?? fallback;
    bool boolean(String key, bool fallback) => m[key] as bool? ?? fallback;
    return CarLayoutConfig(
      targetWidthPx: integer('targetWidthPx', 934).clamp(240, 7680).toInt(),
      targetHeightPx: integer('targetHeightPx', 844).clamp(240, 7680).toInt(),
      columnRatio: decimal('columnRatio', 0.60).clamp(0.50, 0.70).toDouble(),
      calendarWeight: decimal('calendarWeight', 0.25).clamp(0.10, 0.75).toDouble(),
      qrWeight: decimal('qrWeight', 0.45).clamp(0.10, 0.75).toDouble(),
      mediaWeight: decimal('mediaWeight', 0.30).clamp(0.10, 0.75).toDouble(),
      nightMode: boolean('nightMode', true),
      showSpeedEstimates: boolean('showSpeedEstimates', true),
      signWarningMeters: integer('signWarningMeters', 500).clamp(100, 2000).toInt(),
      map3d: boolean('map3d', true),
    );
  }
}
