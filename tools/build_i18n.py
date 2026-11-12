#!/usr/bin/env python3
"""Genera le pagine in altre lingue partendo dalle pagine russe.

Uso:  python3 tools/build_i18n.py        (dalla cartella principale del progetto)
Le traduzioni stanno in i18n/<lingua>.json: {"text": {russo: traduzione}, "script": {russo: traduzione}}.
Le recensioni (<article class="rev">) restano in russo.
"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import i18n_lib as L

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PAGES = ['index.html', 'sila-obedineniya.html', 'privacy.html']
LANGS = {'en': {'locale': 'en_GB', 'name': 'English'}, 'fr': {'locale': 'fr_FR', 'name': 'Français'}, 'de': {'locale': 'de_DE', 'name': 'Deutsch'}}
SITE = 'https://www.luxembourgfestival.com'


def page_url(lang, page):
    base = '/' if lang == 'ru' else '/%s/' % lang
    return base if page == 'index.html' else base + page


def fix_paths(html):
    """Le pagine in /fr/ stanno una cartella sotto: i file (foto, video, send.php) vanno cercati in ../"""
    def rel(v):
        if not v or v == './' or v[0] in '#/' or re.match(r'(https?:|mailto:|tel:|data:|javascript:)', v):
            return v
        if v.split('#')[0].split('?')[0] in PAGES:
            return v
        return '../' + v
    def tag(m):
        t = m.group(0)
        return re.sub(r'\b(src|href|poster|srcset|action)="([^"]*)"', lambda a: '%s="%s"' % (a.group(1), rel(a.group(2))), t)
    out = []
    for kind, chunk in L.split_blocks(html):
        if kind == 'style':
            chunk = chunk.replace('url("foto/', 'url("../foto/')
            out.append(chunk)
        elif kind == 'script':
            out.append(chunk)
        else:
            out.append(re.sub(r'<[a-zA-Z][^>]*>', tag, chunk))
    return ''.join(out)


def build(lang, page, data):
    src = open(os.path.join(ROOT, page), encoding='utf-8').read()
    T, S = data['text'], data['script']
    missing = []

    def tt(t):
        if t in T:
            return T[t]
        missing.append(t)
        return t

    def ta(v):
        if v in T:
            return T[v]
        missing.append(v)
        return v

    def ts(chunk):
        for ru, tr in S.items():
            chunk = chunk.replace(ru, tr)
        return chunk

    html = L.process(src, tt, ta, ts)
    html = fix_paths(html)
    # lingua, indirizzi e metadati
    html = html.replace('<html lang="ru">', '<html lang="%s">' % lang, 1)
    html = html.replace('content="ru_RU"', 'content="%s"' % LANGS[lang]['locale'])
    url_ru = SITE + page_url('ru', page)
    url_l = SITE + page_url(lang, page)
    html = html.replace('<link rel="canonical" href="%s">' % url_ru, '<link rel="canonical" href="%s">' % url_l)
    html = html.replace('<meta property="og:url" content="%s">' % url_ru, '<meta property="og:url" content="%s">' % url_l)
    # modulo di candidatura: indica la lingua della pagina
    html = re.sub(r'(<form class="modal-form" id="apply-form"[^>]*>)', r'\1\n      <input type="hidden" name="lang" value="%s">' % lang, html)
    # selettore lingue: evidenzia quella attiva
    html = re.sub(r'(<a href="[^"]*" class="lang)( active)?(" hreflang="(\w+)")',
                  lambda m: m.group(1) + (' active' if m.group(4) == lang else '') + m.group(3), html)
    return html, missing


def main():
    ok = True
    for lang in LANGS:
        path = os.path.join(ROOT, 'i18n', lang + '.json')
        data = json.load(open(path, encoding='utf-8'))
        outdir = os.path.join(ROOT, lang)
        os.makedirs(outdir, exist_ok=True)
        for page in PAGES:
            html, missing = build(lang, page, data)
            open(os.path.join(outdir, page), 'w', encoding='utf-8').write(html)
            left = len(L.CYR.findall(re.sub(r'<article class="rev".*?</article>', '', html, flags=re.S)))
            print('%s/%s: %d testi senza traduzione, %d lettere cirilliche residue' % (lang, page, len(missing), left))
            for m in missing[:8]:
                print('   MANCA:', m[:90]); ok = False
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
