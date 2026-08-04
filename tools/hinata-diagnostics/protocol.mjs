const PN532_PREAMBLE = [0x00, 0x00, 0xff];

export const PN532_DIRECTION_HOST_TO_CHIP = 0xd4;
export const PN532_DIRECTION_CHIP_TO_HOST = 0xd5;
export const PN532_COMMAND_IN_LIST_PASSIVE_TARGET = 0x4a;
export const PN532_COMMAND_IN_DATA_EXCHANGE = 0x40;
export const PN532_ACK = Object.freeze([0x00, 0x00, 0xff, 0x00, 0xff, 0x00]);

function asBytes(value, name = "bytes") {
  const bytes = Array.from(value ?? []);
  for (const byte of bytes) {
    if (!Number.isInteger(byte) || byte < 0 || byte > 0xff) {
      throw new TypeError(`${name} must contain bytes`);
    }
  }
  return bytes;
}

function sum8(bytes) {
  return bytes.reduce((sum, byte) => (sum + byte) & 0xff, 0);
}

export function formatHex(value, separator = "") {
  return asBytes(value)
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join(separator)
    .toUpperCase();
}

export function normalizeSystemCode(value) {
  const normalized = String(value ?? "")
    .replace(/[^0-9a-f]/gi, "")
    .toUpperCase();
  if (!/^[0-9A-F]{4}$/.test(normalized)) {
    throw new TypeError("System Code must contain exactly four hexadecimal digits");
  }
  return normalized;
}

export function buildPn532Frame(direction, command, payload = []) {
  const body = [direction, command, ...asBytes(payload, "payload")];
  const length = body.length;
  if (length > 0xff) throw new RangeError("PN532 normal frame is too large");

  return [
    ...PN532_PREAMBLE,
    length,
    (-length) & 0xff,
    ...body,
    (-sum8(body)) & 0xff,
    0x00,
  ];
}

export function isPn532Ack(value) {
  const bytes = asBytes(value);
  return PN532_ACK.every((byte, index) => bytes[index] === byte);
}

export function trimPn532Frame(value) {
  const bytes = asBytes(value);
  if (isPn532Ack(bytes)) return bytes.slice(0, PN532_ACK.length);
  if (bytes.length < 7) throw new Error("PN532 frame is too short");
  if (!PN532_PREAMBLE.every((byte, index) => bytes[index] === byte)) {
    throw new Error("Invalid PN532 preamble");
  }

  const length = bytes[3];
  const totalLength = length + 7;
  if (bytes.length < totalLength) throw new Error("Incomplete PN532 frame");
  return bytes.slice(0, totalLength);
}

export function parsePn532Frame(value) {
  const bytes = trimPn532Frame(value);
  if (isPn532Ack(bytes)) return { ack: true, raw: bytes };

  const length = bytes[3];
  if (((length + bytes[4]) & 0xff) !== 0) {
    throw new Error("Invalid PN532 length checksum");
  }

  const body = bytes.slice(5, 5 + length);
  const dataChecksum = bytes[5 + length];
  if (((sum8(body) + dataChecksum) & 0xff) !== 0) {
    throw new Error("Invalid PN532 data checksum");
  }
  if (bytes[6 + length] !== 0x00) throw new Error("Invalid PN532 postamble");
  if (body.length < 2) throw new Error("PN532 response has no command");

  return {
    ack: false,
    direction: body[0],
    command: body[1],
    payload: body.slice(2),
    raw: bytes,
  };
}

export function buildFelicaPollPayload(systemCode) {
  const code = normalizeSystemCode(systemCode);
  return [
    0x01,
    0x01,
    0x00,
    Number.parseInt(code.slice(0, 2), 16),
    Number.parseInt(code.slice(2, 4), 16),
    0x01,
    0x00,
  ];
}

