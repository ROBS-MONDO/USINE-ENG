import { getLocales } from 'expo-localization';

export const SKILLS = ['idraulico', 'elettricista', 'fabbro', 'caldaista', 'imbianchino', 'tuttofare'];

const dict = {
  fr: {
    name: 'Votre nom', enter: 'Entrer',
    whatNeeded: 'Quelle intervention ?', describe: 'Décrivez le problème', find: 'Trouver un technicien',
    status: 'Statut', nearby: 'Techniciens proches', refresh: 'Actualiser', arriving: 'Technicien',
    needLocation: 'Autorisation de localisation requise',
    st: { open: 'Recherche en cours', accepted: 'Accepté', on_the_way: 'En route', in_progress: 'Intervention en cours', done: 'Terminé' },
    sk: { idraulico: 'Plombier', elettricista: 'Électricien', fabbro: 'Serrurier', caldaista: 'Chauffagiste', imbianchino: 'Peintre', tuttofare: 'Homme à tout faire' },
  },
  de: {
    name: 'Ihr Name', enter: 'Weiter',
    whatNeeded: 'Welche Arbeit?', describe: 'Problem beschreiben', find: 'Techniker finden',
    status: 'Status', nearby: 'Techniker in der Nähe', refresh: 'Aktualisieren', arriving: 'Techniker',
    needLocation: 'Standortfreigabe erforderlich',
    st: { open: 'Suche läuft', accepted: 'Angenommen', on_the_way: 'Unterwegs', in_progress: 'In Arbeit', done: 'Erledigt' },
    sk: { idraulico: 'Klempner', elettricista: 'Elektriker', fabbro: 'Schlosser', caldaista: 'Heizungstechniker', imbianchino: 'Maler', tuttofare: 'Handwerker' },
  },
  en: {
    name: 'Your name', enter: 'Continue',
    whatNeeded: 'What do you need?', describe: 'Describe the problem', find: 'Find a technician',
    status: 'Status', nearby: 'Nearby technicians', refresh: 'Refresh', arriving: 'Technician',
    needLocation: 'Location permission required',
    st: { open: 'Searching', accepted: 'Accepted', on_the_way: 'On the way', in_progress: 'Work in progress', done: 'Done' },
    sk: { idraulico: 'Plumber', elettricista: 'Electrician', fabbro: 'Locksmith', caldaista: 'Heating engineer', imbianchino: 'Painter', tuttofare: 'Handyman' },
  },
  it: {
    name: 'Il tuo nome', enter: 'Continua',
    whatNeeded: 'Che intervento serve?', describe: 'Descrivi il problema', find: 'Trova un tecnico',
    status: 'Stato', nearby: 'Tecnici vicini', refresh: 'Aggiorna', arriving: 'Tecnico',
    needLocation: 'Serve il permesso di localizzazione',
    st: { open: 'Ricerca in corso', accepted: 'Accettata', on_the_way: 'In arrivo', in_progress: 'Intervento in corso', done: 'Completata' },
    sk: { idraulico: 'Idraulico', elettricista: 'Elettricista', fabbro: 'Fabbro', caldaista: 'Caldaista', imbianchino: 'Imbianchino', tuttofare: 'Tuttofare' },
  },
  pt: {
    name: 'O seu nome', enter: 'Continuar',
    whatNeeded: 'De que serviço precisa?', describe: 'Descreva o problema', find: 'Encontrar um técnico',
    status: 'Estado', nearby: 'Técnicos próximos', refresh: 'Atualizar', arriving: 'Técnico',
    needLocation: 'É necessária a permissão de localização',
    st: { open: 'A procurar', accepted: 'Aceite', on_the_way: 'A caminho', in_progress: 'Em curso', done: 'Concluído' },
    sk: { idraulico: 'Canalizador', elettricista: 'Eletricista', fabbro: 'Serralheiro', caldaista: 'Técnico de aquecimento', imbianchino: 'Pintor', tuttofare: 'Faz-tudo' },
  },
};

const code = getLocales()?.[0]?.languageCode;
export const t = dict[code] ?? dict.fr;
