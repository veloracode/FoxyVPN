package com.vauth.foxyvpn.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vauth.foxyvpn.data.SettingsStore

/**
 * Reactive holder for every Interface/appearance setting. Each setter writes
 * through to [SettingsStore] (the persistence source of truth) and updates
 * Compose state so the whole UI recomposes instantly.
 */
class AppearanceController(private val settingsStore: SettingsStore) {

    var themeMode: ThemeMode by mutableStateOf(settingsStore.themeMode)
        private set
    var dynamicColor: Boolean by mutableStateOf(settingsStore.dynamicColor)
        private set
    var colorStyle: ColorStyle by mutableStateOf(settingsStore.colorStyle)
        private set
    var accentColorIndex: Int by mutableIntStateOf(settingsStore.accentColorIndex)
        private set
    var colorContrast: ColorContrast by mutableStateOf(settingsStore.colorContrast)
        private set
    var reduceMotion: Boolean by mutableStateOf(settingsStore.reduceMotion)
        private set
    var uiFontScale: UiFontScale by mutableStateOf(settingsStore.uiFontScale)
        private set
    var appLanguage: AppLanguage by mutableStateOf(settingsStore.appLanguage)
        private set
    var screenTransition: ScreenTransition by mutableStateOf(settingsStore.screenTransition)
        private set
    var navBarStyle: NavBarStyle by mutableStateOf(settingsStore.navBarStyle)
        private set
    var connectButtonStyle: ConnectButtonStyle by mutableStateOf(settingsStore.connectButtonStyle)
        private set
    var cardCornerStyle: CardCornerStyle by mutableStateOf(settingsStore.cardCornerStyle)
        private set
    var listDensity: ListDensity by mutableStateOf(settingsStore.listDensity)
        private set
    var themeProfileId: String? by mutableStateOf(settingsStore.themeProfileId)
        private set
    var appDesign: SettingsStore.AppDesign by mutableStateOf(settingsStore.appDesign)
        private set
    var personalization: Personalization by mutableStateOf(settingsStore.toPersonalization())
        private set
    var showServerPing: Boolean by mutableStateOf(settingsStore.showServerPing)
        private set
    var showServerUsage: Boolean by mutableStateOf(settingsStore.showServerUsage)
        private set
    var monospaceAddress: Boolean by mutableStateOf(settingsStore.monospaceAddress)
        private set
    var confirmRemove: Boolean by mutableStateOf(settingsStore.confirmRemove)
        private set
    var homeAfterSelect: Boolean by mutableStateOf(settingsStore.homeAfterSelect)
        private set
    var doubleColumnDisplay: Boolean by mutableStateOf(settingsStore.doubleColumnDisplay)
        private set
    var hapticFeedback: Boolean by mutableStateOf(settingsStore.hapticFeedback)
        private set

    // ---- Overhaul (Zed) appearance state ----
    var amoledBlack: Boolean by mutableStateOf(settingsStore.amoledBlack)
        private set
    var groupAllDisplay: Boolean by mutableStateOf(settingsStore.groupAllDisplay)
        private set
    var uiFont: UiFont by mutableStateOf(settingsStore.uiFont)
        private set
    var uiScale: Float by mutableStateOf(settingsStore.uiScale)
        private set
    var showTrafficTiles: Boolean by mutableStateOf(settingsStore.showTrafficTiles)
        private set
    var trafficTilesAboveHero: Boolean by mutableStateOf(settingsStore.trafficTilesAboveHero)
        private set
    var trafficCardStyle: TrafficCardStyle by mutableStateOf(settingsStore.trafficCardStyle)
        private set
    var trafficTileSize: TrafficTileSize by mutableStateOf(settingsStore.trafficTileSize)
        private set
    var liveNotification: Boolean by mutableStateOf(settingsStore.liveNotification)
        private set
    var notifChip: NotifChip by mutableStateOf(settingsStore.notifChip)
        private set
    var notifStyle: NotifStyle by mutableStateOf(settingsStore.notifStyle)
        private set
    var speedInNotification: Boolean by mutableStateOf(settingsStore.speedInNotification)
        private set
    var bento: BentoPrefs by mutableStateOf(settingsStore.bentoPrefs)
        private set
    var navDestinations: String by mutableStateOf(settingsStore.navDestinations)
        private set

    fun updateThemeMode(next: ThemeMode) {
        settingsStore.themeMode = next
        themeMode = next
    }

    /**
     * AMOLED switch: the overhaul model stores it as its own flag
     * (OverhaulMigration moved legacy ThemeMode.AMOLED users here), never
     * as a theme mode.
     */
    fun updateAmoled(on: Boolean) {
        settingsStore.amoledBlack = on
        amoledBlack = on
    }

    fun updateDynamicColor(on: Boolean) {
        settingsStore.dynamicColor = on
        dynamicColor = on
    }

    fun updateColorStyle(next: ColorStyle) {
        settingsStore.colorStyle = next
        colorStyle = next
        clearProfileSelection()
    }

    fun updateAccentColorIndex(next: Int) {
        settingsStore.accentColorIndex = next
        accentColorIndex = next
        clearProfileSelection()
    }

