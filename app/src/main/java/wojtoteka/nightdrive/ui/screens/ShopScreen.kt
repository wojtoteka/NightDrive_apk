package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.domain.Boost
import wojtoteka.nightdrive.domain.BoostDurationUpgrade
import wojtoteka.nightdrive.domain.Boosts
import wojtoteka.nightdrive.domain.PowerUpType
import wojtoteka.nightdrive.domain.SHIELD_CAPACITY_PRICE
import wojtoteka.nightdrive.domain.SHIELD_PRICE
import wojtoteka.nightdrive.domain.Skin
import wojtoteka.nightdrive.domain.Skins
import wojtoteka.nightdrive.domain.WorldTheme
import wojtoteka.nightdrive.domain.WorldThemes
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.ProfileSnapshot
import wojtoteka.nightdrive.ui.components.BuyButton
import wojtoteka.nightdrive.ui.components.CoinIcon
import wojtoteka.nightdrive.ui.components.CoinReadout
import wojtoteka.nightdrive.ui.components.Overline
import wojtoteka.nightdrive.ui.components.Panel
import wojtoteka.nightdrive.ui.components.PowerUpGlyph
import wojtoteka.nightdrive.ui.components.Rule
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.components.ShieldGlyph
import wojtoteka.nightdrive.ui.components.Tag
import wojtoteka.nightdrive.ui.components.isNavFocus
import wojtoteka.nightdrive.ui.theme.Corner
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Space

private enum class ShopTab(val key: String) {
    SKINS("tab_skins"), THEMES("tab_themes"), SHIELD("tab_shield"), BOOSTS("tab_boosts"), UPGRADES("tab_upgrades")
}

@Composable
fun ShopScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler { vm.goTo(Destination.MENU) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.gutter, vertical = 14.dp),
    ) {
        ScreenHeader(
            title = s["shop"],
            onBack = { vm.goTo(Destination.MENU) },
            backLabel = s["back"],
            trailing = { Panel { CoinReadout(p.totalCoins, Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) } },
        )
        Spacer(Modifier.height(14.dp))
        TabBar(ShopTab.entries.map { s[it.key] }, tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        Crossfade(ShopTab.entries[tab], Modifier.fillMaxWidth().weight(1f), tween(180), label = "tab") { t ->
            when (t) {
                ShopTab.SKINS -> CardRow(Skins.all, { it.id }) { skin ->
                    ItemCard(
                        name = s[skin.nameKey],
                        price = skin.price,
                        owned = p.ownedSkins.contains(skin.id),
                        selected = p.selectedSkin == skin.id,
                        affordable = p.totalCoins >= skin.price,
                        onTap = { vm.onSkinTapped(skin.id) },
                    ) { SkinPreview(skin, Modifier.size(width = 120.dp, height = 68.dp)) }
                }
                ShopTab.THEMES -> CardRow(WorldThemes.all, { it.id }) { theme ->
                    ItemCard(
                        name = s[theme.nameKey],
                        price = theme.price,
                        owned = p.ownedThemes.contains(theme.id),
                        selected = p.selectedTheme == theme.id,
                        affordable = p.totalCoins >= theme.price,
                        onTap = { vm.onThemeTapped(theme.id) },
                    ) { ThemePreview(theme, Modifier.fillMaxSize()) }
                }
                ShopTab.SHIELD -> ShieldTab(p, vm)
                ShopTab.BOOSTS -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (boost in Boosts.all) {
                        BoostCard(boost, pendingStock(p, boost), p.totalCoins >= boost.price, Modifier.weight(1f)) { vm.buyBoost(boost) }
                    }
                }
                ShopTab.UPGRADES -> UpgradesTab(p, vm)
            }
        }
    }
}

