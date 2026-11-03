package com.vauth.foxyvpn.ui.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.Bento
import com.vauth.foxyvpn.ui.theme.BentoDockLabels
import com.vauth.foxyvpn.ui.theme.NavBarStyle

/**
 * Bottom navigation — Phase B1 of the Zed overhaul.
 *
 * Destinations are the fork's four tabs (DESIGN-MAP §3 / DEV-9),
 * expressed through the overhaul's nav model: the visible set and
 * order persist in the nav_destinations pref (Home and Settings are
 * pinned — always present, like the reference's special-cased
 * Settings). Seven styles: the Bento Dock (the Bento design's own
 * chrome) plus the six bridge styles the fork already shipped.
 */
enum class FoxyDestination(val key: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Servers("servers", "Locations", Icons.Filled.Public),
    Accounts("accounts", "Accounts", Icons.Filled.AccountCircle),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

val DEFAULT_NAV_DESTINATIONS: List<FoxyDestination> = FoxyDestination.entries

/** Total decode of the nav_destinations pref; Home + Settings always present. */
fun decodeNavDestinations(raw: String): List<FoxyDestination> =
    decodeNavKeys(raw).mapNotNull { key -> FoxyDestination.entries.firstOrNull { it.key == key } }

fun encodeNavDestinations(destinations: List<FoxyDestination>): String =
    encodeNavKeys(destinations.map { it.key })

@Composable
fun FoxyNavBar(
    style: NavBarStyle,
    destinations: List<FoxyDestination>,
    current: FoxyDestination,
    onNavigate: (FoxyDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (style) {
        NavBarStyle.Dock -> BentoDock(destinations, current, onNavigate, modifier)
        NavBarStyle.FloatingPill -> FloatingPillBar(destinations, current, onNavigate, large = false, modifier = modifier)
        NavBarStyle.CompactDock -> FloatingPillBar(destinations, current, onNavigate, large = false, compact = true, modifier = modifier)
        NavBarStyle.ExpressivePill -> FloatingPillBar(destinations, current, onNavigate, large = true, modifier = modifier)
        NavBarStyle.FullBar -> FullNavBar(destinations, current, onNavigate, modifier)
        NavBarStyle.Underline -> UnderlineBar(destinations, current, onNavigate, modifier)
        NavBarStyle.Minimal -> MinimalBar(destinations, current, onNavigate, modifier)
    }
}

// ---------------------------------------------------------------------
// The Bento dock
// ---------------------------------------------------------------------

val BentoDockHeight = 56.dp
val BentoDockMargin = 20.dp

@Composable
fun BentoDock(
    destinations: List<FoxyDestination>,
    current: FoxyDestination,
    onNavigate: (FoxyDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val fire = LocalBentoHaptics.current
    val labels = Bento.prefs.dockLabels
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = BentoDockMargin),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(BentoDockHeight)
                .shadow(10.dp, RoundedCornerShape(30.dp), ambientColor = c.ink.copy(alpha = 0.18f), spotColor = c.ink.copy(alpha = 0.18f))
                .clip(RoundedCornerShape(30.dp))
                .background(c.dock)
                .border(1.dp, c.dockLine, RoundedCornerShape(30.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { dest ->
                val active = dest == current
                val bg by animateColorAsState(
                    if (active) c.lime else c.dock,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "dock-bg",
                )
                val fg = if (active) c.onLime else c.muted
                val showLabel = when (labels) {
                    BentoDockLabels.Active -> active
                    BentoDockLabels.Always -> true
                    BentoDockLabels.Never -> false
                }
                Row(
                    Modifier
                        .height(48.dp)
                        .widthIn(min = 50.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (!active) {
                                    fire(HapticFeedbackType.TextHandleMove)
                                    onNavigate(dest)
                                }
                            },
                        )
                        .semantics { selected = active }
                        .padding(horizontal = if (showLabel) 14.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(dest.icon, contentDescription = s(dest.label), tint = fg, modifier = Modifier.size(22.dp))
                    if (showLabel) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            s(dest.label),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = fg,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Bridge styles (the fork's pre-overhaul bar, destination-driven)
// ---------------------------------------------------------------------

@Composable
private fun FloatingPillBar(
    destinations: List<FoxyDestination>,
    current: FoxyDestination,
    onNavigate: (FoxyDestination) -> Unit,
    large: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(if (compact) 24.dp else 32.dp),
            color = cs.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 8.dp else 12.dp,
                    vertical = if (compact) 6.dp else 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { dest ->
                    val selected = dest == current
                    val bg by animateColorAsState(
                        if (selected) cs.primary else cs.surfaceContainerHigh,
                        tween(250), label = "pill-bg",
                    )
                    val fg by animateColorAsState(
                        if (selected) cs.onPrimary else cs.onSurfaceVariant,
                        tween(250), label = "pill-fg",
                    )
                    val pad by animateDpAsState(
                        if (selected && large) 20.dp else 12.dp,
                        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        label = "pill-pad",
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(if (compact) 18.dp else 24.dp))
                            .background(bg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = { onNavigate(dest) },
                            )
                            .padding(horizontal = pad, vertical = if (compact) 8.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(dest.icon, contentDescription = s(dest.label), tint = fg, modifier = Modifier.size(22.dp))
                        if (selected && large) {
                            Spacer(Modifier.width(8.dp))
                            Text(s(dest.label), style = MaterialTheme.typography.labelLarge, color = fg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullNavBar(
    destinations: List<FoxyDestination>,
    current: FoxyDestination,
    onNavigate: (FoxyDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        destinations.forEach { dest ->
            NavigationBarItem(
                selected = current == dest,
                onClick = { onNavigate(dest) },
                icon = { Icon(dest.icon, contentDescription = s(dest.label)) },
                label = { Text(s(dest.label)) },
            )
        }
    }
}

@Composable
private fun UnderlineBar(
    destinations: List<FoxyDestination>,
    current: FoxyDestination,
    onNavigate: (FoxyDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { dest ->
            val selected = dest == current
            val fg by animateColorAsState(
                if (selected) cs.primary else cs.onSurfaceVariant, tween(200), label = "ul-fg",
            )
            val indicatorWidth by animateDpAsState(
                if (selected) 28.dp else 0.dp,
                spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                label = "ul-w",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onNavigate(dest) },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(dest.icon, contentDescription = s(dest.label), tint = fg, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(indicatorWidth)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                )
            }
        }
    }
}

@Composable
private fun MinimalBar(
    destinations: List<FoxyDestination>,
    current: FoxyDestination,
    onNavigate: (FoxyDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { dest ->
            val selected = dest == current
            val fg by animateColorAsState(
                if (selected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f),
                tween(200), label = "min-fg",
            )
            val scale by animateDpAsState(
                if (selected) 28.dp else 24.dp,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                label = "min-s",
            )
            Icon(
                dest.icon,
                contentDescription = s(dest.label),
                tint = fg,
                modifier = Modifier
                    .size(scale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onNavigate(dest) },
                    )
                    .padding(2.dp),
            )
        }
    }
}
