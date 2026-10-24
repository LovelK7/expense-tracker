// Firestore security rules tests. Run with the emulator:  npm run test:emulator
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  collection,
  query,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';

const LOVEL = 'lovel.test@example.com'; // dots in the email: map keys in config/members must still work
const MARINA = 'marina.test@example.com';
const STRANGER = 'stranger@example.com';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-troskovi',
    firestore: {
      rules: readFileSync(new URL('../../../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'config/members'), {
      emails: [LOVEL, MARINA],
      persons: { [LOVEL]: 'LOVEL', [MARINA]: 'MARINA' },
    });
  });
});

function db(email, { verified = true } = {}) {
  return env.authenticatedContext(`uid-${email}`, { email, email_verified: verified }).firestore();
}

const lovel = () => db(LOVEL);
const marina = () => db(MARINA);
const stranger = () => db(STRANGER);
const anonymous = () => env.unauthenticatedContext().firestore();

const ts = Timestamp.fromDate(new Date('2026-10-24T08:00:00Z'));

function expense(overrides = {}) {
  return {
    date: '2026-10-24',
    category: 'Hrana',
    description: 'Konzum',
    amountCents: 8540,
    paidBy: 'MARINA',
    split: 'HALF',
    createdAt: ts,
    updatedAt: ts,
    createdBy: 'LOVEL',
    deleted: false,
    ...overrides,
  };
}

function settlement(overrides = {}) {
  return {
    date: '2026-10-24',
    from: 'MARINA',
    to: 'LOVEL',
    amountCents: 53470,
    note: '',
    createdAt: ts,
    updatedAt: ts,
    createdBy: 'LOVEL',
    deleted: false,
    ...overrides,
  };
}

function template(overrides = {}) {
  return {
    name: 'Stanarina',
    category: 'Stanarina',
    description: '',
    amountCents: 50000,
    paidBy: 'MARINA',
    split: 'HALF',
    dayOfMonth: 15,
    active: true,
    skippedPeriods: [],
    createdAt: ts,
    updatedAt: ts,
    createdBy: 'LOVEL',
    deleted: false,
    ...overrides,
  };
}

// An expense posted from template t1 for October 2026 (deterministic id rec-t1-2026-10).
function posted(overrides = {}) {
  return expense({ templateId: 't1', period: '2026-10', category: 'Stanarina', amountCents: 50000, ...overrides });
}

function without(obj, key) {
  const copy = { ...obj };
  delete copy[key];
  return copy;
}

async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), path), data);
  });
}

describe('non-members', () => {
  test('signed-out users can read and write nothing', async () => {
    await seed('expenses/e1', expense());
    const d = anonymous();
    await assertFails(getDoc(doc(d, 'expenses/e1')));
    await assertFails(getDoc(doc(d, 'config/members')));
    await assertFails(setDoc(doc(d, 'expenses/e2'), expense()));
    await assertFails(setDoc(doc(d, 'settlements/s1'), settlement()));
  });

  test('a signed-in account that is not allow-listed is denied', async () => {
    await seed('expenses/e1', expense());
    await seed('settlements/s1', settlement());
    const d = stranger();
    await assertFails(getDoc(doc(d, 'expenses/e1')));
    await assertFails(getDocs(collection(d, 'expenses')));
    await assertFails(getDoc(doc(d, 'settlements/s1')));
    await assertFails(getDoc(doc(d, 'config/members')));
    await assertFails(setDoc(doc(d, 'expenses/e2'), expense()));
    await assertFails(setDoc(doc(d, 'settlements/s2'), settlement()));
  });

  test('an allow-listed email that is not verified is denied', async () => {
    const d = db(LOVEL, { verified: false });
    await assertFails(getDoc(doc(d, 'config/members')));
    await assertFails(setDoc(doc(d, 'expenses/e1'), expense()));
  });

  test('without a members document nobody has access', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), 'config/members'));
    });
    await assertFails(getDoc(doc(lovel(), 'expenses/e1')));
    await assertFails(setDoc(doc(lovel(), 'expenses/e1'), expense()));
  });
});

