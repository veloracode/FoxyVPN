package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.ProxyStateStore
import com.vauth.foxyvpn.data.RECOMMENDED_COUNTRY_CODE
import com.vauth.foxyvpn.data.ServerListClient
import com.vauth.foxyvpn.data.model.VpnCountry
import com.vauth.foxyvpn.data.model.displayName
import com.vauth.foxyvpn.data.model.emojiFlag
import com.vauth.foxyvpn.ui.ServerListViewModel
import com.vauth.foxyvpn.ui.ServerPingState

private sealed class LocationRow {
    data class RecommendedRow(val country: VpnCountry) : LocationRow()

    data class CountryHeader(val country: VpnCountry) : LocationRow()

    data class CityRow(val country: VpnCountry, val cityIndex: Int) : LocationRow()
}

@Composable
fun ServerListScreen(
    serverListClient: ServerListClient,
    proxyStateStore: ProxyStateStore,
    onServerSelected: () -> Unit,
    onBack: () -> Unit,
    viewModel: ServerListViewModel =
        viewModel(factory = ServerListViewModel.factory(serverListClient, proxyStateStore)),
) {
    ServerListScreen(
        viewModel = viewModel,
        proxyStateStore = proxyStateStore,
        onServerSelected = onServerSelected,
        onBack = onBack,
    )
}

@Composable
fun ServerListScreen(
    viewModel: ServerListViewModel,
    proxyStateStore: ProxyStateStore,
    onServerSelected: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val countries = state.countries
    val isLoading = state.isLoading
    val errorMessage = state.errorMessage
    val selectedState by proxyStateStore.selectedProxyFlow.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(errorMessage) {
        val message = errorMessage
        if (message != null && countries.isNotEmpty()) {
            snackbarHost.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val ordered =
        remember(countries) {
            countries.sortedWith(compareBy({ it.code != RECOMMENDED_COUNTRY_CODE }, { it.name }))
        }

    val rows =
        remember(ordered) {
            buildList {
                for (country in ordered) {
                    if (country.code == RECOMMENDED_COUNTRY_CODE) {
                        add(LocationRow.RecommendedRow(country))
                        continue
                    }
                    add(LocationRow.CountryHeader(country))
                    country.cities.indices.forEach { index ->
                        add(LocationRow.CityRow(country, index))
                    }
                }
            }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                errorMessage != null && countries.isEmpty() ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            errorMessage,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = {
                                viewModel.loadServerList(force = true)
                            }
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                else ->
                    LazyColumn(
                        Modifier.selectableGroup(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            rows,
                            key = { row ->
                                when (row) {
                                    is LocationRow.RecommendedRow ->
                                        "recommended:${row.country.code}"
                                    is LocationRow.CountryHeader -> "country:${row.country.code}"
                                    is LocationRow.CityRow ->
                                        "city:${row.country.code}:${row.country.cities[row.cityIndex].code}"
                                }
                            },
                        ) { row ->
                            val selected = selectedState
                            when (row) {
                                is LocationRow.RecommendedRow ->
                                    androidx.compose.foundation.layout.Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .selectable(
                                                selected =
                                                    row.country.code == selected?.countryCode,
                                                role = Role.RadioButton,
                                                onClick = {
                                                    val candidates =
                                                        ServerListClient.candidatesForCountry(
                                                            countries,
                                                            row.country.code,
                                                        )
                                                    val chosen = candidates.randomOrNull()
                                                    if (chosen != null) {
                                                        viewModel.selectCandidate(chosen)
                                                        onServerSelected()
                                                    } else {
                                                        viewModel.setErrorMessage(
                                                            context.getString(
                                                                R.string.server_list_no_servers_country,
                                                                row.country.name,
                                                            )
                                                        )
                                                    }
                                                },
                                            )
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                            Text(
                                                stringResource(R.string.server_list_recommended_title),
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                            Text(
                                                stringResource(R.string.server_list_recommended_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            )
                                        }
                                        if (row.country.code == selected?.countryCode) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                is LocationRow.CountryHeader ->
                                    androidx.compose.foundation.layout.Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(38.dp)
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = row.country.emojiFlag,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                                        Text(
                                            row.country.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                is LocationRow.CityRow -> {
                                    val city = row.country.cities[row.cityIndex]
                                    val available =
                                        remember(countries, row.country.code, city.code) {
                                            ServerListClient.candidatesForCity(
                                                countries,
                                                row.country.code,
                                                city.code,
                                            )
                                        }
                                    val serverCount = available.size
                                    val pingKey = "${row.country.code}:${city.code}"
                                    val pingMs = state.pingResults[pingKey]
                                    val pingState =
                                        state.pingStates[pingKey] ?: ServerPingState.PENDING

                                    LaunchedEffect(pingKey, available, state.directoryRevision) {
                                        viewModel.probeCityLatency(
                                            row.country.code,
                                            city.code,
                                            available,
                                        )
                                    }

                                    val isSelected =
                                        row.country.code == selected?.countryCode &&
                                            city.code == selected?.cityCode
                                    androidx.compose.foundation.layout.Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainer)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        1.5.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                        androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .selectable(
                                                selected = isSelected,
                                                role = Role.RadioButton,
                                                onClick = {
                                                    val candidates =
                                                        ServerListClient.candidatesForCity(
                                                            countries,
                                                            row.country.code,
                                                            city.code,
                                                        )
                                                    val chosen = candidates.randomOrNull()
                                                    if (chosen != null) {
                                                        viewModel.selectCandidate(chosen)
                                                        onServerSelected()
                                                    } else {
                                                        viewModel.setErrorMessage(
                                                            context.getString(
                                                                R.string.server_list_no_servers_city,
                                                                city.code,
                                                            )
                                                        )
                                                    }
                                                },
                                            )
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(38.dp)
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = row.country.emojiFlag,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                            Text(
                                                com.vauth.foxyvpn.ui.map.CityCoordinates.forCityCode(city.code)?.cityName ?: city.code,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            val primaryHostname = available.firstOrNull()?.host
                                            val serverLabel =
                                                when {
                                                    primaryHostname == null ->
                                                        stringResource(R.string.server_list_no_servers_available)
                                                    serverCount > 1 ->
                                                        stringResource(
                                                            R.string.server_list_more_servers,
                                                            primaryHostname,
                                                            serverCount - 1,
                                                        )
                                                    else -> primaryHostname
                                                }
                                            Text(
                                                serverLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                        val pingColor = when {
                                            pingMs == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                            pingMs < 150 -> Color(0xFF34A853)
                                            pingMs < 300 -> Color(0xFFF9AB00)
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                        val pingLabel =
                                            when {
                                                pingMs != null ->
                                                    stringResource(R.string.server_list_tcp_ms, pingMs)
                                                pingState == ServerPingState.FAILED ->
                                                    stringResource(R.string.server_list_tcp_unavailable)
                                                else -> stringResource(R.string.server_list_checking_tcp)
                                            }
                                        Box(
                                            Modifier
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                                .background(pingColor.copy(alpha = 0.14f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                pingLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = pingColor,
                                            )
                                        }
                                        if (isSelected) {
                                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}
