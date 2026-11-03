package com.vauth.foxyvpn.ui.theme

import androidx.compose.ui.graphics.Color
import com.vauth.foxyvpn.data.SettingsStore

/**
 * Per-surface personalization resolved from [SettingsStore]. Null = use the
 * color scheme default. OVERHAUL (Phase B1): the model now carries all
 * eleven personalize colors (the v1 port stored six; the traffic-tile and
 * tap-hint colors join so theme profiles apply completely — their surfaces
 * are the Zed Old design's, Phase B2).
 */
data class Personalization(
    val connectColor: Color? = null,
    val connectedColor: Color? = null,
    val serverCardColor: Color? = null,
    val serverActiveColor: Color? = null,
    val configCardColor: Color? = null,
    val configCardTextColor: Color? = null,
    val downloadColor: Color? = null,
    val uploadColor: Color? = null,
    val downloadTileColor: Color? = null,
    val uploadTileColor: Color? = null,
    val tapHintColor: Color? = null,
    val cornerStyle: CardCornerStyle = CardCornerStyle.Rounded,
    val density: ListDensity = ListDensity.Comfortable,
    val showServerPing: Boolean = true,
    val showServerUsage: Boolean = true,
    val monospaceAddress: Boolean = false,
) {
    companion object {
        val Default = Personalization()
    }
}

private fun Long?.toColorOrNull(): Color? =
    if (this == null || this == SettingsStore.UNSET_COLOR) null else Color(this)

fun SettingsStore.toPersonalization(): Personalization = Personalization(
    connectColor = customConnectColor.toColorOrNull(),
    connectedColor = customConnectedColor.toColorOrNull(),
    serverCardColor = customServerCardColor.toColorOrNull(),
    serverActiveColor = customServerActiveColor.toColorOrNull(),
    configCardColor = customConfigCardColor.toColorOrNull(),
    configCardTextColor = customConfigCardTextColor.toColorOrNull(),
    downloadColor = customDownloadColor.toColorOrNull(),
    uploadColor = customUploadColor.toColorOrNull(),
    downloadTileColor = customDownloadTileColor.toColorOrNull(),
    uploadTileColor = customUploadTileColor.toColorOrNull(),
    tapHintColor = customTapHintColor.toColorOrNull(),
    cornerStyle = cardCornerStyle,
    density = listDensity,
    showServerPing = showServerPing,
    showServerUsage = showServerUsage,
    monospaceAddress = monospaceAddress,
)

fun SettingsStore.hasPersonalization(): Boolean =
    customConnectColor != SettingsStore.UNSET_COLOR ||
        customConnectedColor != SettingsStore.UNSET_COLOR ||
        customServerCardColor != SettingsStore.UNSET_COLOR ||
        customServerActiveColor != SettingsStore.UNSET_COLOR ||
        customConfigCardColor != SettingsStore.UNSET_COLOR ||
        customConfigCardTextColor != SettingsStore.UNSET_COLOR ||
        customDownloadColor != SettingsStore.UNSET_COLOR ||
        customUploadColor != SettingsStore.UNSET_COLOR ||
        customDownloadTileColor != SettingsStore.UNSET_COLOR ||
        customUploadTileColor != SettingsStore.UNSET_COLOR ||
        customTapHintColor != SettingsStore.UNSET_COLOR ||
        cardCornerStyle != CardCornerStyle.Rounded ||
        listDensity != ListDensity.Comfortable ||
        !showServerPing || !showServerUsage || monospaceAddress ||
        themeProfileId != null

fun SettingsStore.resetPersonalization() {
    customConnectColor = SettingsStore.UNSET_COLOR
    customConnectedColor = SettingsStore.UNSET_COLOR
    customServerCardColor = SettingsStore.UNSET_COLOR
    customServerActiveColor = SettingsStore.UNSET_COLOR
    customConfigCardColor = SettingsStore.UNSET_COLOR
    customConfigCardTextColor = SettingsStore.UNSET_COLOR
    customDownloadColor = SettingsStore.UNSET_COLOR
    customUploadColor = SettingsStore.UNSET_COLOR
    customDownloadTileColor = SettingsStore.UNSET_COLOR
    customUploadTileColor = SettingsStore.UNSET_COLOR
    customTapHintColor = SettingsStore.UNSET_COLOR
    themeProfileId = null
    cardCornerStyle = CardCornerStyle.Rounded
    listDensity = ListDensity.Comfortable
    showServerPing = true
    showServerUsage = true
    monospaceAddress = false
}

/**
 * Applies a theme profile exactly as the Zed model does: accent index,
 * dynamic color OFF, the AMOLED flag onto the amoledBlack axis (theme
 * mode itself is not touched), and all eleven personalize colors.
 */
fun SettingsStore.applyThemeProfile(profile: ThemeProfile) {
    accentColorIndex = profile.accentIndex
    dynamicColor = false
    amoledBlack = profile.amoled
    customDownloadColor = profile.download
    customUploadColor = profile.upload
    customDownloadTileColor = profile.downloadTile
    customUploadTileColor = profile.uploadTile
    customConnectColor = profile.connect
    customConnectedColor = profile.connected
    customTapHintColor = profile.tapHint
    customServerCardColor = profile.serverCard
    customServerActiveColor = profile.serverActive
    customConfigCardColor = profile.configCard
    customConfigCardTextColor = profile.configCardText
    themeProfileId = profile.id
}
