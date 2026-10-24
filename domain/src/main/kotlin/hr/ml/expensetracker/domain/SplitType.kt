package hr.ml.expensetracker.domain

enum class SplitType {
    /** Sheet "podjela": shared 50/50, the payer is owed half. */
    HALF,

    /** Sheet "zajam": the payer paid 100% on behalf of the other person. */
    FULL_FOR_OTHER,
}
