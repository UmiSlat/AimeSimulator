package io.github.umislat.aimesimulator.nfc

import io.github.umislat.aimesimulator.data.HexCodec
import java.util.Base64

internal object AicAccessCodeCodec {
    private const val BLOCK_SIZE = 16
    private const val ACCESS_CODE_OFFSET = 6
    private const val ACCESS_CODE_END = 16
    private const val TABLE_COUNT = 9
    private const val TABLE_SIZE = 256
    private const val ROUND_TABLE_COUNT = TABLE_COUNT - 1
    private const val HASH_ADD = 5

    private val tableKeys = intArrayOf(217, 50, 139, 241, 121, 110, 212, 144, 172)

    private val inverseTables: Array<ByteArray> by lazy {
        val encodedTables = Base64.getDecoder().decode(S_BOXES_BASE64)
        require(encodedTables.size == TABLE_COUNT * TABLE_SIZE)
        Array(TABLE_COUNT) { table ->
            ByteArray(TABLE_SIZE).also { inverse ->
                repeat(TABLE_SIZE) { input ->
                    val encoded = encodedTables[table * TABLE_SIZE + input].unsigned() xor tableKeys[table]
                    inverse[encoded] = input.toByte()
                }
            }
        }
    }

    fun decryptSpad0(block: ByteArray): ByteArray? {
        if (block.size != BLOCK_SIZE) return null
        val decoded = block.copyOf()
        repeat(BLOCK_SIZE) { index ->
            decoded[index] = inverseTables[ROUND_TABLE_COUNT][decoded[index].unsigned()]
        }

        val rounds = (decoded.last().unsigned() ushr 4) + 7
        var table = decoded.last().unsigned() + HASH_ADD * rounds
        repeat(rounds) {
            table -= HASH_ADD
            rotateRight(decoded, ACCESS_CODE_END - 1, 5)
            repeat(ACCESS_CODE_END - 1) { index ->
                decoded[index] = inverseTables[table % ROUND_TABLE_COUNT][decoded[index].unsigned()]
            }
        }
        return decoded
    }

    fun decodeAccessCode(encryptedSpad0: ByteArray): String? {
        if (encryptedSpad0.size != BLOCK_SIZE || encryptedSpad0.all { it == 0.toByte() }) return null
        val decoded = decryptSpad0(encryptedSpad0) ?: return null
        if (decoded[5] != 0.toByte() || decoded[ACCESS_CODE_OFFSET].unsigned() and 0xF0 != 0x50) {
            return null
        }
        return HexCodec.encode(decoded.copyOfRange(ACCESS_CODE_OFFSET, ACCESS_CODE_END))
    }

