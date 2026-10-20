<?php
// Riceve il modulo di candidatura e lo invia per email a info@luxembourgfestival.com
header('Content-Type: application/json; charset=utf-8');

$to   = 'info@luxembourgfestival.com';
$from = 'info@luxembourgfestival.com'; // deve essere un indirizzo del dominio

function fail($code) { http_response_code($code); echo json_encode(['ok' => false]); exit; }
function clean($v, $max = 300) {
    $v = trim((string)$v);
    $v = preg_replace('/[\x00-\x1F\x7F]+/u', ' ', $v); // niente a capo né caratteri di controllo: evita l'iniezione di intestazioni
    return mb_substr($v, 0, $max, 'UTF-8');
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') fail(405);

// accetta solo richieste che arrivano dal sito stesso
$origin = $_SERVER['HTTP_ORIGIN'] ?? ($_SERVER['HTTP_REFERER'] ?? '');
if ($origin !== '' && !preg_match('#^https?://(www\.)?luxembourgfestival\.com(/|$)#i', $origin)) fail(403);

// limite anti-spam: max 5 invii ogni 10 minuti per indirizzo IP
$ip   = $_SERVER['REMOTE_ADDR'] ?? 'x';
$file = sys_get_temp_dir() . '/lf_rate_' . md5($ip);
$hits = [];
if (is_file($file)) {
    $hits = array_filter(array_map('intval', (array)@file($file, FILE_IGNORE_NEW_LINES)), function ($t) { return $t > time() - 600; });
}
if (count($hits) >= 5) fail(429);
$hits[] = time();
@file_put_contents($file, implode("\n", $hits), LOCK_EX);
if (!empty($_POST['website'])) { echo json_encode(['ok' => true]); exit; } // campo trappola per i bot

$name    = clean($_POST['name'] ?? '');
$city    = clean($_POST['city'] ?? '');
$phone   = clean($_POST['phone'] ?? '', 60);
$insta   = clean($_POST['instagram'] ?? '', 200);
$product = clean($_POST['product'] ?? '', 500);
$consent = !empty($_POST['consent']);

if ($name === '' || $city === '' || $phone === '' || $product === '' || !$consent) fail(422);

$body  = "Новая заявка тренера\n\n";
$body .= "Имя и фамилия: $name\n";
$body .= "Город и страна: $city\n";
$body .= "Телефон: $phone\n";
$body .= "Instagram: " . ($insta !== '' ? $insta : '—') . "\n";
$body .= "Специализация / продукт: $product\n";
$body .= "Согласие на обработку данных: да\n";
$body .= "\nIP: " . ($_SERVER['REMOTE_ADDR'] ?? '') . "\nДата: " . date('Y-m-d H:i:s') . "\n";

$subject = '=?UTF-8?B?' . base64_encode('Заявка тренера — Luxembourg Festival') . '?=';
$headers = "From: Luxembourg Festival <$from>\r\n"
         . "MIME-Version: 1.0\r\n"
         . "Content-Type: text/plain; charset=UTF-8\r\n";

$sent = @mail($to, $subject, $body, $headers);
echo json_encode(['ok' => (bool)$sent]);
