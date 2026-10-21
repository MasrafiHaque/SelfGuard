package com.selfguard.domain.model

data class BlockedSite(
    val id: Long = 0,
    val domain: String,
    val label: String = "",
    val includeSubdomains: Boolean = true,
    val isEnabled: Boolean = true,
    val source: String = SOURCE_DEFAULT,
    val addedAt: Long = 0L,
) {
    companion object {
        const val SOURCE_DEFAULT = "default"
        const val SOURCE_USER = "user"
    }
}

data class BlockedKeyword(
    val id: Long = 0,
    val keyword: String,
    val label: String = "",
    val matchInPageText: Boolean = true,
    val isEnabled: Boolean = true,
    val source: String = SOURCE_DEFAULT,
    val addedAt: Long = 0L,
) {
    companion object {
        const val SOURCE_DEFAULT = "default"
        const val SOURCE_USER = "user"
    }
}
