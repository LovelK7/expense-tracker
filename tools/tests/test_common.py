"""Pure parts of firebase_common: category normalisation, balance, legacy document encoding, ids."""

import datetime as dt
import re

import pytest

import firebase_common as fc

UTC = dt.timezone.utc


def expense(cents, paid_by, split, deleted=False):
    return {"amountCents": cents, "paidBy": paid_by, "split": split, "deleted": deleted}


def settlement(cents, sender, deleted=False):
    other = "LOVEL" if sender == "MARINA" else "MARINA"
    return {"amountCents": cents, "from": sender, "to": other, "deleted": deleted}


def row(date="2023-01-02", category="Hrana", description="Spar", cents="6720", paid_by="MARINA",
        split="HALF"):
    return {"date": date, "category": category, "description": description, "amount_cents": cents,
            "paid_by": paid_by, "split": split}


# --- categories (CategoryNormalizerTest) -------------------------------------------------------

@pytest.mark.parametrize("raw, expected", [
    ("hrana", "Hrana"),
    ("HRANA", "Hrana"),
    ("  Hrana  ", "Hrana"),
    ("hrana VAN", "Hrana van"),
    ("Hrana   van", "Hrana van"),
    ("Odjeća", "Odjeća i oprema"),
    ("odjeća", "Odjeća i oprema"),
    ("Odjeća i oprema", "Odjeća i oprema"),
    ("režije", "Režije"),
    ("mpo", "MPO"),
])
def test_normalize_category_maps_onto_the_controlled_list(raw, expected):
    assert fc.normalize_category(raw) == expected


def test_unknown_categories_are_trimmed_but_kept():
    assert fc.normalize_category("  Kućni   ljubimci ") == "Kućni ljubimci"
    assert not fc.is_known_category("Kućni ljubimci")
    assert fc.is_known_category("odjeća")


def test_category_lists_match_the_domain_source():
    """CATEGORIES / CATEGORY_ALIASES must equal Categories.DEFAULT / DEFAULT_ALIASES in :domain."""
    source = (fc.REPO_ROOT / "domain" / "src" / "main" / "kotlin" / "hr" / "ml" / "expensetracker"
              / "domain" / "Categories.kt").read_text(encoding="utf-8")
    default = re.search(r"val DEFAULT: List<String> = listOf\((.*?)\)", source, re.S).group(1)
    assert tuple(re.findall(r'"([^"]+)"', default)) == fc.CATEGORIES
    aliases = re.search(r"val DEFAULT_ALIASES: Map<String, String> = mapOf\((.*?)\)\n", source, re.S).group(1)
    assert dict(re.findall(r'"([^"]+)" to "([^"]+)"', aliases)) == fc.CATEGORY_ALIASES


# --- balance (BalanceCalculatorTest) -----------------------------------------------------------

@pytest.mark.parametrize("value, expected", [
    (0, 0), (4, 2), (-4, -2), (1, 0), (-1, 0), (3, 2), (-3, -2), (5, 2), (-5, -2), (7, 4),
])
def test_halve_half_even(value, expected):
    assert fc.halve_half_even(value) == expected


def test_balance_signs():
    assert fc.balance_cents([expense(1000, "MARINA", "HALF")]) == 500
    assert fc.balance_cents([expense(1000, "LOVEL", "HALF")]) == -500
    assert fc.balance_cents([expense(1000, "MARINA", "FULL_FOR_OTHER")]) == 1000
    assert fc.balance_cents([expense(1000, "LOVEL", "FULL_FOR_OTHER")]) == -1000
    assert fc.balance_cents([], [settlement(700, "MARINA")]) == 700
    assert fc.balance_cents([], [settlement(700, "LOVEL")]) == -700


def test_balance_halves_the_half_difference_once():
    # Per-row halving would give 0 + 0 (or 1 + 1); the domain halves the 2-cent total once.
    assert fc.balance_cents([expense(1, "MARINA", "HALF"), expense(1, "MARINA", "HALF")]) == 1
    # Marina 3 - Lovel 0 = 3 -> 1.5 -> 2 (half-even); Marina 1 -> 0.5 -> 0.
    assert fc.balance_cents([expense(3, "MARINA", "HALF")]) == 2
    assert fc.balance_cents([expense(1, "MARINA", "HALF")]) == 0
    assert fc.balance_cents([expense(5, "MARINA", "HALF"), expense(2, "LOVEL", "HALF")]) == 2


def test_balance_skips_deleted_records():
    assert fc.balance_cents([expense(1000, "MARINA", "HALF", deleted=True)],
                            [settlement(5, "LOVEL", deleted=True)]) == 0


