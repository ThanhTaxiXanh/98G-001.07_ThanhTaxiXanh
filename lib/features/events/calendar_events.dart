import '../lunar_calendar/lunar_calendar.dart';

enum EventRepeat { annual, monthly, once, newYearsEve }

class CalEvent {
  const CalEvent({
    required this.id, required this.title, required this.lunar,
    required this.day, required this.month, this.year = 0,
    this.repeat = EventRepeat.annual, this.kind = 0,
    this.leap = false,
  });
  final int id, day, month, year, kind;
  final String title;
  final bool lunar, leap;
  final EventRepeat repeat;

  Map<String, dynamic> toJson() => {
    'id': id, 'title': title, 'lunar': lunar,
    'day': day, 'month': month, 'year': year,
    'repeat': repeat.index, 'kind': kind, 'leap': leap,
  };
  factory CalEvent.fromJson(Map<String, dynamic> j) => CalEvent(
    id: (j['id'] as num).toInt(), title: j['title'] as String,
    lunar: j['lunar'] as bool, day: (j['day'] as num).toInt(),
    month: (j['month'] as num).toInt(),
    year: (j['year'] as num?)?.toInt() ?? 0,
    repeat: EventRepeat.values[(j['repeat'] as num?)?.toInt() ?? 0],
    kind: (j['kind'] as num?)?.toInt() ?? 0,
    leap: j['leap'] as bool? ?? false,
  );
}

class CalendarEvents {
  static final defaults = <CalEvent>[
    const CalEvent(id: -10, title: 'Tết Dương lịch',
      lunar: false, day: 1, month: 1, kind: 1),
    const CalEvent(id: -13, title: 'Giải phóng miền Nam',
      lunar: false, day: 30, month: 4, kind: 1),
    const CalEvent(id: -14, title: 'Quốc tế Lao động',
      lunar: false, day: 1, month: 5, kind: 1),
    const CalEvent(id: -18, title: 'Quốc khánh',
      lunar: false, day: 2, month: 9, kind: 1),
    const CalEvent(id: -19, title: 'Phụ nữ Việt Nam',
      lunar: false, day: 20, month: 10, kind: 1),
    const CalEvent(id: -20, title: 'Nhà giáo Việt Nam',
      lunar: false, day: 20, month: 11, kind: 1),
    const CalEvent(id: -30, title: 'Tết Nguyên Đán',
      lunar: true, day: 1, month: 1, kind: 1),
    const CalEvent(id: -31, title: 'Giao thừa',
      lunar: true, day: 1, month: 1,
      repeat: EventRepeat.newYearsEve, kind: 1),
    const CalEvent(id: -34, title: 'Giỗ Tổ Hùng Vương',
      lunar: true, day: 10, month: 3, kind: 1),
    const CalEvent(id: -38, title: 'Tết Trung Thu',
      lunar: true, day: 15, month: 8, kind: 1),
    const CalEvent(id: -11, title: 'Valentine',
      lunar: false, day: 14, month: 2, kind: 1),
    const CalEvent(id: -12, title: 'Quốc tế Phụ nữ',
      lunar: false, day: 8, month: 3, kind: 1),
    const CalEvent(id: -15, title: 'Quốc tế Thiếu nhi',
      lunar: false, day: 1, month: 6, kind: 1),
    const CalEvent(id: -16, title: 'Ngày Gia đình Việt Nam',
      lunar: false, day: 28, month: 6, kind: 1),
    const CalEvent(id: -17, title: 'Ngày Thương binh Liệt sĩ',
      lunar: false, day: 27, month: 7, kind: 1),
    const CalEvent(id: -21, title: 'Thành lập Quân đội Nhân dân Việt Nam',
      lunar: false, day: 22, month: 12, kind: 1),
    const CalEvent(id: -22, title: 'Giáng sinh',
      lunar: false, day: 25, month: 12, kind: 1),
    const CalEvent(id: -32, title: 'Rằm tháng Giêng',
      lunar: true, day: 15, month: 1, kind: 1),
    const CalEvent(id: -33, title: 'Tết Hàn Thực',
      lunar: true, day: 3, month: 3, kind: 1),
    const CalEvent(id: -35, title: 'Lễ Phật Đản',
      lunar: true, day: 15, month: 4, kind: 1),
    const CalEvent(id: -36, title: 'Tết Đoan Ngọ',
      lunar: true, day: 5, month: 5, kind: 1),
    const CalEvent(id: -37, title: 'Lễ Vu Lan',
      lunar: true, day: 15, month: 7, kind: 1),
    const CalEvent(id: -39, title: 'Ông Công Ông Táo',
      lunar: true, day: 23, month: 12, kind: 1),
    const CalEvent(id: -1, title: 'Mùng một',
      lunar: true, day: 1, month: 1,
      repeat: EventRepeat.monthly, kind: 2),
    const CalEvent(id: -2, title: 'Ngày Rằm',
      lunar: true, day: 15, month: 1,
      repeat: EventRepeat.monthly, kind: 2),
  ];

  static DateTime? _solarStrict(int day, int month, int year) {
    if (year < 1 || month < 1 || month > 12 ||
        day < 1 || day > 31) return null;
    final d = DateTime.utc(year, month, day);
    return d.month == month ? d : null;
  }

  static DateTime? _date(CalEvent e, int year, {int? month}) => e.lunar
    ? LunarCalendar.lunarToSolar(e.day, month ?? e.month, year, leap: e.leap)
    : _solarStrict(e.day, month ?? e.month, year);

  static DateTime? nextOccurrence(CalEvent event, DateTime today) {
    final date = DateTime.utc(today.year, today.month, today.day);
    final lunar = LunarCalendar.solarToLunar(date.day, date.month, date.year);
    final cands = <DateTime>[];
    switch (event.repeat) {
      case EventRepeat.annual:
        for (var year = (event.lunar ? lunar.year : date.year) - 1;
            year <= (event.lunar ? lunar.year : date.year) + 2; year++) {
          final d = _date(event, year);
          if (d != null) cands.add(d);
        }
      case EventRepeat.monthly:
        if (event.lunar) {
          // Only schedule non-leap monthly events in this initial adapter.
          if (!event.leap) {
            for (var i = 0; i < 30; i++) {
              final month = (lunar.month - 1 + i) % 12 + 1;
              final year = lunar.year + (lunar.month - 1 + i) ~/ 12;
              final d = _date(event, year, month: month);
              if (d != null) cands.add(d);
            }
          }
        } else {
          for (var i = 0; i < 15; i++) {
            final monthZero = date.month - 1 + i;
            final d = _date(event, date.year + monthZero ~/ 12,
                month: monthZero % 12 + 1);
            if (d != null) cands.add(d);
          }
        }
      case EventRepeat.once:
        final d = _date(event, event.year);
        if (d != null) cands.add(d);
      case EventRepeat.newYearsEve:
        for (var year = lunar.year - 1; year <= lunar.year + 2; year++) {
          final d = LunarCalendar.lunarToSolar(1, 1, year);
          if (d != null) cands.add(d.subtract(const Duration(days: 1)));
        }
    }
    cands.removeWhere((d) => d.isBefore(date));
    cands.sort();
    return cands.isEmpty ? null : cands.first;
  }
}
