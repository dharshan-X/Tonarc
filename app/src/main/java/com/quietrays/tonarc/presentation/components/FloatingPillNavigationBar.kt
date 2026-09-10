package com.quietrays.tonarc.presentation.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quietrays.tonarc.BottomNavItem
import com.quietrays.tonarc.presentation.navigation.Screen
import com.quietrays.tonarc.presentation.navigation.navigateToTopLevelSafely
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val FloatingPillContentHeight = 54.dp
val FloatingPillBottomMargin = 8.dp
private val FloatingPillSlotWidth = 66.dp
private val FloatingPillHorizontalPadding = 5.dp
private val FloatingPillIndicatorHeight = 42.dp
private val FloatingPillIndicatorWidth = 58.dp

internal fun resolveFloatingPillContainerHeight(systemNavBarInset: Dp): Dp =
    FloatingPillContentHeight + FloatingPillBottomMargin + systemNavBarInset

internal fun calculatePillActiveOffset(
    selectedIndex: Int,
    slotWidth: Dp = FloatingPillSlotWidth,
    horizontalPadding: Dp = FloatingPillHorizontalPadding,
    indicatorWidth: Dp = FloatingPillIndicatorWidth
): Dp {
    val indicatorPadding = (slotWidth - indicatorWidth) / 2
    return horizontalPadding + (slotWidth * selectedIndex.coerceAtLeast(0)) + indicatorPadding
}

internal fun resolveActiveTabIndex(currentRoute: String?, routes: List<String>): Int {
    if (currentRoute == null) return 0
    val index = routes.indexOf(currentRoute)
    return if (index >= 0) index else 0
}

@Composable
fun FloatingPillNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    val routes = remember(navItems) { navItems.map { it.screen.route } }
    val routeIndex = resolveActiveTabIndex(currentRoute, routes)
    var targetIndex by remember { mutableIntStateOf(routeIndex) }

    // Synchronize targetIndex if route changes externally (back gesture, etc.)
    LaunchedEffect(routeIndex) {
        targetIndex = routeIndex
    }

    val animatedIndicatorOffset by animateDpAsState(
        targetValue = calculatePillActiveOffset(targetIndex),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "FloatingPillActiveIndicatorOffset"
    )

    val totalWidth = (FloatingPillSlotWidth * navItems.size) + (FloatingPillHorizontalPadding * 2)

    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    var lastSearchTapTimestamp by remember { mutableLongStateOf(0L) }

    Surface(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .height(FloatingPillContentHeight)
                .width(totalWidth),
            contentAlignment = Alignment.CenterStart
        ) {
            // Sliding active indicator capsule - rendered via graphicsLayer to avoid recompositions
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = animatedIndicatorOffset.toPx()
                    }
                    .size(width = FloatingPillIndicatorWidth, height = FloatingPillIndicatorHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )

            // Destination icon slots
            Row(
                modifier = Modifier
                    .height(FloatingPillContentHeight)
                    .padding(horizontal = FloatingPillHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = index == targetIndex
                    val iconRes = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                        item.selectedIconResId
                    } else {
                        item.iconResId
                    }
                    val targetTint = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    }
                    val iconTint by animateColorAsState(
                        targetValue = targetTint,
                        animationSpec = tween(durationMillis = 180),
                        label = "FloatingPillIconTint_${item.screen.route}"
                    )

                    Box(
                        modifier = Modifier
                            .width(FloatingPillSlotWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .semantics {
                                role = Role.Tab
                                this.selected = isSelected
                            }
                            .clickable(
                                interactionSource = remember(item.screen.route) { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = 24.dp),
                                role = Role.Tab
                            ) {
                                val itemRoute = item.screen.route
                                val isSearchTab = itemRoute == Screen.Search.route
                                val isNavAlreadyOnRoute = latestCurrentRoute == itemRoute
                                val isPendingOrSelected = isNavAlreadyOnRoute || (targetIndex == index)

                                if (isSearchTab) {
                                    val now = SystemClock.elapsedRealtime()
                                    val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                                    lastSearchTapTimestamp = now

                                    if (!isPendingOrSelected) {
                                        targetIndex = index
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                            targetIndex = routeIndex
                                            lastSearchTapTimestamp = 0L
                                            return@clickable
                                        }
                                    }

                                    if (isDoubleTap) {
                                        lastSearchTapTimestamp = 0L
                                        if (isNavAlreadyOnRoute) {
                                            latestOnSearchIconDoubleTap()
                                        } else {
                                            scope.launch {
                                                delay(160L)
                                                latestOnSearchIconDoubleTap()
                                            }
                                        }
                                    }
                                } else if (!isPendingOrSelected) {
                                    targetIndex = index
                                    lastSearchTapTimestamp = 0L
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                        targetIndex = routeIndex
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = item.label,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
