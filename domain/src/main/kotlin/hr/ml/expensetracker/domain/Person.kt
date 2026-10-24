package hr.ml.expensetracker.domain

enum class Person {
    LOVEL,
    MARINA;

    val other: Person
        get() = when (this) {
            LOVEL -> MARINA
            MARINA -> LOVEL
        }
}
