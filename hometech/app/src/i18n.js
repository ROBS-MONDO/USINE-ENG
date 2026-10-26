import { getLocales } from 'expo-localization';

export const SKILLS = ['idraulico', 'elettricista', 'fabbro', 'caldaista', 'imbianchino', 'tuttofare'];

const dict = {
  fr: {
    name: 'Votre nom', client: 'Je cherche un technicien', technician: 'Je suis technicien', enter: 'Entrer',
    whatNeeded: 'Quelle intervention ?', describe: 'Décrivez le problème', find: 'Trouver un technicien',
    status: 'Statut', nearby: 'Techniciens proches', refresh: 'Actualiser', arriving: 'Technicien',
    needLocation: 'Autorisation de localisation requise', available: 'Disponible', unavailable: 'Indisponible',
    open: 'Demandes proches', none: 'Aucune demande', accept: 'Accepter', next: 'Étape suivante', km: 'km',
    skills: 'Vos compétences',
    st: { open: 'Recherche en cours', accepted: 'Accepté', on_the_way: 'En route', in_progress: 'Intervention en cours', done: 'Terminé' },
    sk: { idraulico: 'Plombier', elettricista: 'Électricien', fabbro: 'Serrurier', caldaista: 'Chauffagiste', imbianchino: 'Peintre', tuttofare: 'Homme à tout faire' },
  },
  de: {
    name: 'Ihr Name', client: 'Ich suche einen Techniker', technician: 'Ich bin Techniker', enter: 'Weiter',
    whatNeeded: 'Welche Arbeit?', describe: 'Problem beschreiben', find: 'Techniker finden',
    status: 'Status', nearby: 'Techniker in der Nähe', refresh: 'Aktualisieren', arriving: 'Techniker',
    needLocation: 'Standortfreigabe erforderlich', available: 'Verfügbar', unavailable: 'Nicht verfügbar',
    open: 'Anfragen in der Nähe', none: 'Keine Anfragen', accept: 'Annehmen', next: 'Nächster Schritt', km: 'km',
    skills: 'Ihre Fachgebiete',
    st: { open: 'Suche läuft', accepted: 'Angenommen', on_the_way: 'Unterwegs', in_progress: 'In Arbeit', done: 'Erledigt' },
    sk: { idraulico: 'Klempner', elettricista: 'Elektriker', fabbro: 'Schlosser', caldaista: 'Heizungstechniker', imbianchino: 'Maler', tuttofare: 'Handwerker' },
  },
  en: {
    name: 'Your name', client: 'I need a technician', technician: "I'm a technician", enter: 'Continue',
    whatNeeded: 'What do you need?', describe: 'Describe the problem', find: 'Find a technician',
    status: 'Status', nearby: 'Nearby technicians', refresh: 'Refresh', arriving: 'Technician',
    needLocation: 'Location permission required', available: 'Available', unavailable: 'Unavailable',
    open: 'Nearby requests', none: 'No requests', accept: 'Accept', next: 'Next step', km: 'km',
    skills: 'Your skills',
    st: { open: 'Searching', accepted: 'Accepted', on_the_way: 'On the way', in_progress: 'Work in progress', done: 'Done' },
    sk: { idraulico: 'Plumber', elettricista: 'Electrician', fabbro: 'Locksmith', caldaista: 'Heating engineer', imbianchino: 'Painter', tuttofare: 'Handyman' },
  },
  it: {
    name: 'Il tuo nome', client: 'Cerco un tecnico', technician: 'Sono un tecnico', enter: 'Continua',
    whatNeeded: 'Che intervento serve?', describe: 'Descrivi il problema', find: 'Trova un tecnico',
    status: 'Stato', nearby: 'Tecnici vicini', refresh: 'Aggiorna', arriving: 'Tecnico',
    needLocation: 'Serve il permesso di localizzazione', available: 'Disponibile', unavailable: 'Non disponibile',
    open: 'Richieste vicine', none: 'Nessuna richiesta', accept: 'Accetta', next: 'Passo successivo', km: 'km',
    skills: 'Le tue competenze',
    st: { open: 'Ricerca in corso', accepted: 'Accettata', on_the_way: 'In arrivo', in_progress: 'Intervento in corso', done: 'Completata' },
    sk: { idraulico: 'Idraulico', elettricista: 'Elettricista', fabbro: 'Fabbro', caldaista: 'Caldaista', imbianchino: 'Imbianchino', tuttofare: 'Tuttofare' },
  },
  pt: {
    name: 'O seu nome', client: 'Preciso de um técnico', technician: 'Sou técnico', enter: 'Continuar',
    whatNeeded: 'De que serviço precisa?', describe: 'Descreva o problema', find: 'Encontrar um técnico',
    status: 'Estado', nearby: 'Técnicos próximos', refresh: 'Atualizar', arriving: 'Técnico',
    needLocation: 'É necessária a permissão de localização', available: 'Disponível', unavailable: 'Indisponível',
    open: 'Pedidos próximos', none: 'Sem pedidos', accept: 'Aceitar', next: 'Passo seguinte', km: 'km',
    skills: 'As suas competências',
    st: { open: 'A procurar', accepted: 'Aceite', on_the_way: 'A caminho', in_progress: 'Em curso', done: 'Concluído' },
    sk: { idraulico: 'Canalizador', elettricista: 'Eletricista', fabbro: 'Serralheiro', caldaista: 'Técnico de aquecimento', imbianchino: 'Pintor', tuttofare: 'Faz-tudo' },
  },
};

const code = getLocales()?.[0]?.languageCode;
export const t = dict[code] ?? dict.fr;
