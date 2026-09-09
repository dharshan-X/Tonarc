package com.quietrays.tonarc.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
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
    val selectedIndex = resolveActiveTabIndex(currentRoute, routes)

    val animatedIndicatorOffset by animateDpAsState(
        targetValue = calculatePillActiveOffset(selectedIndex),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "FloatingPillActiveIndicatorOffset"
    )

    val totalWidth = (FloatingPillSlotWidth * navItems.size) + (FloatingPillHorizontalPadding * 2)

    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val scope = rememberCoroutineScope()
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
            // Sliding active indicator capsule
            Box(
                modifier = Modifier
                    .offset(x = animatedIndicatorOffset)
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
                    val isSelected = index == selectedIndex
                    val iconRes = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                        item.selectedIconResId
                    } else {
                        item.iconResId
                    }
                    val iconTint = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    }

                    Box(
                        modifier = Modifier
                            .width(FloatingPillSlotWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = 24.dp)
                            ) {
                                val itemRoute = item.screen.route
                                val isSearchTab = itemRoute == Screen.Search.route
                                val isAlreadySelected = latestCurrentRoute == itemRoute

                                if (isSearchTab) {
                                    val now = SystemClock.elapsedRealtime()
                                    val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                                    lastSearchTapTimestamp = now

                                    if (!isAlreadySelected) {
                                        if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                            lastSearchTapTimestamp = 0L
                                            return@clickable
                                        }
                                    }

                                    if (isDoubleTap) {
                                        lastSearchTapTimestamp = 0L
                                        if (isAlreadySelected) {
                                            latestOnSearchIconDoubleTap()
                                        } else {
                                            scope.launch {
                                                delay(160L)
                                                latestOnSearchIconDoubleTap()
                                            }
                                        }
                                    }
                                } else if (!isAlreadySelected) {
                                    lastSearchTapTimestamp = 0L
                                    navController.navigateToTopLevelSafely(itemRoute)
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
