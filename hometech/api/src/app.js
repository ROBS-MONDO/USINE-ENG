import express from 'express';
import { createStore, SKILLS } from './store.js';

export function createApp(store = createStore()) {
  const app = express();
  app.use(express.json());

  app.get('/health', (_req, res) => res.json({ ok: true }));
  app.get('/skills', (_req, res) => res.json(SKILLS));

  app.post('/users', (req, res) => res.status(201).json(store.register(req.body)));
  app.put('/technicians/:id/location', (req, res) => res.json(store.setLocation(req.params.id, req.body)));
  app.get('/technicians/nearby', (req, res) => {
    const { lat, lng, skill, radiusKm } = req.query;
    res.json(store.nearbyTechnicians({
      lat: Number(lat), lng: Number(lng), skill, radiusKm: radiusKm ? Number(radiusKm) : undefined,
    }));
  });
  app.get('/technicians/:id/requests', (req, res) => res.json(store.openRequestsFor(req.params.id)));

  app.post('/requests', (req, res) => res.status(201).json(store.createRequest(req.body)));
  app.get('/requests/:id', (req, res) => res.json(store.getRequest(req.params.id)));
  app.post('/requests/:id/accept', (req, res) => res.json(store.accept(req.params.id, req.body.technicianId)));
  app.post('/requests/:id/status', (req, res) => res.json(store.advance(req.params.id, req.body.technicianId, req.body.status)));

  app.use((err, _req, res, _next) => res.status(err.status ?? 500).json({ error: err.message }));
  return app;
}
