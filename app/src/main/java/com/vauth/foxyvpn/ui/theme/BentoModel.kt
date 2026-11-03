package com.vauth.foxyvpn.ui.theme

import androidx.compose.runtime.Immutable

/**
 * The overhaul (Zed) appearance model — Phase B1.
 *
 * Value sets and defaults mirror ZedSecure v3.1.8's domain model
 * (AppSettings / BentoPrefs) exactly; this is original code, written
 * against DESIGN-SYSTEM.md's extracted spec. Persisted by name in
 * SettingsStore, so enum order is display order only.
 */

/** Tile tone slots a Bento surface can take (DESIGN-SYSTEM §3). */
enum class BentoToneKind { Stage, Lime, Blue, Violet, Cyan, Pink, Coral, Surface }

/** Zed's 12 Bento palettes. Order = the Interface palette picker order. */
enum class BentoPalette(val label: String) {
    Brand("Brand"),
    Accent("Accent"),
    Pastel("Pastel"),
    Vivid("Vivid"),
    Mono("Mono"),
    Citrus("Citrus"),
    Matcha("Matcha"),
    Neon("Neon"),
    Pistachio("Pistachio"),
    Ocean("Ocean"),
    Sunset("Sunset"),
    Berry("Berry"),
}

enum class BentoCorners(val label: String) {
    Leaf("Leaf"),
    Round("Round"),
    Soft("Soft"),
}

enum class BentoTitle(val label: String) {
    Huge("Huge"),
    Large("Large"),
    Compact("Compact"),
}

enum class BentoDensity(val label: String) {
    Comfortable("Comfortable"),
    Compact("Compact"),
}

enum class BentoDockLabels(val label: String) {
    Active("Active only"),
    Always("Always"),
    Never("Never"),
}

/** Zed's six Bento connect-control morphologies. Default: Slide. */
enum class BentoConnect(val label: String) {
    Slide("Slide"),
    Button("Button"),
    Morph("Morph"),
    Rail("Rail"),
    Hold("Hold"),
    Tile("Tile"),
}

/** Zed's BentoPrefs, field for field, with Zed's defaults. */
@Immutable
data class BentoPrefs(
    val palette: BentoPalette = BentoPalette.Brand,
    val corners: BentoCorners = BentoCorners.Leaf,
    val title: BentoTitle = BentoTitle.Large,
    val density: BentoDensity = BentoDensity.Comfortable,
    val motifs: Boolean = true,
    val playfulTouch: Boolean = true,
    val indexes: Boolean = true,
    val liveTiles: Boolean = true,
    val tiles: String = "",
    val dockLabels: BentoDockLabels = BentoDockLabels.Active,
    val dockAutoHide: Boolean = true,
    val navStyle: NavBarStyle = NavBarStyle.Dock,
    val connect: BentoConnect = BentoConnect.Slide,
    val wave: Boolean = true,
    val homeSpeed: Boolean = true,
    val homeSession: Boolean = true,
    val homeQuick: Boolean = true,
    val homeChips: Boolean = true,
)

/** Interface font axis. Auto = the design/language rule in Type.kt. */
enum class UiFont(val label: String) {
    Auto("Auto"),
    GoogleSans("Google Sans"),
    Inter("Inter"),
    Vazirmatn("Vazirmatn"),
    Estedad("Estedad"),
    Nunito("Nunito"),
    System("System"),
}

/** Old-design traffic card styles (surfaces arrive in Phase B2). */
enum class TrafficCardStyle(val label: String) {
    Cards("Cards"),
    Duo("Duo"),
    Rings("Rings"),
    Minimal("Minimal"),
    Graph("Graph"),
}

enum class TrafficTileSize(val label: String, val vPad: Int, val hPad: Int) {
    Small("Small", 8, 5),
    Normal("Normal", 14, 12),
    Large("Large", 18, 16),
}

/** Notification group (Interface §4.5), read by the VPN service. */
enum class NotifStyle(val label: String) {
    System("System"),
    Bento("Bento"),
}

enum class NotifChip(val label: String) {
    Speed("Speed"),
    ConfigName("Server name"),
    Duration("Duration"),
    Total("Total data"),
    None("None"),
}
