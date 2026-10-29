package com.closecode.ui.ide

import org.junit.Test

class CcIconsTest {
    @Test
    fun everyIconPathParses() {
        listOf(
            CcIcons.Logo,
            CcIcons.Drawer,
            CcIcons.Save,
            CcIcons.Undo,
            CcIcons.Redo,
            CcIcons.Play,
            CcIcons.More,
            CcIcons.Chevron,
            CcIcons.CloudDownload,
            CcIcons.Check,
            CcIcons.Clock,
            CcIcons.History,
            CcIcons.Cross,
            CcIcons.ChevronLeft,
            CcIcons.ChevronRight,
            CcIcons.Sun,
            CcIcons.Moon,
            CcIcons.Android,
            CcIcons.Zap,
            CcIcons.Keyboard,
            CcIcons.Lock,
            CcIcons.Book,
            CcIcons.Github,
            CcIcons.Up,
            CcIcons.Down,
            CcIcons.Copy,
            CcIcons.Warning,
            CcIcons.ErrorCircle,
            CcIcons.AlertCircle,
            CcIcons.Stack,
            CcIcons.Bulb,
            CcIcons.Close,
            CcIcons.Search,
            CcIcons.Bug,
            CcIcons.Trash,
            CcIcons.Phone,
            CcIcons.Tablet,
            CcIcons.Monitor,
            CcIcons.ChevronUp,
            CcIcons.Terminal,
            CcIcons.Code,
            CcIcons.Layers,
            CcIcons.Image,
            CcIcons.FilePlus,
            CcIcons.FolderPlus,
            CcIcons.Document,
            CcIcons.FolderOpen,
            CcIcons.Refresh,
            CcIcons.CollapseAll,
            CcIcons.Branch,
            CcIcons.External,
            CcIcons.Plus,
            CcIcons.Download,
            CcIcons.Upload,
            CcIcons.Archive,
            CcIcons.Filter,
            CcIcons.Sliders,
        ).forEach { icon ->
            check(icon.defaultWidth.value > 0f)
            check(icon.defaultHeight.value > 0f)
        }
    }
}
