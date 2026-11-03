package com.vauth.foxyvpn.ui.easteregg

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The space-flight sandbox behind the title-tap easter egg (Phase B2)
 * — a CLEAN-ROOM reimplementation of the behavior spec in
 * DESIGN-SYSTEM §11 (daily-seeded star system, Kepler/gravity flight,
 * survey mechanics). No code, text or assets from the reference were
 * copied; the constants below are the spec's published values and the
 * name/description generators are this file's own.
 *
 * Pure Kotlin (no Android), deterministic per seed, unit-testable.
 */
object EggPhysics {
    const val GRAVITATION = 7.5e-2
    const val KEPLER_CONSTANT = 50.0
    const val SHIP_MASS = 10.0
    const val SHIP_RADIUS = 12.0
    const val MAIN_ENGINE_ACCEL = 1000.0
    const val SPEED_LIMIT = 5000.0
    const val STAR_MASS = 4.0e10
    const val SOFTENING = 400.0
    const val SURVEY_RANGE = 900.0
    const val SURVEY_MAX_REL_SPEED = 320.0
    const val SURVEY_SECONDS = 3.0
    const val DISCOVERY_BANNER_SECONDS = 3.0
}

enum class StarClass(val label: String, val argb: Long) {
    O("O", 0xFF9DB4FF), B("B", 0xFFB9C8FF), A("A", 0xFFE8ECFF),
    F("F", 0xFFFFF3C4), G("G", 0xFFFFE08A), K("K", 0xFFFFB36B),
    M("M", 0xFFFF8A5C),
}

class EggPlanet(
    val name: String,
    val orbitRadius: Double,
    val radius: Double,
    val startAngle: Double,
    val hue: Int,
    val description: String,
    val atmosphere: String,
    val flora: String,
    val fauna: String,
) {
    var angle: Double = startAngle
    var surveyed: Boolean = false
    var surveyProgress: Double = 0.0

    val x: Double get() = cos(angle) * orbitRadius
    val y: Double get() = sin(angle) * orbitRadius

    /** Orbital velocity vector (Keplerian circular orbit). */
    fun velocity(): Pair<Double, Double> {
        val speed = EggPhysics.KEPLER_CONSTANT * sqrt(1000.0 / orbitRadius)
        return Pair(-sin(angle) * speed, cos(angle) * speed)
    }
}

class EggUniverse(val seed: Long) {
    private val random = Random(seed)
    val starClass: StarClass
    val starName: String
    val planets: List<EggPlanet>

    // Ship state.
    var shipX: Double = 0.0
    var shipY: Double = 0.0
    var shipVx: Double = 0.0
    var shipVy: Double = 0.0
    var thrustX: Double = 0.0
    var thrustY: Double = 0.0
    var autopilot: Boolean = false

    /** Name of the planet whose survey just completed (banner), with age. */
    var lastDiscovery: String? = null
        private set
    private var discoveryAge: Double = Double.POSITIVE_INFINITY

