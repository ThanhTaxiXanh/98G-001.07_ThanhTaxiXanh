import 'dart:async';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../features/car_layout/layout_config.dart';
import '../features/car_layout/layout_store.dart';
import '../features/car_preview/car_preview.dart';
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
        value: value.clamp(min, max).toDouble(), min: min, max: max,
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
      if (mounted) setState(() => eventError = 'Lưu thất bại: ' + e.toString());
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
        Text('Hôm nay: ' + _dateLabel(today) +
            ' • Âm lịch ' + lunar.toString() +
            ' • Năm ' + LunarCalendar.canChi(lunar.year)),
        const Text('Dữ liệu cá nhân lưu ngoại tuyến bằng JSON phiên bản 1; '
            'sẽ chuyển sang SQLite. Chưa bật thông báo nhắc trước.'),
        if (eventStore.loadError != null)
          Text(eventStore.loadError!,
              style: const TextStyle(color: Colors.orange)),
        _section('Sự kiện sắp diễn ra'),
        for (final next in upcoming.take(6))
          ListTile(
            title: Text(next.event.title),
            subtitle: Text(_dateLabel(next.date) +
                (next.event.lunar ? ' • Âm lịch' : ' • Dương lịch')),
            trailing: Text(next.date == today ? 'Hôm nay' :
                'Còn ' + next.date.difference(today).inDays.toString() +
                ' ngày'),
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
            subtitle: Text([e.day, e.month].join('/') +
                (e.lunar ? ' âm lịch' : ' dương lịch')),
            trailing: Wrap(mainAxisSize: MainAxisSize.min, children: [
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
      subtitle: Text('M0–M1 • bản chạy thử, chưa có điều hướng thực'),
    ),
  ]);
}