describe('members', () => {
  test('can read the members document', async () => {
    await assertSucceeds(getDoc(doc(lovel(), 'config/members')));
    await assertSucceeds(getDoc(doc(marina(), 'config/members')));
  });

  test('email case does not matter', async () => {
    const d = db('Lovel.Test@Example.COM');
    await assertSucceeds(getDoc(doc(d, 'config/members')));
    await assertSucceeds(setDoc(doc(d, 'expenses/e1'), expense()));
  });

  test('can create, read, query, update and soft-delete expenses', async () => {
    await assertSucceeds(setDoc(doc(lovel(), 'expenses/e1'), expense()));
    await assertSucceeds(getDoc(doc(marina(), 'expenses/e1')));
    await assertSucceeds(getDocs(query(collection(marina(), 'expenses'), where('deleted', '==', false))));
    // The other member may edit; authorship stays with Lovel.
    await assertSucceeds(setDoc(doc(marina(), 'expenses/e1'), expense({ amountCents: 9000, description: '' })));
    await assertSucceeds(updateDoc(doc(marina(), 'expenses/e1'), { deleted: true, updatedAt: Timestamp.now() }));
  });

  test('can create, update and soft-delete settlements', async () => {
    await assertSucceeds(setDoc(doc(marina(), 'settlements/s1'), settlement({ createdBy: 'MARINA' })));
    await assertSucceeds(getDocs(collection(lovel(), 'settlements')));
    await assertSucceeds(setDoc(doc(lovel(), 'settlements/s1'), settlement({ createdBy: 'MARINA', note: 'Revolut' })));
    await assertSucceeds(updateDoc(doc(lovel(), 'settlements/s1'), { deleted: true, updatedAt: Timestamp.now() }));
  });

  test('cannot hard-delete records', async () => {
    await seed('expenses/e1', expense());
    await seed('settlements/s1', settlement());
    await assertFails(deleteDoc(doc(lovel(), 'expenses/e1')));
    await assertFails(deleteDoc(doc(lovel(), 'settlements/s1')));
  });

  test('cannot touch other collections', async () => {
    await assertFails(setDoc(doc(lovel(), 'other/x'), { a: 1 }));
    await assertFails(getDoc(doc(lovel(), 'other/x')));
    await assertFails(setDoc(doc(lovel(), 'config/other'), { a: 1 }));
  });
});

describe('createdBy', () => {
  test('must be the writer on create', async () => {
    await assertFails(setDoc(doc(lovel(), 'expenses/e1'), expense({ createdBy: 'MARINA' })));
    await assertFails(setDoc(doc(marina(), 'expenses/e2'), expense({ createdBy: 'LOVEL' })));
    await assertSucceeds(setDoc(doc(marina(), 'expenses/e3'), expense({ createdBy: 'MARINA' })));
    await assertFails(setDoc(doc(lovel(), 'settlements/s1'), settlement({ createdBy: 'MARINA' })));
    await assertSucceeds(setDoc(doc(lovel(), 'settlements/s2'), settlement({ createdBy: 'LOVEL' })));
  });

  test('never changes on update', async () => {
    await seed('expenses/e1', expense({ createdBy: 'LOVEL' }));
    await seed('settlements/s1', settlement({ createdBy: 'LOVEL' }));
    await assertFails(updateDoc(doc(marina(), 'expenses/e1'), { createdBy: 'MARINA' }));
    await assertFails(updateDoc(doc(lovel(), 'expenses/e1'), { createdBy: 'MARINA' }));
    await assertFails(updateDoc(doc(marina(), 'settlements/s1'), { createdBy: 'MARINA' }));
  });

  test('createdAt never changes on update', async () => {
    await seed('expenses/e1', expense());
    await assertFails(updateDoc(doc(lovel(), 'expenses/e1'), { createdAt: Timestamp.now() }));
  });
});

