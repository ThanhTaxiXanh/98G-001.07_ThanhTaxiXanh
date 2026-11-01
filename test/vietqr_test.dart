import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/features/payments/vietqr.dart';

void main() {
  test('CRC16 standard test vector', () {
    expect(VietQr.crc16('123456789'), 0x29b1);
  });
  test('leading zero account preserved and static/dynamic codes differ', () {
    final static = VietQr.build(bin: '970422', account: '001234567890');
    final dynamic = VietQr.build(
      bin: '970422', account: '001234567890', amount: 100000,
      message: 'Thanh toán taxi',
    );
    expect(static, contains('001234567890'));
    expect(static, contains('010211'));
    expect(dynamic, contains('010212'));
    expect(dynamic, contains('5406100000'));
    expect(dynamic, contains('QRIBFTTA'));
    expect(dynamic.substring(dynamic.length - 4),
        VietQr.crc16(dynamic.substring(0, dynamic.length - 4))
            .toRadixString(16).toUpperCase().padLeft(4, '0'));
  });
  test('invalid bank or account rejected', () {
    expect(() => VietQr.build(bin: '123', account: '12345678'),
        throwsFormatException);
    expect(() => VietQr.build(bin: '970422', account: '000'),
        throwsFormatException);
  });
}
