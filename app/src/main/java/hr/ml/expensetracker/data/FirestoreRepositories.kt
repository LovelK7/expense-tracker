package hr.ml.expensetracker.data

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.RecurringRepository
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SettlementRepository
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "FirestoreRepo"

/**
 * Shared plumbing for one Firestore collection of soft-deletable records.
 *
 * Writes are not awaited: with offline persistence the local cache (and every snapshot listener)
 * sees them at once, while the returned Task only completes when the server acknowledges, which
 * may be never while offline. A write the rules reject is rolled back by Firestore and the next
 * snapshot no longer contains it; the failure is logged.
 */
internal class RecordCollection<T>(
    private val collection: CollectionReference,
    private val toDocument: (T) -> Map<String, Any>,
    private val fromDocument: (String, Map<String, Any?>) -> T,
    /** The order [observeAll] emits (newest first for expenses and settlements). */
    private val newestFirst: Comparator<T>,
    private val clock: () -> Instant,
) {
    fun observeAll(): Flow<List<T>> = callbackFlow {
        // MetadataChanges.INCLUDE: also fire when only hasPendingWrites/fromCache flip, so a
        // record written offline shows up immediately and stays in sync once it is acknowledged.
        val registration = collection.whereEqualTo(Firestore.DELETED, false)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "${collection.path} listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val records = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    runCatching { fromDocument(doc.id, doc.data.orEmpty()) }
                        .onFailure { Log.w(TAG, "skipping malformed ${doc.reference.path}: ${it.message}") }
                        .getOrNull()
                }
                trySend(records.sortedWith(newestFirst))
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    fun newId(): String = collection.document().id

    /**
     * Writes [record] under [id]; if that document already exists, the stored `createdBy` and
     * `createdAt` are kept (the rules never let them change). The existence check reads the
     * server, or the local cache while offline; when neither knows the document it is created.
     */
    fun setKeepingAuthorship(id: String, record: T) {
        val doc = collection.document(id)
        doc.get()
            .addOnSuccessListener { snapshot ->
                val stored = snapshot.data
                val fields = toDocument(record).toMutableMap()
                if (snapshot.exists() && stored != null) {
                    stored[Firestore.CREATED_BY]?.let { fields[Firestore.CREATED_BY] = it }
                    stored[Firestore.CREATED_AT]?.let { fields[Firestore.CREATED_AT] = it }
                }
                doc.set(fields).addOnFailureListener { Log.w(TAG, "write ${collection.path}/$id failed", it) }
            }
            .addOnFailureListener {
                // Offline and not cached: nobody here has seen it, so create it.
                set(id, record)
            }
    }

    fun set(id: String, record: T) {
        collection.document(id).set(toDocument(record))
            .addOnFailureListener { Log.w(TAG, "write ${collection.path}/$id failed", it) }
    }

    fun softDelete(id: String) {
        collection.document(id)
            .update(mapOf(Firestore.DELETED to true, Firestore.UPDATED_AT to clock().toTimestamp()))
            .addOnFailureListener { Log.w(TAG, "delete ${collection.path}/$id failed", it) }
    }
}

class FirestoreExpenseRepository(db: FirebaseFirestore, clock: () -> Instant = Instant::now) : ExpenseRepository {
    private val records = RecordCollection(
        collection = db.collection(Firestore.EXPENSES),
        toDocument = ExpenseMapper::toDocument,
        fromDocument = ExpenseMapper::fromDocument,
        newestFirst = compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt },
        clock = clock,
    )

    override fun observeAll() = records.observeAll()

    override fun newId() = records.newId()

    override fun add(expense: Expense) = records.set(expense.id, expense)

    override fun update(expense: Expense) = records.set(expense.id, expense)

    override fun addOrRestore(expense: Expense) = records.setKeepingAuthorship(expense.id, expense)

    override fun softDelete(id: String) = records.softDelete(id)
}

class FirestoreSettlementRepository(db: FirebaseFirestore, clock: () -> Instant = Instant::now) : SettlementRepository {
    private val records = RecordCollection(
        collection = db.collection(Firestore.SETTLEMENTS),
        toDocument = SettlementMapper::toDocument,
        fromDocument = SettlementMapper::fromDocument,
        newestFirst = compareByDescending<Settlement> { it.date }.thenByDescending { it.createdAt },
        clock = clock,
    )

    override fun observeAll() = records.observeAll()

    override fun newId() = records.newId()

    override fun add(settlement: Settlement) = records.set(settlement.id, settlement)

    override fun update(settlement: Settlement) = records.set(settlement.id, settlement)

    override fun softDelete(id: String) = records.softDelete(id)
}

class FirestoreRecurringRepository(db: FirebaseFirestore, clock: () -> Instant = Instant::now) : RecurringRepository {
    private val records = RecordCollection(
        collection = db.collection(Firestore.RECURRING),
        toDocument = RecurringMapper::toDocument,
        fromDocument = RecurringMapper::fromDocument,
        newestFirst = RecurringTemplate.BY_NAME,
        clock = clock,
    )

    override fun observeAll() = records.observeAll()

    override fun newId() = records.newId()

    override fun add(template: RecurringTemplate) = records.set(template.id, template)

    override fun update(template: RecurringTemplate) = records.set(template.id, template)

    override fun softDelete(id: String) = records.softDelete(id)
}