describe('field validation', () => {
  const badExpenses = {
    'zero amount': expense({ amountCents: 0 }),
    'negative amount': expense({ amountCents: -100 }),
    'fractional amount': expense({ amountCents: 85.4 }),
    'string amount': expense({ amountCents: '8540' }),
    'date with time': expense({ date: '2026-10-24T10:00:00' }),
    'Croatian date format': expense({ date: '24.10.2026.' }),
    'short date': expense({ date: '2026-1-5' }),
    'date as timestamp': expense({ date: ts }),
    'unknown payer': expense({ paidBy: 'ANA' }),
    'lowercase payer': expense({ paidBy: 'lovel' }),
    'unknown split': expense({ split: 'THIRDS' }),
    'unknown createdBy': expense({ createdBy: 'ANA' }),
    'empty category': expense({ category: '' }),
    'numeric description': expense({ description: 5 }),
    'string timestamp': expense({ createdAt: '2026-10-24' }),
    'string deleted': expense({ deleted: 'false' }),
    'unknown field': expense({ id: 'e1' }),
    'missing field': without(expense(), 'description'),
  };

  for (const [name, data] of Object.entries(badExpenses)) {
    test(`expense rejected: ${name}`, async () => {
      await assertFails(setDoc(doc(lovel(), 'expenses/bad'), data));
    });
  }

  const badSettlements = {
    'from equals to': settlement({ from: 'LOVEL', to: 'LOVEL' }),
    'zero amount': settlement({ amountCents: 0 }),
    'fractional amount': settlement({ amountCents: 10.5 }),
    'unknown person': settlement({ to: 'ANA' }),
    'bad date': settlement({ date: '2026/10/24' }),
    'unknown field': settlement({ category: 'Prijenos' }),
    'missing field': without(settlement(), 'note'),
  };

  for (const [name, data] of Object.entries(badSettlements)) {
    test(`settlement rejected: ${name}`, async () => {
      await assertFails(setDoc(doc(lovel(), 'settlements/bad'), data));
    });
  }

  test('updates are validated too', async () => {
    await seed('expenses/e1', expense());
    await assertFails(updateDoc(doc(lovel(), 'expenses/e1'), { amountCents: 0 }));
    await assertFails(updateDoc(doc(lovel(), 'expenses/e1'), { extra: true }));
    await assertFails(updateDoc(doc(lovel(), 'expenses/e1'), { split: 'NONE' }));
    await seed('settlements/s1', settlement());
    await assertFails(updateDoc(doc(lovel(), 'settlements/s1'), { to: 'MARINA' }));
  });
});

describe('config/members', () => {
  test('is not writable by anyone from the client', async () => {
    const takeover = { emails: [LOVEL, MARINA, STRANGER], persons: { [STRANGER]: 'LOVEL' } };
    await assertFails(setDoc(doc(lovel(), 'config/members'), takeover));
    await assertFails(updateDoc(doc(marina(), 'config/members'), { emails: [STRANGER] }));
    await assertFails(deleteDoc(doc(lovel(), 'config/members')));
    await assertFails(setDoc(doc(stranger(), 'config/members'), takeover));
    await assertFails(setDoc(doc(anonymous(), 'config/members'), takeover));
  });
});

