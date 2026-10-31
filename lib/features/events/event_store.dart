import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'calendar_events.dart';

/// Stores personal events offline. Versioned JSON is a transitional backend:
/// a future SQLite migration will read this same key without dropping events.
class CalendarEventStore extends ChangeNotifier {
  CalendarEventStore({SharedPreferences? preferences}) : _preferences = preferences;

  static const storageKey = 'calendar_custom_events_v1';
  final SharedPreferences? _preferences;
  List<CalEvent> events = [];
  bool loaded = false;
  String? loadError;

  Future<SharedPreferences> _prefs() async =>
      _preferences ?? await SharedPreferences.getInstance();

  Future<void> load() async {
    try {
      final raw = (await _prefs()).getString(storageKey);
      if (raw != null && raw.isNotEmpty) {
        final decoded = jsonDecode(raw);
        if (decoded is! List) {
          throw const FormatException('Events must be a list');
        }
        final restored = <CalEvent>[];
        for (final entry in decoded) {
          if (entry is! Map<String, dynamic>) {
            continue;
          }
          try {
            final event = CalEvent.fromJson(entry);
            if (event.id > 0 && event.title.trim().isNotEmpty &&
                event.day >= 1 && event.day <= (event.lunar ? 30 : 31) &&
                event.month >= 1 && event.month <= 12) {
              restored.add(event);
            }
          } on Object {
            // Skip corrupted rows; valid events must remain accessible.
          }
        }
        events = restored;
      }
      loadError = null;
    } catch (e) {
      loadError = 'Không thể đọc sự kiện đã lưu: $e';
    }
    loaded = true;
    notifyListeners();
  }

  int nextId() {
    var id = DateTime.now().microsecondsSinceEpoch;
    for (final e in events) {
      if (e.id >= id) id = e.id + 1;
    }
    return id;
  }

  Future<void> upsert(CalEvent event) async {
    if (event.id <= 0 || event.title.trim().isEmpty) {
      throw const FormatException('Invalid personal event');
    }
    final next = [...events];
    final at = next.indexWhere((e) => e.id == event.id);
    if (at < 0) {
      next.add(event);
    } else {
      next[at] = event;
    }
    await _write(next);
  }

  Future<void> remove(int id) async =>
      _write(events.where((e) => e.id != id).toList());

  Future<void> _write(List<CalEvent> next) async {
    final success = await (await _prefs()).setString(
      storageKey, jsonEncode(next.map((e) => e.toJson()).toList()));
    if (!success) throw StateError('Không lưu được sự kiện');
    events = next;
    loadError = null;
    notifyListeners();
  }
}
