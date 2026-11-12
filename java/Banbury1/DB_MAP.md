# Banbury 1 – mappa DB (da dump del 12/11/2026 14:10, macchina a regime)

Stato: **ipotesi** derivate dal dump; da confermare con valori Runtime letti nello stesso istante.
Offset in byte; `DBW n` = INT a offset n; STRING S7 = [max][len][caratteri].

## DB150 – lotto/identificazione (34 byte)
| Offset | Valore letto | Interpretazione | Fiducia |
|---|---|---|---|
| STRING[4] @6 | `"26"` | LOT anno (sinottico: `26 / 9004`) | alta |
| STRING[8] @12 | `"9004"` | LOT numero (sinottico: `9004`) | alta |
| DBW0 | 10 | SEQ (prima cifra, sinottico `10 / 21`) | media |
| DBW2 | 23 | SEQ (seconda cifra, sinottico `21`, potrebbe essere avanzata) | media |
| DBW22/24/26/28/30 | 4/2/1/1/1 | da capire (tipo/pass/suffisso/colore?) | – |
| @32 | `"12"` | stringa/campo da capire | – |

## DB201 – ricetta (384 byte)
| Offset | Valore letto | Interpretazione | Fiducia |
|---|---|---|---|
| DBW2 | 24 | n. ricetta? | bassa |
| STRING[32] @4 | `"gomma2"` | **nome ricetta** (dopo i 6 caratteri restano residui `carico opqr`: si ignorano) | alta |
| DBW28.. | 115, 80, 600, 300, … | parametri ricetta (80 = %?) | – |
| blocchi da 20–30 byte | `18, 36, 900 …` ripetuti da 52, 82, 112 | passi/fasi della ricetta | media |

## DB110 – stato macchina/valori (256 byte)
| Offset | Valore | Osservazione |
|---|---|---|
| DBW194, 222 | +1 al secondo, uguali | contatore tempo (s) |
| DBW202, 220 | +1 al secondo, uguali (194+140) | contatore tempo (s) |
| DBW72, DBW186 | 661…664, oscillano | misura analogica (da identificare) |
| DBW106, 108 (e 38/66 DB201) | 80 | probabile velocita' pistone su/giu' in % (sinottico: 80 %, 80 %) |
| DBW80, 100, 104, 208 | 26 | probabile temperatura/valore a freddo (da confermare) |
| DBW110, 118, 120 | 27 | idem |
| DBW112, 114, 116, 122, 124 | -3276/-3277 | valore di fondo scala negativo (input non collegato?) |
| DBW6, 22 | 0x0C00, 0x0C02 | parole con bit 10/11 attivi; bit 1 solo in DBW22 |
| DBW24 | 0x80AA | parola di stato |
| DBW44 | 0x100A | parola di stato |
| DBW54 | 0x177F | parola di stato |

## Per chiudere la mappa
Serve un dump con **screenshot del Runtime scattato nello stesso istante** (pressione,
temperatura, giri, corrente, lotto/seq, stati lampade) e, per i comandi, note a console
mentre si muovono pistone / porte / rotori.
