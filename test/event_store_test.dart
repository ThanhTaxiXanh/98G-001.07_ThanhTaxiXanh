import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:thanhtaxixanh_driver/features/events/calendar_events.dart';
import 'package:thanhtaxixanh_driver/features/events/event_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('personal lunar and solar events survive reload, edit and deletion', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = CalendarEventStore(preferences: prefs);
    await store.load();
    expect(store.events, isEmpty);

    final lunar = CalEvent(
      id: store.nextId(), title: 'Giỗ gia đình',
      lunar: true, day: 15, month: 7,
    );
    await store.upsert(lunar);
    await store.upsert(const CalEvent(
      id: 123, title: 'Họp định kỳ',
      lunar: false, day: 31, month: 1,
      repeat: EventRepeat.monthly,
    ));

    final reloaded = CalendarEventStore(preferences: prefs);
    await reloaded.load();
    expect(reloaded.events.length, 2);
    expect(reloaded.events.first.lunar, isTrue);
    expect(reloaded.events.first.title, 'Giỗ gia đình');
    await reloaded.upsert(CalEvent(
      id: lunar.id, title: 'Giỗ năm nay',
      lunar: true, day: 15, month: 7,
    ));
    expect(reloaded.events.first.title, 'Giỗ năm nay');
    await reloaded.remove(123);
    expect(reloaded.events.length, 1);
    expect(jsonDecode(prefs.getString(CalendarEventStore.storageKey)!),
        isA<List<dynamic>>());
  });

  test('corrupt event row does not erase valid rows', () async {
    SharedPreferences.setMockInitialValues({
      CalendarEventStore.storageKey: jsonEncode([
        {'id': 44, 'title': 'Ngày quan trọng', 'lunar': false,
         'day': 2, 'month': 9, 'repeat': 0},
        {'id': -1, 'title': 'Không hợp lệ', 'lunar': false,
         'day': 1, 'month': 1, 'repeat': 0},
        {'id': 55, 'title': 'Hỏng', 'repeat': 900},
      ]),
    });
    final store = CalendarEventStore(
      preferences: await SharedPreferences.getInstance());
    await store.load();
    expect(store.events.map((e) => e.id).toList(), [44]);
  });
}
