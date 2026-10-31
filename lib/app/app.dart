import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../features/car_layout/layout_config.dart';
import '../features/car_layout/grid12_layout.dart';
import '../features/car_layout/grid12_editor.dart';
import '../features/car_layout/layout_store.dart';
import '../features/car_preview/car_preview.dart';
import '../features/car_preview/car_surface_diagnostics_panel.dart';
import '../features/traffic_signs/traffic_point_panel.dart';
import '../features/payments/vietqr.dart';
import '../features/lunar_calendar/lunar_calendar.dart';
import '../features/events/calendar_events.dart';
import '../features/events/event_store.dart';

class TaxiApp extends StatefulWidget {
  const TaxiApp({super.key});
  @override
  State<TaxiApp> createState() => _TaxiAppState();
}

class _TaxiAppState extends State<TaxiApp> {
  final store = LayoutStore();
  @override
  void initState() {
    super.initState();
    unawaited(store.load());
  }
  @override
  void dispose() {
    store.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'ThanhTaxiXanh • Cấu hình Android Auto',
    theme: ThemeData(
      useMaterial3: true, brightness: Brightness.dark,
      colorSchemeSeed: Colors.amber,
    ),
    home: SettingsHome(store: store),
  );
}

class SettingsHome extends StatefulWidget {
  const SettingsHome({super.key, required this.store});
  final LayoutStore store;
  @override
  State<SettingsHome> createState() => _SettingsHomeState();
}
class _SettingsHomeState extends State<SettingsHome> {
  int tab = 0;
  Grid12Layout? draftLayout;
  bool liveMapEnabled = false;
  String layoutError = '';
  final bin = TextEditingController();
  final account = TextEditingController();
  final holder = TextEditingController();
  final memo = TextEditingController();
  final amount = TextEditingController();
  String? qrPayload;
  String qrError = '';
  final eventTitle = TextEditingController();
  final eventDay = TextEditingController();
  final eventMonth = TextEditingController();
  final eventYear = TextEditingController();
  static const locationChannel = MethodChannel('vn.g98g00107.thanhtaxixanh.driver/config');
  bool? locationGranted;
  String locationError = '';
  Map<String, dynamic> roadStatus = {};
  Map<String, dynamic>? roadQuality;
  String roadError = '';
  bool roadBusy = false;
  final eventStore = CalendarEventStore();
  bool eventLunar = false;
  bool eventLeap = false;
  EventRepeat eventRepeat = EventRepeat.annual;
  int? editingEventId;
  String eventError = '';

