package com.selfguard.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.selfguard.domain.match.HostNormalizer

class BrowserProbe(private val service: AccessibilityService) {

    data class Extraction(
        val host: String?,
        val urlText: String?,
        val title: String?,
        val pageText: String?,
    )

    fun extract(
        root: AccessibilityNodeInfo?,
        packageName: String,
        includePageText: Boolean,
    ): Extraction {
        if (root == null) return Extraction(null, null, null, null)

        var urlText: String? = null
        var host: String? = null
        var fallbackUrl: String? = null
        var fallbackHost: String? = null
        val pageBuilder = StringBuilder()
        var visited = 0
        var scannedText = 0
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)

        try {
            while (stack.isNotEmpty() && visited < MAX_NODES) {
                val (node, depth) = stack.removeLast()
                visited++
                try {
                    if (node.isVisibleToUser) {
                        val text = node.text?.toString()
                        val description = node.contentDescription?.toString()
                        val id = node.viewIdResourceName
                        val className = node.className?.toString()

                        if (!text.isNullOrBlank()) {
                            val normalized = HostNormalizer.normalizeHost(text)
                            if (normalized != null) {
                                val isAddressBar = BrowserProfiles.isUrlBarId(packageName, id) ||
                                    className == "android.widget.EditText"
                                if (urlText == null && isAddressBar) {
                                    urlText = text.trim()
                                    host = normalized
                                } else if (fallbackUrl == null && scannedText < FALLBACK_SCAN_LIMIT) {
                                    val bounds = Rect()
                                    node.getBoundsInScreen(bounds)
                                    val topLimit =
                                        service.resources.displayMetrics.heightPixels * FALLBACK_TOP_RATIO
                                    if (bounds.top <= topLimit) {
                                        fallbackUrl = text.trim()
                                        fallbackHost = normalized
                                    }
                                }
                            }
                            scannedText++
                            if (includePageText &&
                                pageBuilder.length < MAX_PAGE_TEXT &&
                                text.length <= MAX_NODE_TEXT
                            ) {
                                pageBuilder.append(text).append('\n')
                            }
                        }

                        if (urlText == null && !description.isNullOrBlank()) {
                            val normalized = HostNormalizer.normalizeHost(description)
                            if (normalized != null && BrowserProfiles.isUrlBarId(packageName, id)) {
                                urlText = description.trim()
                                host = normalized
                            }
                        }
                    }

                    if (depth < MAX_DEPTH && node.childCount > 0) {
                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i) ?: continue
                            stack.addLast(child to depth + 1)
                        }
                    }
                } finally {
                    recycle(node)
                }
            }
        } finally {
            if (urlText == null && fallbackUrl != null) {
                urlText = fallbackUrl
                host = fallbackHost
            }
        }

        return Extraction(
            host = host,
            urlText = urlText,
            title = pageBuilder.lineSequence().firstOrNull { it.isNotBlank() },
            pageText = if (includePageText) pageBuilder.toString() else null,
        )
    }

    private fun recycle(node: AccessibilityNodeInfo) {
        try {
            @Suppress("DEPRECATION")
            node.recycle()
        } catch (e: Exception) {
            // Recycling is a no-op on API 33+ and may throw on some OEM builds.
        }
    }

    private companion object {
        const val MAX_NODES = 260
        const val MAX_DEPTH = 14
        const val MAX_NODE_TEXT = 300
        const val MAX_PAGE_TEXT = 6000
        const val FALLBACK_SCAN_LIMIT = 40
        const val FALLBACK_TOP_RATIO = 0.32f
    }
}