describe('recurring templates', () => {
  test('members can create, read, update and soft-delete them', async () => {
    await assertSucceeds(setDoc(doc(lovel(), 'recurring/t1'), template()));
    await assertSucceeds(getDoc(doc(marina(), 'recurring/t1')));
    await assertSucceeds(getDocs(query(collection(marina(), 'recurring'), where('deleted', '==', false))));
    // The other member may edit (skip a month, pause it); authorship stays with Lovel.
    await assertSucceeds(setDoc(doc(marina(), 'recurring/t1'), template({ skippedPeriods: ['2026-09', '2026-10'], active: false })));
    await assertSucceeds(updateDoc(doc(marina(), 'recurring/t1'), { deleted: true, updatedAt: Timestamp.now() }));
  });

  test('non-members are denied', async () => {
    await seed('recurring/t1', template());
    for (const d of [stranger(), anonymous(), db(LOVEL, { verified: false })]) {
      await assertFails(getDoc(doc(d, 'recurring/t1')));
      await assertFails(getDocs(collection(d, 'recurring')));
      await assertFails(setDoc(doc(d, 'recurring/t2'), template()));
      await assertFails(updateDoc(doc(d, 'recurring/t1'), { active: false }));
    }
  });

  test('cannot be hard-deleted', async () => {
    await seed('recurring/t1', template());
    await assertFails(deleteDoc(doc(lovel(), 'recurring/t1')));
  });

  test('createdBy is the writer and authorship never changes', async () => {
    await assertFails(setDoc(doc(lovel(), 'recurring/t1'), template({ createdBy: 'MARINA' })));
    await assertSucceeds(setDoc(doc(marina(), 'recurring/t2'), template({ createdBy: 'MARINA' })));
    await seed('recurring/t3', template({ createdBy: 'LOVEL' }));
    await assertFails(updateDoc(doc(marina(), 'recurring/t3'), { createdBy: 'MARINA' }));
    await assertFails(updateDoc(doc(lovel(), 'recurring/t3'), { createdAt: Timestamp.now() }));
  });

  test('accepts the day range edges and many skipped months', async () => {
    await assertSucceeds(setDoc(doc(lovel(), 'recurring/a'), template({ dayOfMonth: 1 })));
    await assertSucceeds(setDoc(doc(lovel(), 'recurring/b'), template({ dayOfMonth: 31 })));
    const months = Array.from({ length: 36 }, (_, i) => `${2024 + Math.floor(i / 12)}-${String((i % 12) + 1).padStart(2, '0')}`);
    await assertSucceeds(setDoc(doc(lovel(), 'recurring/c'), template({ skippedPeriods: months })));
    await assertSucceeds(setDoc(doc(lovel(), 'recurring/d'), template({ skippedPeriods: ['2026-12'], name: 'x'.repeat(100), description: 'y'.repeat(1000) })));
  });

  const badTemplates = {
    'day 0': template({ dayOfMonth: 0 }),
    'day 32': template({ dayOfMonth: 32 }),
    'fractional day': template({ dayOfMonth: 15.5 }),
    'string day': template({ dayOfMonth: '15' }),
    'string active': template({ active: 'true' }),
    'skipped periods not a list': template({ skippedPeriods: '2026-10' }),
    'short period': template({ skippedPeriods: ['2026-1'] }),
    'month 13': template({ skippedPeriods: ['2026-13'] }),
    'month 00': template({ skippedPeriods: ['2026-00'] }),
    'period with a day': template({ skippedPeriods: ['2026-10-01'] }),
    'two periods in one element': template({ skippedPeriods: ['2026-09,2026-10'] }),
    'a good and a bad period': template({ skippedPeriods: ['2026-09', 'rujan'] }),
    'numeric period': template({ skippedPeriods: [202610] }),
    'empty name': template({ name: '' }),
    'long name': template({ name: 'x'.repeat(101) }),
    'long description': template({ description: 'y'.repeat(1001) }),
    'empty category': template({ category: '' }),
    'zero amount': template({ amountCents: 0 }),
    'fractional amount': template({ amountCents: 500.5 }),
    'unknown payer': template({ paidBy: 'ANA' }),
    'unknown split': template({ split: 'THIRDS' }),
    'string deleted': template({ deleted: 'false' }),
    'unknown field': template({ frequency: 'MONTHLY' }),
    'missing field': without(template(), 'skippedPeriods'),
    'missing day': without(template(), 'dayOfMonth'),
  };

  for (const [name, data] of Object.entries(badTemplates)) {
    test(`template rejected: ${name}`, async () => {
      await assertFails(setDoc(doc(lovel(), 'recurring/bad'), data));
    });
  }

  test('updates are validated too', async () => {
    await seed('recurring/t1', template());
    await assertFails(updateDoc(doc(lovel(), 'recurring/t1'), { dayOfMonth: 40 }));
    await assertFails(updateDoc(doc(lovel(), 'recurring/t1'), { skippedPeriods: ['10-2026'] }));
    await assertFails(updateDoc(doc(lovel(), 'recurring/t1'), { active: 1 }));
    await assertSucceeds(updateDoc(doc(lovel(), 'recurring/t1'), { skippedPeriods: ['2026-10'], updatedAt: Timestamp.now() }));
  });
});

