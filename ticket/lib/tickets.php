<?php
// Creazione dei biglietti, invio email, annullamento
require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/ticket_image.php';
require_once __DIR__ . '/../vendor/PHPMailer/Exception.php';
require_once __DIR__ . '/../vendor/PHPMailer/PHPMailer.php';
require_once __DIR__ . '/../vendor/PHPMailer/SMTP.php';

use PHPMailer\PHPMailer\PHPMailer;

function tk_clean_name($v, $fallback = 'Участник') {
    $v = trim(preg_replace('/[\x00-\x1F\x7F]+/u', ' ', (string)$v));
    $v = mb_substr($v, 0, 120, 'UTF-8');
    return $v !== '' ? $v : $fallback;
}

// Crea (una sola volta) $qty biglietti per un pagamento. Restituisce gli ID dei biglietti del pagamento.
function tk_create_for_session($sessionId, $paymentIntent, $name, $email, $qty) {
    $pdo = db();
    $prefix = cfg()['event']['prefix'] ?? 'ZC26';
    $ids = [];
    for ($seq = 1; $seq <= $qty; $seq++) {
        $pdo->beginTransaction();
        try {
            $st = $pdo->prepare('INSERT IGNORE INTO tickets (stripe_session_id, payment_intent, seq, holder_name, email, created_at) VALUES (?,?,?,?,?,?)');
            $st->execute([$sessionId, $paymentIntent, $seq, $name, $email, now()]);
            if ($st->rowCount() === 1) {
                $id = (int)$pdo->lastInsertId();
                $code = sprintf('%s-P%04d-%s', $prefix, $id, strtoupper(bin2hex(random_bytes(3))));
                $pdo->prepare('UPDATE tickets SET code=? WHERE id=?')->execute([$code, $id]);
            }
            $pdo->commit();
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }
    }
    $st = $pdo->prepare('SELECT id FROM tickets WHERE stripe_session_id=? ORDER BY seq');
    $st->execute([$sessionId]);
    return array_map('intval', array_column($st->fetchAll(), 'id'));
}

// Invia per email tutti i biglietti validi di un pagamento (per default solo quelli non ancora inviati)
function tk_send_for_session($sessionId, $onlyUnsent = true) {
    $pdo = db();
    $sql = "SELECT * FROM tickets WHERE stripe_session_id=? AND status='valid' AND code IS NOT NULL" . ($onlyUnsent ? ' AND emailed_at IS NULL' : '') . ' ORDER BY seq';
    $st = $pdo->prepare($sql);
    $st->execute([$sessionId]);
    $rows = $st->fetchAll();
    if (!$rows) { return true; }

    $c = cfg(); $smtp = $c['smtp']; $ev = $c['event'];
    $to = $rows[0]['email'];
    $n = count($rows);

    $m = new PHPMailer(true);
    $m->CharSet = 'UTF-8';
    $m->isSMTP();
    $m->Host = $smtp['host']; $m->Port = (int)$smtp['port'];
    $m->SMTPSecure = $smtp['secure']; $m->SMTPAuth = ($smtp['pass'] ?? '') !== '';
    $m->Username = $smtp['user']; $m->Password = $smtp['pass'];
    $m->Timeout = 20;
    $m->setFrom($smtp['from'], $smtp['from_name']);
    $m->addReplyTo($smtp['from'], $smtp['from_name']);
    $m->addAddress($to);
    if (!empty($smtp['bcc'])) { $m->addBCC($smtp['bcc']); }
    $m->Subject = $n > 1 ? 'Ваши билеты на «Звёздный час» — ' . $ev['date'] : 'Ваш билет на «Звёздный час» — ' . $ev['date'];

    $html = '<div style="font-family:Arial,Helvetica,sans-serif;color:#2b0d1d;max-width:560px;margin:0 auto">'
          . '<p style="font-size:16px">Здравствуйте!</p>'
          . '<p style="font-size:16px">Спасибо за оплату! ' . ($n > 1 ? 'Ваши билеты' : 'Ваш билет') . ' на международный трансформационный фестиваль «Звёздный час» (' . htmlspecialchars($ev['date']) . ', Люксембург) — ниже и во вложении.</p>'
          . '<p style="font-size:16px">Покажите QR-код на входе (с экрана телефона или распечатанный). Каждый билет действителен один раз.</p>';
    $text = "Здравствуйте!\n\nСпасибо за оплату! " . ($n > 1 ? 'Ваши билеты' : 'Ваш билет') . " на «Звёздный час» (" . $ev['date'] . ", Люксембург) — во вложении.\nПокажите QR-код на входе. Каждый билет действителен один раз.\n";

    $i = 0;
    foreach ($rows as $r) {
        $i++;
        $png = ticket_png($r['holder_name'], $r['code'], $ev);
        $cid = 'ticket' . $i;
        $m->addStringEmbeddedImage($png, $cid, 'bilet-' . $r['code'] . '.png', 'base64', 'image/png');
        $html .= '<p style="margin:24px 0 6px;font-size:14px;color:#6b5a66">' . ($n > 1 ? 'Билет ' . $i . ' из ' . $n . ' — ' : '') . htmlspecialchars($r['code']) . '</p>'
               . '<img src="cid:' . $cid . '" alt="Билет ' . htmlspecialchars($r['code']) . '" style="width:100%;max-width:360px;height:auto;border-radius:12px">';
    }
    $html .= '<p style="margin-top:28px;font-size:15px">Вопросы? Просто ответьте на это письмо или напишите на <a href="mailto:info@luxembourgfestival.com">info@luxembourgfestival.com</a>.</p>'
           . '<p style="font-size:15px">До встречи 12 декабря!<br>Команда «Звёздный час»</p></div>';
    $m->isHTML(true);
    $m->Body = $html;
    $m->AltBody = $text;
    $m->send();

    $ids = array_column($rows, 'id');
    $in = implode(',', array_fill(0, count($ids), '?'));
    $pdo->prepare("UPDATE tickets SET emailed_at=? WHERE id IN ($in)")->execute(array_merge([now()], $ids));
    return true;
}

// Annulla i biglietti di un pagamento rimborsato
function tk_cancel_by_payment_intent($pi) {
    $st = db()->prepare("UPDATE tickets SET status='cancelled' WHERE payment_intent=?");
    $st->execute([$pi]);
    return $st->rowCount();
}
