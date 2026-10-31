import 'dart:convert';
import 'grid12_layout.dart';

/// Pixel dimensions describe the *design preview*, never force Android Auto.
class CarLayoutConfig {
  const CarLayoutConfig({
    this.schemaVersion = 2,
    this.layoutPresetId = 'vf_e34',
    this.useCustomLayout = false,
    this.customLayout,
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
  final String layoutPresetId;
  final bool useCustomLayout;
  final Grid12Layout? customLayout;

  Grid12Layout get activeLayout => useCustomLayout && customLayout != null
      ? customLayout!
      : Grid12Presets.byId(layoutPresetId).layout;
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
    String? layoutPresetId, bool? useCustomLayout, Grid12Layout? customLayout,
    double? columnRatio, double? calendarWeight, double? qrWeight,
    double? mediaWeight, bool? nightMode, bool? showSpeedEstimates,
    bool? map3d,
  }) => CarLayoutConfig(
    schemaVersion: schemaVersion,
    layoutPresetId: layoutPresetId ?? this.layoutPresetId,
    useCustomLayout: useCustomLayout ?? this.useCustomLayout,
    customLayout: customLayout ?? this.customLayout,
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
    'layoutPresetId': layoutPresetId,
    'useCustomLayout': useCustomLayout,
    if (customLayout != null) 'customLayout': customLayout!.toJson(),
    'targetWidthPx': targetWidthPx, 'targetHeightPx': targetHeightPx,
    'columnRatio': columnRatio, 'calendarWeight': calendarWeight,
    'qrWeight': qrWeight, 'mediaWeight': mediaWeight,
    'nightMode': nightMode, 'showSpeedEstimates': showSpeedEstimates,
    'signWarningMeters': signWarningMeters, 'map3d': map3d,
  };

  String encode() => jsonEncode(toJson());

  factory CarLayoutConfig.decode(String raw) {
    final m = jsonDecode(raw) as Map<String, dynamic>;
    final version = m['schemaVersion'];
    if (version != 1 && version != 2) {
      throw const FormatException('Unsupported layout schema');
    }
    int integer(String key, int fallback) => (m[key] as num?)?.toInt() ?? fallback;
    double decimal(String key, double fallback) =>
        (m[key] as num?)?.toDouble() ?? fallback;
    bool boolean(String key, bool fallback) => m[key] as bool? ?? fallback;
    final oldColumns = decimal('columnRatio', 0.60);
    final oldCalendar = decimal('calendarWeight', 0.25);
    final oldQr = decimal('qrWeight', 0.45);
    final oldMedia = decimal('mediaWeight', 0.30);
    if (version == 2 && !Grid12Presets.exists(
        m['layoutPresetId'] is String ? m['layoutPresetId'] as String : 'vf_e34')) {
      throw const FormatException('Unknown grid preset');
    }
    final savedCustom = version == 1
        ? Grid12Presets.fromLegacy(oldColumns, oldCalendar, oldQr, oldMedia)
        : m.containsKey('customLayout')
            ? Grid12Layout.fromJson(m['customLayout'])
            : null;
    if (version == 2 && m['useCustomLayout'] == true && savedCustom == null) {
      throw const FormatException('Missing custom layout');
    }
    return CarLayoutConfig(
      schemaVersion: 2,
      layoutPresetId: version == 1 ? 'vf_e34' :
          (m['layoutPresetId'] as String? ?? 'vf_e34'),
      useCustomLayout: version == 1 || boolean('useCustomLayout', false),
      customLayout: savedCustom,
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
