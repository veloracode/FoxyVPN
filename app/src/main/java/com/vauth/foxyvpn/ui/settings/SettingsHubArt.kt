package com.vauth.foxyvpn.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.MaterialShapes
import com.vauth.foxyvpn.ui.bento.BentoTiles
import com.vauth.foxyvpn.ui.bento.SettingsArt

/**
 * Hub tile artwork registry — Phase B1 of the Zed overhaul.
 *
 * Each settings destination gets a MaterialShapes polygon + icon,
 * the same art on its hub tile and on its page header.
 */
fun hubArt(key: String): SettingsArt = when (key) {
    BentoTiles.APPEARANCE -> SettingsArt(key, MaterialShapes.Clover4Leaf, Icons.Filled.Palette)
    BentoTiles.VPN -> SettingsArt(key, MaterialShapes.Flower, Icons.Filled.VpnKey)
    BentoTiles.DNS -> SettingsArt(key, MaterialShapes.Cookie6Sided, Icons.Filled.Dns)
    BentoTiles.ROUTING -> SettingsArt(key, MaterialShapes.Cookie7Sided, Icons.Filled.AltRoute)
    BentoTiles.PER_APP -> SettingsArt(key, MaterialShapes.Sunny, Icons.Filled.Apps)
    BentoTiles.CORE -> SettingsArt(key, MaterialShapes.Cookie9Sided, Icons.Filled.Hub)
    BentoTiles.MODE -> SettingsArt(key, MaterialShapes.SoftBurst, Icons.Filled.SwapHoriz)
    BentoTiles.ACCOUNTS -> SettingsArt(key, MaterialShapes.Clover8Leaf, Icons.Filled.AccountCircle)
    BentoTiles.MUX -> SettingsArt(key, MaterialShapes.Cookie12Sided, Icons.Filled.CallMerge)
    BentoTiles.FRAGMENT -> SettingsArt(key, MaterialShapes.SoftBoom, Icons.Filled.ContentCut)
    BentoTiles.ADVANCED -> SettingsArt(key, MaterialShapes.Ghostish, Icons.Filled.Tune)
    BentoTiles.SUPPORT -> SettingsArt(key, MaterialShapes.Heart, Icons.Filled.HelpOutline)
    BentoTiles.UPDATE -> SettingsArt(key, MaterialShapes.Arrow, Icons.Filled.SystemUpdate)
    BentoTiles.ABOUT -> SettingsArt(key, MaterialShapes.Oval, Icons.Filled.Info)
    BentoTiles.ANTI_BLOCK -> SettingsArt(key, MaterialShapes.Diamond, Icons.Filled.Shield)
    else -> SettingsArt(key, MaterialShapes.Clover4Leaf, Icons.Filled.Tune)
}