export function parseFelicaPollResponse(payload) {
  const bytes = asBytes(payload, "poll payload");
  if (bytes.length === 0) throw new Error("Empty InListPassiveTarget response");
  if (bytes[0] === 0) return null;

  const base = 1;
  if (bytes.length < base + 3) throw new Error("Truncated FeliCa target header");
  const targetNumber = bytes[base];
  const sensfLength = bytes[base + 1];
  const recordEnd = base + 1 + sensfLength;
  if (sensfLength < 18 || bytes.length < recordEnd) {
    throw new Error("Truncated FeliCa SENSF_RES");
  }
  if (bytes[base + 2] !== 0x01) throw new Error("Unexpected FeliCa polling response code");

  const idm = bytes.slice(base + 3, base + 11);
  const pmm = bytes.slice(base + 11, base + 19);
  if (idm.length !== 8 || pmm.length !== 8) throw new Error("Invalid IDm or PMm length");

  const systemCodeCount = Math.max(0, Math.floor((sensfLength - 18) / 2));
  const systemCodes = [];
  for (let index = 0; index < systemCodeCount; index += 1) {
    const offset = base + 19 + index * 2;
    if (offset + 1 >= recordEnd) throw new Error("Truncated System Code response");
    systemCodes.push((bytes[offset] << 8) | bytes[offset + 1]);
  }

  return { targetNumber, idm, pmm, systemCodes };
}

export function buildFelicaReadWithoutEncryption(idm, blocks, serviceCode = 0x000b) {
  const cardId = asBytes(idm, "IDm");
  const blockNumbers = asBytes(blocks, "blocks");
  if (cardId.length !== 8) throw new TypeError("IDm must contain eight bytes");
  if (blockNumbers.length === 0 || blockNumbers.length > 0xff) {
    throw new RangeError("At least one block is required");
  }
  if (!Number.isInteger(serviceCode) || serviceCode < 0 || serviceCode > 0xffff) {
    throw new TypeError("Service Code must be a 16-bit integer");
  }

  const command = [
    0x00,
    0x06,
    ...cardId,
    0x01,
    serviceCode & 0xff,
    (serviceCode >> 8) & 0xff,
    blockNumbers.length,
  ];
  for (const block of blockNumbers) command.push(0x80, block);
  command[0] = command.length;
  return command;
}

export function parseFelicaReadResponse(payload) {
  const bytes = asBytes(payload, "data exchange payload");
  if (bytes.length < 2) throw new Error("Empty InDataExchange response");
  if (bytes[0] !== 0x00) {
    throw new Error(`PN532 data exchange failed with status ${formatHex([bytes[0]])}`);
  }

  const felica = bytes.slice(1);
  const packetLength = felica[0];
  if (packetLength < 13 || felica.length < packetLength) {
    throw new Error("Truncated FeliCa read response");
  }
  if (felica[1] !== 0x07) throw new Error("Unexpected FeliCa read response code");

  const statusFlag1 = felica[10];
  const statusFlag2 = felica[11];
  const blockCount = felica[12];
  const expectedLength = 13 + blockCount * 16;
  if (packetLength < expectedLength || felica.length < expectedLength) {
    throw new Error("Truncated FeliCa block data");
  }

  const blockData = [];
  for (let index = 0; index < blockCount; index += 1) {
    const offset = 13 + index * 16;
    blockData.push(felica.slice(offset, offset + 16));
  }

  return {
    idm: felica.slice(2, 10),
    statusFlag1,
    statusFlag2,
    blockData,
  };
}

export function classifyDiagnostic(results) {
  const aic = results?.["88B4"];
  const generic = results?.["4000"];
  const wildcard = results?.FFFF;

  if (aic?.state === "found" && aic.read?.state === "read") return "aic-read";
  if (aic?.state === "found") return "aic-poll";
  if (generic?.state === "found") return "generic-only";
  if (wildcard?.state === "found") return "wildcard-only";
  if ([aic, generic, wildcard].some((result) => result?.state === "error")) return "error";
  return "none";
}
