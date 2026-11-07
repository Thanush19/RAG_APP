package com.ragclient.android.core.designsystem

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Menu
import compose.icons.feathericons.MessageCircle
import compose.icons.feathericons.MoreVertical
import compose.icons.feathericons.Paperclip
import compose.icons.feathericons.RotateCw
import compose.icons.feathericons.Search
import compose.icons.feathericons.Send
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.Upload
import compose.icons.feathericons.X

/**
 * The glyph set, from the Feather pack of
 * `br.com.devsrsouza.compose.icons` (client plan 27). Names say what the
 * control does rather than what the drawing looks like, so a screen never
 * imports an icon pack and the whole set can be re-skinned from one file.
 *
 * Tint them with [AppColors.Icon]; the two glyphs that sit on an emerald
 * fill take `onPrimary` instead.
 */
object AppIcons {

    val Menu: ImageVector = FeatherIcons.Menu
    val Search: ImageVector = FeatherIcons.Search
    val NewChat: ImageVector = FeatherIcons.Edit2
    val Ask: ImageVector = FeatherIcons.MessageCircle
    val Send: ImageVector = FeatherIcons.Send
    val Close: ImageVector = FeatherIcons.X
    val Delete: ImageVector = FeatherIcons.Trash2
    val More: ImageVector = FeatherIcons.MoreVertical
    val Upload: ImageVector = FeatherIcons.Upload
    // Feather has no pushpin; the paperclip is its "attach a file" glyph.
    val Attach: ImageVector = FeatherIcons.Paperclip
    val Retry: ImageVector = FeatherIcons.RotateCw
    val Settings: ImageVector = FeatherIcons.Settings
    val Back: ImageVector = FeatherIcons.ArrowLeft
    val File: ImageVector = FeatherIcons.FileText
    val Forward: ImageVector = FeatherIcons.ChevronRight
}
