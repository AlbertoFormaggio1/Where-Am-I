package com.example.progettoembedded

import java.util.*

/**
 * Data class used to store location details about the last measurement of the location
 *
 * @property longitude
 * @property latitude
 * @property altitude
 * @property timestamp time when the last measurement was done
 * @constructor Create empty Location details
 */
data class LocationDetails (
    val longitude : String?,

    val latitude : String?,

    val altitude : String?,

    val timestamp: Date
        )
