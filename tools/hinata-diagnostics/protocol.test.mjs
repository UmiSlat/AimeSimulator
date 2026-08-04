import test from "node:test";
import assert from "node:assert/strict";

import {
  PN532_DIRECTION_CHIP_TO_HOST,
  PN532_DIRECTION_HOST_TO_CHIP,
  buildFelicaPollPayload,
  buildFelicaReadWithoutEncryption,
  buildPn532Frame,
  classifyDiagnostic,
  formatHex,
  parseFelicaPollResponse,
  parseFelicaReadResponse,
  parsePn532Frame,
} from "./protocol.mjs";

test("builds the HINATA Go compatible FFFF polling frame", () => {
  const frame = buildPn532Frame(
    PN532_DIRECTION_HOST_TO_CHIP,
    0x4a,
    buildFelicaPollPayload("FFFF"),
  );
  assert.equal(formatHex(frame), "0000FF09F7D44A010100FFFF0100E100");
  assert.deepEqual(parsePn532Frame(frame).payload, [0x01, 0x01, 0x00, 0xff, 0xff, 0x01, 0x00]);
});

test("parses a FeliCa target with one returned System Code", () => {
  const responsePayload = [
    0x01,
    0x01,
    0x14,
    0x01,
    0x02, 0xfe, 0x00, 0x11, 0x45, 0x14, 0x19, 0x19,
    0x00, 0xf1, 0x00, 0x00, 0x00, 0x01, 0x43, 0x00,
    0x40, 0x00,
  ];
  const target = parseFelicaPollResponse(responsePayload);
  assert.equal(target.targetNumber, 1);
  assert.equal(formatHex(target.idm), "02FE001145141919");
  assert.equal(formatHex(target.pmm), "00F1000000014300");
  assert.deepEqual(target.systemCodes, [0x4000]);
});

test("builds a little-endian 000B read for blocks 00 and 82", () => {
  const command = buildFelicaReadWithoutEncryption(
    [0x02, 0xfe, 0x00, 0x11, 0x45, 0x14, 0x19, 0x19],
    [0x00, 0x82],
  );
  assert.equal(formatHex(command), "120602FE001145141919010B000280008082");
});

test("parses a two-block Read Without Encryption response", () => {
  const idm = [0x02, 0xfe, 0x00, 0x11, 0x45, 0x14, 0x19, 0x19];
  const block0 = Array.from({ length: 16 }, (_, index) => index);
  const block82 = [...idm, ...Array(8).fill(0xaa)];
  const felica = [0x2d, 0x07, ...idm, 0x00, 0x00, 0x02, ...block0, ...block82];
  const parsed = parseFelicaReadResponse([0x00, ...felica]);
  assert.equal(parsed.statusFlag1, 0);
  assert.equal(formatHex(parsed.blockData[0]), "000102030405060708090A0B0C0D0E0F");
  assert.equal(formatHex(parsed.blockData[1]), "02FE001145141919AAAAAAAAAAAAAAAA");
});

test("validates PN532 checksums", () => {
  const frame = buildPn532Frame(PN532_DIRECTION_CHIP_TO_HOST, 0x4b, [0x00]);
  frame[frame.length - 2] ^= 0x01;
  assert.throws(() => parsePn532Frame(frame), /data checksum/);
});

test("classifies targeted polling independently from wildcard discovery", () => {
  assert.equal(
    classifyDiagnostic({ FFFF: { state: "found" }, "4000": { state: "found" }, "88B4": { state: "empty" } }),
    "generic-only",
  );
  assert.equal(
    classifyDiagnostic({ "88B4": { state: "found", read: { state: "read" } } }),
    "aic-read",
  );
});
