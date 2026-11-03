package com.vauth.foxyvpn.ui.map

/**
 * Declared locations of the server inventory's cities (Phase B2 Map).
 *
 * The inventory (Mozilla Remote Settings server list) names one city
 * per country, identified by an IATA-style code; the coordinates are
 * the city centers of those codes — public geographic facts, bundled
 * offline. NO pin is ever derived from a lookup: a city whose code is
 * not in this table (including the anycast endpoints "CA", "US",
 * "Q30" and the "REC" placeholder) gets NO pin, exactly as the spec
 * requires — never an approximated one.
 */
data class CityPoint(val cityName: String, val lat: Double, val lon: Double)

object CityCoordinates {
    private val byCityCode: Map<String, CityPoint> = mapOf(
        "EZE" to CityPoint("Buenos Aires", -34.6037, -58.3816),
        "VIE" to CityPoint("Vienna", 48.2082, 16.3738),
        "YSBK" to CityPoint("Sydney", -33.8688, 151.2093),
        "BRU" to CityPoint("Brussels", 50.8503, 4.3517),
        "SOF" to CityPoint("Sofia", 42.6977, 23.3219),
        "SBSP" to CityPoint("São Paulo", -23.5505, -46.6333),
        "SCL" to CityPoint("Santiago", -33.4489, -70.6693),
        "SKBO" to CityPoint("Bogotá", 4.7109, -74.0721),
        "MUC" to CityPoint("Munich", 48.1351, 11.5820),
        "CPH" to CityPoint("Copenhagen", 55.6761, 12.5683),
        "LETO" to CityPoint("Madrid", 40.4168, -3.7038),
        "HEL" to CityPoint("Helsinki", 60.1699, 24.9384),
        "LFPB" to CityPoint("Paris", 48.8566, 2.3522),
        "EGLC" to CityPoint("London", 51.5074, -0.1278),
        "ACC" to CityPoint("Accra", 5.6037, -0.1870),
        "DUB" to CityPoint("Dublin", 53.3498, -6.2603),
        "MXP" to CityPoint("Milan", 45.4642, 9.1900),
        "RJTF" to CityPoint("Tokyo", 35.6762, 139.6503),
        "ICN" to CityPoint("Seoul", 37.5665, 126.9780),
        "MMQT" to CityPoint("Querétaro", 20.5888, -100.3899),
        "KUL" to CityPoint("Kuala Lumpur", 3.1390, 101.6869),
        "EHRD" to CityPoint("Rotterdam", 51.9244, 4.4777),
        "OSL" to CityPoint("Oslo", 59.9139, 10.7522),
        "AKL" to CityPoint("Auckland", -36.8485, 174.7633),
        "SPJC" to CityPoint("Lima", -12.0464, -77.0428),
        "MNL" to CityPoint("Manila", 14.5995, 120.9842),
        "LIS" to CityPoint("Lisbon", 38.7223, -9.1393),
        "ESSB" to CityPoint("Stockholm", 59.3294, 18.0686),
        "WSAT" to CityPoint("Singapore", 1.3521, 103.8198),
        "BKK" to CityPoint("Bangkok", 13.7563, 100.5018),
        "FAOR" to CityPoint("Johannesburg", -26.2041, 28.0473),
    )

    /** The declared point for an inventory city code, or null (no pin). */
    fun forCityCode(cityCode: String): CityPoint? = byCityCode[cityCode.uppercase()]
}
