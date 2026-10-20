package com.example.logipass.ui.format

/** Russian plural form: plural(5, "сервис", "сервиса", "сервисов") = "сервисов". */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

fun servicesCount(n: Int): String = "$n ${plural(n, "сервис", "сервиса", "сервисов")}"

fun accountsCount(n: Int): String = "$n ${plural(n, "аккаунт", "аккаунта", "аккаунтов")}"

/** Time left until a lockout ends: "0:28", "4:59", "1:00:00". */
fun formatCountdown(millis: Long): String {
    val total = ((millis + 999) / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
