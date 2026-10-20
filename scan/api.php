<?php
// API della pagina di scansione (solo staff, con login)
require_once __DIR__ . '/../ticket/lib/tickets.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
session_name('lfscan');
session_set_cookie_params(['lifetime' => 0, 'path' => '/scan/', 'secure' => $https, 'httponly' => true, 'samesite' => 'Strict']);
session_start();

function reply($data, $code = 200) { http_response_code($code); echo json_encode($data, JSON_UNESCAPED_UNICODE); exit; }

$action = $_GET['action'] ?? $_POST['action'] ?? '';
$isPost = $_SERVER['REQUEST_METHOD'] === 'POST';

// Le richieste che modificano dati devono arrivare dal nostro JavaScript
if ($isPost && (($_SERVER['HTTP_X_REQUESTED_WITH'] ?? '') !== 'lf-scan')) { reply(['error' => 'forbidden'], 403); }

// Login con limite di tentativi per IP (8 ogni 15 minuti)
if ($action === 'login' && $isPost) {
    $ip = $_SERVER['REMOTE_ADDR'] ?? 'x';
    $file = sys_get_temp_dir() . '/lf_login_' . md5($ip);
    $hits = is_file($file) ? array_filter(array_map('intval', (array)@file($file, FILE_IGNORE_NEW_LINES)), function ($t) { return $t > time() - 900; }) : [];
    if (count($hits) >= 8) { reply(['error' => 'too_many'], 429); }
    $hash = cfg()['scan']['password_hash'] ?? '';
    $pw = (string)($_POST['password'] ?? '');
    if ($hash !== '' && password_verify($pw, $hash)) {
        session_regenerate_id(true);
        $_SESSION['staff'] = true;
        @unlink($file);
        reply(['ok' => true]);
    }
    $hits[] = time();
    @file_put_contents($file, implode("\n", $hits), LOCK_EX);
    usleep(600000);
    reply(['error' => 'bad_password'], 401);
}
if ($action === 'logout' && $isPost) { $_SESSION = []; session_destroy(); reply(['ok' => true]); }
if ($action === 'me') { reply(['staff' => !empty($_SESSION['staff'])]); }

if (empty($_SESSION['staff'])) { reply(['error' => 'login_required'], 401); }

function csv_cell($v) {
    $v = (string)$v;
    if ($v !== '' && strpos("=+-@\t\r", $v[0]) !== false) { $v = "'" . $v; } // protezione da formule nei fogli di calcolo
    return '"' . str_replace('"', '""', $v) . '"';
}

try {
    $pdo = db();

    if ($action === 'checkin' && $isPost) {
        $code = strtoupper(trim((string)($_POST['code'] ?? '')));
        if (!preg_match('/^[A-Z0-9]{2,8}-P\d{4,}-[0-9A-F]{6}$/', $code)) { reply(['result' => 'invalid']); }
        $who = 'staff';
        $st = $pdo->prepare("UPDATE tickets SET checked_in_at=?, checked_in_by=? WHERE code=? AND status='valid' AND checked_in_at IS NULL");
        $st->execute([now(), $who, $code]);
        $row = $pdo->prepare('SELECT code, holder_name, email, status, checked_in_at, seq, stripe_session_id FROM tickets WHERE code=?');
        $row->execute([$code]);
        $t = $row->fetch();
        if (!$t) { reply(['result' => 'invalid']); }
        $cnt = $pdo->prepare('SELECT COUNT(*) FROM tickets WHERE stripe_session_id=?');
        $cnt->execute([$t['stripe_session_id']]);
        $info = ['code' => $t['code'], 'name' => $t['holder_name'], 'seq' => (int)$t['seq'], 'of' => (int)$cnt->fetchColumn()];
        if ($st->rowCount() === 1) { reply(['result' => 'ok'] + $info); }
        if ($t['status'] !== 'valid') { reply(['result' => 'cancelled'] + $info); }
        reply(['result' => 'used', 'at' => $t['checked_in_at']] + $info);
    }

    if ($action === 'stats') {
        $r = $pdo->query("SELECT COUNT(*) AS total, SUM(checked_in_at IS NOT NULL) AS inside FROM tickets WHERE status='valid'")->fetch();
        reply(['total' => (int)$r['total'], 'inside' => (int)($r['inside'] ?? 0)]);
    }

    if ($action === 'search') {
        $q = trim((string)($_GET['q'] ?? ''));
        if (mb_strlen($q, 'UTF-8') < 2) { reply(['items' => []]); }
        $like = '%' . str_replace(['\\', '%', '_'], ['\\\\', '\\%', '\\_'], $q) . '%';
        $st = $pdo->prepare("SELECT code, holder_name, email, status, checked_in_at FROM tickets WHERE code IS NOT NULL AND (holder_name LIKE ? OR email LIKE ? OR code LIKE ?) ORDER BY holder_name LIMIT 25");
        $st->execute([$like, $like, $like]);
        reply(['items' => $st->fetchAll()]);
    }

    if ($action === 'resend' && $isPost) {
        $code = strtoupper(trim((string)($_POST['code'] ?? '')));
        $st = $pdo->prepare('SELECT stripe_session_id FROM tickets WHERE code=?');
        $st->execute([$code]);
        $sid = $st->fetchColumn();
        if (!$sid) { reply(['error' => 'not_found'], 404); }
        tk_send_for_session($sid, false);
        reply(['ok' => true]);
    }

    if ($action === 'export') {
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="biglietti-' . date('Ymd-His') . '.csv"');
        echo "\xEF\xBB\xBF";
        echo "codice,nome,email,stato,creato,email_inviata,ingresso\n";
        foreach ($pdo->query('SELECT code, holder_name, email, status, created_at, emailed_at, checked_in_at FROM tickets ORDER BY id') as $r) {
            echo implode(',', array_map('csv_cell', [$r['code'], $r['holder_name'], $r['email'], $r['status'], $r['created_at'], $r['emailed_at'], $r['checked_in_at']])) . "\n";
        }
        exit;
    }

    reply(['error' => 'bad_action'], 400);
} catch (Throwable $e) {
    log_line('scan: ' . get_class($e) . ' ' . $e->getMessage());
    reply(['error' => 'server_error'], 500);
}