  @override
  void initState() {
    super.initState();
    unawaited(_loadFields());
    unawaited(_checkLocationPermission());
    unawaited(_readRoadStatus());
    unawaited(eventStore.load());
  }
  Future<void> _loadFields() async {
    final p = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      bin.text = p.getString('bank_bin') ?? '';
      account.text = p.getString('bank_account') ?? '';
      holder.text = p.getString('bank_holder') ?? '';
      memo.text = p.getString('bank_message') ?? '';
      amount.text = p.getString('bank_amount') ?? '';
    });
    try {
      _buildQr(persist: false);
    } catch (_) {/* User input can be incomplete. */}
  }

  Future<void> _checkLocationPermission() async {
    try {
      final granted = await locationChannel.invokeMethod<bool>('getLocationPermission');
      if (mounted) {
        setState(() => locationGranted = granted ?? false);
      }
    } on MissingPluginException {
      // Widget tests or non-Android preview.
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => locationError = e.message ?? 'Không kiểm tra được GPS');
      }
    }
  }

  Future<void> _requestLocationPermission() async {
    try {
      final granted = await locationChannel.invokeMethod<bool>('requestLocationPermission');
      if (mounted) {
        setState(() {
          locationGranted = granted ?? false;
          locationError = locationGranted == true
              ? '' : 'Chưa cấp quyền vị trí. Có thể mở Cài đặt Android để cấp lại.';
        });
      }
      if (granted == true && liveMapEnabled) {
        try {
          await locationChannel.invokeMethod<void>(
            'phoneMapPreviewCommand', {'action': 'refreshGps'});
        } on MissingPluginException {
          // Only available on Android with the live phone map.
        } on PlatformException {
          // The map can still be reconnected with the refresh GPS button.
        }
      }
    } on MissingPluginException {
      if (mounted) {
        setState(() => locationError = 'Chỉ cấp quyền trên thiết bị Android.');
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => locationError = e.message ?? 'Yêu cầu GPS không thành công');
      }
    }
  }

  Future<void> _readRoadStatus() async {
    try {
      final status = await locationChannel.invokeMapMethod<String, dynamic>(
        'getRoadDataStatus');
      if (mounted) {
        setState(() => roadStatus = Map<String, dynamic>.from(status ?? {}));
      }
    } on MissingPluginException {
      // Android-only API: widget tests do not need an Android document picker.
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => roadError = e.message ?? 'Không đọc được dữ liệu đường');
      }
    }
  }

  Future<void> _importRoadGeoJson({bool append = false}) async {
    if (roadBusy) {
      return;
    }
    setState(() {
      roadBusy = true;
      roadError = '';
    });
    try {
      final status = await locationChannel.invokeMapMethod<String, dynamic>(
        'importRoadGeoJson', {'append': append});
      if (mounted && status != null) {
        setState(() {
          roadStatus = Map<String, dynamic>.from(status);
          roadQuality = null;
        });
      }
    } on MissingPluginException {
      if (mounted) {
        setState(() => roadError = 'Chỉ nhập tệp trên điện thoại Android.');
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => roadError = e.message ?? 'Không thể nhập GeoJSON');
      }
    } finally {
      if (mounted) {
        setState(() => roadBusy = false);
      }
    }
  }

  Future<void> _importRoadTopologyGeoJson() async {
    if (roadBusy) return;
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Nhập OSM có định danh nút đường?'),
        content: const Text(
          'Chế độ này thay toàn bộ dữ liệu đường hiện tại. Tệp GeoJSON '
          'phải có osm_way_id và osm_node_ids theo thứ tự tọa độ, '
          'cùng maxspeed hợp lệ. Nếu nhập lỗi, dữ liệu cũ được giữ lại. '
          'Dữ liệu mới chỉ dùng để rà soát, không xác nhận giới hạn tốc độ.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false),
              child: const Text('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(context, true),
              child: const Text('Chọn tệp')),
        ],
      ),
    );
    if (confirm != true || !mounted) return;
    setState(() {
      roadBusy = true;
      roadError = '';
    });
    try {
      final status = await locationChannel.invokeMapMethod<String, dynamic>(
        'importRoadTopologyGeoJson');
      if (mounted && status != null) {
        setState(() {
          roadStatus = Map<String, dynamic>.from(status);
          roadQuality = null;
        });
      }
    } on MissingPluginException {
      if (mounted) setState(() =>
          roadError = 'Chỉ hỗ trợ nhập topology trên Android.');
    } on PlatformException catch (e) {
      if (mounted) setState(() =>
          roadError = e.message ?? 'Tệp topology không hợp lệ');
    } finally {
      if (mounted) setState(() => roadBusy = false);
    }
  }

  Future<void> _scanRoadQuality() async {
    if (roadBusy) return;
    setState(() {
      roadBusy = true;
      roadError = '';
    });
    try {
      final quality = await locationChannel.invokeMapMethod<String, dynamic>(
        'getRoadQuality');
      if (mounted && quality != null) {
        setState(() => roadQuality = Map<String, dynamic>.from(quality));
      }
    } on MissingPluginException {
      if (mounted) setState(() => roadError = 'Chỉ rà soát trên Android.');
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => roadError = e.message ?? 'Rà soát dữ liệu thất bại');
      }
    } finally {
      if (mounted) setState(() => roadBusy = false);
    }
  }

  Future<void> _cleanRoadDuplicates() async {
    if (roadBusy || roadQuality?['integrityOk'] != true ||
        (roadQuality?['legacyDuplicates'] as int? ?? 0) <= 0) {
      return;
    }
    final count = roadQuality!['legacyDuplicates'] as int;
    final approved = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Dọn các bản sao đoạn đường cũ?'),
        content: Text('Đã tìm thấy $count bản sao khớp hoàn toàn. '
            'Chỉ xóa các bản sao được xác thực, không xóa đường có tốc độ '
            'mâu thuẫn. Nếu phát hiện chỉ mục hỏng, toàn bộ thao tác sẽ '
            'được hoàn tác.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận dọn trùng'),
          ),
        ],
      ),
    );
    if (approved != true || !mounted || roadBusy) return;
    setState(() {
      roadBusy = true;
      roadError = '';
    });
    try {
      final outcome = await locationChannel.invokeMapMethod<String, dynamic>(
        'cleanLegacyRoadDuplicates');
      if (mounted && outcome != null) {
        setState(() {
          roadStatus = Map<String, dynamic>.from(
            outcome['status'] as Map<dynamic, dynamic>? ?? {});
          roadQuality = Map<String, dynamic>.from(
            outcome['quality'] as Map<dynamic, dynamic>? ?? {});
        });
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => roadError = e.message ?? 'Dọn dữ liệu không thành công');
      }
    } finally {
      if (mounted) setState(() => roadBusy = false);
    }
  }

  Future<void> _deleteRoadGeoJson() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Xóa dữ liệu đường OSM đã nhập?'),
        content: const Text('Dữ liệu đường ngoại tuyến sẽ bị xóa khỏi ứng dụng. '
            'Không ảnh hưởng đến sự kiện hoặc tài khoản ngân hàng.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false),
              child: const Text('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(context, true),
              child: const Text('Xóa dữ liệu')),
        ],
      ),
    );
    if (confirm != true || !mounted) {
      return;
    }
    try {
      final status = await locationChannel.invokeMapMethod<String, dynamic>(
        'clearRoadGeoJson');
      if (mounted) {
        setState(() {
          roadStatus = Map<String, dynamic>.from(status ?? {});
          roadQuality = null;
          roadError = '';
        });
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => roadError = e.message ?? 'Xóa dữ liệu thất bại');
      }
    }
  }

  void _buildQr({bool persist = true}) {
    try {
      qrPayload = VietQr.build(
        bin: bin.text.trim(),
        account: account.text.trim(),
        amount: amount.text.trim().isEmpty
            ? null : int.parse(amount.text.trim()),
        message: memo.text,
      );
      qrError = '';
      if (persist) {
        unawaited(SharedPreferences.getInstance().then((p) async {
          await p.setString('bank_bin', bin.text.trim());
          await p.setString('bank_account', account.text.trim());
          await p.setString('bank_holder', holder.text.trim());
          await p.setString('bank_message', memo.text);
          await p.setString('bank_amount', amount.text.trim());
        }));
      }
    } catch (e) {
      qrPayload = null;
      qrError = 'Thông tin QR chưa hợp lệ: $e';
    }
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    for (final c in [bin, account, holder, memo, amount,
      eventTitle, eventDay, eventMonth, eventYear]) {
      c.dispose();
    }
    eventStore.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.store,
    builder: (context, child) {
      final c = widget.store.config;
      final pages = [
        _layoutPage(c),
        _mapPage(c),
        _eventsPage(),
        _qrPage(),
        _mediaPage(),
      ];
      return Scaffold(
        appBar: AppBar(
          title: const Text('THANHTAXIXANH'),
          actions: const [
            Padding(padding: EdgeInsets.symmetric(horizontal: 12),
                child: Center(child: Text('SMART AUTO V5',
                    style: TextStyle(fontSize: 11)))),
          ],
        ),
        body: SafeArea(child: pages[tab]),
        bottomNavigationBar: NavigationBar(
          selectedIndex: tab,
          onDestinationSelected: (value) => setState(() => tab = value),
          destinations: const [
            NavigationDestination(icon: Icon(Icons.dashboard_customize),
                label: 'Bố cục'),
            NavigationDestination(icon: Icon(Icons.map_outlined), label: 'Bản đồ'),
            NavigationDestination(icon: Icon(Icons.event), label: 'Lịch'),
            NavigationDestination(icon: Icon(Icons.qr_code), label: 'VietQR'),
            NavigationDestination(icon: Icon(Icons.settings), label: 'Hệ thống'),
          ],
        ),
      );
    },
  );

  Widget _scroll(List<Widget> content) => ListView(
    padding: const EdgeInsets.all(16),
    children: content,
  );
  Widget _section(String title) => Padding(
    padding: const EdgeInsets.only(top: 10, bottom: 10),
    child: Text(title, style: Theme.of(context).textTheme.titleLarge),
  );
  Widget _layoutPage(CarLayoutConfig c) => _scroll([
    _section('Bố cục màn hình Android Auto'),
    const Text('Chỉnh bố cục trên điện thoại, không phải giao diện '
        'Android Auto đã được phép phát hành.'),
    const SizedBox(height: 10),
    SwitchListTile.adaptive(
      key: const Key('live-map-toggle'),
      contentPadding: EdgeInsets.zero,
      title: const Text('Bật bản đồ MapLibre động (điện thoại)'),
      subtitle: const Text('Bản đồ tương tác cần mạng để tải lớp nền; '
          'chỉ tạo MapView khi bật và hủy khi tắt. Không thay Android Auto.'),
      value: liveMapEnabled,
      onChanged: (enabled) => setState(() => liveMapEnabled = enabled),
    ),
    if (liveMapEnabled) ...[
      SwitchListTile.adaptive(
        key: const Key('map-3d-toggle'),
        contentPadding: EdgeInsets.zero,
        title: const Text('Góc nhìn nghiêng 3D (48°)'),
        subtitle: const Text('Chỉ thay đổi góc camera, không cam kết nhà 3D.'),
        value: c.map3d,
        onChanged: (value) => unawaited(widget.store.update(
            c.copyWith(map3d: value))),
      ),
      if (locationGranted != true)
        OutlinedButton.icon(
          onPressed: () => unawaited(_requestLocationPermission()),
          icon: const Icon(Icons.location_on_outlined),
          label: const Text('Cấp quyền GPS cho bản đồ động'),
        ),
      const Text('FPS là số khung hình thực tế đo gần đây khi bản đồ '
          'đang hoạt động; Java/Native Heap là bộ nhớ cấp phát của tiến trình, '
          'không phải tổng RAM của ứng dụng.'),
    ],
    CarPreview(
      liveMap: liveMapEnabled,
      config: draftLayout == null ? c :
          c.copyWith(customLayout: draftLayout, useCustomLayout: true),
      vietQrPayload: qrPayload,
    ),
    _section('Chọn một trong 8 bố cục'),
    DropdownButton<String>(
      key: const Key('grid12-preset-picker'),
      isExpanded: true,
      value: c.useCustomLayout && c.customLayout != null
          ? 'custom' : c.layoutPresetId,
      items: [
        for (final p in Grid12Presets.all)
          DropdownMenuItem(value: p.id, child: Text(p.title)),
        if (c.customLayout != null)
          const DropdownMenuItem(value: 'custom',
              child: Text('Bố cục tùy chỉnh đã lưu')),
      ],
      onChanged: (id) {
        if (id == null) return;
        setState(() { draftLayout = null; layoutError = ''; });
        if (id == 'custom') {
          unawaited(widget.store.update(c.copyWith(useCustomLayout: true)));
        } else {
          unawaited(widget.store.update(c.copyWith(
              layoutPresetId: id, useCustomLayout: false)));
        }
      },
    ),
    _section('Chỉnh sửa lưới 12×12'),
    const Text('Kéo ô để di chuyển; kéo chấm vàng ở góc dưới phải '
        'để thay đổi kích thước. Thanh biểu tượng cố định. '
        'Ô chồng lấn hoặc vượt lưới sẽ bị từ chối.'),
    const SizedBox(height: 8),
    Grid12Editor(
      key: ValueKey(c.layoutPresetId + (c.useCustomLayout ? 'custom' : 'preset')),
      layout: draftLayout ?? c.activeLayout,
      ratio: c.targetWidthPx / c.targetHeightPx,
      onEdited: (next) => setState(() {
        draftLayout = next; layoutError = '';
      }),
      onRejected: (error) => setState(() => layoutError = error),
    ),
    if (layoutError.isNotEmpty)
      Text(layoutError, style: const TextStyle(color: Colors.orange)),
    const SizedBox(height: 8),
    Wrap(spacing: 8, runSpacing: 8, children: [
      FilledButton.icon(
        key: const Key('save-grid12-layout'),
        onPressed: draftLayout == null ? null : () {
          final next = draftLayout!;
          setState(() { draftLayout = null; layoutError = ''; });
          unawaited(widget.store.update(c.copyWith(
              customLayout: next, useCustomLayout: true)));
        },
        icon: const Icon(Icons.save_outlined),
        label: const Text('Lưu bố cục tùy chỉnh'),
      ),
      OutlinedButton.icon(
        onPressed: draftLayout == null ? null : () =>
            setState(() { draftLayout = null; layoutError = ''; }),
        icon: const Icon(Icons.undo),
        label: const Text('Hủy thay đổi'),
      ),
      OutlinedButton.icon(
        onPressed: () {
          setState(() { draftLayout = null; layoutError = ''; });
          unawaited(widget.store.update(c.copyWith(
              layoutPresetId: 'vf_e34', useCustomLayout: false)));
        },
        icon: const Icon(Icons.restore),
        label: const Text('Về mẫu VF e34'),
      ),
    ]),
    _section('Kích thước màn hình mô phỏng'),
    DropdownButton<(int, int)>(
      isExpanded: true,
      value: (c.targetWidthPx, c.targetHeightPx),
      items: [
        for (final profile in {
          ...CarLayoutConfig.profiles,
          (c.targetWidthPx, c.targetHeightPx),
        })
          DropdownMenuItem(value: profile,
              child: Text('${profile.$1} × ${profile.$2} px')),
      ],
      onChanged: (value) {
        if (value != null) {
          unawaited(widget.store.update(c.copyWith(
              targetWidthPx: value.$1, targetHeightPx: value.$2)));
        }
      },
    ),
    const Text('M4.4A: Bố cục đã lưu chỉ áp dụng cho bản preview. '
        'Android Auto thực tế tiếp tục hiển thị bản đồ mặc định.'),
  ]);

  Widget _mapPage(CarLayoutConfig c) => _scroll([
    _section('Bản đồ và cảnh báo giao thông'),
    const Text('Bản đồ trên xe lấy vị trí từ điện thoại. Chỉ yêu cầu GPS '
        'khi người dùng nhấn cấp quyền; dữ liệu có thể cần kết nối Internet.'),
    FilledButton.icon(
      onPressed: () => unawaited(_requestLocationPermission()),
      icon: const Icon(Icons.my_location),
      label: Text(locationGranted == true ? 'GPS đã được cấp quyền' : 'Cấp quyền vị trí GPS'),
    ),
    if (locationError.isNotEmpty)
      Text(locationError, style: const TextStyle(color: Colors.orange)),
    _section('Dữ liệu tốc độ OSM · SQLite (M4.2)'),
    Text(roadStatus['available'] == true
        ? 'Đã nhập: ${roadStatus['segments']} / ${roadStatus['maximumSegments'] ?? 1000000} đoạn đường có maxspeed'
        : 'Chưa nhập dữ liệu đường. Tốc độ giới hạn hiển thị là --.'),
    if (roadStatus['available'] == true)
      Text('Chỉ mục: ${roadStatus['storage'] ?? 'SQLite'}'
          ' • Lần nhập thành công gần nhất: +${roadStatus['lastAdded'] ?? 0}'
          ' đoạn, bỏ qua ${roadStatus['lastSkippedDuplicates'] ?? 0} đoạn trùng.'),
    const Text('Dữ liệu lưu trong SQLite, ưu tiên RTree và tự dùng chỉ mục '
        'B-tree nếu môi trường không hỗ trợ RTree. ' 
        'Các đoạn có maxspeed xung đột được giữ riêng và có thể trả về chưa rõ. '
        
        'Chọn GeoJSON FeatureCollection xuất từ OpenStreetMap hoặc '
        'Overpass Turbo, có đường LineString và thuộc tính maxspeed. '
        'Mỗi tệp giới hạn 8 MB / 50.000 đoạn. Đây là dữ liệu cộng đồng '
        'tham khảo, không thay thế biển báo thực tế.'),
    const SizedBox(height: 8),
    FilledButton.icon(
      onPressed: roadBusy ? null : () => unawaited(_importRoadGeoJson()),
      icon: const Icon(Icons.file_open),
      label: Text(roadBusy ? 'Đang nhập dữ liệu…' : 'Thay toàn bộ bằng tệp GeoJSON'),
    ),
    OutlinedButton.icon(
      onPressed: roadBusy ? null : () => unawaited(_importRoadTopologyGeoJson()),
      icon: const Icon(Icons.account_tree_outlined),
      label: const Text('Nhập GeoJSON có OSM Node ID (thay toàn bộ)'),
    ),
    if (roadStatus['available'] == true)
      OutlinedButton.icon(
        onPressed: roadBusy ? null
            : () => unawaited(_importRoadGeoJson(append: true)),
        icon: const Icon(Icons.library_add),
        label: const Text('Nhập bổ sung khu vực (giữ dữ liệu cũ)'),
      ),
    if (roadStatus['available'] == true)
      TextButton.icon(
        onPressed: roadBusy ? null : () => unawaited(_deleteRoadGeoJson()),
        icon: const Icon(Icons.delete_outline),
        label: const Text('Xóa dữ liệu maxspeed đã nhập'),
      ),
    _section('M4.3 · Rà soát chất lượng dữ liệu đã lưu'),
    const Text('Rà soát chỉ đọc: kiểm đếm chỉ mục và các bản sao của '
        'phiên bản cũ. Không đánh giá độ đúng của biển báo hoặc maxspeed.'),
    OutlinedButton.icon(
      onPressed: roadBusy || roadStatus['available'] != true
          ? null : () => unawaited(_scanRoadQuality()),
      icon: const Icon(Icons.fact_check_outlined),
      label: Text(roadBusy ? 'Đang xử lý dữ liệu…' : 'Rà soát dữ liệu SQLite'),
    ),
    if (roadQuality != null) ...[
      Text('Đoạn đường: ${roadQuality!['segments'] ?? '--'}'
          ' • Trùng tiềm năng: ${roadQuality!['legacyDuplicates'] ?? '--'}'
          ' • Bản ghi chỉ mục: ${roadQuality!['spatialRows'] ?? '--'}'),
      Text(roadQuality!['integrityOk'] == true
          ? 'Chỉ mục cấu trúc: nhất quán'
          : 'CẢNH BÁO: chỉ mục không nhất quán, không được dọn tự động.'),
      if (roadQuality!['integrityOk'] == true &&
          (roadQuality!['legacyDuplicates'] as int? ?? 0) > 0)
        OutlinedButton.icon(
          onPressed: roadBusy ? null : () => unawaited(_cleanRoadDuplicates()),
          icon: const Icon(Icons.cleaning_services_outlined),
          label: const Text('Dọn bản sao cũ (cần xác nhận)'),
        ),
    ],
    if ((roadStatus['lastCleanedAt'] as int? ?? 0) > 0)
      Text('Lần dọn gần nhất: đã xóa '
          '${roadStatus['lastCleanedDuplicates'] ?? 0} bản sao.'),
    if (roadError.isNotEmpty)
      Text(roadError, style: const TextStyle(color: Colors.orange)),
    const Text('Ngắt và kết nối lại Android Auto sau khi thay dữ liệu đường.'),
    _section('M5.1 · Kho biển báo / camera có tọa độ'),
    const TrafficPointPanel(),
    _section('M4.4C · Chẩn đoán Android Auto trên xe'),
    const CarSurfaceDiagnosticsPanel(),

    SwitchListTile(
      title: const Text('Bản đồ 3D'),
      subtitle: const Text('Bản đồ Android Auto đã có MapLibre 2D; chưa hỗ trợ 3D.'),
      value: c.map3d,
      onChanged: (v) => unawaited(widget.store.update(c.copyWith(map3d: v))),
    ),
    SwitchListTile(
      title: const Text('Hiện tốc độ ước tính'),
      subtitle: const Text('Phải phân biệt với biển tốc độ đã kiểm chứng'),
      value: c.showSpeedEstimates,
      onChanged: (v) => unawaited(widget.store.update(
          c.copyWith(showSpeedEstimates: v))),
    ),
    SwitchListTile(
      title: const Text('Giao diện đêm'), value: c.nightMode,
      onChanged: (v) => unawaited(widget.store.update(c.copyWith(nightMode: v))),
    ),
    ListTile(title: const Text('Khoảng cách cảnh báo'),
      subtitle: Text('${c.signWarningMeters} mét')),
    Slider(
      value: c.signWarningMeters.toDouble(), min: 100, max: 2000,
      divisions: 19,
      onChanged: (v) => unawaited(widget.store.update(
          c.copyWith(signWarningMeters: v.round()))),
    ),
    const Text('Chưa có dữ liệu biển báo hay định tuyến thực. '
        'Không dùng bản preview để quyết định tốc độ lái xe.'),
  ]);

  Future<void> _saveEvent() async {
    final title = eventTitle.text.trim();
    final day = int.tryParse(eventDay.text.trim());
    final month = int.tryParse(eventMonth.text.trim());
    final year = int.tryParse(eventYear.text.trim());
    String? error;
    if (title.isEmpty || day == null || month == null ||
        month < 1 || month > 12 || day < 1 ||
        day > (eventLunar ? 30 : 31)) {
      error = 'Nhập tên, ngày và tháng hợp lệ.';
    } else if (eventRepeat == EventRepeat.once &&
        (year == null || year < 1900 || year > 2100)) {
      error = 'Sự kiện một lần cần có năm từ 1900 đến 2100.';
    } else if (!eventLunar) {
      final probe = DateTime.utc(year ?? 2028, month, day);
      if (probe.day != day || probe.month != month) {
        error = 'Ngày này không tồn tại trong tháng đã chọn.';
      }
    } else if (eventRepeat == EventRepeat.once &&
        LunarCalendar.lunarToSolar(day, month, year!,
            leap: eventLeap) == null) {
      error = 'Ngày âm lịch hoặc tháng nhuận không hợp lệ.';
    }
    if (error != null) {
      setState(() => eventError = error!);
      return;
    }
    try {
      await eventStore.upsert(CalEvent(
        id: editingEventId ?? eventStore.nextId(),
        title: title,
        lunar: eventLunar,
        leap: eventLunar && eventLeap,
        day: day!,
        month: month!,
        year: eventRepeat == EventRepeat.once ? year! : 0,
        repeat: eventRepeat,
      ));
      if (!mounted) return;
      setState(_resetEventForm);
    } catch (e) {
      if (mounted) setState(() => eventError = 'Lưu thất bại: $e');
    }
  }

  void _resetEventForm() {
    eventTitle.clear();
    eventDay.clear();
    eventMonth.clear();
    eventYear.clear();
    eventLunar = false;
    eventLeap = false;
    eventRepeat = EventRepeat.annual;
    editingEventId = null;
    eventError = '';
  }

  void _editEvent(CalEvent e) {
    setState(() {
      editingEventId = e.id;
      eventTitle.text = e.title;
      eventDay.text = e.day.toString();
      eventMonth.text = e.month.toString();
      eventYear.text = e.repeat == EventRepeat.once ? e.year.toString() : '';
      eventLunar = e.lunar;
      eventLeap = e.leap;
      eventRepeat = e.repeat;
      eventError = '';
    });
  }

  String _dateLabel(DateTime d) => [d.day, d.month, d.year].join('/');

  Widget _eventsPage() => AnimatedBuilder(
    animation: eventStore,
    builder: (context, child) {
      final today = LunarCalendar.vietnamToday(DateTime.now().toUtc());
      final lunar = LunarCalendar.solarToLunar(
        today.day, today.month, today.year);
      final upcoming = <({CalEvent event, DateTime date})>[];
      for (final e in [...CalendarEvents.defaults, ...eventStore.events]) {
        final when = CalendarEvents.nextOccurrence(e, today);
        if (when != null) upcoming.add((event: e, date: when));
      }
      upcoming.sort((a, b) => a.date.compareTo(b.date));
      return _scroll([
        _section('Lịch và sự kiện Việt Nam'),
        Text('Hôm nay: ${_dateLabel(today)} • Âm lịch $lunar • Năm ${LunarCalendar.canChi(lunar.year)}'),
        const Text('Dữ liệu cá nhân lưu ngoại tuyến bằng JSON phiên bản 1; '
            'sẽ chuyển sang SQLite. Chưa bật thông báo nhắc trước.'),
        if (eventStore.loadError != null)
          Text(eventStore.loadError!,
              style: const TextStyle(color: Colors.orange)),
        _section('Sự kiện sắp diễn ra'),
        for (final next in upcoming.take(6))
          ListTile(
            title: Text(next.event.title),
            subtitle: Text('${_dateLabel(next.date)}${next.event.lunar ? ' • Âm lịch' : ' • Dương lịch'}'),
            trailing: Text(next.date == today ? 'Hôm nay' :
                'Còn ${next.date.difference(today).inDays} ngày'),
          ),
        _section(editingEventId == null ? 'Thêm sự kiện cá nhân' :
            'Chỉnh sửa sự kiện'),
        TextField(controller: eventTitle,
            decoration: const InputDecoration(labelText: 'Tên sự kiện')),
        SwitchListTile(
          title: const Text('Sự kiện âm lịch'),
          value: eventLunar,
          onChanged: (v) => setState(() {
            eventLunar = v;
            if (!v) eventLeap = false;
          }),
        ),
        if (eventLunar)
          SwitchListTile(
            title: const Text('Tháng nhuận'),
            subtitle: const Text('Chỉ lặp trong đúng tháng nhuận đã chọn'),
            value: eventLeap,
            onChanged: (v) => setState(() => eventLeap = v),
          ),
        DropdownButton<EventRepeat>(
          isExpanded: true,
          value: eventRepeat,
          items: const [
            DropdownMenuItem(value: EventRepeat.annual,
                child: Text('Lặp hằng năm')),
            DropdownMenuItem(value: EventRepeat.monthly,
                child: Text('Lặp hằng tháng')),
            DropdownMenuItem(value: EventRepeat.once,
                child: Text('Chỉ một lần')),
          ],
          onChanged: (value) {
            if (value != null) setState(() => eventRepeat = value);
          },
        ),
        Row(children: [
          Expanded(child: TextField(controller: eventDay,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Ngày'))),
          const SizedBox(width: 12),
          Expanded(child: TextField(controller: eventMonth,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Tháng'))),
          if (eventRepeat == EventRepeat.once) ...[
            const SizedBox(width: 12),
            Expanded(child: TextField(controller: eventYear,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Năm'))),
          ],
        ]),
        if (eventError.isNotEmpty)
          Text(eventError, style: const TextStyle(color: Colors.orange)),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: () => unawaited(_saveEvent()),
          icon: Icon(editingEventId == null ? Icons.add : Icons.save),
          label: Text(editingEventId == null ? 'Lưu sự kiện' :
              'Cập nhật sự kiện'),
        ),
        if (editingEventId != null)
          TextButton(onPressed: () => setState(_resetEventForm),
              child: const Text('Hủy chỉnh sửa')),
        _section('Sự kiện cá nhân đã lưu'),
        if (!eventStore.loaded) const Text('Đang đọc dữ liệu ngoại tuyến…'),
        if (eventStore.loaded && eventStore.events.isEmpty)
          const Text('Chưa có sự kiện cá nhân.'),
        for (final e in eventStore.events)
          ListTile(
            title: Text(e.title),
            subtitle: Text('${e.day}/${e.month}${e.lunar ? ' âm lịch' : ' dương lịch'}'),
            trailing: Row(mainAxisSize: MainAxisSize.min, children: [
              IconButton(
                icon: const Icon(Icons.edit_outlined),
                tooltip: 'Chỉnh sửa',
                onPressed: () => _editEvent(e),
              ),
              IconButton(
                icon: const Icon(Icons.delete_outline),
                tooltip: 'Xóa sự kiện',
                onPressed: () => unawaited(eventStore.remove(e.id)),
              ),
            ]),
          ),
      ]);
    },
  );

  Widget _qrPage() => _scroll([
    _section('Tạo VietQR ngoại tuyến'),
    const Text('Nhập BIN ngân hàng (6 số) và tài khoản (6–19 số). '
        'Chưa có danh sách ngân hàng đồng bộ; tự kiểm tra BIN trước khi dùng.'),
    for (final item in <(TextEditingController, String, TextInputType)>[
      (bin, 'BIN ngân hàng', TextInputType.number),
      (account, 'Số tài khoản', TextInputType.number),
      (holder, 'Tên chủ tài khoản (chỉ nhãn hiển thị)', TextInputType.text),
      (amount, 'Số tiền VND (có thể bỏ trống)', TextInputType.number),
      (memo, 'Nội dung chuyển khoản', TextInputType.text),
    ])
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: TextField(controller: item.$1, keyboardType: item.$3,
            decoration: InputDecoration(labelText: item.$2)),
      ),
    const SizedBox(height: 10),
    FilledButton(onPressed: _buildQr, child: const Text('Tạo và lưu mã QR')),
    if (qrError.isNotEmpty)
      Text(qrError, style: const TextStyle(color: Colors.orange)),
    if (qrPayload != null)
      const Text('Đã tạo payload. Xem QR tại trang Bố cục. '
          'Cần quét thử bằng ứng dụng ngân hàng trước khi sử dụng.'),
  ]);
  Widget _mediaPage() => _scroll([
    _section('Media và hệ thống'),
    const ListTile(
      leading: Icon(Icons.music_note),
      title: Text('Media trên Android Auto'),
      subtitle: Text('Chưa tích hợp MediaLibraryService. '
          'Không có video trong khi lái xe.'),
    ),
    const ListTile(
      leading: Icon(Icons.directions_car),
      title: Text('Trạng thái Android Auto'),
      subtitle: Text('PENDING_DEVICE_TEST • cần kiểm chứng trên xe'),
    ),
    const ListTile(
      leading: Icon(Icons.security),
      title: Text('Cài đặt ứng dụng'),
      subtitle: Text('Không dùng KingInstaller, root hoặc giả mạo nguồn cài.'),
    ),
    const ListTile(
      leading: Icon(Icons.build_circle_outlined),
      title: Text('Giai đoạn hiện tại'),
      subtitle: Text('M3 • OSM maxspeed theo vùng, chưa có định tuyến thực'),
    ),
  ]);
}
