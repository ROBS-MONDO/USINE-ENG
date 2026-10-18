<?php
// Riceve il modulo di candidatura e lo invia per email a info@luxembourgfestival.com
header('Content-Type: application/json; charset=utf-8');

$to   = 'info@luxembourgfestival.com';
$from = 'info@luxembourgfestival.com'; // deve essere un indirizzo del dominio

function fail($code) { http_response_code($code); echo json_encode(['ok' => false]); exit; }
function clean($v, $max = 300) {
    $v = trim((string)$v);
    $v = preg_replace('/[\r\n]+/', ' ', $v);          // niente a capo: evita l'iniezione di intestazioni
    return mb_substr($v, 0, $max, 'UTF-8');
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') fail(405);
if (!empty($_POST['website'])) { echo json_encode(['ok' => true]); exit; } // campo trappola per i bot

$name    = clean($_POST['name'] ?? '');
$city    = clean($_POST['city'] ?? '');
$phone   = clean($_POST['phone'] ?? '', 60);
$insta   = clean($_POST['instagram'] ?? '');
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
