package ma.tkpay.naps.protocol

import ma.tkpay.naps.models.NapsError
import java.text.SimpleDateFormat
import java.util.*

/**
 * TLV Protocol implementation for NAPS Pay M2M
 *
 * Tag-Length-Value format:
 * - TAG: 3 digits
 * - LENGTH: 3 digits
 * - VALUE: variable length
 *
 * Example: 001003001 = Tag 001, Length 003, Value "001"
 */
object TlvProtocol {

    /**
     * TLV Tag codes (3-digit numeric)
     */
    object Tags {
        const val TM = "001"      // Message Type
        const val MT = "002"      // Amount (minor units)
        const val NCAI = "003"    // Register(2) + Cashier(5)
        const val NS = "004"      // Sequence Number
        const val NCAR = "007"    // Card Number (masked)
        const val STAN = "008"    // System Trace Audit Number
        const val NA = "009"      // Authorization Number
        const val DP = "010"      // Print Data (Receipt)
        const val DE = "012"      // Currency Code
        const val CR = "013"      // Response Code
        const val DV = "014"      // Card Expiry (YYMM)
        const val SH = "015"      // Entry Mode
        const val DT = "016"      // Transaction Date (DDMMYYYY)
        const val HT = "017"      // Transaction Time (HHMMSS)
        const val NC = "018"      // Cardholder Name
    }

    /**
     * Message Types
     */
    object MessageTypes {
        const val PAYMENT_REQUEST = "001"
        const val PAYMENT_RESPONSE = "101"
        const val CONFIRMATION_REQUEST = "002"
        const val CONFIRMATION_RESPONSE = "102"
    }

    /**
     * Currency Codes
     */
    object Currency {
        const val MAD = "504"  // Moroccan Dirham
    }

    /**
     * Receipt Sub-tags (within DP/010)
     */
    object ReceiptTags {
        const val LINE_NUMBER = "030"    // 2 chars
        const val FORMAT = "031"         // S=Simple, G=Gras/Bold
        const val ALIGNMENT = "032"      // C=Center, D=Droite/Right, G=Gauche/Left
        const val CONTENT = "033"        // Variable length
    }

    /**
     * Build TLV field
     */
    fun buildField(tag: String, value: String): String {
        val length = value.length.toString().padStart(3, '0')
        return "$tag$length$value"
    }

    /**
     * Build payment request TLV
     */
    fun buildPaymentRequest(
        amount: Double,
        ncai: String,
        sequence: String
    ): String {
        val amountMinor = (amount * 100).toInt().toString()
        val dateTime = getCurrentDateTime()

        return buildField(Tags.TM, MessageTypes.PAYMENT_REQUEST) +
               buildField(Tags.MT, amountMinor) +
               buildField(Tags.NCAI, ncai) +
               buildField(Tags.NS, sequence) +
               buildField(Tags.DE, Currency.MAD) +
               buildField(Tags.DT, dateTime.date) +
               buildField(Tags.HT, dateTime.time)
    }

    /**
     * Build confirmation request TLV
     */
    fun buildConfirmationRequest(
        stan: String,
        ncai: String,
        sequence: String
    ): String {
        val dateTime = getCurrentDateTime()

        return buildField(Tags.TM, MessageTypes.CONFIRMATION_REQUEST) +
               buildField(Tags.STAN, stan) +
               buildField(Tags.NCAI, ncai) +
               buildField(Tags.NS, sequence) +
               buildField(Tags.DT, dateTime.date) +
               buildField(Tags.HT, dateTime.time)
    }

    /**
     * Parse TLV string into fields map
     */
    fun parse(tlvString: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        var index = 0

        while (index < tlvString.length) {
            // Need at least 6 chars for tag + length
            if (index + 6 > tlvString.length) break

            val tag = tlvString.substring(index, index + 3)
            val lengthStr = tlvString.substring(index + 3, index + 6)

            // Validate length is numeric
            if (!lengthStr.all { it.isDigit() }) break

            val length = lengthStr.toInt()

            // Check if we have enough data for the value
            if (index + 6 + length > tlvString.length) break

            var value = tlvString.substring(index + 6, index + 6 + length)

            // SECURITY: Immediately mask PAN in tag 007 (NCAR)
            if (tag == Tags.NCAR) {
                value = maskCardNumber(value)
            }

            fields[tag] = value
            index += 6 + length
        }

        return fields
    }

    /**
     * Mask card number to show only first 6 and last 4 digits
     * Example: 5167940123453315 -> 516794******3315
     */
    fun maskCardNumber(cardNumber: String): String {
        if (cardNumber.length < 10) return cardNumber

        val first6 = cardNumber.substring(0, 6)
        val last4 = cardNumber.substring(cardNumber.length - 4)
        val maskedMiddle = "*".repeat(cardNumber.length - 10)

        return "$first6$maskedMiddle$last4"
    }

    /**
     * Mask any 16-digit card numbers in text
     */
    fun maskCardNumbersInText(text: String): String {
        val panPattern = Regex("\\b\\d{16}\\b")
        return panPattern.replace(text) { matchResult ->
            maskCardNumber(matchResult.value)
        }
    }

    /**
     * Get current date and time for NAPS Pay format
     */
    private fun getCurrentDateTime(): DateTime {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("ddMMyyyy", Locale.US)
        val timeFormat = SimpleDateFormat("HHmmss", Locale.US)

        return DateTime(
            date = dateFormat.format(calendar.time),
            time = timeFormat.format(calendar.time)
        )
    }

    /**
     * Data class for date/time
     */
    private data class DateTime(
        val date: String,
        val time: String
    )

    /**
     * Get tag name for debugging
     */
    fun getTagName(tag: String): String = when (tag) {
        Tags.TM -> "Message Type"
        Tags.MT -> "Amount"
        Tags.NCAI -> "NCAI"
        Tags.NS -> "Sequence"
        Tags.NCAR -> "Card Number"
        Tags.STAN -> "STAN"
        Tags.NA -> "Auth Number"
        Tags.DP -> "Receipt Data"
        Tags.DE -> "Currency"
        Tags.CR -> "Response Code"
        Tags.DV -> "Card Expiry"
        Tags.SH -> "Entry Mode"
        Tags.DT -> "Date"
        Tags.HT -> "Time"
        Tags.NC -> "Cardholder Name"
        else -> "Unknown Tag $tag"
    }
}
