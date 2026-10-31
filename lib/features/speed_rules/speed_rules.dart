enum SpeedSource { observedSign, osmTag, legalDefaultEstimate, unknown }
enum Zone { inside, outside, unknown }
enum VehicleProfile { passengerCar, other }
enum RoadForm { undividedTwoWay, dividedCarriageway, oneWay, unknown }

class SpeedLimitEstimate {
  const SpeedLimitEstimate(this.valueKmh, this.source, this.reason);
  final int? valueKmh;
  final SpeedSource source;
  final String reason;
  bool get isEstimate => source == SpeedSource.legalDefaultEstimate;
  String get display => valueKmh == null
      ? '--' : '${isEstimate ? '≈' : ''}$valueKmh';
}

/// Reference baseline is TT38/2024, Article 6; must re-verify before release.
/// NEVER interpret urban buildings alone as a confirmed legally posted zone.
class SpeedRules {
  static const regulationVersion = '38/2024/TT-BGTVT';
  static SpeedLimitEstimate evaluate({
    required Zone zone,
    required RoadForm roadForm,
    VehicleProfile vehicle = VehicleProfile.passengerCar,
    int? oneWayMotorLanes,
    String? highway,
    String? maxspeed,
    int? verifiedSignKmh,
  }) {
    if (verifiedSignKmh != null &&
        verifiedSignKmh >= 5 && verifiedSignKmh <= 130) {
      return SpeedLimitEstimate(verifiedSignKmh, SpeedSource.observedSign,
          'Biển báo đã xác minh theo hướng/làn đang xét');
    }
    final speed = int.tryParse((maxspeed ?? '').trim());
    if (speed != null && speed >= 5 && speed <= 130) {
      return SpeedLimitEstimate(speed, SpeedSource.osmTag,
          'OSM maxspeed; chưa thay thế kiểm tra biển hiện trường');
    }
    if (highway == 'motorway' || highway == 'motorway_link') {
      return const SpeedLimitEstimate(null, SpeedSource.unknown,
          'Đường cao tốc có giới hạn riêng; không áp bảng mặc định');
    }
    if (vehicle != VehicleProfile.passengerCar) {
      return const SpeedLimitEstimate(null, SpeedSource.unknown,
          'Chưa triển khai bảng riêng cho loại phương tiện này');
    }
    if (zone == Zone.unknown || roadForm == RoadForm.unknown) {
      return const SpeedLimitEstimate(null, SpeedSource.unknown,
          'Thiếu vùng pháp lý hoặc dạng đường được xác minh');
    }
    bool multi;
    switch (roadForm) {
      case RoadForm.dividedCarriageway:
        multi = true;
      case RoadForm.undividedTwoWay:
        multi = false; // Even if OSM lanes=4.
      case RoadForm.oneWay:
        if (oneWayMotorLanes == null || oneWayMotorLanes <= 0) {
          return const SpeedLimitEstimate(null, SpeedSource.unknown,
              'Chưa biết số làn xe cơ giới trên đường một chiều');
        }
        multi = oneWayMotorLanes >= 2;
      case RoadForm.unknown:
        return const SpeedLimitEstimate(null, SpeedSource.unknown,
            'Chưa rõ phân cách giữa');
    }
    final speedLimit = switch ((zone, multi)) {
      (Zone.inside, false) => 50,
      (Zone.inside, true) => 60,
      (Zone.outside, false) => 80,
      (Zone.outside, true) => 90,
      _ => null,
    };
    return SpeedLimitEstimate(speedLimit, SpeedSource.legalDefaultEstimate,
        'Ước tính theo $regulationVersion, không phải biển tốc độ đã xác minh');
  }
}
