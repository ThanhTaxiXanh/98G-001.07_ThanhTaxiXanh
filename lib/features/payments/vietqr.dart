import 'dart:convert';

/// Vietnamese bank transfer: EMVCo TLV, NAPAS AID and CRC16/CCITT-FALSE.
class VietQr {
  static String tlv(String tag, String value) {
    final bytes = utf8.encode(value).length;
    if (bytes > 99) throw const FormatException('EMV field exceeds 99 bytes');
    return '$tag${bytes.toString().padLeft(2, '0')}$value';
  }

  static int crc16(String input) {
    var crc = 0xffff;
    for (final b in utf8.encode(input)) {
      crc ^= b << 8;
      for (var i = 0; i < 8; i++) {
        crc = (crc & 0x8000 != 0)
            ? ((crc << 1) ^ 0x1021) & 0xffff
            : (crc << 1) & 0xffff;
      }
    }
    return crc;
  }

  static String asciiMessage(String text) {
    const marks = 'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩ'
        'òóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ';
    const replacements = 'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiii'
        'ooooooooooooooooouuuuuuuuuuuyyyyyd';
    var s = text.toLowerCase();
    for (var i = 0; i < marks.length; i++) {
      s = s.replaceAll(marks[i], replacements[i]);
    }
    return s.toUpperCase().replaceAll(RegExp(r'[^A-Z0-9 -]'), '')
        .replaceAll(RegExp(' +'), ' ').trim();
  }

  static String build({
    required String bin,
    required String account,
    int? amount,
    String message = '',
  }) {
    if (!RegExp(r'^\d{6}$').hasMatch(bin)) {
      throw const FormatException('BIN must contain exactly 6 digits');
    }
    if (!RegExp(r'^\d{6,19}$').hasMatch(account)) {
      throw const FormatException('Account must contain 6-19 digits');
    }
    if (amount != null && amount <= 0) {
      throw const FormatException('Amount must be positive');
    }
    final memo = asciiMessage(message);
    if (utf8.encode(memo).length > 25) {
      throw const FormatException('Message exceeds 25 bytes');
    }
    final beneficiary = "${tlv('00', bin)}${tlv('01', account)}";
    final merchant = "${tlv('00', 'A000000727')}${tlv('01', beneficiary)}${tlv('02', 'QRIBFTTA')}";
    final base = "${tlv('00', '01')}${tlv('01', amount == null ? '11' : '12')}"
        "${tlv('38', merchant)}${tlv('53', '704')}"
        "${amount == null ? '' : tlv('54', '$amount')}"
        "${tlv('58', 'VN')}${memo.isEmpty ? '' : tlv('62', tlv('08', memo))}6304";
    return '$base${crc16(base).toRadixString(16).toUpperCase().padLeft(4, '0')}';
  }
}
