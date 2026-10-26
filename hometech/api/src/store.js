import { randomUUID } from 'node:crypto';
import { distanceKm } from './geo.js';

export const SKILLS = ['idraulico', 'elettricista', 'fabbro', 'caldaista', 'imbianchino', 'tuttofare'];
export const STATUS_FLOW = { accepted: 'on_the_way', on_the_way: 'in_progress', in_progress: 'done' };

// Archivio in memoria: da sostituire con PostgreSQL/PostGIS.
export function createStore() {
  const users = new Map();
  const requests = new Map();

  function httpError(status, message) {
    return Object.assign(new Error(message), { status });
  }

  return {
    register({ name, role, skills = [], phone }) {
      if (!name || !['client', 'technician'].includes(role)) throw httpError(400, 'name e role (client|technician) obbligatori');
      if (role === 'technician' && !skills.every((s) => SKILLS.includes(s))) throw httpError(400, 'competenza non valida');
      const user = { id: randomUUID(), name, role, phone, skills, location: null, available: false };
      users.set(user.id, user);
      return user;
    },

    getUser(id) {
      const u = users.get(id);
      if (!u) throw httpError(404, 'utente non trovato');
      return u;
    },

    setLocation(id, { lat, lng, available }) {
      const u = this.getUser(id);
      if (u.role !== 'technician') throw httpError(403, 'solo i tecnici');
      if (typeof lat !== 'number' || typeof lng !== 'number') throw httpError(400, 'lat e lng numerici');
      u.location = { lat, lng };
      if (typeof available === 'boolean') u.available = available;
      return u;
    },

    nearbyTechnicians({ lat, lng, skill, radiusKm = 15 }) {
      return [...users.values()]
        .filter((u) => u.role === 'technician' && u.available && u.location && (!skill || u.skills.includes(skill)))
        .map((u) => ({ id: u.id, name: u.name, skills: u.skills, distanceKm: distanceKm({ lat, lng }, u.location) }))
        .filter((t) => t.distanceKm <= radiusKm)
        .sort((a, b) => a.distanceKm - b.distanceKm);
    },

    createRequest({ clientId, skill, description, lat, lng }) {
      const client = this.getUser(clientId);
      if (client.role !== 'client') throw httpError(403, 'solo i clienti');
      if (!SKILLS.includes(skill)) throw httpError(400, 'tipo di intervento non valido');
      if (typeof lat !== 'number' || typeof lng !== 'number') throw httpError(400, 'lat e lng numerici');
      const req = {
        id: randomUUID(), clientId, skill, description: description ?? '', location: { lat, lng },
        status: 'open', technicianId: null, createdAt: new Date().toISOString(),
      };
      requests.set(req.id, req);
      return { ...req, candidates: this.nearbyTechnicians({ lat, lng, skill }).slice(0, 5) };
    },

    getRequest(id) {
      const r = requests.get(id);
      if (!r) throw httpError(404, 'richiesta non trovata');
      return r;
    },

    openRequestsFor(technicianId) {
      const t = this.getUser(technicianId);
      if (t.role !== 'technician' || !t.location) return [];
      return [...requests.values()]
        .filter((r) => r.status === 'open' && t.skills.includes(r.skill))
        .map((r) => ({ ...r, distanceKm: distanceKm(t.location, r.location) }))
        .sort((a, b) => a.distanceKm - b.distanceKm);
    },

    accept(id, technicianId) {
      const r = this.getRequest(id);
      const t = this.getUser(technicianId);
      if (t.role !== 'technician') throw httpError(403, 'solo i tecnici');
      if (r.status !== 'open') throw httpError(409, 'richiesta non piu disponibile');
      if (!t.skills.includes(r.skill)) throw httpError(403, 'competenza non compatibile');
      r.status = 'accepted';
      r.technicianId = technicianId;
      t.available = false;
      return r;
    },

    advance(id, technicianId, status) {
      const r = this.getRequest(id);
      if (r.technicianId !== technicianId) throw httpError(403, 'non assegnata a questo tecnico');
      if (STATUS_FLOW[r.status] !== status) throw httpError(409, `transizione non valida da ${r.status} a ${status}`);
      r.status = status;
      if (status === 'done') this.getUser(technicianId).available = true;
      return r;
    },
  };
}
