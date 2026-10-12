package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
import wojtoteka.nightdrive.ui.components.CoinIcon
import wojtoteka.nightdrive.ui.components.CoinPill
import wojtoteka.nightdrive.ui.components.NDCard
import wojtoteka.nightdrive.ui.components.NeonButton
import wojtoteka.nightdrive.ui.components.PowerUpGlyph
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.components.ShieldIcon
import wojtoteka.nightdrive.ui.components.isNavFocus
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Radius

@Composable
fun ShopScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    BackHandler { vm.goTo(Destination.MENU) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        ScreenHeader(
            title = s["shop"],
            onBack = { vm.goTo(Destination.MENU) },
            accent = NDColors.Gold,
            trailing = { CoinPill(p.totalCoins) },
        )
        Spacer(Modifier.height(16.dp))
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column {
                    SectionLabel(s["shop_skins"])
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        Modifier.fillMaxWidth().height(210.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(Skins.all, key = { it.id }) { skin ->
                            SkinCard(
                                skin = skin,
                                owned = p.ownedSkins.contains(skin.id),
                                selected = p.selectedSkin == skin.id,
                                affordable = p.totalCoins >= skin.price,
                                onTap = { vm.onSkinTapped(skin.id) },
                            )
                        }
                    }
                }
            }
            item {
                Column {
                    SectionLabel(s["shop_themes"])
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        Modifier.fillMaxWidth().height(210.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(WorldThemes.all, key = { it.id }) { theme ->
                            ThemeCard(
                                theme = theme,
                                owned = p.ownedThemes.contains(theme.id),
                                selected = p.selectedTheme == theme.id,
                                affordable = p.totalCoins >= theme.price,
                                onTap = { vm.onThemeTapped(theme.id) },
                            )
                        }
                    }
                }
            }
            item {
                Column {
                    SectionLabel(s["pw_shield"])
                    Spacer(Modifier.height(10.dp))
                    ShieldSection(p, vm)
                    Spacer(Modifier.height(12.dp))
                    ShieldCapacityCard(p, vm)
                }
            }
            item {
                Column {
                    SectionLabel(s["shop_boosts"])
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        Modifier.fillMaxWidth().height(284.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(Boosts.all, key = { it.id }) { boost ->
                            BoostCard(
                                boost = boost,
                                stock = pendingStock(p, boost),
                                affordable = p.totalCoins >= boost.price,
                                onTap = { vm.buyBoost(boost) },
                            )
                        }
                    }
                }
            }
            item {
                Column {
                    SectionLabel(s["duration_upgrades"])
                    Spacer(Modifier.height(10.dp))
                    DurationAllCard(p, vm)
                    for (type in BoostDurationUpgrade.upgradableTypes) {
                        Spacer(Modifier.height(10.dp))
                        DurationTypeRow(type, p, vm)
                    }
                }
            }
        }
        ScrollHint(
            visible = listState.canScrollForward,
            onClick = {
                scope.launch {
                    val next = (listState.firstVisibleItemIndex + 1)
                        .coerceAtMost(listState.layoutInfo.totalItemsCount - 1)
                    listState.animateScrollToItem(next)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        }
    }
}

/**
 * Floating "more below" pill: gently bobs to catch the eye, fades away at the end of the list,
 * and taps through to the next shop section - so it's obvious the garage scrolls vertically.
 */
@Composable
private fun ScrollHint(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val bob by rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "bob",
    )
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        NDCard(
            shape = Radius.pill,
            borderColor = NDColors.Gold.copy(alpha = 0.55f),
            modifier = Modifier
                .graphicsLayer { translationY = bob * density }
                .clickable { onClick() },
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = NDColors.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(s["shop_more"], style = NDType.LabelSmall, color = NDColors.Gold)
            }
        }
    }
}

private fun pendingStock(p: ProfileSnapshot, boost: Boost): Int = when (boost.type) {
    wojtoteka.nightdrive.domain.PowerUpType.MAGNET -> p.pendingMagnetStarts
    wojtoteka.nightdrive.domain.PowerUpType.SLOWMO -> p.pendingSlowmoStarts
    wojtoteka.nightdrive.domain.PowerUpType.DOUBLE -> p.pendingDoubleStarts
    wojtoteka.nightdrive.domain.PowerUpType.COIN_RUSH -> p.pendingCoinRushStarts
    wojtoteka.nightdrive.domain.PowerUpType.SHIELD -> 0
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = NDType.LabelSmall, color = NDColors.TextMuted)
}