    private fun rotateRight(data: ByteArray, byteCount: Int, bitCount: Int) {
        var previous = data[byteCount - 1].unsigned()
        repeat(byteCount) { index ->
            val current = data[index].unsigned()
            data[index] = (
                (current ushr bitCount) or
                    ((previous and ((1 shl bitCount) - 1)) shl (8 - bitCount))
                ).toByte()
            previous = current
        }
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    // SPAD0 tables and round behavior verified against hinata_go commit c56d8bad.
    private const val S_BOXES_BASE64 =
        "dnG6cP0ZKGc+anXPXGDItsno63doofoLexMg8pxh5bvDtFtV2QA7o5/A8xXKfj1uXUJXJiJS2lDp8Ape2GbsA4CFFNCmqZhY" +
            "tYaKG6sNoJFMbYLgnUQkiKgvDgz0RjTqM3ODzrNjZDBFF5SlweLUN9VZeNNWbLIu5gie/B/vMaSi5ypArNzFORbtYkcySHT/" +
            "VB6/0T+Bmc1/HCPLNR0p+FNfDzjfb7mt9zon/vaHLWux8Y82Q7j7ehDS20+WAjxJk5VLEkGwCY691+6JcmUHr94R5JAlrk0h" +
            "GE6845Jat6pRx4Tdjb4GzGkaSvXhBCx9jNaXxHnG+XwFmisBp4vCmzF5iqWFvT9D7nuVos2Sr8oRbRJeI8tbvN35wibtKUj0" +
            "BGn3oYQaB4AJ497BrbJWFzf9Zp10DaQ01yLJmOxQc2Nr1RuHfw/ABVjaJLlumixl0DvZAPschsOWROHzl0JSIIPYVNN4oJNf" +
            "CMbnXejqOGoqURmRb1VFWQvx6X4wiPyJZzMltxXWAkD6v85KK4txHzqpKOv/FMiw/k1HS6xXzN+j9QPiYBh6tkbvT9ucXAF1" +
            "5GI+YZSPEJ64NrtwcoF3aHbUmy/2s9EnLWS15gw9p+XSppkWWjWQqkncx4K68gquq2yf4A4eE/i+TC5T8AZOHTmxqH2MfI5B" +
            "ITLEPLSNz8XKjBgBy3SFpN93J1+VO0MCVIvvxHhAp0sE5Zw0HhKhhtomwEF84t6zx03jSCiUCLWA+0w5xUR1xnYAPx1FcnpY" +
            "yOslXjhQwTJuYeHD1iwMUTONph9K7a+tOhGug9fSLSSsGQN5ZpCXmlmpnf3d7J8UZdRX0C/NVpu4ovTRQhwPDsk3IPy3Kkkh" +
            "vMLqvhcNktzVbWkrj+ZaIxZniTZTzOmRjvaKtJPk/s9v8X0iY/l/4HD1YL3ZVfKoBmz66BPnUmKwmBuI8/+rCjG/uXHwul22" +
            "u4FomQWEljAu+NgJCz5carGgW2uqNUae7j1kRxp+h0+lFdN7ThCygin3zjyjB3PbhzznjhiaZMu/Sp89I0KYWINeNnPJnk3G" +
            "V9nHTDI+dLf7aJJmcqd5UPOVXYiklA4zHouo2u0RfX6rFUVuDQa0trsbMfwaOYk4lvL0wOUvIoIL7keh4vVGirjrCOa8vnfR" +
            "MAxRVLkQzZED+sH+vR98nE9/Nwcp+WXq20RvU2IE01nF11aNHc7/ykMF2D/fs3DD1K6XKowmFtyym6roXM9qNFp1NZ3CKwp7" +
            "4KUPprrMj/hSYLFOmRRIAmcJoDtrQWlf722vGfHI4y394XqsJ93WJJB2okvSgN7shXhsLMTkIbVAOgGTqVWGEuklF3EgW2GB" +
            "8NUA0Cj3hEmwrWMuHPYTo9Pt4A3rr2U/PgBWLwOOzHOwf66DOWjchmaRefWkzjsXTInHu2Szz/ebPTfmKPjyNSQgQar2XMpw" +
            "SNRyEncpwqHv8acwk80z40kUVXa0jwc2K6CCUX4aYN2XeIGSq4xEdOgiHsEuilKfEW8CFhy6D5pFVzhHti2Ybt48VCOFv/uW" +
            "sgHVTcMIXluLa0ZjXdG4575hnSaHFdaxTrwOxHtQBIQn+pDGUyUKnKOelfkTeiFCWGqoKgnqxdhpjR38/skYyIg6G72A29et" +
            "ff+mX6nST2cymRnpteHsQ+XibGJK9DRa5P23y3zfdcBtMazaDHEFlAal8B/QuUALWe7z2SwQokuwNHUHUizNPy5Wn3lGctbC" +
            "hvcrznwGoHunKQOmKCBwyH+WUF+UiEOTXHryb9+DOR+NXqFgiaJZJBbFc/iES+tOJyMbPICBwTpdl2fw748dRJxTTH385Z6u" +
            "6mPGarLj1UL6bZu/vkAzhTAMAU/e/5ClZILZQVpKuIcI7Kh3cbevyZjmbOlps7YJq9dHyqwLvT3EVReOHLlodDb9EGL+ABph" +
            "ImWKAi28L1jTtVs3McfxO+5X4J27iw2tGcw1FRHA1M9+sSVF9XjSbql2lQqRmpnQazijZiEE9BgU2/ukPhMSBcuqVORJtNgP" +
            "TegeJvnzkuEyKtzD7dFIut0O9oxR4trnzfqJro+oqu07X/sYUs8WWPQvjYv1GUtAc99u2pOvcdJjDB3yJEm8JorG7rFkiDb2" +
            "TVbEIZCEoHUtlnzxmT7c6kJGEPl29+B9kb+tqxyYl7P90C5hytESYHvJbyPkOTh/lDRMPZ/FU8HMajqsZdaDROZatR/oMgOh" +
            "5W2A4jUJuEO2eJVdSLQit2jAqaMB3UELw57Y/4bsKmLncIWnuZtna6U/vevH1Mi7T+Eed/6iWVTVwtfpMVekW46MXCc32xfT" +
            "kiudKTwaUQUIJWwHugB0eaYOM+9QLAb8ChXOsEXeMBHwvgKHE1X4FEcN2XIPehsggbLL4yhpnH6agvNKBF5mTsRJ0sAo5Dui" +
            "NrTY/2DuR6Ak/ZYyGEbJqdy8P/44u/bMKYx3w4nGGoFI8c+Xk0olIJhAt9WQU2Uh6ftUy7Fyxa0wbqEKpp98WyviuYpp5hf6" +
            "PlnCJ3vrY5UElJFWMWSnc2tR3yoOELi/ymYBrl/RPS4mowtsXlD4fq/BL95C2TlnHxZYuveodN1XvvPvf08HOs3ym+BBdoMJ" +
            "AmhDgqz0FDeaIgx6i1zO045dHQNx4atiM7N5Uk0IG+MGx7KlnjU8nEQPmUuqhvB1GRW9540RsFXqfVqEE2qPtojXDZJwyIBt" +
            "teUs1k40hdDopNqHRZ359Uzb7AV4Hm/tABwS/CMt1GFvpxS5rAiRdVG6RaSMSiFo65SJP/IGQIa8wvQ5iv+osj4ygycRpsUF" +
            "l+1cfHbGDd2/z0M8OBxHS3DfFdTWSGvB0w8AKfxfPUaaVjciWwJt77BsZiPiDp2+2IAxXQGTxBeZNtCvV6WPi3jhu3LgQtow" +
            "DE3Zd/e9C40Dru7nL39iM2FgnIf+jmf10bhVHdz4TC32saLmWChBOrZ7ZS4lgSufTvD9lrdaSXSeofPeJHobanGzLJvIGddz" +
            "U37sUMypGsrNZIQ1EAmYtVRSiOR9oPnOxyofFiaCwwrLya1EtJLS24UgXqvABNXj6OqqbgejGB4SNHnlT1ljO/ET+5CVaen6"
}
