package com.akira.island.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.akira.island.R
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.service.BankGuard
import com.akira.island.service.BankingAppInfo
import com.akira.island.service.IslandTile
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.posOf

@Composable
fun BanksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val running by IslandBus.serviceRunning.collectAsState()
    val s by SettingsRepo.state.collectAsState()
    val detectedApps by produceState(initialValue = emptyList<BankingAppInfo>(), s.customBankingApps, s.bankingAppExclusions) {
        value = BankGuard.getInstalledBankingApps(context)
    }

    Page(
        tr("Банк не пускает", "Bank app won't open"), onBack,
        subtitle = tr(
            "Защита от блокировки банками и безопасный режим",
            "Banking app compatibility & security protection"
        )
    ) {
        InfoText(
            tr(
                "Приложения банков блокируют службы спец. возможностей, если они запрашивают чтение экрана или снимки экрана. В Dynamic Island эти разрешения отключены, а при открытии банка остров автоматически исчезает.",
                "Banking apps block accessibility services requesting screen reading or screenshots. Dynamic Island uses a clean security profile without these capabilities and automatically vanishes while banking apps are open."
            )
        )

        SectionHeader(tr("Автоматическая защита", "Automatic protection"))
        Group {
            GroupToggle(
                pos = Pos.Single,
                icon = Icons.Rounded.Shield,
                title = tr("Скрывать в приложениях банков", "Hide in banking apps"),
                subtitle = tr(
                    "Остров исчезает и не перехватывает касания, пока открыт банк",
                    "The island vanishes and releases all touches while a banking app is open"
                ),
                checked = s.hideInBankingApps,
                color = Tile.Green,
                onChange = { v ->
                    SettingsRepo.update { copy(hideInBankingApps = v) }
                    BankGuard.clearCache()
                }
            )
        }

        if (detectedApps.isNotEmpty()) {
            SectionHeader(tr("Обнаруженные банки и кошельки", "Detected banking & wallet apps"))
            Group {
                detectedApps.forEachIndexed { i, app ->
                    val isHidden = !app.isExcluded
                    GroupToggle(
                        pos = posOf(i, detectedApps.size),
                        icon = Icons.Rounded.AccountBalance,
                        title = app.label,
                        subtitle = if (isHidden) tr("Остров скрывается при открытии", "Island hides when opened")
                        else tr("Исключено: остров не скрывается", "Excluded: island stays visible"),
                        checked = isHidden,
                        color = Tile.Blue,
                        onChange = { checked ->
                            SettingsRepo.update {
                                if (checked) copy(bankingAppExclusions = bankingAppExclusions - app.packageName)
                                else copy(bankingAppExclusions = bankingAppExclusions + app.packageName)
                            }
                            BankGuard.clearCache()
                        }
                    )
                }
            }
        }

        SectionHeader(tr("Управление службой", "Service controls"))
        Group {
            val tileAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            if (tileAsk) {
                GroupItem(
                    Pos.First, Icons.Rounded.AddBox, tr("Добавить плитку «Остров»", "Add the Island tile"),
                    tr("В шторку быстрых настроек — одно нажатие", "To quick settings — one tap"),
                    color = Tile.Blue, onClick = { addTile(context) },
                ) { Arrow() }
            }
            GroupItem(
                if (tileAsk) Pos.Middle else Pos.First, Icons.Rounded.PowerSettingsNew, tr("Выключить остров сейчас", "Turn the island off now"),
                if (running) tr("Включить обратно — в спец. возможностях или плиткой", "Turn it back on in Accessibility or with the tile")
                else tr("Остров уже выключен", "The island is already off"),
                color = Tile.Orange, onClick = { if (running) Controls.turnOffIsland() },
            )
            GroupItem(
                Pos.Last, Icons.Rounded.Lock, tr("Включить остров", "Turn the island on"),
                tr("Откроются спец. возможности — «Динамический остров» → включить", "Opens Accessibility — Dynamic Island → turn on"),
                color = Tile.Green, onClick = { Perms.openAccessibility(context) },
            ) { Arrow() }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            SectionHeader(tr("Плитка в шторке", "The quick settings tile"))
            InfoText(
                tr(
                    "Откройте шторку целиком, нажмите карандаш и перетащите плитку «Остров» к остальным.",
                    "Open quick settings fully, tap the pencil and drag the Island tile to the others."
                ),
            )
        }
    }
}

/** Android 13+: система сама спрашивает, добавить ли плитку острова в шторку. */
private fun addTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val sm = context.getSystemService(StatusBarManager::class.java) ?: return
    val cn = ComponentName(context, IslandTile::class.java)
    sm.requestAddTileService(cn, context.getString(R.string.tile_label), Icon.createWithResource(context, R.drawable.ic_tab_island), context.mainExecutor) { res ->
        val msg = when (res) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> tr("Плитка добавлена в шторку", "The tile was added to quick settings")
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> tr("Плитка уже есть в шторке", "The tile is already in quick settings")
            else -> null
        }
        if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

/** Пункт «Банк не пускает» — на главной, рядом с «Чтобы остров не закрывался». */
@Composable
internal fun BanksItem(pos: Pos, open: (Screen) -> Unit) {
    GroupItem(pos, Icons.Rounded.AccountBalance, tr("Банк не пускает", "Bank app won't open"),
        tr("Плитка, чтобы выключать остров на время", "A tile to turn the island off for a while"),
        onClick = { open(Screen.BANKS) }, color = Tile.Indigo) { Arrow() }
}
