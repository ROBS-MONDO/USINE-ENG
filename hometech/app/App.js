import { useState } from 'react';
import { Button, SafeAreaView, StyleSheet, Text, TextInput, View } from 'react-native';
import * as Location from 'expo-location';

const API = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:3000';
const SKILLS = ['idraulico', 'elettricista', 'fabbro', 'caldaista', 'imbianchino', 'tuttofare'];

async function api(method, path, body) {
  const res = await fetch(API + path, { method, headers: { 'content-type': 'application/json' }, body: body && JSON.stringify(body) });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error);
  return data;
}

// Schermata cliente minimale: login fittizio, scelta intervento, richiesta con posizione GPS.
export default function App() {
  const [client, setClient] = useState(null);
  const [name, setName] = useState('');
  const [skill, setSkill] = useState(SKILLS[0]);
  const [description, setDescription] = useState('');
  const [request, setRequest] = useState(null);
  const [error, setError] = useState('');

  const run = (fn) => async () => { setError(''); try { await fn(); } catch (e) { setError(e.message); } };

  const register = run(async () => setClient(await api('POST', '/users', { name, role: 'client' })));

  const send = run(async () => {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') throw new Error('Serve il permesso di localizzazione');
    const { coords } = await Location.getCurrentPositionAsync({});
    setRequest(await api('POST', '/requests', { clientId: client.id, skill, description, lat: coords.latitude, lng: coords.longitude }));
  });

  const refresh = run(async () => setRequest(await api('GET', `/requests/${request.id}`)));

  return (
    <SafeAreaView style={s.root}>
      <Text style={s.title}>HomeTech</Text>
      {!client ? (
        <View style={s.box}>
          <TextInput style={s.input} placeholder="Il tuo nome" value={name} onChangeText={setName} />
          <Button title="Entra" onPress={register} />
        </View>
      ) : !request ? (
        <View style={s.box}>
          <Text>Che intervento serve?</Text>
          <View style={s.row}>{SKILLS.map((k) => <Button key={k} title={k === skill ? `[${k}]` : k} onPress={() => setSkill(k)} />)}</View>
          <TextInput style={s.input} placeholder="Descrivi il problema" value={description} onChangeText={setDescription} />
          <Button title="Trova un tecnico" onPress={send} />
        </View>
      ) : (
        <View style={s.box}>
          <Text>Stato: {request.status}</Text>
          <Text>Tecnici vicini trovati: {request.candidates?.length ?? 0}</Text>
          <Button title="Aggiorna" onPress={refresh} />
        </View>
      )}
      {!!error && <Text style={s.error}>{error}</Text>}
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, padding: 24, paddingTop: 64 },
  title: { fontSize: 28, fontWeight: '700', marginBottom: 24 },
  box: { gap: 12 },
  row: { flexDirection: 'row', flexWrap: 'wrap' },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 12 },
  error: { color: 'crimson', marginTop: 12 },
});
