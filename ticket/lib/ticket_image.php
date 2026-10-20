<?php
// Disegna il biglietto (PNG 1200x2120) con GD, come nel progetto grafico: sfondo scuro, cornice oro, QR.
require_once __DIR__ . '/../vendor/qrcode/qrcode.php';

const TK_SCALE = 2; // 1 punto del progetto = 2 pixel

function tk_px($v) { return (int)round($v * TK_SCALE); }

// Larghezza in pixel di un testo con un certo corpo
function tk_text_width($size, $font, $text) {
    $b = imagettfbbox($size, 0, $font, $text);
    return $b[2] - $b[0];
}

// Corpo del carattere (in pt) per far stare il testo nella larghezza voluta (in pt)
function tk_fit_size($text, $font, $widthPt, $maxPt) {
    $lo = 4.0; $hi = (float)$maxPt;
    for ($i = 0; $i < 18; $i++) {
        $mid = ($lo + $hi) / 2;
        if (tk_text_width($mid * TK_SCALE * 0.75, $font, $text) <= $widthPt * TK_SCALE) { $lo = $mid; } else { $hi = $mid; }
    }
    return $lo;
}

// Scrive il testo centrato su $cx (pt); $baseline e' la linea di base (pt). Il corpo e' in pt.
function tk_center_text($im, $sizePt, $font, $color, $text, $cx, $baseline) {
    $size = $sizePt * TK_SCALE * 0.75; // GD usa i punti a 72 dpi: 1 pt del progetto = 2 px
    $w = tk_text_width($size, $font, $text);
    imagettftext($im, $size, 0, (int)round($cx * TK_SCALE - $w / 2), tk_px($baseline), $color, $font, $text);
}

function tk_rounded_rect($im, $x1, $y1, $x2, $y2, $r, $color) {
    imagefilledrectangle($im, $x1 + $r, $y1, $x2 - $r, $y2, $color);
    imagefilledrectangle($im, $x1, $y1 + $r, $x2, $y2 - $r, $color);
    imagefilledellipse($im, $x1 + $r, $y1 + $r, $r * 2, $r * 2, $color);
    imagefilledellipse($im, $x2 - $r, $y1 + $r, $r * 2, $r * 2, $color);
    imagefilledellipse($im, $x1 + $r, $y2 - $r, $r * 2, $r * 2, $color);
    imagefilledellipse($im, $x2 - $r, $y2 - $r, $r * 2, $r * 2, $color);
}

function tk_star($im, $cx, $cy, $r, $color) {
    $k = $r * 0.18;
    imagefilledpolygon($im, [$cx, $cy - $r, $cx + $k, $cy - $k, $cx + $r, $cy, $cx + $k, $cy + $k, $cx, $cy + $r, $cx - $k, $cy + $k, $cx - $r, $cy, $cx - $k, $cy - $k], 8, $color);
}

/**
 * @param string $name  Nome del partecipante
 * @param string $code  Codice del biglietto (e contenuto del QR)
 * @param array  $ev    Dati evento: date, city
 * @return string       Contenuto del file PNG
 */
