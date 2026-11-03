package com.vauth.foxyvpn.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.ui.bento.BentoGroupLabel
import com.vauth.foxyvpn.ui.bento.FoxyDestination
import com.vauth.foxyvpn.ui.bento.SettingsActionRow
import com.vauth.foxyvpn.ui.bento.SettingsChoiceRow
import com.vauth.foxyvpn.ui.bento.SettingsGroup
import com.vauth.foxyvpn.ui.bento.SettingsInfoRow
import com.vauth.foxyvpn.ui.bento.SettingsListRow
import com.vauth.foxyvpn.ui.bento.SettingsPageScaffold
import com.vauth.foxyvpn.ui.bento.SettingsSwitchRow
import com.vauth.foxyvpn.ui.bento.decodeNavDestinations
import com.vauth.foxyvpn.ui.bento.encodeNavDestinations
import com.vauth.foxyvpn.ui.bento.settingsSegmentShape
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.AccentPresets
import com.vauth.foxyvpn.ui.theme.AppLanguage
import com.vauth.foxyvpn.ui.theme.AppearanceController
import com.vauth.foxyvpn.ui.theme.BentoPalette
import com.vauth.foxyvpn.ui.theme.BentoPalettes
import com.vauth.foxyvpn.ui.theme.CardCornerStyle
import com.vauth.foxyvpn.ui.theme.ColorContrast
import com.vauth.foxyvpn.ui.theme.ColorStyle
import com.vauth.foxyvpn.ui.theme.ListDensity
import com.vauth.foxyvpn.ui.theme.NavBarStyle
import com.vauth.foxyvpn.ui.theme.NotifChip
import com.vauth.foxyvpn.ui.theme.NotifStyle
import com.vauth.foxyvpn.ui.theme.PersonalizeSlot
import com.vauth.foxyvpn.ui.theme.ScreenTransition
import com.vauth.foxyvpn.ui.theme.ThemeMode
import com.vauth.foxyvpn.ui.theme.ThemeProfiles
import com.vauth.foxyvpn.ui.theme.UiFont
import com.vauth.foxyvpn.ui.theme.UiFontScale
import com.vauth.foxyvpn.ui.theme.accentSeed
import com.vauth.foxyvpn.ui.theme.fontFamilyOf
import com.vauth.foxyvpn.ui.theme.hasPersonalization
import com.vauth.foxyvpn.ui.theme.resetPersonalization
import com.vauth.foxyvpn.ui.theme.toPersonalization
import com.vauth.foxyvpn.ui.theme.transitionTransform
import kotlinx.coroutines.delay

/**
 * The Interface page — Phase B1 of the Zed overhaul.
 *
 * Implements the source-verified Interface enumeration (DESIGN-MAP
 * §4.1–4.15, Zed UiPage group order) against the fork's appearance
 * model. Every row writes a real pref and the reader of each pref is
 * live in the app (theme, Home, dock, service notification, server
 * list). Old-design-only groups (connect-button style, traffic
 * tiles/personalize) do NOT render under Bento — they arrive with
 * the Zed Old design in Phase B2; the design picker therefore offers
 * Bento only in this preview.
 */
