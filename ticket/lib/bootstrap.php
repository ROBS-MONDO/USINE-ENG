<?php
// Funzioni comuni: configurazione, database, log
date_default_timezone_set('Europe/Luxembourg');

function cfg() {
    static $c = null;
    if ($c === null) {
        $f = __DIR__ . '/../config.php';
        if (!is_file($f)) { throw new RuntimeException('config.php mancante'); }
        $c = require $f;
    }
    return $c;
}

function db() {
    static $pdo = null;
    if ($pdo === null) {
        $d = cfg()['db'];
        $pdo = new PDO('mysql:host=' . $d['host'] . ';dbname=' . $d['name'] . ';charset=utf8mb4', $d['user'], $d['pass'], [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}

function now() { return date('Y-m-d H:i:s'); }

function log_line($msg) {
    $dir = __DIR__ . '/../logs';
    if (!is_dir($dir)) { @mkdir($dir, 0750, true); }
    @file_put_contents($dir . '/webhook.log', '[' . now() . '] ' . $msg . "\n", FILE_APPEND | LOCK_EX);
}
