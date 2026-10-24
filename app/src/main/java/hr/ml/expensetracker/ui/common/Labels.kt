package hr.ml.expensetracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.CroatianPlural
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SplitType
import java.time.LocalDate
import java.time.YearMonth

@get:StringRes
val Person.label: Int
    get() = when (this) {
        Person.LOVEL -> R.string.person_lovel
        Person.MARINA -> R.string.person_marina
    }

/** "Platio Lovel" / "Platila Marina". */
@get:StringRes
val Person.paidByLabel: Int
    get() = when (this) {
        Person.LOVEL -> R.string.paid_by_lovel
        Person.MARINA -> R.string.paid_by_marina
    }

@get:StringRes
val SplitType.label: Int
    get() = when (this) {
        SplitType.HALF -> R.string.split_half
        SplitType.FULL_FOR_OTHER -> R.string.split_full
    }

/** "Danas", "Jučer", otherwise "24. listopada 2026.". */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.minusDays(1) -> stringResource(R.string.day_yesterday)
    else -> longDate(date)
}

/** "listopad 2026." (the nominative month name, as on the legacy sheet). */
@Composable
fun monthLabel(month: YearMonth): String {
    val names = stringArrayResource(R.array.months_nominative)
    return stringResource(R.string.month_year, names[month.monthValue - 1], month.year)
}

/** "1 trošak", "3 troška", "23 troškova" with the Croatian plural, whatever the device language. */
@Composable
fun expenseCountLabel(count: Int): String = stringResource(
    CroatianPlural.choose(count.toLong(), R.string.expense_count_one, R.string.expense_count_few, R.string.expense_count_many),
    count,
)

/** "24. listopada 2026." (Croatian genitive month name). */
@Composable
fun longDate(date: LocalDate): String {
    val months = stringArrayResource(R.array.months_genitive)
    return stringResource(R.string.date_long, date.dayOfMonth, months[date.monthValue - 1], date.year)
}