describe('expenses posted from a template', () => {
  test('are accepted with templateId and period', async () => {
    await assertSucceeds(setDoc(doc(lovel(), 'expenses/rec-t1-2026-10'), posted()));
    await assertSucceeds(getDoc(doc(marina(), 'expenses/rec-t1-2026-10')));
    // Editing and soft-deleting keep working.
    await assertSucceeds(setDoc(doc(marina(), 'expenses/rec-t1-2026-10'), posted({ amountCents: 52000 })));
    await assertSucceeds(updateDoc(doc(marina(), 'expenses/rec-t1-2026-10'), { deleted: true, updatedAt: Timestamp.now() }));
    await assertSucceeds(setDoc(doc(lovel(), 'expenses/rec-t1-2026-11'), posted({ templateId: 'x'.repeat(64), period: '2026-11' })));
  });

  test('the second phone posting the same month cannot take over authorship', async () => {
    await assertSucceeds(setDoc(doc(lovel(), 'expenses/rec-t1-2026-10'), posted({ createdBy: 'LOVEL' })));
    const later = Timestamp.fromDate(new Date('2026-10-24T09:00:00Z'));
    await assertFails(setDoc(doc(marina(), 'expenses/rec-t1-2026-10'), posted({ createdBy: 'MARINA', createdAt: later, updatedAt: later })));
    // Re-posting with the stored authorship (what the app does) is fine.
    await assertSucceeds(setDoc(doc(marina(), 'expenses/rec-t1-2026-10'), posted({ createdBy: 'LOVEL', updatedAt: later })));
  });

  const badPosted = {
    'templateId without period': without(posted(), 'period'),
    'period without templateId': without(posted(), 'templateId'),
    'short period': posted({ period: '2026-1' }),
    'month 13': posted({ period: '2026-13' }),
    'period with a day': posted({ period: '2026-10-15' }),
    'Croatian period': posted({ period: '10/2026' }),
    'numeric period': posted({ period: 202610 }),
    'empty templateId': posted({ templateId: '' }),
    'long templateId': posted({ templateId: 'x'.repeat(65) }),
    'numeric templateId': posted({ templateId: 1 }),
    'null templateId': posted({ templateId: null, period: null }),
    'extra field besides the pair': posted({ dayOfMonth: 15 }),
  };

  for (const [name, data] of Object.entries(badPosted)) {
    test(`posted expense rejected: ${name}`, async () => {
      await assertFails(setDoc(doc(lovel(), 'expenses/bad'), data));
    });
  }

  test('an existing expense cannot gain half of the pair', async () => {
    await seed('expenses/e1', expense());
    await assertFails(updateDoc(doc(lovel(), 'expenses/e1'), { templateId: 't1' }));
    await assertFails(updateDoc(doc(lovel(), 'expenses/e1'), { period: '2026-10' }));
    await assertSucceeds(updateDoc(doc(lovel(), 'expenses/e1'), { templateId: 't1', period: '2026-10' }));
  });
});
