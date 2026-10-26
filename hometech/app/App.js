import { useState } from 'react';
import { Button, SafeAreaView, StyleSheet, Text, TextInput, View } from 'react-native';
import { api } from './src/api';
import { SKILLS, t } from './src/i18n';
import ClientScreen from './src/ClientScreen';
import TechnicianScreen from './src/TechnicianScreen';

// Accesso fittizio (solo nome) finche' l'API non ha l'autenticazione vera.
export default function App() {
  const [role, setRole] = useState('client');
  const [name, setName] = useState('');
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');

  const enter = async () => {
    setError('');
    try {
      setUser(await api('POST', '/users', { name, role, skills: role === 'technician' ? SKILLS : [] }));
    } catch (e) { setError(e.message); }
  };

  return (
    <SafeAreaView style={s.root}>
      <Text style={s.title}>HomeTech</Text>
      {!user ? (
        <View style={s.box}>
          <View style={s.row}>
            <Button title={role === 'client' ? `[${t.client}]` : t.client} onPress={() => setRole('client')} />
            <Button title={role === 'technician' ? `[${t.technician}]` : t.technician} onPress={() => setRole('technician')} />
          </View>
          <TextInput style={s.input} placeholder={t.name} value={name} onChangeText={setName} />
          <Button title={t.enter} onPress={enter} />
        </View>
      ) : user.role === 'client' ? (
        <ClientScreen user={user} onError={setError} />
      ) : (
        <TechnicianScreen user={user} onError={setError} />
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