    fun updateColorContrast(next: ColorContrast) {
        settingsStore.colorContrast = next
        colorContrast = next
    }

    fun updateReduceMotion(on: Boolean) {
        settingsStore.reduceMotion = on
        reduceMotion = on
    }

    fun updateHapticFeedback(on: Boolean) {
        settingsStore.hapticFeedback = on
        hapticFeedback = on
    }

    fun updateUiFontScale(next: UiFontScale) {
        settingsStore.uiFontScale = next
        uiFontScale = next
    }

    fun updateAppLanguage(next: AppLanguage) {
        settingsStore.appLanguage = next
        appLanguage = next
    }

    fun updateScreenTransition(next: ScreenTransition) {
        settingsStore.screenTransition = next
        screenTransition = next
    }

    fun updateNavBarStyle(next: NavBarStyle) {
        settingsStore.navBarStyle = next
        navBarStyle = next
    }

    fun updateConnectButtonStyle(next: ConnectButtonStyle) {
        settingsStore.connectButtonStyle = next
        connectButtonStyle = next
    }

    fun updateCardCornerStyle(next: CardCornerStyle) {
        settingsStore.cardCornerStyle = next
        cardCornerStyle = next
        refreshPersonalization()
    }

    fun updateListDensity(next: ListDensity) {
        settingsStore.listDensity = next
        listDensity = next
        refreshPersonalization()
    }

    fun updateAppDesign(next: SettingsStore.AppDesign) {
        settingsStore.appDesign = next
        appDesign = next
    }

    fun updateCustomColor(which: PersonalizeSlot, argb: Long?) {
        val value = argb ?: SettingsStore.UNSET_COLOR
        when (which) {
            PersonalizeSlot.Connect -> settingsStore.customConnectColor = value
            PersonalizeSlot.Connected -> settingsStore.customConnectedColor = value
            PersonalizeSlot.Download -> settingsStore.customDownloadColor = value
            PersonalizeSlot.Upload -> settingsStore.customUploadColor = value
            PersonalizeSlot.DownloadTile -> settingsStore.customDownloadTileColor = value
            PersonalizeSlot.UploadTile -> settingsStore.customUploadTileColor = value
            PersonalizeSlot.TapHint -> settingsStore.customTapHintColor = value
            PersonalizeSlot.ServerCard -> settingsStore.customServerCardColor = value
            PersonalizeSlot.ServerActive -> settingsStore.customServerActiveColor = value
            PersonalizeSlot.ConfigCard -> settingsStore.customConfigCardColor = value
            PersonalizeSlot.ConfigCardText -> settingsStore.customConfigCardTextColor = value
        }
        refreshPersonalization()
        clearProfileSelection()
    }

    fun updateShowServerPing(on: Boolean) {
        settingsStore.showServerPing = on
        showServerPing = on
        refreshPersonalization()
    }

    fun updateShowServerUsage(on: Boolean) {
        settingsStore.showServerUsage = on
        showServerUsage = on
        refreshPersonalization()
    }

    fun updateMonospaceAddress(on: Boolean) {
        settingsStore.monospaceAddress = on
        monospaceAddress = on
        refreshPersonalization()
    }

    fun updateConfirmRemove(on: Boolean) {
        settingsStore.confirmRemove = on
        confirmRemove = on
    }

    fun updateHomeAfterSelect(on: Boolean) {
        settingsStore.homeAfterSelect = on
        homeAfterSelect = on
    }

    fun updateDoubleColumnDisplay(on: Boolean) {
        settingsStore.doubleColumnDisplay = on
        doubleColumnDisplay = on
    }

    // ---- Overhaul updaters ----

    fun updateGroupAllDisplay(on: Boolean) {
        settingsStore.groupAllDisplay = on
        groupAllDisplay = on
    }

    fun updateUiFont(next: UiFont) {
        settingsStore.uiFont = next
        uiFont = next
    }

    fun updateUiScale(next: Float) {
        settingsStore.uiScale = next
        uiScale = settingsStore.uiScale
    }

    fun updateShowTrafficTiles(on: Boolean) {
        settingsStore.showTrafficTiles = on
        showTrafficTiles = on
        refreshPersonalization()
    }

    fun updateTrafficTilesAboveHero(on: Boolean) {
        settingsStore.trafficTilesAboveHero = on
        trafficTilesAboveHero = on
        refreshPersonalization()
    }

    fun updateTrafficCardStyle(next: TrafficCardStyle) {
        settingsStore.trafficCardStyle = next
        trafficCardStyle = next
        refreshPersonalization()
    }

    fun updateTrafficTileSize(next: TrafficTileSize) {
        settingsStore.trafficTileSize = next
        trafficTileSize = next
        refreshPersonalization()
    }

    fun updateLiveNotification(on: Boolean) {
        settingsStore.liveNotification = on
        liveNotification = on
    }

    fun updateNotifChip(next: NotifChip) {
        settingsStore.notifChip = next
        notifChip = next
    }

    fun updateNotifStyle(next: NotifStyle) {
        settingsStore.notifStyle = next
        notifStyle = next
    }