def test_settling_the_balance_brings_it_to_zero():
    expenses = [expense(6720, "MARINA", "HALF"), expense(17880, "LOVEL", "HALF"),
                expense(4100, "LOVEL", "FULL_FOR_OTHER")]
    balance = fc.balance_cents(expenses)
    assert balance == -9680
    assert fc.balance_cents(expenses, [settlement(-balance, "MARINA")]) == 0


def test_category_totals_largest_first_without_deleted():
    docs = [{"category": "Hrana", "amountCents": 100}, {"category": "Režije", "amountCents": 300},
            {"category": "Hrana", "amountCents": 250}, {"category": "MPO", "amountCents": 999, "deleted": True}]
    assert list(fc.category_totals(docs).items()) == [("Hrana", 350), ("Režije", 300)]


def test_format_euros():
    assert fc.format_euros(-53469) == "-534,69 €"
    assert fc.format_euros(1234567) == "12.345,67 €"
    assert fc.format_euros(5) == "0,05 €"


# --- ids and documents --------------------------------------------------------------------------

def test_legacy_ids_match_the_domain_parser():
    assert fc.legacy_id(1) == "legacy-0001"
    assert fc.legacy_id(1069) == "legacy-1069"
    assert fc.legacy_id(12345) == "legacy-12345"
    with pytest.raises(ValueError):
        fc.legacy_id(0)


def test_legacy_documents_are_deterministic_and_keep_duplicate_rows():
    rows = [row(), row(), row(date="2023-01-03", category="hrana")]  # first two identical
    first, second = fc.legacy_documents(rows), fc.legacy_documents([dict(r) for r in rows])
    assert first == second
    assert list(first) == ["legacy-0001", "legacy-0002", "legacy-0003"]
    assert first["legacy-0001"] == first["legacy-0002"]
    assert first["legacy-0003"]["category"] == "Hrana"


def test_legacy_document_encoding_matches_the_expense_mapper():
    doc = fc.legacy_expense_document(row(category="Odjeća", description="", cents="4100",
                                         paid_by="LOVEL", split="FULL_FOR_OTHER"))
    midnight = dt.datetime(2023, 1, 2, tzinfo=UTC)
    assert doc == {
        "date": "2023-01-02",
        "category": "Odjeća i oprema",
        "description": "",
        "amountCents": 4100,
        "paidBy": "LOVEL",
        "split": "FULL_FOR_OTHER",
        "createdAt": midnight,
        "updatedAt": midnight,
        "createdBy": "LOVEL",
        "deleted": False,
    }
    assert set(doc) == set(fc.EXPENSE_FIELDS)
    assert type(doc["amountCents"]) is int


@pytest.mark.parametrize("field, value", [
    ("amount_cents", "-5"), ("amount_cents", "12.50"), ("paid_by", "Lovel"), ("split", "podjela"),
])
def test_malformed_legacy_rows_are_rejected(field, value):
    with pytest.raises(ValueError):
        fc.legacy_expense_document({**row(), field: value})


def test_rule_validator_accepts_imported_documents_and_flags_zero_amounts():
    doc = fc.legacy_expense_document(row())
    assert fc.expense_rule_violations(doc) == []
    zero = fc.legacy_expense_document(row(cents="0"))
    assert any("amountCents" in p for p in fc.expense_rule_violations(zero))
    assert fc.expense_rule_violations({**doc, "id": "x"})  # unknown field
    assert fc.expense_rule_violations({**doc, "amountCents": True})
    assert fc.expense_rule_violations({**doc, "category": ""})
    assert fc.expense_rule_violations({**doc, "date": "2.1.2023"})
    assert fc.expense_rule_violations({**doc, "createdAt": "2023-01-02"})


def test_read_legacy_csv(tmp_path):
    path = tmp_path / "legacy.csv"
    path.write_text("﻿date,category,description,amount_cents,paid_by,split\r\n"
                    '2023-01-02,Hrana,"Spar, Martinkovac",6720,MARINA,HALF\r\n'
                    "2023-01-03,Prijevoz,,4100,LOVEL,FULL_FOR_OTHER\r\n\r\n", encoding="utf-8")
    rows = fc.read_legacy_csv(path)
    assert [r["description"] for r in rows] == ["Spar, Martinkovac", ""]
    assert rows[1]["split"] == "FULL_FOR_OTHER"


def test_read_legacy_csv_rejects_another_header(tmp_path):
    path = tmp_path / "other.csv"
    path.write_text("Datum,Kategorija\n", encoding="utf-8")
    with pytest.raises(SystemExit):
        fc.read_legacy_csv(path)


@pytest.mark.skipif(not fc.LEGACY_CSV.is_file(), reason="private/legacy_popis.csv not present")
def test_golden_balance_of_the_real_history():
    docs = fc.legacy_documents(fc.read_legacy_csv(fc.LEGACY_CSV))
    assert len(docs) == 1069
    assert fc.balance_cents(docs.values()) == -53469