    init {
        val roll = random.nextDouble()
        starClass = when {
            roll < 0.03 -> StarClass.O
            roll < 0.10 -> StarClass.B
            roll < 0.22 -> StarClass.A
            roll < 0.38 -> StarClass.F
            roll < 0.62 -> StarClass.G
            roll < 0.85 -> StarClass.K
            else -> StarClass.M
        }
        starName = generateName(random)
        val count = random.nextInt(2, 10)
        val built = ArrayList<EggPlanet>(count)
        var orbit = 16_000.0 + random.nextDouble() * 10_000.0
        repeat(count) {
            orbit += random.nextDouble() * 14_000.0 + 6_000.0
            if (orbit > 150_000.0) orbit = 150_000.0
            built += EggPlanet(
                name = generateName(random),
                orbitRadius = orbit,
                radius = 50.0 + random.nextDouble() * 1950.0,
                startAngle = random.nextDouble() * Math.PI * 2,
                hue = random.nextInt(0, 360),
                description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.size)],
                atmosphere = ATMOSPHERES[random.nextInt(ATMOSPHERES.size)],
                flora = FLORA[random.nextInt(FLORA.size)],
                fauna = FAUNA[random.nextInt(FAUNA.size)],
            )
        }
        planets = built
        // Ship starts on a stable-ish orbit outside the innermost planet.
        val startOrbit = (planets.firstOrNull()?.orbitRadius ?: 20_000.0) * 0.8
        shipX = startOrbit
        shipY = 0.0
        val orbitalSpeed = sqrt(EggPhysics.GRAVITATION * EggPhysics.STAR_MASS / startOrbit)
        shipVx = 0.0
        shipVy = orbitalSpeed
    }

    val shipSpeed: Double get() = hypot(shipVx, shipVy)

    fun nearestPlanet(): EggPlanet? =
        planets.minByOrNull { hypot(it.x - shipX, it.y - shipY) }

    fun discoveryBannerVisible(): Boolean =
        lastDiscovery != null && discoveryAge < EggPhysics.DISCOVERY_BANNER_SECONDS

    /** Advance the simulation by [dtSeconds] (already frame-clamped). */
    fun step(dtSeconds: Double) {
        if (dtSeconds <= 0.0) return
        discoveryAge += dtSeconds

        for (planet in planets) {
            // Angular speed consistent with EggPlanet.velocity():
            // omega = v / r = KEPLER * sqrt(1000 / r) / r.
            val omega = EggPhysics.KEPLER_CONSTANT * sqrt(1000.0) /
                (planet.orbitRadius * sqrt(planet.orbitRadius))
            planet.angle += omega * dtSeconds
        }

        // Autopilot: steer toward the nearest unsurveyed planet.
        var ax = 0.0
        var ay = 0.0
        if (autopilot) {
            val target = planets.firstOrNull { !it.surveyed } ?: nearestPlanet()
            if (target != null) {
                val dx = target.x - shipX
                val dy = target.y - shipY
                val dist = hypot(dx, dy).coerceAtLeast(1.0)
                val (pvx, pvy) = target.velocity()
                val desiredSpeed = minOf(1200.0, dist * 0.02)
                val dvx = dx / dist * desiredSpeed + pvx - shipVx
                val dvy = dy / dist * desiredSpeed + pvy - shipVy
                val mag = hypot(dvx, dvy)
                if (mag > 1.0) {
                    val scale = minOf(EggPhysics.MAIN_ENGINE_ACCEL, mag) / mag
                    ax += dvx * scale
                    ay += dvy * scale
                }
            }
        } else {
            ax += thrustX * EggPhysics.MAIN_ENGINE_ACCEL
            ay += thrustY * EggPhysics.MAIN_ENGINE_ACCEL
        }

        // Gravity from the star (softened).
        val distSq = shipX * shipX + shipY * shipY + EggPhysics.SOFTENING * EggPhysics.SOFTENING
        val dist = sqrt(distSq)
        val grav = EggPhysics.GRAVITATION * EggPhysics.STAR_MASS / distSq
        ax -= grav * shipX / dist
        ay -= grav * shipY / dist

        shipVx += ax * dtSeconds
        shipVy += ay * dtSeconds
        val speed = shipSpeed
        if (speed > EggPhysics.SPEED_LIMIT) {
            shipVx *= EggPhysics.SPEED_LIMIT / speed
            shipVy *= EggPhysics.SPEED_LIMIT / speed
        }
        shipX += shipVx * dtSeconds
        shipY += shipVy * dtSeconds

        // Survey: close + slow relative to a planet builds progress.
        for (planet in planets) {
            if (planet.surveyed) continue
            val dx = planet.x - shipX
            val dy = planet.y - shipY
            val distTo = hypot(dx, dy)
            val (pvx, pvy) = planet.velocity()
            val relSpeed = hypot(shipVx - pvx, shipVy - pvy)
            if (distTo < planet.radius + EggPhysics.SURVEY_RANGE &&
                relSpeed < EggPhysics.SURVEY_MAX_REL_SPEED
            ) {
                planet.surveyProgress += dtSeconds / EggPhysics.SURVEY_SECONDS
                if (planet.surveyProgress >= 1.0) {
                    planet.surveyed = true
                    lastDiscovery = planet.name
                    discoveryAge = 0.0
                }
            } else {
                planet.surveyProgress = (planet.surveyProgress - dtSeconds * 0.5).coerceAtLeast(0.0)
            }
        }
    }

    companion object {
        private val SYLLABLES = listOf(
            "ze", "ph", "yr", "an", "te", "os", "il", "um", "ar", "ek",
            "ov", "is", "ta", "ru", "ne", "xi", "on", "dra", "mi", "sol",
            "ka", "ve", "thi", "or", "un", "esh", "al", "ir", "om", "az",
        )
        private val DESCRIPTIONS = listOf(
            "A quiet world of dust and stone",
            "Oceans under a permanent twilight",
            "A young world, still cooling",
            "High plains cut by dry riverbeds",
            "A world of glass deserts and thin ice",
            "Dense forests under a small hot sun",
        )
        private val ATMOSPHERES = listOf(
            "Nitrogen and oxygen", "Thin carbon dioxide", "Methane haze",
            "Water vapour and argon", "Almost none", "Sulphur traces",
        )
        private val FLORA = listOf(
            "None detected", "Moss mats", "Towering fern forests",
            "Bioluminescent reeds", "Grass seas", "Cactus groves",
        )
        private val FAUNA = listOf(
            "None detected", "Microbial mats only", "Small burrowers",
            "Gliding herbivores", "Shell-backed grazers", "Night hunters",
        )

        fun generateName(random: Random): String {
            val parts = random.nextInt(2, 4)
            val builder = StringBuilder()
            repeat(parts) { builder.append(SYLLABLES[random.nextInt(SYLLABLES.size)]) }
            return builder.toString().replaceFirstChar { it.uppercase() }
        }

        /** The daily system's seed (epoch day scrambled, per the spec's shape). */
        fun dailySeed(epochDay: Long): Long = epochDay xor 0x5DEECE66DL
    }
}
