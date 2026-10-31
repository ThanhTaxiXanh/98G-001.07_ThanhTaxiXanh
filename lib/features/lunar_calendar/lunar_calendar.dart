import 'dart:math' as math;

/// Pure Dart port of user-provided LunarCalendar.txt (UTC+7 lunar dates).
class LunarDate {
  const LunarDate(this.day, this.month, this.year, this.isLeap);
  final int day, month, year;
  final bool isLeap;
  @override
  String toString() => '$day/$month/$year${isLeap ? " nhuận" : ""}';
}

class LunarCalendar {
  static const _epoch = 2415021.076998695;
  static const _synodic = 29.530588853;
  static const _rad = math.pi / 180;
  static const _timezone = 7.0;
  static final Map<int, int> _newMoonCache = {};
  static const can = ['Giáp', 'Ất', 'Bính', 'Đinh', 'Mậu',
    'Kỷ', 'Canh', 'Tân', 'Nhâm', 'Quý'];
  static const chi = ['Tý', 'Sửu', 'Dần', 'Mão', 'Thìn',
    'Tỵ', 'Ngọ', 'Mùi', 'Thân', 'Dậu', 'Tuất', 'Hợi'];

  static String canChi(int year) =>
      '${can[(year + 6) % 10]} ${chi[(year + 8) % 12]}';

  static int _jd(int d, int m, int y) {
    final a = (14 - m) ~/ 12;
    final yy = y + 4800 - a;
    final mm = m + 12 * a - 3;
    return d + (153 * mm + 2) ~/ 5 + 365 * yy +
        yy ~/ 4 - yy ~/ 100 + yy ~/ 400 - 32045;
  }

  static DateTime _date(int jd) {
    final int b, c;
    if (jd > 2299160) {
      final a = jd + 32044;
      b = (4 * a + 3) ~/ 146097;
      c = a - b * 146097 ~/ 4;
    } else {
      b = 0;
      c = jd + 32082;
    }
    final d = (4 * c + 3) ~/ 1461;
    final e = c - 1461 * d ~/ 4;
    final m = (5 * e + 2) ~/ 153;
    return DateTime.utc(b * 100 + d - 4800 + m ~/ 10,
      m + 3 - 12 * (m ~/ 10),
      e - (153 * m + 2) ~/ 5 + 1);
  }

  static double _newMoon(double k) {
    final t = k / 1236.85;
    final t2 = t * t, t3 = t2 * t;
    var jd = 2415020.75933 + 29.53058868 * k +
        0.0001178 * t2 - 0.000000155 * t3;
    jd += 0.00033 * math.sin((166.56 + 132.87 * t -
        0.009173 * t2) * _rad);
    final m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 -
        0.00000347 * t3;
    final mp = 306.0253 + 385.81691806 * k + 0.0107306 * t2 +
        0.00001236 * t3;
    final f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 -
        0.00000239 * t3;
    var c1 = (0.1734 - 0.000393 * t) * math.sin(m * _rad);
    c1 += 0.0021 * math.sin(2 * m * _rad);
    c1 -= 0.4068 * math.sin(mp * _rad);
    c1 += 0.0161 * math.sin(2 * mp * _rad);
    c1 -= 0.0004 * math.sin(3 * mp * _rad);
    c1 += 0.0104 * math.sin(2 * f * _rad);
    c1 -= 0.0051 * math.sin((m + mp) * _rad);
    c1 -= 0.0074 * math.sin((m - mp) * _rad);
    c1 += 0.0004 * math.sin((2 * f + m) * _rad);
    c1 -= 0.0004 * math.sin((2 * f - m) * _rad);
    c1 -= 0.0006 * math.sin((2 * f + mp) * _rad);
    c1 += 0.0010 * math.sin((2 * f - mp) * _rad);
    c1 += 0.0005 * math.sin((2 * mp + m) * _rad);
    final dt = t < -11
        ? 0.001 + 0.000839 * t + 0.0002261 * t2 -
            0.00000845 * t3 - 0.000000081 * t * t3
        : -0.000278 + 0.000265 * t + 0.000262 * t2;
    return jd + c1 - dt;
  }

  static int _newMoonDay(int k) => _newMoonCache.putIfAbsent(k,
      () => (_newMoon(k.toDouble()) + 0.5 + _timezone / 24 + 1e-12).floor());