    fun updateSpeedInNotification(on: Boolean) {
        settingsStore.speedInNotification = on
        speedInNotification = on
    }

    fun updateNavDestinations(encoded: String) {
        settingsStore.navDestinations = encoded
        navDestinations = encoded
    }

    /** Rewrites every Bento field from [next] into the store, then re-reads. */
    fun updateBento(next: BentoPrefs) {
        settingsStore.bentoPalette = next.palette
        settingsStore.bentoCorners = next.corners
        settingsStore.bentoTitle = next.title
        settingsStore.bentoDensity = next.density
        settingsStore.bentoMotifs = next.motifs
        settingsStore.bentoPlayfulTouch = next.playfulTouch
        settingsStore.bentoIndexes = next.indexes
        settingsStore.bentoLiveTiles = next.liveTiles
        settingsStore.bentoTiles = next.tiles
        settingsStore.bentoDockLabels = next.dockLabels
        settingsStore.bentoDockAutoHide = next.dockAutoHide
        settingsStore.bentoNavStyle = next.navStyle
        settingsStore.bentoConnect = next.connect
        settingsStore.bentoWave = next.wave
        settingsStore.bentoHomeSpeed = next.homeSpeed
        settingsStore.bentoHomeSession = next.homeSession
        settingsStore.bentoHomeQuick = next.homeQuick
        settingsStore.bentoHomeChips = next.homeChips
        bento = settingsStore.bentoPrefs
    }

    fun updateBentoPalette(next: BentoPalette) = updateBento(bento.copy(palette = next))
    fun updateBentoCorners(next: BentoCorners) = updateBento(bento.copy(corners = next))
    fun updateBentoTitle(next: BentoTitle) = updateBento(bento.copy(title = next))
    fun updateBentoDensity(next: BentoDensity) = updateBento(bento.copy(density = next))
    fun updateBentoMotifs(on: Boolean) = updateBento(bento.copy(motifs = on))
    fun updateBentoPlayfulTouch(on: Boolean) = updateBento(bento.copy(playfulTouch = on))
    fun updateBentoIndexes(on: Boolean) = updateBento(bento.copy(indexes = on))
    fun updateBentoLiveTiles(on: Boolean) = updateBento(bento.copy(liveTiles = on))
    fun updateBentoTiles(encoded: String) = updateBento(bento.copy(tiles = encoded))
    fun updateBentoDockLabels(next: BentoDockLabels) = updateBento(bento.copy(dockLabels = next))
    fun updateBentoDockAutoHide(on: Boolean) = updateBento(bento.copy(dockAutoHide = on))
    fun updateBentoNavStyle(next: NavBarStyle) = updateBento(bento.copy(navStyle = next))
    fun updateBentoConnect(next: BentoConnect) = updateBento(bento.copy(connect = next))
    fun updateBentoWave(on: Boolean) = updateBento(bento.copy(wave = on))
    fun updateBentoHomeSpeed(on: Boolean) = updateBento(bento.copy(homeSpeed = on))
    fun updateBentoHomeSession(on: Boolean) = updateBento(bento.copy(homeSession = on))
    fun updateBentoHomeQuick(on: Boolean) = updateBento(bento.copy(homeQuick = on))
    fun updateBentoHomeChips(on: Boolean) = updateBento(bento.copy(homeChips = on))

    /** Zed's "Reset look": every Bento field back to default except the tile layout. */
    fun resetBentoLook() {
        updateBento(BentoPrefs(tiles = bento.tiles))
    }

    fun applyThemeProfile(profile: ThemeProfile?) {
        if (profile == null) {
            settingsStore.resetPersonalization()
        } else {
            settingsStore.applyThemeProfile(profile)
        }
        themeProfileId = settingsStore.themeProfileId
        themeMode = settingsStore.themeMode
        accentColorIndex = settingsStore.accentColorIndex
        amoledBlack = settingsStore.amoledBlack
        dynamicColor = settingsStore.dynamicColor
        refreshPersonalization()
    }

    fun resetPersonalization() {
        settingsStore.resetPersonalization()
        themeProfileId = null
        refreshPersonalization()
    }

    fun hasPersonalization(): Boolean = settingsStore.hasPersonalization()

    private fun refreshPersonalization() {
        personalization = settingsStore.toPersonalization()
    }

    private fun clearProfileSelection() {
        if (themeProfileId != null) {
            settingsStore.themeProfileId = null
            themeProfileId = null
        }
    }
}

enum class PersonalizeSlot(val title: String) {
    Connect("Connect button"),
    Connected("Connected state"),
    Download("Download color"),
    Upload("Upload color"),
    DownloadTile("Download tile"),
    UploadTile("Upload tile"),
    TapHint("Tap hint"),
    ServerCard("Location card"),
    ServerActive("Active location card"),
    ConfigCard("Config card"),
    ConfigCardText("Config card text"),
}

@Composable
fun rememberAppearanceController(settingsStore: SettingsStore): AppearanceController =
    remember(settingsStore) { AppearanceController(settingsStore) }