function ticket_png($name, $code, array $ev) {
    $dir = __DIR__ . '/../fonts/';
    $serifB = $dir . 'DejaVuSerif-Bold.ttf';
    $sansR  = $dir . 'Carlito-Regular.ttf';
    $sansB  = $dir . 'Carlito-Bold.ttf';
    $W = tk_px(600); $H = tk_px(1060);
    $im = imagecreatetruecolor($W, $H);
    imagealphablending($im, true);
    imageantialias($im, true);

    // Sfondo: sfumatura verticale
    for ($y = 0; $y < $H; $y++) {
        $t = $y / ($H - 1);
        $c = imagecolorallocate($im, (int)round(43 - 17 * $t), (int)round(13 - 6 * $t), (int)round(29 - 12 * $t));
        imageline($im, 0, $y, $W, $y, $c);
    }
    $gold   = imagecolorallocate($im, 233, 201, 135);
    $white  = imagecolorallocate($im, 255, 255, 255);
    $soft   = imagecolorallocate($im, 247, 223, 232);
    $dark   = imagecolorallocate($im, 43, 13, 29);
    $line   = imagecolorallocate($im, 173, 144, 101);
    $border = imagecolorallocate($im, 153, 124, 90);

    // Stelline di sfondo
    mt_srand(2026);
    for ($i = 0; $i < 70; $i++) {
        $x = mt_rand(30, $W - 30); $y = mt_rand(30, $H - 30);
        $a = mt_rand(70, 115);
        imagefilledellipse($im, $x, $y, mt_rand(2, 4), mt_rand(2, 4), imagecolorallocatealpha($im, 233, 201, 135, $a));
    }
    foreach ([[535, 74, 22], [274, 78, 11], [342, 153, 10], [488, 192, 15], [494, 347, 9]] as $s) {
        tk_star($im, tk_px($s[0]), tk_px($s[1]), tk_px($s[2]), $gold);
    }

    // Cornice: anello oro, poi l'interno ridisegnato con lo sfondo (sfumatura e stelline) come "tessera"
    $bx1 = tk_px(19); $by1 = tk_px(19); $bx2 = $W - tk_px(19); $by2 = $H - tk_px(19); $br = tk_px(26); $in = 4;
    $bg = imagecreatetruecolor($W, $H);
    imagecopy($bg, $im, 0, 0, 0, 0, $W, $H);
    tk_rounded_rect($im, $bx1, $by1, $bx2, $by2, $br, $border);
    imagesettile($im, $bg);
    tk_rounded_rect($im, $bx1 + $in, $by1 + $in, $bx2 - $in, $by2 - $in, $br - $in, IMG_COLOR_TILED);
    imagedestroy($bg);

    // Titolo
    $titleSize = tk_fit_size('ЗВЁЗДНЫЙ', $serifB, 351.5, 80) ;
    tk_center_text($im, $titleSize, $serifB, $white, 'ЗВЁЗДНЫЙ', 300, 120);
    tk_center_text($im, $titleSize, $serifB, $white, 'ЧАС', 300, 175);
    $sub = 'МЕЖДУНАРОДНЫЙ ТРАНСФОРМАЦИОННЫЙ ФЕСТИВАЛЬ';
    tk_center_text($im, tk_fit_size($sub, $sansR, 466.8, 40), $sansR, $soft, $sub, 300, 216);

    // Fascia "Билет участника"
    tk_rounded_rect($im, tk_px(60), tk_px(244), tk_px(540), tk_px(299), tk_px(27), $gold);
    $pill = 'БИЛЕТ УЧАСТНИКА';
    tk_center_text($im, tk_fit_size($pill, $serifB, 269.1, 40), $serifB, $dark, $pill, 300, 282);

    // Data e citta'
    $date = (string)($ev['date'] ?? '12.12.2026');
    tk_center_text($im, tk_fit_size($date, $sansR, 182.4, 60), $sansR, $white, $date, 300, 357);
    $city = (string)($ev['city'] ?? 'ЛЮКСЕМБУРГ');
    tk_center_text($im, tk_fit_size($city, $sansB, 139.5, 40), $sansB, $gold, $city, 300, 392);

    // Partecipante
    tk_center_text($im, tk_fit_size('УЧАСТНИК', $sansR, 76.3, 40), $sansR, $soft, 'УЧАСТНИК', 300, 451);
    $nameFit = mb_substr(trim($name), 0, 60, 'UTF-8');
    // corpo di riferimento: "Имя Фамилия" larga 197 pt; i nomi lunghi si riducono fino a 480 pt
    $nameSize = min(tk_fit_size($nameFit, $sansB, 480, 100), tk_fit_size('Имя Фамилия', $sansB, 197, 100));
    tk_center_text($im, $nameSize, $sansB, $white, $nameFit, 300, 490);

    // Linea tratteggiata con "tacche" ai lati
    imagesetthickness($im, 2);
    for ($x = tk_px(34); $x < $W - tk_px(34); $x += 24) {
        imageline($im, $x, tk_px(530), min($x + 12, $W - tk_px(34)), tk_px(530), $line);
    }
    imagesetthickness($im, 1);
    $notch = imagecolorallocate($im, 26, 7, 17);
    imagefilledellipse($im, tk_px(19), tk_px(530), tk_px(28), tk_px(28), $notch);
    imagefilledellipse($im, $W - tk_px(19), tk_px(530), tk_px(28), tk_px(28), $notch);

    // Riquadro bianco con il QR
    tk_rounded_rect($im, tk_px(145), tk_px(550), tk_px(455), tk_px(859), tk_px(17), $white);
    $qr = QRCode::getMinimumQRCode($code, QR_ERROR_CORRECT_LEVEL_M);
    $n = $qr->getModuleCount();
    $quiet = 0; // il riquadro bianco fa da margine
    $cell = (int)floor(tk_px(210) / ($n + 2 * $quiet));
    $qrSize = $cell * $n;
    $ox = (int)round(tk_px(300) - $qrSize / 2);
    $oy = (int)round(tk_px(704.5) - $qrSize / 2);
    $black = imagecolorallocate($im, 0, 0, 0);
    for ($r = 0; $r < $n; $r++) {
        for ($c = 0; $c < $n; $c++) {
            if ($qr->isDark($r, $c)) {
                imagefilledrectangle($im, $ox + $c * $cell, $oy + $r * $cell, $ox + ($c + 1) * $cell - 1, $oy + ($r + 1) * $cell - 1, $black);
            }
        }
    }

    // Codice sotto il QR e piede
    tk_center_text($im, tk_fit_size('ZC26-P0001-CE2F02', $sansB, 196.5, 40) , $sansB, $gold, $code, 300, 900);
    $f1 = 'Покажите QR-код на входе — билет действителен один раз';
    $f2 = 'luxembourgfestival.com  ·  info@luxembourgfestival.com';
    tk_center_text($im, tk_fit_size($f1, $sansR, 432, 40), $sansR, $soft, $f1, 300, 961);
    tk_center_text($im, tk_fit_size($f2, $sansR, 395.1, 40), $sansR, $soft, $f2, 300, 991);

    ob_start();
    imagepng($im, null, 6);
    $png = ob_get_clean();
    imagedestroy($im);
    return $png;
}