  static double _sunLongitude(double jd) {
    final t = (jd - 2451545.0) / 36525, t2 = t * t;
    final m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 -
        0.00000048 * t * t2;
    final l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
    final dl = (1.914600 - 0.004817 * t - 0.000014 * t2) *
        math.sin(m * _rad) +
        (0.019993 - 0.000101 * t) * math.sin(2 * m * _rad) +
        0.000290 * math.sin(3 * m * _rad);
    final longitude = (l0 + dl) * _rad;
    return longitude - 2 * math.pi * (longitude / (2 * math.pi)).floor();
  }

  static int _sunIndex(int jdn) =>
      (_sunLongitude(jdn - 0.5 - _timezone / 24) / math.pi * 6 + 1e-12).floor();

  static int _month11(int year) {
    final k = ((_jd(31, 12, year) - _epoch) / _synodic).floor();
    var nm = _newMoonDay(k);
    if (_sunIndex(nm) >= 9) nm = _newMoonDay(k - 1);
    return nm;
  }

  static int _leapMonthOffset(int a11) {
    final k = ((a11 - _epoch) / _synodic + 0.5).floor();
    var i = 1;
    var arc = _sunIndex(_newMoonDay(k + i));
    while (i < 15) {
      final next = _sunIndex(_newMoonDay(k + i + 1));
      if (next == arc) break;
      arc = next;
      i++;
    }
    return i;
  }

  static LunarDate solarToLunar(int d, int m, int y) {
    if (d < 1 || m < 1 || m > 12 ||
        DateTime.utc(y, m, d).month != m ||
        DateTime.utc(y, m, d).day != d) {
      throw const FormatException('Invalid solar date');
    }
    final dayNumber = _jd(d, m, y);
    final k = ((dayNumber - _epoch) / _synodic).floor();
    var monthStart = _newMoonDay(k + 1);
    if (monthStart > dayNumber) monthStart = _newMoonDay(k);
    var a11 = _month11(y), b11 = a11;
    int lunarYear;
    if (a11 >= monthStart) {
      lunarYear = y;
      a11 = _month11(y - 1);
    } else {
      lunarYear = y + 1;
      b11 = _month11(y + 1);
    }
    final lunarDay = dayNumber - monthStart + 1;
    final diff = (monthStart - a11) ~/ 29;
    var lunarMonth = diff + 11;
    var leap = false;
    if (b11 - a11 > 365) {
      final leapDiff = _leapMonthOffset(a11);
      if (diff >= leapDiff) {
        lunarMonth = diff + 10;
        if (diff == leapDiff) leap = true;
      }
    }
    if (lunarMonth > 12) lunarMonth -= 12;
    if (lunarMonth >= 11 && diff < 4) lunarYear--;
    return LunarDate(lunarDay, lunarMonth, lunarYear, leap);
  }

  static DateTime? lunarToSolar(int d, int m, int y, {bool leap = false}) {
    if (d < 1 || d > 30 || m < 1 || m > 12) return null;
    final a11 = m < 11 ? _month11(y - 1) : _month11(y);
    final b11 = m < 11 ? _month11(y) : _month11(y + 1);
    final k = ((a11 - _epoch) / _synodic + 0.5).floor();
    var off = m - 11;
    if (off < 0) off += 12;
    if (b11 - a11 > 365) {
      final leapOff = _leapMonthOffset(a11);
      var leapMonth = leapOff - 2;
      if (leapMonth < 0) leapMonth += 12;
      if (leap && m != leapMonth) { return null; }
      if (leap || off >= leapOff) { off++; }
    } else if (leap) {
      return null;
    }
    final solar = _date(_newMoonDay(k + off) + d - 1);
    final back = solarToLunar(solar.day, solar.month, solar.year);
    if (back.day != d || back.month != m ||
        back.year != y || back.isLeap != leap) return null;
    return solar;
  }

  /// Civil clock in Vietnam without requiring device timezone to be Vietnam.
  static DateTime vietnamToday(DateTime instantUtc) {
    final vn = instantUtc.toUtc().add(const Duration(hours: 7));
    return DateTime.utc(vn.year, vn.month, vn.day);
  }
}
