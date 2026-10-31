import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/features/lunar_calendar/lunar_calendar.dart';
import 'package:thanhtaxixanh_driver/features/events/calendar_events.dart';

void main() {
  test('Vietnamese Tet 2024 2025 2026', () {
    for (final date in [
      (2024, 2, 10), (2025, 1, 29), (2026, 2, 17),
    ]) {
      final lunar = LunarCalendar.solarToLunar(date.$3, date.$2, date.$1);
      expect((lunar.day, lunar.month, lunar.year),
          (1, 1, date.$1));
      final solar = LunarCalendar.lunarToSolar(1, 1, date.$1);
      expect((solar!.year, solar.month, solar.day), date);
    }
    expect(LunarCalendar.canChi(2026), 'Bính Ngọ');
  });
  test('impossible lunar day is invalid rather than silently shifting', () {
    expect(LunarCalendar.lunarToSolar(31, 1, 2026), isNull);
  });
  test('Vietnam day boundary is fixed at UTC+7', () {
    final before = LunarCalendar.vietnamToday(
      DateTime.utc(2026, 2, 16, 16, 59));
    final after = LunarCalendar.vietnamToday(
      DateTime.utc(2026, 2, 16, 17, 00));
    expect(before.day, 16);
    expect(after.day, 17);
  });
  test('Giao thua is exactly the day before lunar new year', () {
    final eve = CalendarEvents.defaults.firstWhere((e) => e.id == -31);
    final next = CalendarEvents.nextOccurrence(eve, DateTime.utc(2026, 2, 1));
    expect((next!.year, next.month, next.day), (2026, 2, 16));
  });
  test('event kind and leap fields survive serialization', () {
    const e = CalEvent(id: 3, title: 'Giỗ thử', lunar: true,
        day: 15, month: 6, kind: 2, leap: true);
    final restored = CalEvent.fromJson(e.toJson());
    expect(restored.kind, 2);
    expect(restored.leap, isTrue);
  });
}
