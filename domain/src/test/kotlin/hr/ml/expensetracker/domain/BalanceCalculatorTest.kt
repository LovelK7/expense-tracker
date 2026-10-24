package hr.ml.expensetracker.domain

import hr.ml.expensetracker.domain.Person.LOVEL
import hr.ml.expensetracker.domain.Person.MARINA
import hr.ml.expensetracker.domain.SplitType.FULL_FOR_OTHER
import hr.ml.expensetracker.domain.SplitType.HALF
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class BalanceCalculatorTest {

    private fun balance(vararg e: Expense, settlements: List<Settlement> = emptyList()) =
        BalanceCalculator.balanceCents(e.toList(), settlements)

    @Test
    fun `no records means zero`() {
        assertEquals(0, BalanceCalculator.balanceCents(emptyList()))
    }

    @Nested
    inner class FormulaBranches {
        @Test
        fun `HALF paid by Marina - Lovel owes half`() = assertEquals(500, balance(expense(1000, MARINA, HALF)))

        @Test
        fun `HALF paid by Lovel - Marina owes half`() = assertEquals(-500, balance(expense(1000, LOVEL, HALF)))

        @Test
        fun `FULL_FOR_OTHER paid by Marina - Lovel owes everything`() =
            assertEquals(1000, balance(expense(1000, MARINA, FULL_FOR_OTHER)))

        @Test
        fun `FULL_FOR_OTHER paid by Lovel - Marina owes everything`() =
            assertEquals(-1000, balance(expense(1000, LOVEL, FULL_FOR_OTHER)))

        @Test
        fun `settlement from Marina to Lovel increases balance`() =
            assertEquals(700, balance(settlements = listOf(settlement(700, MARINA))))

        @Test
        fun `settlement from Lovel to Marina decreases balance`() =
            assertEquals(-700, balance(settlements = listOf(settlement(700, LOVEL))))

        @Test
        fun `all branches combine`() {
            val result = balance(
                expense(10_000, MARINA, HALF), // +5000
                expense(4_000, LOVEL, HALF), // -2000
                expense(1_500, MARINA, FULL_FOR_OTHER), // +1500
                expense(2_500, LOVEL, FULL_FOR_OTHER), // -2500
                settlements = listOf(settlement(300, MARINA), settlement(1_000, LOVEL)), // +300 -1000
            )
            assertEquals(1300, result)
        }

        @Test
        fun `settling the balance brings it to zero`() {
            val expenses = listOf(expense(5_347, MARINA, HALF), expense(20_000, LOVEL, FULL_FOR_OTHER))
            val owed = BalanceCalculator.balanceCents(expenses)
            assertEquals(-17_326, owed) // 5347 / 2 = 2673.5 -> 2674 (half-even), minus 20000
            val settle = settlement(-owed, MARINA)
            assertEquals(0, BalanceCalculator.balanceCents(expenses, listOf(settle)))
        }
    }

    @Nested
    inner class Rounding {
        @Test
        fun `HALF sums are totalled before halving`() {
            // Halving each row would give 0 + 0 + 0 = 0 or 1 + 1 + 1 = 3; halving the 3-cent total gives 2 (1.5 -> 2).
            assertEquals(2, balance(expense(1, MARINA), expense(1, MARINA), expense(1, MARINA)))
        }

        @Test
        fun `only the HALF difference is halved`() {
            // (101 - 50) / 2 = 25.5 -> 26 (half-even), then + 7 full
            assertEquals(33, balance(expense(101, MARINA), expense(50, LOVEL), expense(7, MARINA, FULL_FOR_OTHER)))
        }

        @ParameterizedTest(name = "{0} / 2 = {1}")
        @CsvSource("1, 0", "3, 2", "5, 2", "7, 4", "-1, 0", "-3, -2", "-5, -2", "-7, -4", "4, 2", "-4, -2", "0, 0")
        fun `halving uses banker's rounding`(value: Long, expected: Long) {
            assertEquals(expected, halveHalfEven(value))
        }

        @Test
        fun `odd negative HALF difference rounds to even`() {
            // Marina 100, Lovel 105 -> -5 / 2 = -2.5 -> -2
            assertEquals(-2, balance(expense(100, MARINA), expense(105, LOVEL)))
        }
    }

    @Nested
    inner class Deleted {
        @Test
        fun `deleted expenses are excluded`() {
            assertEquals(
                500,
                balance(expense(1000, MARINA), expense(9999, LOVEL, deleted = true), expense(1, MARINA, FULL_FOR_OTHER, deleted = true)),
            )
        }

        @Test
        fun `deleted settlements are excluded`() {
            assertEquals(
                500,
                balance(expense(1000, MARINA), settlements = listOf(settlement(500, LOVEL, deleted = true))),
            )
        }
    }

    @Nested
    inner class Validation {
        @Test
        fun `settlement must be between two people`() {
            assertThrows<IllegalArgumentException> { settlement(100, LOVEL, to = LOVEL) }
        }

        @Test
        fun `settlement must be positive`() {
            assertThrows<IllegalArgumentException> { settlement(0, LOVEL) }
        }

        @Test
        fun `expense amount must not be negative`() {
            assertThrows<IllegalArgumentException> { expense(-1, LOVEL) }
        }
    }
}
