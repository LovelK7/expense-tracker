package hr.ml.expensetracker.domain

/**
 * Computes how much Lovel owes Marina, in cents (see docs/DESIGN.md, "Balance rules").
 *
 * Positive: Lovel pays Marina. Negative: Marina pays Lovel.
 *
 * ```
 * balance =   Σ HALF           paidBy MARINA / 2
 *           − Σ HALF           paidBy LOVEL  / 2
 *           + Σ FULL_FOR_OTHER paidBy MARINA
 *           − Σ FULL_FOR_OTHER paidBy LOVEL
 *           + Σ Settlement from MARINA to LOVEL
 *           − Σ Settlement from LOVEL to MARINA
 * ```
 *
 * Deleted records are excluded. The HALF sums are totalled first and their difference is halved
 * once, with banker's rounding (half-even) on cents; nothing else needs rounding.
 */
object BalanceCalculator {

    fun balanceCents(
        expenses: Iterable<Expense>,
        settlements: Iterable<Settlement> = emptyList(),
    ): Long {
        var halfPaidByMarina = 0L
        var halfPaidByLovel = 0L
        var full = 0L
        for (e in expenses) {
            if (e.deleted) continue
            when (e.split) {
                SplitType.HALF -> when (e.paidBy) {
                    Person.MARINA -> halfPaidByMarina += e.amountCents
                    Person.LOVEL -> halfPaidByLovel += e.amountCents
                }
                SplitType.FULL_FOR_OTHER -> full += e.amountCents.signedFor(e.paidBy)
            }
        }
        var settled = 0L
        for (s in settlements) {
            if (s.deleted) continue
            settled += s.amountCents.signedFor(s.from)
        }
        return halveHalfEven(halfPaidByMarina - halfPaidByLovel) + full + settled
    }

    /** Money moving away from Marina increases what Lovel owes her; away from Lovel decreases it. */
    private fun Long.signedFor(payer: Person): Long = when (payer) {
        Person.MARINA -> this
        Person.LOVEL -> -this
    }
}

/** [value] / 2 rounded half-to-even (banker's rounding). */
internal fun halveHalfEven(value: Long): Long {
    val floor = Math.floorDiv(value, 2L)
    if (Math.floorMod(value, 2L) == 0L) return floor
    // Exactly x.5: pick the even neighbour.
    return if (floor % 2L == 0L) floor else floor + 1
}
