// Load test for the transfer path. Run against the compose stack:
//   docker compose up -d --build && k6 run load/transferencias.js
//
// Two scenarios, one after the other:
//   conta_disputada  200 virtual users fire R$ 1,00 at the same instant from an account holding
//                    R$ 100,00. Exactly 100 must succeed and the balance must end at zero.
//   vazao            50 virtual users move random amounts between 20 accounts for 30 s.
//                    Money only moves inside the pool, so its total must not change.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8085';
const POOL = 20;
const POOL_FUNDS = 100_000; // R$ 1.000,00 per pool account

const accepted = new Counter('transferencias_aceitas');
const refused = new Counter('transferencias_recusadas_sem_saldo');

http.setResponseCallback(http.expectedStatuses(200, 201, 422));

export const options = {
  scenarios: {
    conta_disputada: {
      executor: 'per-vu-iterations', vus: 200, iterations: 1, exec: 'contaDisputada', maxDuration: '30s',
    },
    vazao: {
      executor: 'constant-vus', vus: 50, duration: '30s', exec: 'vazao', startTime: '10s',
    },
  },
  thresholds: {
    'transferencias_aceitas{scenario:conta_disputada}': ['count==100'],
    'transferencias_recusadas_sem_saldo{scenario:conta_disputada}': ['count==100'],
    'http_req_failed{scenario:vazao}': ['rate==0'],
    'http_req_duration{scenario:vazao}': ['p(95)<250'],
    checks: ['rate==1'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

const json = { headers: { 'Content-Type': 'application/json' } };
let seq = 0; // per-VU counter; __ITER is not available during setup()
const key = (tag) => `${tag}-${++seq}-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
const auth = (token, extra = {}) => ({ headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...extra } });

function signUp(name) {
  const email = `${name}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@carga.dev`;
  const reg = http.post(`${BASE}/api/auth/register`, JSON.stringify({ name, email, password: 'senha-forte-1' }), json);
  const login = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: 'senha-forte-1' }), json);
  return { token: login.json('accessToken'), accountId: reg.json('accountId') };
}

function deposit(user, cents) {
  const res = http.post(`${BASE}/api/me/deposits`, JSON.stringify({ amountCents: cents }), auth(user.token, { 'Idempotency-Key': key('dep') }));
  check(res, { 'depósito aceito': (r) => r.status === 201 });
}

function balance(user) {
  return http.get(`${BASE}/api/me`, auth(user.token)).json('balanceCents');
}

function transfer(from, to, cents) {
  return http.post(`${BASE}/api/transfers`,
    JSON.stringify({ destinationAccountId: to.accountId, amountCents: cents }),
    auth(from.token, { 'Idempotency-Key': key('tx') }));
}

export function setup() {
  const payer = signUp('Pagadora');
  const receiver = signUp('Recebedor');
  deposit(payer, 10_000);
  const pool = [];
  for (let i = 0; i < POOL; i++) {
    const u = signUp(`Pool${i}`);
    deposit(u, POOL_FUNDS);
    pool.push(u);
  }
  return { payer, receiver, pool };
}

export function contaDisputada(data) {
  const res = transfer(data.payer, data.receiver, 100);
  check(res, { 'conta disputada: 201 ou 422': (r) => r.status === 201 || r.status === 422 });
  if (res.status === 201) accepted.add(1);
  if (res.status === 422 && res.json('code') === 'INSUFFICIENT_FUNDS') refused.add(1);
}

export function vazao(data) {
  const from = data.pool[Math.floor(Math.random() * POOL)];
  let to = data.pool[Math.floor(Math.random() * POOL)];
  while (to.accountId === from.accountId) to = data.pool[Math.floor(Math.random() * POOL)];
  const res = transfer(from, to, 1 + Math.floor(Math.random() * 500));
  if (res.status === 201) accepted.add(1);
  else if (res.status === 422) refused.add(1);
}

export function teardown(data) {
  check(null, {
    'conta disputada terminou com R$ 0,00': () => balance(data.payer) === 0,
    'recebedor ficou com R$ 100,00': () => balance(data.receiver) === 10_000,
    'total do pool não mudou': () => data.pool.reduce((sum, u) => sum + balance(u), 0) === POOL * POOL_FUNDS,
  });
}
