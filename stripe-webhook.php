<?php
// Riceve gli eventi di Stripe: dopo il pagamento del biglietto crea i biglietti con QR e li invia per email.
require_once __DIR__ . '/ticket/lib/tickets.php';

header('Content-Type: text/plain; charset=utf-8');

function out($code, $msg = '') { http_response_code($code); echo $msg; exit; }

if ($_SERVER['REQUEST_METHOD'] !== 'POST') { out(405); }

$payload = file_get_contents('php://input');
$sigHeader = $_SERVER['HTTP_STRIPE_SIGNATURE'] ?? '';

try {
    $cfg = cfg();
} catch (Throwable $e) {
    log_line('config: ' . $e->getMessage());
    out(500);
}
$secret = $cfg['stripe']['webhook_secret'] ?? '';
if ($secret === '') { log_line('webhook_secret non impostato'); out(500); }

// Verifica della firma Stripe (HMAC-SHA256 su "timestamp.payload", tolleranza 5 minuti)
$ts = null; $sigs = [];
foreach (explode(',', $sigHeader) as $part) {
    $kv = explode('=', trim($part), 2);
    if (count($kv) !== 2) { continue; }
    if ($kv[0] === 't') { $ts = $kv[1]; }
    if ($kv[0] === 'v1') { $sigs[] = $kv[1]; }
}
if ($ts === null || !$sigs || abs(time() - (int)$ts) > 300) { out(400, 'bad signature'); }
$expected = hash_hmac('sha256', $ts . '.' . $payload, $secret);
$ok = false;
foreach ($sigs as $s) { if (hash_equals($expected, $s)) { $ok = true; } }
if (!$ok) { out(400, 'bad signature'); }

$event = json_decode($payload, true);
if (!is_array($event) || empty($event['type'])) { out(400); }
$type = $event['type'];
$obj = $event['data']['object'] ?? [];

try {
    if ($type === 'checkout.session.completed' || $type === 'checkout.session.async_payment_succeeded') {
        if (($obj['payment_status'] ?? '') !== 'paid') { out(200, 'not paid yet'); }
        $linkId = $cfg['stripe']['payment_link_id'] ?? '';
        if ($linkId === '' || ($obj['payment_link'] ?? '') !== $linkId) { out(200, 'other product'); }

        $sessionId = (string)$obj['id'];
        $email = trim((string)($obj['customer_details']['email'] ?? $obj['customer_email'] ?? ''));
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) { log_line("session $sessionId: email mancante"); out(200, 'no email'); }
        $name = tk_clean_name($obj['customer_details']['name'] ?? '');

        // Quantita': dall'API Stripe se c'e' la chiave, altrimenti dall'importo pagato
        $price = max(1, (int)($cfg['stripe']['ticket_price_cents'] ?? 6000));
        $qty = 0;
        $apiKey = $cfg['stripe']['api_key'] ?? '';
        if ($apiKey !== '') {
            $ctx = stream_context_create(['http' => ['method' => 'GET', 'header' => "Authorization: Bearer $apiKey\r\n", 'timeout' => 10, 'ignore_errors' => true]]);
            $res = @file_get_contents('https://api.stripe.com/v1/checkout/sessions/' . rawurlencode($sessionId) . '/line_items?limit=10', false, $ctx);
            $j = $res ? json_decode($res, true) : null;
            foreach (($j['data'] ?? []) as $li) { $qty += (int)($li['quantity'] ?? 0); }
        }
        if ($qty < 1) { $qty = intdiv((int)($obj['amount_total'] ?? 0), $price); }
        $qty = max(1, min(20, $qty));

        $lock = db()->query("SELECT GET_LOCK(" . db()->quote('lf_' . substr(md5($sessionId), 0, 20)) . ", 20)")->fetchColumn();
        tk_create_for_session($sessionId, (string)($obj['payment_intent'] ?? ''), $name, $email, $qty);
        tk_send_for_session($sessionId, true);
        log_line("session $sessionId: $qty biglietti");
        out(200, 'ok');
    }

    if ($type === 'charge.refunded') {
        // rimborso totale: annulla i biglietti collegati
        if (!empty($obj['refunded']) && !empty($obj['payment_intent'])) {
            $n = tk_cancel_by_payment_intent((string)$obj['payment_intent']);
            log_line('rimborso ' . $obj['payment_intent'] . ": $n biglietti annullati");
        }
        out(200, 'ok');
    }

    out(200, 'ignored');
} catch (Throwable $e) {
    log_line('errore: ' . get_class($e) . ' ' . $e->getMessage());
    out(500, 'error'); // Stripe riprova piu' volte
}
