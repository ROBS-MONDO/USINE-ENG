import test from 'node:test';
import assert from 'node:assert/strict';
import { createApp } from '../src/app.js';

async function setup() {
  const server = createApp().listen(0);
  const base = `http://localhost:${server.address().port}`;
  const call = async (method, path, body) => {
    const r = await fetch(base + path, { method, headers: { 'content-type': 'application/json' }, body: body && JSON.stringify(body) });
    return { status: r.status, body: await r.json() };
  };
  return { call, close: () => server.close() };
}

test('flusso completo: richiesta, assegnazione al piu vicino, intervento', async () => {
  const { call, close } = await setup();
  try {
    const client = (await call('POST', '/users', { name: 'Anna', role: 'client' })).body;
    const near = (await call('POST', '/users', { name: 'Vicino', role: 'technician', skills: ['idraulico'] })).body;
    const far = (await call('POST', '/users', { name: 'Lontano', role: 'technician', skills: ['idraulico'] })).body;
    await call('PUT', `/technicians/${near.id}/location`, { lat: 49.611, lng: 6.131, available: true });
    await call('PUT', `/technicians/${far.id}/location`, { lat: 49.7, lng: 6.2, available: true });

    const req = (await call('POST', '/requests', { clientId: client.id, skill: 'idraulico', description: 'perdita', lat: 49.61, lng: 6.13 })).body;
    assert.equal(req.candidates[0].id, near.id);

    assert.equal((await call('POST', `/requests/${req.id}/accept`, { technicianId: near.id })).body.status, 'accepted');
    assert.equal((await call('POST', `/requests/${req.id}/accept`, { technicianId: far.id })).status, 409);
    assert.equal((await call('POST', `/requests/${req.id}/status`, { technicianId: near.id, status: 'done' })).status, 409);
    for (const s of ['on_the_way', 'in_progress', 'done']) {
      assert.equal((await call('POST', `/requests/${req.id}/status`, { technicianId: near.id, status: s })).body.status, s);
    }
  } finally { close(); }
});

test('un tecnico senza la competenza non vede ne accetta la richiesta', async () => {
  const { call, close } = await setup();
  try {
    const client = (await call('POST', '/users', { name: 'Anna', role: 'client' })).body;
    const el = (await call('POST', '/users', { name: 'Elio', role: 'technician', skills: ['elettricista'] })).body;
    await call('PUT', `/technicians/${el.id}/location`, { lat: 49.61, lng: 6.13, available: true });
    const req = (await call('POST', '/requests', { clientId: client.id, skill: 'idraulico', lat: 49.61, lng: 6.13 })).body;
    assert.deepEqual(req.candidates, []);
    assert.equal((await call('POST', `/requests/${req.id}/accept`, { technicianId: el.id })).status, 403);
  } finally { close(); }
});