/** Underlined text tabs: active tab in white with a 3dp accent bar, the rest dimmed. */
@Composable
private fun TabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Rule(Modifier.align(Alignment.BottomStart))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val nav = isNavFocus(focused)
                Box(
                    Modifier
                        .clickable(interaction, null) { onSelect(i) }
                        .background(if (nav) NDColors.PanelRaised else Color.Transparent)
                        .drawBehind {
                            if (active) {
                                val h = 3.dp.toPx()
                                drawRect(NDColors.Accent, Offset(0f, size.height - h), Size(size.width, h))
                            }
                        }
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.uppercase(),
                        style = NDType.Button,
                        color = if (active) NDColors.Text else NDColors.TextFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> CardRow(items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    LazyRow(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        items(items, key = key) { card(it) }
    }
}

/** Clickable card shell shared by every shelf item: lifts on press, white ring on D-pad focus. */
@Composable
private fun CardShell(
    onTap: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val nav = isNavFocus(focused)
    Box(
        modifier
            .width(184.dp)
            .fillMaxHeight()
            .clip(Corner.panel)
            .background(if (pressed) NDColors.PanelRaised else NDColors.Panel)
            .border(
                BorderStroke(if (nav || selected) 2.dp else 1.dp, if (nav) Color.White else if (selected) NDColors.Accent else Color.Transparent),
                Corner.panel,
            )
            .clickable(interaction, null, onClick = onTap),
    ) { content() }
}

@Composable
private fun ItemCard(
    name: String,
    price: Int,
    owned: Boolean,
    selected: Boolean,
    affordable: Boolean,
    onTap: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val s = LocalStrings.current
    CardShell(onTap, selected) {
        Column(Modifier.padding(10.dp).fillMaxSize()) {
            PreviewWell(Modifier.fillMaxWidth().weight(1f)) { preview() }
            Spacer(Modifier.height(12.dp))
            Text(name.uppercase(), style = NDType.Heading, color = NDColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 4.dp).height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    selected -> Tag(s["selected"], filled = true)
                    owned -> Overline(s["equip"], color = NDColors.TextDim)
                    else -> {
                        CoinIcon(15)
                        Spacer(Modifier.width(6.dp))
                        Text(price.toString(), style = NDType.ReadoutSmall, color = if (affordable) NDColors.Text else NDColors.TextFaint)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun PreviewWell(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(modifier.clip(Corner.control).background(NDColors.Inset), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun BoostCard(boost: Boost, stock: Int, affordable: Boolean, modifier: Modifier, onBuy: () -> Unit) {
    val s = LocalStrings.current
    Panel(modifier.fillMaxHeight()) {
        Column(Modifier.padding(10.dp).fillMaxSize()) {
            PreviewWell(Modifier.fillMaxWidth().weight(1f)) {
                PowerUpGlyph(boost.type, 52)
                if (stock > 0) {
                    Tag("${s["in_stock"]} $stock", NDColors.Positive, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(s[boost.nameKey].uppercase(), style = NDType.Heading, color = NDColors.Text, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(s[boost.descKey], style = NDType.Caption, color = NDColors.TextDim, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            BuyButton(boost.price, onBuy, Modifier.fillMaxWidth(), enabled = affordable, prefix = s["buy"].uppercase())
        }
    }
}

// ---- Shield ----------------------------------------------------------------------------------

@Composable
private fun ShieldTab(p: ProfileSnapshot, vm: GameViewModel) {
    val s = LocalStrings.current
    val maxed = p.shieldCharges >= p.shieldMax
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.padding(18.dp).fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShieldGlyph(Color(PowerUpType.SHIELD.color), 26.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s["pw_shield"].uppercase(), style = NDType.Heading, color = NDColors.Text)
                        Text(s["shop_shield_desc"], style = NDType.Caption, color = NDColors.TextDim)
                    }
                }
                Spacer(Modifier.weight(1f))
                Overline(s["shield_charges"])
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.shieldCharges}/${p.shieldMax}", style = NDType.Readout, color = NDColors.Text)
                    Spacer(Modifier.width(16.dp))
                    Pips(p.shieldCharges, p.shieldMax, Color(PowerUpType.SHIELD.color), Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                if (maxed) {
                    Tag(s["max"], NDColors.Positive)
                } else {
                    BuyButton(
                        SHIELD_PRICE, { vm.buyShieldCharge() }, Modifier.fillMaxWidth(),
                        enabled = p.totalCoins >= SHIELD_PRICE, prefix = "+1",
                    )
                }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight(), edge = if (p.shieldCapacityUpgrade) NDColors.Accent else null) {
            Column(Modifier.padding(18.dp).fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShieldGlyph(NDColors.Text, 26.dp, plus = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s["shield_capacity"].uppercase(), style = NDType.Heading, color = NDColors.Text)
                        Text(s["shield_capacity_d"], style = NDType.Caption, color = NDColors.TextDim)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (p.shieldCapacityUpgrade) {
                    Tag(s["owned"], filled = true)
                } else {
                    BuyButton(
                        SHIELD_CAPACITY_PRICE, { vm.buyShieldCapacityUpgrade() }, Modifier.fillMaxWidth(),
                        enabled = p.totalCoins >= SHIELD_CAPACITY_PRICE, prefix = s["buy"].uppercase(),
                    )
                }
            }
        }
    }
}

/** Row of square pips, [filled] of [total] lit. */
@Composable
private fun Pips(filled: Int, total: Int, color: Color, modifier: Modifier = Modifier, pip: Dp = 10.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.size(pip).background(if (i < filled) color else Color.Transparent)
                    .border(BorderStroke(1.dp, if (i < filled) color else NDColors.LineStrong)),
            )
        }
    }
}

// ---- Duration upgrades -----------------------------------------------------------------------

@Composable
private fun UpgradesTab(p: ProfileSnapshot, vm: GameViewModel) {
    val s = LocalStrings.current
    Panel(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            UpgradeRow(
                icon = { Text("%", style = NDType.Title, color = NDColors.Accent) },
                title = s["boost_duration_all"],
                detail = s["boost_duration_all_d"],
                level = p.boostDurationLevelAll,
                nextPrice = BoostDurationUpgrade.nextPriceAll(p.boostDurationLevelAll),
                coins = p.totalCoins,
                onBuy = { vm.buyBoostDurationAll() },
            )
            for (type in BoostDurationUpgrade.upgradableTypes) {
                Rule()
                val level = durationLevel(p, type)
                val total = BoostDurationUpgrade.percent(p.boostDurationLevelAll) + BoostDurationUpgrade.percent(level)
                UpgradeRow(
                    icon = { PowerUpGlyph(type, 30) },
                    title = s[type.labelKey],
                    detail = if (total > 0) "${s["total_bonus"]} +$total%" else null,
                    level = level,
                    nextPrice = BoostDurationUpgrade.nextPriceEach(level),
                    coins = p.totalCoins,
                    onBuy = { vm.buyBoostDurationFor(type) },
                )
            }
        }
    }
}

@Composable
private fun UpgradeRow(
    icon: @Composable () -> Unit,
    title: String,
    detail: String?,
    level: Int,
    nextPrice: Int?,
    coins: Int,
    onBuy: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = NDType.Heading, color = NDColors.Text)
            if (detail != null) Text(detail, style = NDType.Caption, color = NDColors.TextDim, maxLines = 2)
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Overline("${s["tier"]} $level/${BoostDurationUpgrade.MAX_LEVEL}")
            Spacer(Modifier.height(5.dp))
            Pips(level, BoostDurationUpgrade.MAX_LEVEL, NDColors.Accent, pip = 8.dp)
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(150.dp), contentAlignment = Alignment.CenterEnd) {
            if (nextPrice == null) {
                Tag(s["max"], NDColors.Positive)
            } else {
                BuyButton(
                    nextPrice, onBuy, Modifier.fillMaxWidth(), enabled = coins >= nextPrice,
                    prefix = "+${BoostDurationUpgrade.percent(level + 1)}%",
                )
            }
        }
    }
}

private fun durationLevel(p: ProfileSnapshot, type: PowerUpType): Int = when (type) {
    PowerUpType.MAGNET -> p.boostDurationLevelMagnet
    PowerUpType.SLOWMO -> p.boostDurationLevelSlowmo
    PowerUpType.DOUBLE -> p.boostDurationLevelDouble
    PowerUpType.COIN_RUSH -> p.boostDurationLevelCoinRush
    PowerUpType.SHIELD -> 0
}

private fun pendingStock(p: ProfileSnapshot, boost: Boost): Int = when (boost.type) {
    PowerUpType.MAGNET -> p.pendingMagnetStarts
    PowerUpType.SLOWMO -> p.pendingSlowmoStarts
    PowerUpType.DOUBLE -> p.pendingDoubleStarts
    PowerUpType.COIN_RUSH -> p.pendingCoinRushStarts
    PowerUpType.SHIELD -> 0
}

// ---- Previews --------------------------------------------------------------------------------

/** Miniature of the world in the theme's colours: sky, retro sun, horizon and grid road. */
@Composable
private fun ThemePreview(theme: WorldTheme, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val horizon = h * 0.56f
        drawRect(
            Brush.verticalGradient(listOf(Color(theme.skyTop), Color(theme.skyBottom)), startY = 0f, endY = horizon),
            size = Size(w, horizon),
        )
        val sunR = h * 0.26f
        val sunC = Offset(w / 2f, horizon - sunR * 0.35f)
        drawCircle(
            Brush.verticalGradient(
                listOf(Color(theme.sunTop), Color(theme.sunMid), Color(theme.sunLow)),
                startY = sunC.y - sunR, endY = sunC.y + sunR,
            ),
            radius = sunR, center = sunC,
        )
        drawRect(
            Brush.verticalGradient(listOf(Color(theme.roadTop), Color(theme.roadBottom)), startY = horizon, endY = h),
            topLeft = Offset(0f, horizon), size = Size(w, h - horizon),
        )
        val grid = Color(theme.grid).copy(alpha = 0.45f)
        for (i in -4..4) {
            drawLine(grid, Offset(w / 2f + i * w * 0.07f, horizon), Offset(w / 2f + i * w * 0.30f, h), strokeWidth = 1.dp.toPx())
        }
        drawLine(Color(theme.horizonLine), Offset(0f, horizon), Offset(w, horizon), strokeWidth = 1.5.dp.toPx())
    }
}

