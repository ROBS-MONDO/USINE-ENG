"""Strumenti per generare le versioni in altre lingue partendo dalle pagine russe.

Le pagine russe (index.html, sila-obedineniya.html, privacy.html) restano la fonte.
Le traduzioni stanno in i18n/<lingua>.py (dizionari T e S). Lo script tools/build_i18n.py genera <lingua>/*.html.
"""
import re

CYR = re.compile(r'[Ѐ-ӿ]')
TAG = re.compile(r'(<!--.*?-->|<[^>]+>)', re.S)
ATTRS = ('alt', 'placeholder', 'aria-label', 'title', 'content')
META_OK = ('description', 'keywords', 'og:title', 'og:description', 'twitter:title', 'twitter:description')


def norm(t):
    return re.sub(r'\s+', ' ', t.strip())


def split_blocks(html):
    """Divide l'HTML in pezzi: testo semplice, tag, script/style (il contenuto resta intero)."""
    out = []
    pos = 0
    while pos < len(html):
        m = re.compile(r'<(script|style)\b[^>]*>', re.I).search(html, pos)
        if not m:
            out.append(('html', html[pos:]))
            break
        out.append(('html', html[pos:m.end()]))
        end = re.compile(r'</%s\s*>' % m.group(1), re.I).search(html, m.end())
        e = end.start() if end else len(html)
        out.append(('script' if m.group(1).lower() == 'script' else 'style', html[m.end():e]))
        pos = e
    return out


def iter_html(html_chunk, skip_depth_state):
    """Scorre un blocco di HTML restituendo (tipo, testo): 'text', 'tag'."""
    for part in TAG.split(html_chunk):
        if not part:
            continue
        if part.startswith('<!--') or part.startswith('<'):
            yield 'tag', part
        else:
            yield 'text', part


def attr_values(tag):
    """Restituisce i valori degli attributi traducibili di un tag."""
    res = []
    for m in re.finditer(r'\b(%s)="([^"]*)"' % '|'.join(ATTRS), tag):
        name, val = m.group(1), m.group(2)
        if name == 'content':
            mm = re.search(r'\b(?:name|property)="([^"]*)"', tag)
            if not mm or mm.group(1) not in META_OK:
                continue
        if CYR.search(val):
            res.append((name, val))
    return res


def process(html, translate_text, translate_attr, translate_script, skip_reviews=True):
    """Applica le funzioni di traduzione ai testi, agli attributi e agli script. Gli articoli .rev restano in russo."""
    out = []
    depth_rev = 0
    for kind, chunk in split_blocks(html):
        if kind in ('script', 'style'):
            out.append(translate_script(chunk) if kind == 'script' else chunk)
            continue
        for k, part in iter_html(chunk, None):
            if k == 'tag':
                if re.match(r'<article\b[^>]*class="[^"]*\brev\b', part):
                    depth_rev += 1
                elif depth_rev and part.startswith('</article'):
                    depth_rev -= 1
                if attr_values(part) and not depth_rev:
                    def rep(m):
                        name, val = m.group(1), m.group(2)
                        if name == 'content':
                            mm = re.search(r'\b(?:name|property)="([^"]*)"', part)
                            if not mm or mm.group(1) not in META_OK:
                                return m.group(0)
                        if not CYR.search(val):
                            return m.group(0)
                        return '%s="%s"' % (name, translate_attr(val))
                    part = re.sub(r'\b(%s)="([^"]*)"' % '|'.join(ATTRS), rep, part)
                out.append(part)
            else:
                if CYR.search(part) and not (skip_reviews and depth_rev):
                    lead = part[:len(part) - len(part.lstrip())]
                    trail = part[len(part.rstrip()):]
                    out.append(lead + translate_text(norm(part)) + trail)
                else:
                    out.append(part)
    return ''.join(out)


def collect(html):
    """Elenco dei testi e degli attributi in russo (per preparare le traduzioni)."""
    texts, attrs = [], []
    def tt(t):
        if t not in texts: texts.append(t)
        return t
    def ta(v):
        if v not in attrs: attrs.append(v)
        return v
    process(html, tt, ta, lambda s: s)
    return texts, attrs