@Composable
fun InterfacePage(
    appearance: AppearanceController,
    settingsStore: SettingsStore,
    onOpenNavBar: () -> Unit,
    onBack: () -> Unit,
) {
    val bento = appearance.bento
    var personalization by remember { mutableStateOf(settingsStore.toPersonalization()) }
    fun refreshPersonalization() {
        personalization = settingsStore.toPersonalization()
    }

    SettingsPageScaffold(
        title = s("Interface"),
        subtitle = s("Design, colors and behaviour"),
        art = hubArt(com.vauth.foxyvpn.ui.bento.BentoTiles.APPEARANCE),
        onBack = onBack,
    ) {
        // --- Design ------------------------------------------------------
        BentoGroupLabel(s("Design"))
        Column(
            Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Bento", style = MaterialTheme.typography.bodyLargeEmphasized)
                    Text(
                        s("The current design"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SettingsGroup(null, listOf {
            SettingsInfoRow(
                s("More designs"),
                s("The Pixel and Old designs arrive in a later update."),
            )
        })

        // --- Look --------------------------------------------------------
        SettingsGroup(s("Look"), listOf<@Composable () -> Unit>({ PalettePicker(appearance) }) + listOf<@Composable () -> Unit>(
            {
                SettingsChoiceRow(
                    title = s("Corners"),
                    options = com.vauth.foxyvpn.ui.theme.BentoCorners.entries.map { it.label },
                    selected = bento.corners.label,
                    onSelect = { label ->
                        appearance.updateBentoCorners(
                            com.vauth.foxyvpn.ui.theme.BentoCorners.entries.first { it.label == label },
                        )
                    },
                )
            },
            {
                SettingsChoiceRow(
                    title = s("Title size"),
                    options = com.vauth.foxyvpn.ui.theme.BentoTitle.entries.map { it.label },
                    selected = bento.title.label,
                    onSelect = { label ->
                        appearance.updateBentoTitle(
                            com.vauth.foxyvpn.ui.theme.BentoTitle.entries.first { it.label == label },
                        )
                    },
                )
            },
            {
                SettingsChoiceRow(
                    title = s("Density"),
                    options = com.vauth.foxyvpn.ui.theme.BentoDensity.entries.map { it.label },
                    selected = bento.density.label,
                    onSelect = { label ->
                        appearance.updateBentoDensity(
                            com.vauth.foxyvpn.ui.theme.BentoDensity.entries.first { it.label == label },
                        )
                    },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Motifs"),
                    description = s("Decorative shapes on cards"),
                    checked = bento.motifs,
                    onCheckedChange = { appearance.updateBentoMotifs(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Playful touch"),
                    description = s("Springy presses and spins"),
                    checked = bento.playfulTouch,
                    onCheckedChange = { appearance.updateBentoPlayfulTouch(it) },
                )
            },
        ))

        // --- Board -------------------------------------------------------
        SettingsGroup(s("Board"), listOf(
            {
                SettingsSwitchRow(
                    title = s("Tile indexes"),
                    description = s("Numbers on the settings tiles"),
                    checked = bento.indexes,
                    onCheckedChange = { appearance.updateBentoIndexes(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Live tiles"),
                    description = s("Tiles react while you use the app"),
                    checked = bento.liveTiles,
                    onCheckedChange = { appearance.updateBentoLiveTiles(it) },
                )
            },
            {
                SettingsActionRow(
                    title = s("Reset tiles"),
                    onClick = { appearance.updateBentoTiles("") },
                )
            },
        ))

        // --- Home --------------------------------------------------------
        SettingsGroup(s("Home"), listOf<@Composable () -> Unit>(
            {
                SettingsListRow(
                    title = s("Connect control"),
                    options = com.vauth.foxyvpn.ui.theme.BentoConnect.entries.map { it.label },
                    selected = bento.connect.label,
                    onSelect = { label ->
                        appearance.updateBentoConnect(
                            com.vauth.foxyvpn.ui.theme.BentoConnect.entries.first { it.label == label },
                        )
                    },
                )
            },
        ) + (if (bento.connect == com.vauth.foxyvpn.ui.theme.BentoConnect.Slide) {
            listOf<@Composable () -> Unit>({
                SettingsSwitchRow(
                    title = s("Wave track"),
                    description = s("Wavy progress line on the slider"),
                    checked = bento.wave,
                    onCheckedChange = { appearance.updateBentoWave(it) },
                )
            })
        } else {
            emptyList()
        }) + listOf<@Composable () -> Unit>(
            {
                SettingsSwitchRow(
                    title = s("Speed tiles"),
                    checked = bento.homeSpeed,
                    onCheckedChange = { appearance.updateBentoHomeSpeed(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Session tiles"),
                    checked = bento.homeSession,
                    onCheckedChange = { appearance.updateBentoHomeSession(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Fastest servers"),
                    checked = bento.homeQuick,
                    onCheckedChange = { appearance.updateBentoHomeQuick(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Quick actions"),
                    checked = bento.homeChips,
                    onCheckedChange = { appearance.updateBentoHomeChips(it) },
                )
            },
            {
                SettingsActionRow(
                    title = s("Reset look"),
                    onClick = { appearance.resetBentoLook() },
                )
            },
        ))

        // --- Notification --------------------------------------------------
        SettingsGroup(s("Notification"), listOf<@Composable () -> Unit>(
            {
                SettingsSwitchRow(
                    title = s("Live notification"),
                    description = s("Keep the status notification updated"),
                    checked = appearance.liveNotification,
                    onCheckedChange = { appearance.updateLiveNotification(it) },
                )
            },
            {
                SettingsListRow(
                    title = s("Notification shows"),
                    options = NotifChip.entries.map { it.label },
                    selected = appearance.notifChip.label,
                    enabled = appearance.liveNotification,
                    onSelect = { label ->
                        appearance.updateNotifChip(NotifChip.entries.first { it.label == label })
                    },
                )
            },
            {
                SettingsListRow(
                    title = s("Notification style"),
                    options = NotifStyle.entries.map { it.label },
                    selected = appearance.notifStyle.label,
                    enabled = !appearance.liveNotification,
                    onSelect = { label ->
                        appearance.updateNotifStyle(NotifStyle.entries.first { it.label == label })
                    },
                )
            },
        ) + (if (appearance.liveNotification) {
            listOf<@Composable () -> Unit>({
                SettingsInfoRow(
                    s("Live notification"),
                    s("The notification follows the live style with the line you picked above."),
                )
            })
        } else {
            emptyList()
        }) + listOf<@Composable () -> Unit>({
            SettingsSwitchRow(
                title = s("Speed in notification"),
                description = s("Show live speeds while connected"),
                checked = appearance.speedInNotification,
                onCheckedChange = { appearance.updateSpeedInNotification(it) },
            )
        }))

        // --- Colors --------------------------------------------------------
        SettingsGroup(s("Colors"), listOf<@Composable () -> Unit>(
            {
                SettingsChoiceRow(
                    title = s("Theme"),
                    options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).map { it.name.lowercase().replaceFirstChar { ch -> ch.uppercase() } },
                    selected = when (appearance.themeMode) {
                        ThemeMode.AMOLED -> ThemeMode.SYSTEM
                        else -> appearance.themeMode
                    }.name.lowercase().replaceFirstChar { it.uppercase() },
                    onSelect = { label ->
                        appearance.updateThemeMode(
                            when (label) {
                                "Light" -> ThemeMode.LIGHT
                                "Dark" -> ThemeMode.DARK
                                else -> ThemeMode.SYSTEM
                            },
                        )
                    },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Dynamic color"),
                    description = s("Follow your wallpaper colors (Android 12+)"),
                    checked = appearance.dynamicColor,
                    onCheckedChange = { appearance.updateDynamicColor(it) },
                )
            },
        ) + (if (!appearance.dynamicColor) {
            listOf<@Composable () -> Unit>(
                { ColorStylePicker(appearance) },
                { AccentPicker(appearance) },
                {
                    SettingsChoiceRow(
                        title = s("Contrast"),
                        options = ColorContrast.entries.map { it.label },
                        selected = appearance.colorContrast.label,
                        onSelect = { label ->
                            appearance.updateColorContrast(ColorContrast.entries.first { it.label == label })
                        },
                    )
                },
            )
        } else {
            emptyList()
        }) + listOf<@Composable () -> Unit>({
            SettingsSwitchRow(
                title = s("AMOLED black"),
                description = s("Pure black background to save battery on OLED screens"),
                checked = appearance.amoledBlack,
                onCheckedChange = { appearance.updateAmoled(it) },
            )
        }))

        // --- Theme profiles -------------------------------------------------
        BentoGroupLabel(s("Theme profiles"))
        Column(
            Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 12.dp),
        ) {
            Text(
                s("One tap applies a curated look."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThemeProfileCard(
                    name = s("Default"),
                    swatches = listOf(
                        accentSeed(appearance.accentColorIndex, dark = false),
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.primary,
                    ),
                    selected = appearance.themeProfileId == null,
                    onClick = {
                        appearance.applyThemeProfile(null)
                        refreshPersonalization()
                    },
                )
                ThemeProfiles.forEach { profile ->
                    ThemeProfileCard(
                        name = profile.name,
                        swatches = profile.swatches,
                        selected = appearance.themeProfileId == profile.id,
                        onClick = {
                            appearance.applyThemeProfile(profile)
                            refreshPersonalization()
                        },
                    )
                }
            }
        }

        // --- Display --------------------------------------------------------
        SettingsGroup(s("Display"), listOf<@Composable () -> Unit>(
            {
                SettingsListRow(
                    title = s("Language"),
                    options = AppLanguage.entries.map { it.label },
                    selected = appearance.appLanguage.label,
                    onSelect = { label ->
                        appearance.updateAppLanguage(AppLanguage.entries.first { it.label == label })
                    },
                )
            },
            {
                SettingsListRow(
                    title = s("Font size"),
                    options = UiFontScale.entries.map { it.label },
                    selected = appearance.uiFontScale.label,
                    onSelect = { label ->
                        appearance.updateUiFontScale(UiFontScale.entries.first { it.label == label })
                    },
                )
            },
            { FontPickerRow(appearance) },
            { ScaleRow(appearance) },
            {
                SettingsSwitchRow(
                    title = s("Reduce motion"),
                    description = s("Minimize animations across the app"),
                    checked = appearance.reduceMotion,
                    onCheckedChange = { appearance.updateReduceMotion(it) },
                )
            },
        ))

        // --- Transition ------------------------------------------------------
        BentoGroupLabel(s("Screen transition"))
        Column(
            Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TransitionPreview(appearance.screenTransition, appearance.reduceMotion)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                items(ScreenTransition.entries) { style ->
                    val chosen = style == appearance.screenTransition
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(
                                if (chosen) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            .clickable(role = Role.Button) { appearance.updateScreenTransition(style) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(
                            style.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (chosen) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // --- Navigation -------------------------------------------------------
        SettingsGroup(s("Navigation"), listOf {
            SettingsActionRow(
                title = s("Navigation bar"),
                description = appearance.bento.navStyle.label,
                onClick = onOpenNavBar,
            )
        })

        // --- Servers ------------------------------------------------------------
        SettingsGroup(s("Servers"), listOf<@Composable () -> Unit>(
            {
                PersonalizeColorRow(
                    slot = PersonalizeSlot.ServerCard,
                    current = personalization.serverCardColor,
                    appearance = appearance,
                    onChanged = ::refreshPersonalization,
                )
            },
            {
                PersonalizeColorRow(
                    slot = PersonalizeSlot.ServerActive,
                    current = personalization.serverActiveColor,
                    appearance = appearance,
                    onChanged = ::refreshPersonalization,
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Show ping"),
                    description = s("Measure latency next to each location"),
                    checked = appearance.showServerPing,
                    onCheckedChange = { appearance.updateShowServerPing(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Show server counts"),
                    description = s("Show how many servers each location has"),
                    checked = appearance.showServerUsage,
                    onCheckedChange = { appearance.updateShowServerUsage(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Monospace addresses"),
                    description = s("Use a fixed-width font for server addresses"),
                    checked = appearance.monospaceAddress,
                    onCheckedChange = { appearance.updateMonospaceAddress(it) },
                )
            },
        ))

        // --- Shape ----------------------------------------------------------------
        SettingsGroup(s("Shape"), listOf(
            {
                SettingsListRow(
                    title = s("Card corners"),
                    options = CardCornerStyle.entries.map { it.label },
                    selected = appearance.cardCornerStyle.label,
                    onSelect = { label ->
                        appearance.updateCardCornerStyle(CardCornerStyle.entries.first { it.label == label })
                    },
                )
            },
            {
                SettingsListRow(
                    title = s("List density"),
                    options = ListDensity.entries.map { it.label },
                    selected = appearance.listDensity.label,
                    onSelect = { label ->
                        appearance.updateListDensity(ListDensity.entries.first { it.label == label })
                    },
                )
            },
            {
                SettingsActionRow(
                    title = s("Reset personalization"),
                    enabled = settingsStore.hasPersonalization(),
                    onClick = {
                        settingsStore.resetPersonalization()
                        appearance.applyThemeProfile(null)
                        refreshPersonalization()
                    },
                )
            },
        ))

        // --- Behaviour ---------------------------------------------------------------
        SettingsGroup(s("Behaviour"), listOf(
            {
                SettingsSwitchRow(
                    title = s("Confirm before removing"),
                    description = s("Ask before deleting an account"),
                    checked = appearance.confirmRemove,
                    onCheckedChange = { appearance.updateConfirmRemove(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Group locations"),
                    description = s("Group all locations together"),
                    checked = appearance.groupAllDisplay,
                    onCheckedChange = { appearance.updateGroupAllDisplay(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Two-column locations"),
                    description = s("Show the location list in two columns"),
                    checked = appearance.doubleColumnDisplay,
                    onCheckedChange = { appearance.updateDoubleColumnDisplay(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Back home after selecting"),
                    description = s("Return to the home screen after picking a location"),
                    checked = appearance.homeAfterSelect,
                    onCheckedChange = { appearance.updateHomeAfterSelect(it) },
                )
            },
            {
                SettingsSwitchRow(
                    title = s("Haptic feedback"),
                    description = s("Vibrate on connect control and theme changes"),
                    checked = appearance.hapticFeedback,
                    onCheckedChange = { appearance.updateHapticFeedback(it) },
                )
            },
        ))
    }
}

@Composable
private fun PalettePicker(appearance: AppearanceController) {
    val cs = MaterialTheme.colorScheme
    val dark = when (appearance.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val seed = accentSeed(appearance.accentColorIndex, dark = false)
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            s("Palette"),
            style = MaterialTheme.typography.bodyLargeEmphasized,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            items(BentoPalette.entries) { palette ->
                val colors = BentoPalettes.of(palette, dark, seed, appearance.amoledBlack)
                val selected = appearance.bento.palette == palette
                Column(
                    Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (selected) Modifier.border(2.dp, cs.primary, RoundedCornerShape(20.dp))
                            else Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp)),
                        )
                        .clickable(role = Role.Button) { appearance.updateBentoPalette(palette) }
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(colors.ground)
                            .padding(6.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(5.dp)).background(colors.stage))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(colors.lime))
                                Box(Modifier.size(14.dp).clip(CircleShape).background(colors.blue.container))
                                Box(Modifier.size(14.dp).clip(CircleShape).background(colors.pink.container))
                            }
                        }
                        if (selected) {
                            Box(
                                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(cs.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    Text(
                        palette.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorStylePicker(appearance: AppearanceController) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            s("Color style"),
            style = MaterialTheme.typography.bodyLargeEmphasized,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorStyle.entries.forEach { style ->
                val selected = appearance.colorStyle == style
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (selected) cs.primary else cs.surfaceContainerHighest)
                        .clickable(role = Role.Button) { appearance.updateColorStyle(style) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        style.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentPicker(appearance: AppearanceController) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            s("Accent color"),
            style = MaterialTheme.typography.bodyLargeEmphasized,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentPresets.forEachIndexed { index, preset ->
                val selected = appearance.accentColorIndex == index
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(accentSeed(index, dark = false))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) cs.primary else cs.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable(role = Role.Button) { appearance.updateAccentColorIndex(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = preset.name, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeProfileCard(
    name: String,
    swatches: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) cs.primary else cs.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            swatches.take(3).forEach { swatch ->
                Box(Modifier.size(16.dp).clip(CircleShape).background(swatch))
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun FontPickerRow(appearance: AppearanceController) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            s("Font"),
            style = MaterialTheme.typography.bodyLargeEmphasized,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UiFont.entries.forEach { font ->
                val selected = appearance.uiFont == font
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                        .background(if (selected) cs.primary else cs.surfaceContainerHighest)
                        .clickable(role = Role.Button) { appearance.updateUiFont(font) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        font.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = fontFamilyOf(font) ?: MaterialTheme.typography.labelLarge.fontFamily,
                        ),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScaleRow(appearance: AppearanceController) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                s("Interface scale"),
                style = MaterialTheme.typography.bodyLargeEmphasized,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(appearance.uiScale * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = appearance.uiScale,
            onValueChange = { appearance.updateUiScale(it) },
            valueRange = 0.5f..2.0f,
            steps = 29,
        )
    }
}

@Composable
private fun TransitionPreview(style: ScreenTransition, reduceMotion: Boolean) {
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(style, reduceMotion) {
        while (true) {
            delay(1600)
            index++
        }
    }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(110.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = { transitionTransform(style, reduceMotion) },
            label = "transition-preview",
        ) { i ->
            val palette = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
            )
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(palette[i % palette.size]),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${i % 3 + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun PersonalizeColorRow(
    slot: PersonalizeSlot,
    current: Color?,
    appearance: AppearanceController,
    onChanged: () -> Unit,
) {
    var showPicker by remember(slot) { mutableStateOf(false) }
    if (showPicker) {
        PersonalizeColorDialog(
            title = slot.title,
            current = current,
            onDismiss = { showPicker = false },
            onConfirm = { argb ->
                appearance.updateCustomColor(slot, argb)
                showPicker = false
                onChanged()
            },
        )
    }
    com.vauth.foxyvpn.ui.bento.RowBody(
        title = slot.title,
        description = if (current == null) s("Default") else s("Custom"),
        trailing = {
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(current ?: MaterialTheme.colorScheme.primary)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        onClick = { showPicker = true },
    )
}

@Composable
private fun PersonalizeColorDialog(
    title: String,
    current: Color?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var hex by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(cs.surfaceContainerHighest)
                            .border(2.dp, if (current == null) cs.primary else cs.outlineVariant, CircleShape)
                            .clickable { onConfirm(null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("A", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    AccentPresets.forEachIndexed { index, _ ->
                        val color = accentSeed(index, dark = false)
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(color)
                                .border(2.dp, if (current == color) cs.primary else cs.outlineVariant, CircleShape)
                                .clickable { onConfirm(color.toArgb().toLong() and 0xFFFFFFFFL) },
                        )
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        error = false
                    },
                    label = { Text("Custom hex (e.g. #4A90D9)") },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = parseHexColor(hex)
                    if (parsed != null) onConfirm(parsed) else error = true
                },
            ) { Text(s("Apply")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s("Cancel")) }
        },
    )
}

private fun parseHexColor(input: String): Long? {
    val raw = input.trim().removePrefix("#")
    if (raw.length != 6 && raw.length != 8) return null
    val value = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) (0xFF000000L or value) else value
}

/** The Navigation page: style, dock behaviour and the tab checklist. */
@Composable
fun NavBarPage(
    appearance: AppearanceController,
    onBack: () -> Unit,
) {
    val destinations = remember(appearance.navDestinations) {
        decodeNavDestinations(appearance.navDestinations)
    }
    fun commit(next: List<FoxyDestination>) {
        appearance.updateNavDestinations(encodeNavDestinations(next))
    }
    SettingsPageScaffold(
        title = s("Navigation bar"),
        subtitle = s("Style, dock and tabs"),
        art = null,
        onBack = onBack,
    ) {
        SettingsGroup(s("Style"), listOf {
            SettingsListRow(
                title = s("Navigation bar style"),
                options = NavBarStyle.entries.map { it.label },
                selected = appearance.bento.navStyle.label,
                onSelect = { label ->
                    appearance.updateBentoNavStyle(NavBarStyle.entries.first { it.label == label })
                },
            )
        })
        if (appearance.bento.navStyle == NavBarStyle.Dock) {
            SettingsGroup(s("Dock"), listOf(
                {
                    SettingsChoiceRow(
                        title = s("Dock labels"),
                        options = com.vauth.foxyvpn.ui.theme.BentoDockLabels.entries.map { it.label },
                        selected = appearance.bento.dockLabels.label,
                        onSelect = { label ->
                            appearance.updateBentoDockLabels(
                                com.vauth.foxyvpn.ui.theme.BentoDockLabels.entries.first { it.label == label },
                            )
                        },
                    )
                },
                {
                    SettingsSwitchRow(
                        title = s("Auto-hide dock"),
                        description = s("Hide the dock while scrolling"),
                        checked = appearance.bento.dockAutoHide,
                        onCheckedChange = { appearance.updateBentoDockAutoHide(it) },
                    )
                },
            ))
        }
        BentoGroupLabel(s("Tabs"))
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val all = FoxyDestination.entries
            all.forEachIndexed { index, dest ->
                val included = dest in destinations
                val pinned = dest == FoxyDestination.Home || dest == FoxyDestination.Settings
                val position = destinations.indexOf(dest)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(settingsSegmentShape(index, all.size))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = included,
                        onCheckedChange = { on ->
                            if (pinned) return@Checkbox
                            commit(
                                if (on) destinations + dest
                                else destinations - dest,
                            )
                        },
                        enabled = !pinned,
                    )
                    Text(
                        s(dest.label),
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                    if (included && position > 0) {
                        com.vauth.foxyvpn.ui.bento.BentoIconButton(
                            icon = Icons.Filled.KeyboardArrowUp,
                            label = s("Move up"),
                            onClick = {
                                val next = destinations.toMutableList()
                                next.removeAt(position)
                                next.add(position - 1, dest)
                                commit(next)
                            },
                            size = 36.dp,
                        )
                    }
                    if (included && position in 0 until destinations.lastIndex) {
                        com.vauth.foxyvpn.ui.bento.BentoIconButton(
                            icon = Icons.Filled.KeyboardArrowDown,
                            label = s("Move down"),
                            onClick = {
                                val next = destinations.toMutableList()
                                next.removeAt(position)
                                next.add(position + 1, dest)
                                commit(next)
                            },
                            size = 36.dp,
                        )
                    }
                }
            }
        }
        SettingsGroup(null, listOf {
            SettingsInfoRow(
                s("Tabs"),
                s("Home and Settings always stay on the bar."),
            )
        })
    }
}