/** Wireframe car in the skin's colours - same construction as the in-game car. */
@Composable
private fun SkinPreview(skin: Skin, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val body = Color(skin.bodyColor); val acc = Color(skin.accentColor)
        val sw = 1.6.dp.toPx()
        val fx0 = w * 0.42f; val fx1 = w * 0.90f; val fy0 = h * 0.18f; val fy1 = h * 0.52f
        val nx0 = w * 0.10f; val nx1 = w * 0.66f; val ny0 = h * 0.40f; val ny1 = h * 0.76f
        drawLine(acc, Offset(nx0, ny0), Offset(fx0, fy0), sw, StrokeCap.Round)
        drawLine(acc, Offset(nx1, ny0), Offset(fx1, fy0), sw, StrokeCap.Round)
        drawLine(acc, Offset(nx1, ny1), Offset(fx1, fy1), sw, StrokeCap.Round)
        drawLine(acc, Offset(nx0, ny1), Offset(fx0, fy1), sw, StrokeCap.Round)
        drawRect(acc, Offset(fx0, fy0), Size(fx1 - fx0, fy1 - fy0), style = Stroke(sw))
        drawRect(body, Offset(nx0, ny0), Size(nx1 - nx0, ny1 - ny0), style = Stroke(sw * 1.3f))
        val r = h * 0.12f
        drawCircle(body, r, Offset(nx0 + r * 1.2f, ny1), style = Stroke(sw))
        drawCircle(body, r, Offset(nx1 - r * 1.2f, ny1), style = Stroke(sw))
    }
}
