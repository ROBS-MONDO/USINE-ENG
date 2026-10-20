<?php
// Strumento da riga di comando per creare l'hash della password dello staff.
// Uso (non dal browser):  php make-hash.php "LA_TUA_PASSWORD"
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
if (empty($argv[1])) { fwrite(STDERR, "Uso: php make-hash.php \"password\"\n"); exit(1); }
echo password_hash($argv[1], PASSWORD_DEFAULT), "\n";
