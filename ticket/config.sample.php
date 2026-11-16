<?php
// Copia questo file in "config.php" (stessa cartella) e compila i valori.
// config.php contiene dati riservati: non va mai pubblicato né condiviso.
return [
    // Database MySQL (pannello Aruba > Database)
    'db' => [
        'host' => '',   // Hostname del database
        'name' => '',   // Nome database, per esempio Sql1234567_1
        'user' => '',   // Username database
        'pass' => '',   // Password database
    ],

    'stripe' => [
        'webhook_secret'  => '',   // "Signing secret" del webhook, inizia con whsec_
        // I Payment Link dei biglietti (ID che iniziano con plink_): per ognuno, quanti biglietti vale un acquisto e il prezzo in centesimi.
        'payment_links' => [
            'plink_INDIVIDUALE' => ['tickets' => 1, 'price_cents' => 3000],   // biglietto individuale 30 euro
            'plink_UNO_PIU_UNO' => ['tickets' => 2, 'price_cents' => 4000],   // formato 1+1, due partecipanti, 40 euro
            'plink_ADULTO_RAGAZZO' => ['tickets' => 2, 'price_cents' => 3000], // adulto + ragazzo, due persone, 30 euro
        ],
        'api_key' => '',           // facoltativa: chiave "restricted" con sola lettura su Checkout Sessions (rk_live_...), per contare i biglietti con precisione
    ],

    // Invio email dalla casella Aruba
    'smtp' => [
        'host'      => 'smtps.aruba.it',
        'port'      => 465,
        'secure'    => 'ssl',
        'user'      => 'info@luxembourgfestival.com',
        'pass'      => '',         // password della casella email
        'from'      => 'info@luxembourgfestival.com',
        'from_name' => 'Luxembourg Festival',
        'bcc'       => '',         // facoltativo: copia di ogni biglietto inviato (per esempio la tua email)
    ],

    // Evento (testi stampati sul biglietto)
    'event' => [
        'prefix' => 'ZC26',
        'date'   => '12.12.2026',
        'city'   => 'ЛЮКСЕМБУРГ',
    ],

    // Accesso alla pagina di scansione /scan/ (staff).
    // Crea l'hash con:  php -r "echo password_hash('LA_TUA_PASSWORD', PASSWORD_DEFAULT);"
    // oppure apri sul tuo computer strumenti/crea-hash-password.html (non va caricato sul sito).
    'scan' => [
        'password_hash' => '',
    ],
];
