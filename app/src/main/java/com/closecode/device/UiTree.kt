package com.closecode.device

import java.util.Locale

data class UiNode(
    val text: String?,
    val description: String?,
    val id: String?,
    val className: String?,
    val bounds: List<Int>,
    val clickable: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val scrollable: Boolean,
    val visible: Boolean,
    val password: Boolean,
    val depth: Int,
) {
    val centerX: Int get() = if (bounds.size == 4) (bounds[0] + bounds[2]) / 2 else 0
    val centerY: Int get() = if (bounds.size == 4) (bounds[1] + bounds[3]) / 2 else 0
}

data class UiSnapshot(
    val packageName: String,
    val activity: String?,
    val width: Int,
    val height: Int,
    val nodes: List<UiNode>,
    val truncated: Boolean,
) {
    fun find(selector: UiSelector): UiNode? =
        nodes.filter { selector.matches(it) }.getOrNull(selector.index.coerceAtLeast(0))
}

data class UiSelector(
    val text: String? = null,
    val textContains: String? = null,
    val id: String? = null,
    val description: String? = null,
    val index: Int = 0,
) {
    fun matches(node: UiNode): Boolean {
        if (!node.visible) return false
        if (text != null && node.text != text) return false
        if (textContains != null &&
            !node.text.orEmpty().contains(textContains, ignoreCase = true)
        ) {
            return false
        }
        if (id != null && !matchesId(node.id, id)) return false
        if (description != null && node.description != description) return false
        return text != null || textContains != null || id != null || description != null
    }

    companion object {
        fun parse(arguments: List<String>): UiSelector? {
            var text: String? = null
            var textContains: String? = null
            var id: String? = null
            var description: String? = null
            var index = 0
            var i = 0
            while (i < arguments.size) {
                when (arguments[i].lowercase(Locale.US)) {
                    "--text" -> {
                        text = arguments.getOrNull(++i) ?: return null
                    }
                    "--text-contains", "--contains" -> {
                        textContains = arguments.getOrNull(++i) ?: return null
                    }
                    "--id" -> {
                        id = arguments.getOrNull(++i) ?: return null
                    }
                    "--desc", "--description" -> {
                        description = arguments.getOrNull(++i) ?: return null
                    }
                    "--index" -> {
                        index = arguments.getOrNull(++i)?.toIntOrNull() ?: return null
                    }
                    else -> {
                        if (text != null) return null
                        text = arguments[i]
                    }
                }
                i++
            }
            if (text == null && textContains == null && id == null && description == null) {
                return null
            }
            return UiSelector(
                text = text,
                textContains = textContains,
                id = id,
                description = description,
                index = index.coerceAtLeast(0),
            )
        }

        private fun matchesId(nodeId: String?, selector: String): Boolean {
            if (nodeId == null) return false
            if (nodeId == selector) return true
            return nodeId.endsWith(":id/$selector") || nodeId.endsWith("/$selector")
        }
    }
}
