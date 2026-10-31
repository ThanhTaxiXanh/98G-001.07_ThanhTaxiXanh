import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/features/speed_rules/speed_rules.dart';
void main() {
  SpeedLimitEstimate s(Zone z, RoadForm form,
          {int? lanes, String? highway, int? sign, String? osm,
          VehicleProfile vehicle = VehicleProfile.passengerCar}) =>
      SpeedRules.evaluate(zone: z, roadForm: form,
        oneWayMotorLanes: lanes, highway: highway,
        verifiedSignKmh: sign, maxspeed: osm, vehicle: vehicle);
  test('undivided two-way urban with 4 lanes remains approx 50', () {
    expect(s(Zone.inside, RoadForm.undividedTwoWay).display, '≈50');
  });
  test('undivided two-way outside is approx 80', () {
    expect(s(Zone.outside, RoadForm.undividedTwoWay).display, '≈80');
  });
  test('divided carriageway inside is approx 60', () {
    expect(s(Zone.inside, RoadForm.dividedCarriageway).display, '≈60');
  });
  test('divided carriageway outside is approx 90', () {
    expect(s(Zone.outside, RoadForm.dividedCarriageway).display, '≈90');
  });
  test('one-way two motor lanes inside/outside', () {
    expect(s(Zone.inside, RoadForm.oneWay, lanes: 2).display, '≈60');
    expect(s(Zone.outside, RoadForm.oneWay, lanes: 2).display, '≈90');
  });
  test('one-way single lane inside/outside', () {
    expect(s(Zone.inside, RoadForm.oneWay, lanes: 1).display, '≈50');
    expect(s(Zone.outside, RoadForm.oneWay, lanes: 1).display, '≈80');
  });
  test('unknown legal population zone means unknown', () {
    expect(s(Zone.unknown, RoadForm.dividedCarriageway).display, '--');
  });
  test('known divided road needs no lane count', () {
    expect(s(Zone.inside, RoadForm.dividedCarriageway).valueKmh, 60);
  });
  test('verified sign 40 outranks OSM and fallback', () {
    final result = s(Zone.outside, RoadForm.dividedCarriageway,
        sign: 40, osm: '80');
    expect(result.valueKmh, 40);
    expect(result.source, SpeedSource.observedSign);
  });
  test('motorway not assigned legal default', () {
    expect(s(Zone.outside, RoadForm.dividedCarriageway,
        highway: 'motorway').display, '--');
  });
  test('other vehicle requires own regulation matrix', () {
    expect(s(Zone.inside, RoadForm.oneWay, lanes: 2,
        vehicle: VehicleProfile.other).display, '--');
  });
  test('unknown oneway lane count gives unknown', () {
    expect(s(Zone.inside, RoadForm.oneWay).display, '--');
  });
  test('OSM maxspeed distinct from estimates', () {
    final result = s(Zone.unknown, RoadForm.unknown, osm: '50');
    expect(result.source, SpeedSource.osmTag);
    expect(result.display, '50');
  });
}
