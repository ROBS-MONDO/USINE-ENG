import { useEffect, useState } from 'react';
import { Button, StyleSheet, Switch, Text, View } from 'react-native';
import * as Location from 'expo-location';
import { api } from './api';
import { t } from './i18n';

const NEXT = { accepted: 'on_the_way', on_the_way: 'in_progress', in_progress: 'done' };

export default function TechnicianScreen({ user, onError }) {
  const [available, setAvailable] = useState(false);
  const [requests, setRequests] = useState([]);
  const [job, setJob] = useState(null);

  const run = (fn) => async () => { onError(''); try { await fn(); } catch (e) { onError(e.message); } };

  const pushLocation = async (isAvailable) => {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') throw new Error(t.needLocation);
    const { coords } = await Location.getCurrentPositionAsync({});
    await api('PUT', `/technicians/${user.id}/location`, { lat: coords.latitude, lng: coords.longitude, available: isAvailable });
  };

  const loadRequests = run(async () => setRequests(await api('GET', `/technicians/${user.id}/requests`)));

  const toggle = (value) => run(async () => {
    await pushLocation(value);
    setAvailable(value);
    if (value) await loadRequests();
  })();

  // Mentre e' disponibile e senza lavoro in corso, aggiorna posizione e richieste ogni 15 secondi.
  useEffect(() => {
    if (!available || job) return undefined;
    const id = setInterval(run(async () => { await pushLocation(true); await loadRequests(); }), 15000);
    return () => clearInterval(id);
  }, [available, job]);

  const accept = (r) => run(async () => {
    setJob(await api('POST', `/requests/${r.id}/accept`, { technicianId: user.id }));
    setRequests([]);
  })();

  const advance = run(async () => {
    const next = await api('POST', `/requests/${job.id}/status`, { technicianId: user.id, status: NEXT[job.status] });
    if (next.status === 'done') { setJob(null); setAvailable(true); } else setJob(next);
  });

  if (job) {
    return (
      <View style={s.box}>
        <Text style={s.big}>{t.sk[job.skill]} - {t.st[job.status]}</Text>
        {!!job.description && <Text>{job.description}</Text>}
        <Button title={t.next} onPress={advance} />
      </View>
    );
  }
  return (
    <View style={s.box}>
      <View style={s.row}>
        <Text style={s.big}>{available ? t.available : t.unavailable}</Text>
        <Switch value={available} onValueChange={toggle} />
      </View>
      {available && <Text>{t.open}</Text>}
      {available && requests.length === 0 && <Text>{t.none}</Text>}
      {requests.map((r) => (
        <View key={r.id} style={s.card}>
          <Text>{t.sk[r.skill]} - {r.distanceKm.toFixed(1)} {t.km}</Text>
          {!!r.description && <Text>{r.description}</Text>}
          <Button title={t.accept} onPress={() => accept(r)} />
        </View>
      ))}
    </View>
  );
}

const s = StyleSheet.create({
  box: { gap: 12 },
  big: { fontSize: 22, fontWeight: '700' },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  card: { borderWidth: 1, borderColor: '#ccc', borderRadius: 10, padding: 12, gap: 8 },
});
