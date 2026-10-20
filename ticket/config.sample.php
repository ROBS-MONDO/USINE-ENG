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
        'payment_link_id' => '',   // ID del Payment Link del biglietto da 60 euro, inizia con plink_
        'ticket_price_cents' => 6000,
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
