package hr.ml.expensetracker.session

import hr.ml.expensetracker.domain.Person

/**
 * The signed-in member's [Person], stamped as `createdBy` on new records. In the app it reads
 * [SessionRepository.currentPerson]; tests supply a fixed person.
 */
fun interface CurrentPerson {
    fun get(): Person?
}
