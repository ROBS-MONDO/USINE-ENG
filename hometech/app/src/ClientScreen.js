import { useState } from 'react';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import * as Location from 'expo-location';
import { api } from './api';
import { SKILLS, t } from './i18n';

export default function ClientScreen({ user, onError }) {
  const [skill, setSkill] = useState(SKILLS[0]);
  const [description, setDescription] = useState('');
  const [request, setRequest] = useState(null);

  const run = (fn) => async () => { onError(''); try { await fn(); } catch (e) { onError(e.message); } };

  const send = run(async () => {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') throw new Error(t.needLocation);
    const { coords } = await Location.getCurrentPositionAsync({});
    setRequest(await api('POST', '/requests', { clientId: user.id, skill, description, lat: coords.latitude, lng: coords.longitude }));
  });
  const refresh = run(async () => setRequest(await api('GET', `/requests/${request.id}`)));

  if (request) {
    return (
      <View style={s.box}>
        <Text style={s.big}>{t.st[request.status]}</Text>
        {request.technician && <Text>{t.arriving}: {request.technician.name}{request.technician.phone ? ` (${request.technician.phone})` : ''}</Text>}
        {request.status === 'open' && <Text>{t.nearby}: {request.candidates?.length ?? 0}</Text>}
        <Button title={t.refresh} onPress={refresh} />
        {request.status === 'done' && <Button title={t.find} onPress={() => setRequest(null)} />}
      </View>
    );
  }
  return (
    <View style={s.box}>
      <Text>{t.whatNeeded}</Text>
      <View style={s.row}>
        {SKILLS.map((k) => <Button key={k} title={k === skill ? `[${t.sk[k]}]` : t.sk[k]} onPress={() => setSkill(k)} />)}
      </View>
      <TextInput style={s.input} placeholder={t.describe} value={description} onChangeText={setDescription} />
      <Button title={t.find} onPress={send} />
    </View>
  );
}

const s = StyleSheet.create({
  box: { gap: 12 },
  big: { fontSize: 22, fontWeight: '700' },
  row: { flexDirection: 'row', flexWrap: 'wrap' },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 12 },
});
