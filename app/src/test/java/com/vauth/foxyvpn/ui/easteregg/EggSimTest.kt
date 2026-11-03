package com.vauth.foxyvpn.ui.easteregg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Easter egg (B2): the simulation is deterministic and surveys work. */
class EggSimTest {

    @Test
    fun sameSeed_sameSystem() {
        val a = EggUniverse(42L)
        val b = EggUniverse(42L)
        assertEquals(a.starClass, b.starClass)
        assertEquals(a.starName, b.starName)
        assertEquals(a.planets.size, b.planets.size)
        for (i in a.planets.indices) {
            assertEquals(a.planets[i].name, b.planets[i].name)
            assertEquals(a.planets[i].orbitRadius, b.planets[i].orbitRadius, 1e-9)
            assertEquals(a.planets[i].startAngle, b.planets[i].startAngle, 1e-9)
        }
    }

    @Test
    fun differentSeed_differentSystem() {
        val a = EggUniverse(1L)
        val b = EggUniverse(2L)
        val sameLayout = a.planets.size == b.planets.size &&
            a.planets.indices.all {
                a.planets[it].name == b.planets[it].name &&
                    a.planets[it].orbitRadius == b.planets[it].orbitRadius
            }
        assertNotEquals(true, sameLayout)
    }

    @Test
    fun planetCount_inSpecRange() {
        for (seed in 0L until 25L) {
            val universe = EggUniverse(seed)
            assertTrue(universe.planets.size in 2..9)
            assertTrue(universe.planets.all { it.orbitRadius in 16_000.0..150_000.0 })
            assertTrue(universe.planets.all { it.radius in 50.0..2_000.0 })
        }
    }

    @Test
    fun unpoweredShip_staysBounded() {
        val universe = EggUniverse(7L)
        val startDistance = kotlin.math.hypot(universe.shipX, universe.shipY)
        repeat(60 * 30) { universe.step(1.0 / 30.0) }
        val distance = kotlin.math.hypot(universe.shipX, universe.shipY)
        assertTrue(distance > startDistance * 0.2)
        assertTrue(distance < startDistance * 3.0)
        assertTrue(universe.shipSpeed < EggPhysics.SPEED_LIMIT)
    }

    @Test
    fun survey_completesWhenTrackingAPlanet() {
        val universe = EggUniverse(99L)
        val planet = universe.planets.first()
        // Put the ship exactly on the planet's orbit with its velocity:
        // it then tracks the planet and the survey clock fills.
        universe.shipX = planet.x
        universe.shipY = planet.y
        val (vx, vy) = planet.velocity()
        universe.shipVx = vx
        universe.shipVy = vy
        var steps = 0
        while (!planet.surveyed && steps < 60 * 30) {
            universe.step(1.0 / 30.0)
            steps++
        }
        assertTrue("survey should complete while tracking", planet.surveyed)
        assertEquals(planet.name, universe.lastDiscovery)
        assertTrue(universe.discoveryBannerVisible())
    }
}