@Composable
private fun ShieldSection(p: ProfileSnapshot, vm: GameViewModel) {
    val s = LocalStrings.current
    val maxed = p.shieldCharges >= p.shieldMax
    val affordable = p.totalCoins >= SHIELD_PRICE
    NDCard(borderColor = NDColors.Cyan.copy(alpha = 0.4f)) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(NDColors.Cyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { ShieldIcon(22, NDColors.Cyan) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s["pw_shield"], style = NDType.Label, color = NDColors.TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(s["shop_shield_desc"], style = NDType.BodyMuted, color = NDColors.TextMuted)
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    if (maxed) s["max"] else "${p.shieldCharges}/${p.shieldMax}",
                    if (maxed) NDColors.Green else NDColors.Cyan,
                )
            }
            if (!maxed) {
                Spacer(Modifier.height(14.dp))
                NeonButton(
                    text = "${s["buy"]} · $SHIELD_PRICE",
                    onClick = { vm.buyShieldCharge() },
                    color = NDColors.Gold,
                    enabled = affordable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One-time permanent purchase: raises the shield charge cap (see [wojtoteka.nightdrive.domain.PlayerProfile.shieldMax]). */
@Composable
private fun ShieldCapacityCard(p: ProfileSnapshot, vm: GameViewModel) {
    val s = LocalStrings.current
    val owned = p.shieldCapacityUpgrade
    val affordable = p.totalCoins >= SHIELD_CAPACITY_PRICE
    NDCard(borderColor = (if (owned) NDColors.Green else NDColors.Violet).copy(alpha = 0.4f)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(NDColors.Violet.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { ShieldIcon(22, NDColors.Violet) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s["shield_capacity"], style = NDType.Label, color = NDColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(s["shield_capacity_d"], style = NDType.BodyMuted, color = NDColors.TextMuted)
            }
            Spacer(Modifier.width(12.dp))
            if (owned) {
                StatusPill(s["owned"], NDColors.Green)
            } else {
                NeonButton(
                    text = "${s["buy"]} · $SHIELD_CAPACITY_PRICE",
                    onClick = { vm.buyShieldCapacityUpgrade() },
                    color = NDColors.Violet,
                    enabled = affordable,
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

/** The "+% duration on ALL power-ups" ladder: +20% → +50% → +100%, stacking with per-type tiers. */
@Composable
private fun DurationAllCard(p: ProfileSnapshot, vm: GameViewModel) {
    val s = LocalStrings.current
    val level = p.boostDurationLevelAll
    val nextPrice = BoostDurationUpgrade.nextPriceAll(level)
    val maxed = nextPrice == null
    NDCard(borderColor = (if (maxed) NDColors.Green else NDColors.Magenta).copy(alpha = 0.4f)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(NDColors.Magenta.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text("%", style = NDType.Heading, color = NDColors.Magenta) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s["boost_duration_all"], style = NDType.Label, color = NDColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(s["boost_duration_all_d"], style = NDType.BodyMuted, color = NDColors.TextMuted)
            }
            Spacer(Modifier.width(12.dp))
            DurationTierAction(
                level = level,
                nextPrice = nextPrice,
                affordable = nextPrice != null && p.totalCoins >= nextPrice,
                accent = NDColors.Magenta,
                onBuy = { vm.buyBoostDurationAll() },
            )
        }
    }
}

/** One power-up's personal duration ladder, with the combined (all + own) bonus shown below. */
@Composable
private fun DurationTypeRow(type: PowerUpType, p: ProfileSnapshot, vm: GameViewModel) {
    val s = LocalStrings.current
    val color = Color(type.color)
    val level = durationLevel(p, type)
    val nextPrice = BoostDurationUpgrade.nextPriceEach(level)
    val totalPercent = BoostDurationUpgrade.percent(p.boostDurationLevelAll) + BoostDurationUpgrade.percent(level)
    NDCard(borderColor = color.copy(alpha = 0.35f)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { PowerUpGlyph(type, 20) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s[type.labelKey], style = NDType.Label, color = NDColors.TextPrimary)
                if (totalPercent > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text("${s["total_bonus"]}: +$totalPercent%", style = NDType.BodyMuted, color = NDColors.TextMuted)
                }
            }
            Spacer(Modifier.width(12.dp))
            DurationTierAction(
                level = level,
                nextPrice = nextPrice,
                affordable = nextPrice != null && p.totalCoins >= nextPrice,
                accent = color,
                onBuy = { vm.buyBoostDurationFor(type) },
            )
        }
    }
}

/** Right-side action for a duration ladder: the next tier as a buy button, or a "Max" pill. */
@Composable
private fun DurationTierAction(level: Int, nextPrice: Int?, affordable: Boolean, accent: Color, onBuy: () -> Unit) {
    val s = LocalStrings.current
    if (nextPrice == null) {
        StatusPill(s["max"], NDColors.Green)
    } else {
        NeonButton(
            text = "+${BoostDurationUpgrade.percent(level + 1)}% · $nextPrice",
            onClick = onBuy,
            color = accent,
            enabled = affordable,
        )
    }
}

@Composable
private fun BoostCard(boost: Boost, stock: Int, affordable: Boolean, onTap: () -> Unit) {
    val s = LocalStrings.current
    val color = Color(boost.type.color)
    val accent = if (affordable) color else Color.White.copy(alpha = 0.15f)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val navFocused = isNavFocus(focused)
    NDCard(
        modifier = Modifier.width(168.dp).clickable(interaction, ripple(color = accent)) { onTap() },
        borderColor = if (navFocused) Color.White else accent.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(Radius.small)
                    .background(Color(0x33101228)),
                contentAlignment = Alignment.Center,
            ) { PowerUpGlyph(boost.type, size = 40) }
            Spacer(Modifier.height(12.dp))
            // Fixed line counts + an always-reserved stock slot keep all four cards the same
            // height, whatever the description length or stock state - the row reads as one shelf.
            Text(
                s[boost.nameKey], style = NDType.Label, color = NDColors.TextPrimary,
                textAlign = TextAlign.Center, minLines = 1, maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                s[boost.descKey], style = NDType.Caption, color = NDColors.TextMuted,
                textAlign = TextAlign.Center, minLines = 3, maxLines = 3,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(15)
                Spacer(Modifier.width(6.dp))
                Text(boost.price.toString(), style = NDType.Label, color = if (affordable) NDColors.Gold else NDColors.TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                if (stock > 0) StatusPill("${s["in_stock"]}: $stock", NDColors.Green)
            }
        }
    }
}

@Composable
private fun SkinCard(skin: Skin, owned: Boolean, selected: Boolean, affordable: Boolean, onTap: () -> Unit) {
    val s = LocalStrings.current
    val accent = when {
        selected -> NDColors.Green
        owned -> NDColors.Cyan
        affordable -> NDColors.Gold
        else -> Color.White.copy(alpha = 0.15f)
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val navFocused = isNavFocus(focused)
    NDCard(
        modifier = Modifier.width(168.dp).clickable(
            interaction, ripple(color = accent), enabled = selected.not() || owned,
        ) { onTap() },
        borderColor = if (navFocused) Color.White else accent.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(Radius.small)
                    .background(Color(0x33101228)),
                contentAlignment = Alignment.Center,
            ) {
                SkinPreview(skin.bodyColor, skin.accentColor, Modifier.size(width = 118.dp, height = 66.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(s[skin.nameKey], style = NDType.Label, color = NDColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            when {
                selected -> StatusPill(s["selected"], NDColors.Green)
                owned -> StatusPill(s["equip"], NDColors.Cyan)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(15)
                    Spacer(Modifier.width(6.dp))
                    Text(skin.price.toString(), style = NDType.Label, color = if (affordable) NDColors.Gold else NDColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: WorldTheme, owned: Boolean, selected: Boolean, affordable: Boolean, onTap: () -> Unit) {
    val s = LocalStrings.current
    val accent = when {
        selected -> NDColors.Green
        owned -> NDColors.Cyan
        affordable -> NDColors.Gold
        else -> Color.White.copy(alpha = 0.15f)
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val navFocused = isNavFocus(focused)
    NDCard(
        modifier = Modifier.width(168.dp).clickable(
            interaction, ripple(color = accent), enabled = selected.not() || owned,
        ) { onTap() },
        borderColor = if (navFocused) Color.White else accent.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(Radius.small),
            ) {
                ThemePreview(theme, Modifier.fillMaxWidth().height(84.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(s[theme.nameKey], style = NDType.Label, color = NDColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            when {
                selected -> StatusPill(s["selected"], NDColors.Green)
                owned -> StatusPill(s["equip"], NDColors.Cyan)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(15)
                    Spacer(Modifier.width(6.dp))
                    Text(theme.price.toString(), style = NDType.Label, color = if (affordable) NDColors.Gold else NDColors.TextMuted)
                }
            }
        }
    }
}

/** Miniature of the world in the theme's colours: sky, retro sun, horizon and grid road. */
@Composable
private fun ThemePreview(theme: WorldTheme, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val horizon = h * 0.56f
        // sky
        drawRect(
            Brush.verticalGradient(listOf(Color(theme.skyTop), Color(theme.skyBottom)), startY = 0f, endY = horizon),
            size = Size(w, horizon),
        )
        // sun sitting on the horizon
        val sunR = w * 0.15f
        val sunC = Offset(w / 2f, horizon - sunR * 0.35f)
        drawCircle(
            Brush.verticalGradient(
                listOf(Color(theme.sunTop), Color(theme.sunMid), Color(theme.sunLow)),
                startY = sunC.y - sunR, endY = sunC.y + sunR,
            ),
            radius = sunR, center = sunC,
        )
        // road
        drawRect(
            Brush.verticalGradient(listOf(Color(theme.roadTop), Color(theme.roadBottom)), startY = horizon, endY = h),
            topLeft = Offset(0f, horizon), size = Size(w, h - horizon),
        )
        // converging grid rails
        val grid = Color(theme.grid).copy(alpha = 0.45f)
        for (i in -3..3) {
            drawLine(grid, Offset(w / 2f + i * w * 0.07f, horizon), Offset(w / 2f + i * w * 0.30f, h), strokeWidth = w * 0.008f)
        }
        // neon horizon line on top
        drawLine(Color(theme.horizonLine), Offset(0f, horizon), Offset(w, horizon), strokeWidth = h * 0.03f)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier
            .clip(Radius.pill)
            .background(color.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), Radius.pill)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(text, style = NDType.LabelSmall, color = color)
    }
}

/** A small wireframe car preview matching the in-game art style. */
@Composable
private fun SkinPreview(bodyColor: Long, accentColor: Long, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val body = Color(bodyColor); val acc = Color(accentColor)
        val sw = w * 0.02f
        // far face (upper-right)
        val fx0 = w * 0.42f; val fx1 = w * 0.90f; val fy0 = h * 0.24f; val fy1 = h * 0.56f
        // near face (lower-left)
        val nx0 = w * 0.12f; val nx1 = w * 0.66f; val ny0 = h * 0.44f; val ny1 = h * 0.78f
        // connecting edges
        drawLine(acc, Offset(nx0, ny0), Offset(fx0, fy0), sw, StrokeCap.Round)
        drawLine(acc, Offset(nx1, ny0), Offset(fx1, fy0), sw, StrokeCap.Round)
        drawLine(acc, Offset(nx1, ny1), Offset(fx1, fy1), sw, StrokeCap.Round)
        drawLine(acc, Offset(nx0, ny1), Offset(fx0, fy1), sw, StrokeCap.Round)
        // far body
        drawRect(acc, Offset(fx0, fy0), Size(fx1 - fx0, fy1 - fy0), style = Stroke(sw))
        // near body
        drawRect(body, Offset(nx0, ny0), Size(nx1 - nx0, ny1 - ny0), style = Stroke(sw * 1.3f))
        // wheels
        val r = h * 0.12f
        drawCircle(body, r, Offset(nx0 + r, ny1), style = Stroke(sw))
        drawCircle(body, r, Offset(nx1 - r, ny1), style = Stroke(sw))
    }
}
