import 'dart:async';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../features/car_layout/layout_config.dart';
import '../features/car_layout/layout_store.dart';
import '../features/car_preview/car_preview.dart';
import '../features/payments/vietqr.dart';

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
  List<Map<String, dynamic>> events = [];

  @override
  void initState() {
    super.initState();
    unawaited(_loadFields());
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
      eventTitle, eventDay, eventMonth]) {
      c.dispose();
    }
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
    const Text('Điện thoại chỉ dùng cài đặt và xem trước. '
        'Bố cục mục tiêu trên xe chưa được xác minh.'),
    const SizedBox(height: 12),
    CarPreview(config: c, vietQrPayload: qrPayload),
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
    _configSlider('Cột bản đồ', c.columnRatio, 0.50, 0.70,
        (value) => c.copyWith(columnRatio: value)),
    _configSlider('Hàng lịch', c.calendarWeight, 0.10, 0.75,
        (value) => c.copyWith(calendarWeight: value)),
    _configSlider('Hàng VietQR', c.qrWeight, 0.10, 0.75,
        (value) => c.copyWith(qrWeight: value)),
    _configSlider('Hàng Media', c.mediaWeight, 0.10, 0.75,
        (value) => c.copyWith(mediaWeight: value)),
    const Text('Trọng số ba hàng được tự chuẩn hóa về 100%.'),
  ]);

  Widget _configSlider(String label, double value, double min, double max,
      CarLayoutConfig Function(double) build) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('$label: ${(value * 100).toStringAsFixed(0)}'),
      Slider(
        value: value.clamp(min, max), min: min, max: max,
        divisions: 20,
        label: (value * 100).round().toString(),
        onChanged: (next) => unawaited(widget.store.update(build(next))),
      ),
    ],
  );

  Widget _mapPage(CarLayoutConfig c) => _scroll([
    _section('Bản đồ và cảnh báo giao thông'),
    SwitchListTile(
      title: const Text('Bản đồ 3D'),
      subtitle: const Text('Tùy chọn thiết kế; chưa tích hợp renderer MapLibre'),
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

  Widget _eventsPage() => _scroll([
    _section('Lịch và sự kiện Việt Nam'),
    const Text('Danh sách nhập thử cho mốc đầu; chuyển sang lịch âm + '
        'SQLite và nhắc sự kiện ở giai đoạn tiếp theo.'),
    TextField(controller: eventTitle,
        decoration: const InputDecoration(labelText: 'Tên sự kiện')),
    Row(children: [
      Expanded(child: TextField(controller: eventDay,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: 'Ngày dương'))),
      const SizedBox(width: 12),
      Expanded(child: TextField(controller: eventMonth,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: 'Tháng dương'))),
    ]),
    const SizedBox(height: 8),
    FilledButton.icon(
      onPressed: () {
        final d = int.tryParse(eventDay.text);
        final m = int.tryParse(eventMonth.text);
        if (eventTitle.text.trim().isEmpty || d == null ||
            m == null || d < 1 || m < 1 || m > 12) return;
        try {
          DateTime(2028, m, d);
          final limit = DateTime(2028, m + 1, 0).day;
          if (d > limit) return;
        } catch (_) { return; }
        setState(() => events.add({
          'title': eventTitle.text.trim(), 'day': d, 'month': m,
        }));
        eventTitle.clear();
      },
      icon: const Icon(Icons.add), label: const Text('Thêm sự kiện thử'),
    ),
    for (var i = 0; i < events.length; i++)
      ListTile(
        title: Text(events[i]['title'] as String),
        subtitle: Text('${events[i]['day']}/${events[i]['month']}'),
        trailing: IconButton(
          icon: const Icon(Icons.delete_outline),
          onPressed: () => setState(() => events.removeAt(i)),
        ),
      ),
  ]);
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
      subtitle: Text('M0–M1 • bản chạy thử, chưa có điều hướng thực'),
    ),
  ]);
}
