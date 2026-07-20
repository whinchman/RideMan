package com.two17industries.rideman.hrm

/**
 * A decoded Bluetooth SIG Heart Rate Measurement (characteristic 0x2A37).
 *
 * Wire format:
 * ```
 * byte 0: flags
 *   bit 0: 0 = HR is uint8 (byte 1), 1 = HR is uint16 LE (bytes 1-2)
 *   bits 1-2: sensor contact (2 = supported+absent, 3 = supported+detected, else unsupported)
 *   bit 3: energy expended present (uint16 LE, kJ)
 *   bit 4: RR intervals present (one or more uint16 LE, units of 1/1024 s)
 * bytes 1..N: HR value, then optional energy, then the RR list to the end of the packet
 * ```
 *
 * Pure — no Android types — so it is unit tested directly, the way TelemetryPacket is.
 */
data class HeartRateMeasurement(
    val bpm: Int,
    /**
     * True when the strap reports skin contact, OR when it does not support contact reporting
     * at all. Straps that never report contact must not be gated out of everything that
     * requires it.
     */
    val contactOk: Boolean,
    /** Cumulative energy expended in kJ, or null when the strap does not report it. */
    val energyKj: Int?,
    /** RR intervals in milliseconds. Usually empty, sometimes several in one notification. */
    val rrIntervalsMs: List<Double>,
) {
    companion object {

        /** Decode a notification payload, or null if it is too short or malformed. */
        fun parse(bytes: ByteArray): HeartRateMeasurement? {
            if (bytes.size < 2) return null
            val flags = bytes[0].toInt() and 0xFF

            var i = 1
            val bpm: Int
            if (flags and 0x01 != 0) {
                if (bytes.size < 3) return null
                bpm = u16(bytes, i)
                i += 2
            } else {
                bpm = bytes[i].toInt() and 0xFF
                i += 1
            }

            // 0b10 = supported but not detected. Everything else (including "not supported")
            // counts as usable contact.
            val contactBits = (flags shr 1) and 0x03
            val contactOk = contactBits != 0x02

            var energyKj: Int? = null
            if (flags and 0x08 != 0) {
                if (bytes.size < i + 2) return null
                energyKj = u16(bytes, i)
                i += 2
            }

            val rr = mutableListOf<Double>()
            if (flags and 0x10 != 0) {
                // Parse to the end of the buffer — a notification may carry several intervals.
                while (i + 1 < bytes.size) {
                    rr.add(u16(bytes, i) * 1000.0 / 1024.0)
                    i += 2
                }
            }

            return HeartRateMeasurement(bpm, contactOk, energyKj, rr)
        }

        private fun u16(b: ByteArray, offset: Int): Int =
            (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)
    }
}
