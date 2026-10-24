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
