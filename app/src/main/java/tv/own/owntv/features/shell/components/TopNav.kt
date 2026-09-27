package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * EXPERIMENTAL Netflix-style horizontal top navigation, replacing the vertical [Sidebar].
 *
 * Layout: profile avatar (left) · the section tabs, centred · a search button (right). The active tab
 * is white + bold with a red underline; the rest are the Netflix secondary grey. D-pad moves left/
 * right between tabs and DOWN into the content below — the reverse of the old left rail, which is why
 * [tv.own.owntv.ui.components.trapVerticalFocusExit] now lets UP escape back here.
 *
 * The same [onSelect] / [selectedItemFocusRequester] plumbing the Sidebar used is kept, so the rest
 * of the shell is unchanged: entering the nav from below redirects focus to the selected tab.
 */
@Composable
fun TopNav(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    visibleSections: Set<MainSection>,
    profileName: String,
    onSwitchProfile: () -> Unit,
    onSearchClick: () -> Unit,
    selectedItemFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    val tabs = MainSection.browseOrder.filter { it in visibleSections }
    // Search maps onto Home for the "which tab is lit" question (Search has no tab of its own), and
    // Settings lives behind More — mirror the Sidebar's focusSection fallback.
    val activeSection = when {
        selected == MainSection.SEARCH -> MainSection.HOME
        selected == MainSection.SETTINGS || selected == MainSection.MORE -> MainSection.MORE
        else -> selected
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.Black)
            // Redirect every entry from below onto the active tab, and report focus to the shell —
            // mirrors the old Sidebar so the rest of the focus model is unchanged.
            .onFocusChanged {
                val entered = it.hasFocus && !hasFocus
                hasFocus = it.hasFocus
                if (it.hasFocus) onFocused()
                if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
            }
            .focusGroup()
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Profile avatar — a simple initial disc; OK switches profile.
        FocusableSurface(
            onClick = onSwitchProfile,
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    profileName.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                )
            }
        }

        Spacer(Modifier.width(28.dp))

        // Centred tab strip.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { section ->
                val isActive = section == activeSection
                FocusableSurface(
                    onClick = { onSelect(section) },
                    selected = isActive,
                    shape = RoundedCornerShape(8.dp),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    selectedContainerColor = Color.Transparent,
                    showFocusBorder = false,
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .then(
                            if (activeSection == section) Modifier.focusRequester(selectedItemFocusRequester)
                            else Modifier
                        ),
                ) { focused ->
                    // IntrinsicSize.Max makes the column exactly as wide as the label, so the
                    // underline's fillMaxWidth traces the text rather than stretching the whole bar.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(IntrinsicSize.Max),
                    ) {
                        Text(
                            stringResourceSection(section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isActive || focused) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                focused -> Color.White
                                isActive -> Color.White
                                else -> colors.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                        // Red underline marks the active/focused tab; a transparent one reserves the
                        // 3dp so the labels never shift vertically as focus moves between tabs.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isActive || focused) colors.primary else Color.Transparent),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(28.dp))

        // Search.
        FocusableSurface(
            onClick = onSearchClick,
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) { focused ->
            OwnTVIcon(
                icon = OwnTVIcon.SEARCH,
                tint = if (focused) colors.primary else colors.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    // Report focus for the shell's layer tracking.
    // (onFocused wired via the container in OwnTVShell.)
}

/** Section tab label. Kept local so the whole nav is self-contained. */
@Composable
private fun stringResourceSection(section: MainSection): String =
    androidx.compose.ui.res.stringResource(section.labelRes)
