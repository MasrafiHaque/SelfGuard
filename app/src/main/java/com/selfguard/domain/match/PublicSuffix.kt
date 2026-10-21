package com.selfguard.domain.match

class PublicSuffix(suffixes: Collection<String>) {

    private val suffixes: Set<String> = suffixes
        .map { it.trim().lowercase().removePrefix(".") }
        .filter { it.isNotEmpty() }
        .toSet()

    fun isPublicSuffix(domain: String): Boolean {
        val d = domain.trim().lowercase()
        if (d.isEmpty()) return false
        if (!d.contains('.')) return true
        return d in suffixes
    }

    fun registrableDomain(host: String): String {
        val labels = host.trim().lowercase().split('.')
        if (labels.size <= 1) return host
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in suffixes) {
                return if (i > 0) {
                    labels.subList(i - 1, labels.size).joinToString(".")
                } else {
                    candidate
                }
            }
        }
        return host
    }

    companion object {
        val BUILT_IN: Set<String> = setOf(
            "com", "net", "org", "edu", "gov", "mil", "int", "info", "biz", "name", "pro",
            "co", "io", "me", "tv", "cc", "xyz", "top", "site", "online", "club", "shop",
            "store", "blog", "news", "app", "dev", "web", "live", "life", "world", "space",
            "adult", "porn", "sex", "cam", "xxx", "fun", "date", "party", "pictures",
            "us", "uk", "de", "fr", "es", "it", "nl", "be", "ch", "at", "se", "no", "dk",
            "fi", "pl", "cz", "sk", "hu", "ro", "bg", "gr", "pt", "ie", "ru", "ua", "tr",
            "ca", "mx", "br", "ar", "cl", "co", "pe", "ve", "au", "nz", "jp", "kr", "cn",
            "in", "id", "ph", "th", "vn", "my", "sg", "hk", "tw", "za", "eg", "ng", "ke",
            "ae", "sa", "il", "ir", "pk", "bd", "lk", "np",
            "co.uk", "org.uk", "me.uk", "ac.uk", "gov.uk", "net.uk", "sch.uk",
            "com.au", "net.au", "org.au", "edu.au", "gov.au",
            "co.nz", "net.nz", "org.nz", "govt.nz",
            "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
            "com.br", "net.br", "org.br", "gov.br",
            "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn",
            "co.in", "net.in", "org.in", "gov.in", "ac.in",
            "co.kr", "or.kr", "ne.kr",
            "com.mx", "com.ar", "com.co", "com.pe", "com.tr", "com.tw", "com.hk",
            "com.sg", "com.my", "com.ph", "com.vn", "com.pk", "com.sa", "com.eg",
            "co.za", "co.il", "co.id", "co.th", "co.ke", "com.ua", "com.pl",
            "co.ug", "co.tz", "com.gh", "com.ng",
            "github.io", "gitlab.io", "herokuapp.com", "herokussl.com",
            "netlify.app", "vercel.app", "now.sh", "pages.dev", "web.app",
            "firebaseapp.com", "glitch.me", "repl.co", "replit.dev", "onrender.com",
            "blogspot.com", "wordpress.com", "wixsite.com", "weebly.com",
            "freeboxos.fr", "telus.net", "sympatico.ca", "rogers.com", "shawcable.net",
            "att.net", "sbcglobal.net", "verizon.net", "comcast.net", "cox.net",
            "operaunite.com", "fastmail.fm", "zoho.com", "yandex.ru",
            "it.com", "amazonaws.com", "cloudfront.net", "azurewebsites.net",
            "s3.amazonaws.com", "storage.googleapis.com", "web.app", "firebase.com"
        )
    }
}
