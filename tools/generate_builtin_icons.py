"""Regenerates app/src/main/kotlin/com/eversorhn/laun/data/BuiltinIcons.kt.

Source of truth for the glyphs is the "LAUN Icon Library" design artifact:
https://claude.ai/code/artifact/62e7a8f2-8c0f-4e82-89b5-7ba5356945d3

Point SRC below at a local copy of that page and run this file. It flattens every SVG shape the
page uses (rect/circle/line/polyline/polygon/ellipse) into plain path data, so the app itself never
needs an SVG parser — only Compose's own PathParser. BuiltinIcons.kt is GENERATED: edit this script
and re-run it, never the .kt file by hand.

Note the `~` flag separator in the emitted ops strings: it must not be `.`, which would split
decimal values like an opacity of 0.55 in half.
"""

import re, collections, io, math

SRC = r"C:\Users\eve\.claude\projects\C--eVe-Launcher\b933f7d4-d84b-4ec4-8225-24cfdc82e06d\tool-results\artifact-62e7a8f2-1787674921-c4e3.html"
OUT = r"C:\eVe-Launcher\app\src\main\kotlin\com\eversorhn\laun\data\BuiltinIcons.kt"

src = open(SRC, encoding='utf-8').read()
blk = src[src.index('const CATEGORIES'):]
cats = re.findall(r"\{ name: '([^']+)', icons: \[(.*?)\n\]\}", blk, re.S)


def num(s):
    return float(s)


def fmt(v):
    # compact number formatting
    if abs(v - round(v)) < 1e-6:
        return str(int(round(v)))
    return f'{v:.2f}'.rstrip('0').rstrip('.')


def pts(s):
    raw = re.split(r'[\s,]+', s.strip())
    return [(num(raw[i]), num(raw[i + 1])) for i in range(0, len(raw) - 1, 2)]


def rect_path(x, y, w, h, rx):
    if rx <= 0:
        return f'M{fmt(x)} {fmt(y)}H{fmt(x+w)}V{fmt(y+h)}H{fmt(x)}Z'
    r = min(rx, w / 2, h / 2)
    return (f'M{fmt(x+r)} {fmt(y)}H{fmt(x+w-r)}A{fmt(r)} {fmt(r)} 0 0 1 {fmt(x+w)} {fmt(y+r)}'
            f'V{fmt(y+h-r)}A{fmt(r)} {fmt(r)} 0 0 1 {fmt(x+w-r)} {fmt(y+h)}'
            f'H{fmt(x+r)}A{fmt(r)} {fmt(r)} 0 0 1 {fmt(x)} {fmt(y+h-r)}'
            f'V{fmt(y+r)}A{fmt(r)} {fmt(r)} 0 0 1 {fmt(x+r)} {fmt(y)}Z')


def ellipse_path(cx, cy, rx, ry):
    return (f'M{fmt(cx-rx)} {fmt(cy)}A{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx+rx)} {fmt(cy)}'
            f'A{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx-rx)} {fmt(cy)}Z')


def attrs_of(rest):
    return dict(re.findall(r'([a-zA-Z][a-zA-Z0-9-]*)="([^"]*)"', rest))


def element_path(tag, a):
    if tag == 'path':
        return a['d']
    if tag == 'rect':
        return rect_path(num(a['x']), num(a['y']), num(a['width']), num(a['height']), num(a.get('rx', 0)))
    if tag == 'circle':
        return ellipse_path(num(a['cx']), num(a['cy']), num(a['r']), num(a['r']))
    if tag == 'ellipse':
        return ellipse_path(num(a['cx']), num(a['cy']), num(a['rx']), num(a['ry']))
    if tag == 'line':
        return f"M{fmt(num(a['x1']))} {fmt(num(a['y1']))}L{fmt(num(a['x2']))} {fmt(num(a['y2']))}"
    if tag in ('polyline', 'polygon'):
        p = pts(a['points'])
        d = 'M' + ' L'.join(f'{fmt(x)} {fmt(y)}' for x, y in p)
        return d + ('Z' if tag == 'polygon' else '')
    raise ValueError(tag)


def parse_transform(t):
    """Returns (tx, ty, deg, px, py)."""
    tx = ty = deg = px = py = 0.0
    for kind, args in re.findall(r'(translate|rotate)\(([^)]*)\)', t):
        v = [num(x) for x in re.split(r'[\s,]+', args.strip()) if x]
        if kind == 'translate':
            tx += v[0]
            ty += v[1] if len(v) > 1 else 0.0
        else:
            deg += v[0]
            if len(v) > 2:
                px, py = v[1], v[2]
    return tx, ty, deg, px, py


icons = []
seen = collections.Counter()
for cat, body in cats:
    for name, svg in re.findall(r"\{ n:'([^']+)', s:`(.*?)`\s*\}", body, re.S):
        ops = []
        for tag, rest in re.findall(r'<(\w+)([^>]*?)/?>', svg):
            a = attrs_of(rest)
            d = element_path(tag, a)
            flags = []
            if a.get('class') == 'fill':
                flags.append('f')
            if 'opacity' in a:
                flags.append('o' + fmt(num(a['opacity'])))
            if 'stroke-width' in a:
                flags.append('w' + fmt(num(a['stroke-width'])))
            if 'stroke-dasharray' in a:
                on, off = [num(x) for x in re.split(r'[\s,]+', a['stroke-dasharray'].strip())][:2]
                flags.append(f'a{fmt(on)},{fmt(off)}')
            if 'transform' in a:
                tx, ty, deg, px, py = parse_transform(a['transform'])
                if tx or ty:
                    flags.append(f't{fmt(tx)},{fmt(ty)}')
                if deg:
                    flags.append(f'r{fmt(deg)},{fmt(px)},{fmt(py)}')
            ops.append(('~'.join(flags)) + '|' + d)
        slug = re.sub(r'[^a-z0-9]+', '_', name.lower()).strip('_')
        seen[slug] += 1
        if seen[slug] > 1:
            slug = f'{slug}_{seen[slug]}'
        icons.append((slug, name, cat, ';'.join(ops)))

cat_names = [c for c, _ in cats]
print('icons', len(icons), 'dupes', [k for k, v in seen.items() if v > 1])

esc = lambda s: s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$')
lines = []
w = lines.append
w('package com.eversorhn.laun.data')
w('')
w('/**')
w(' * LAUN\'s own tile icon set — 300 monochrome glyphs in 10 categories, drawn in the same angular')
w(' * vocabulary as the hex grid, selectable per tile as an alternative to the app\'s name text or its')
w(' * real launcher icon (see [BUILTIN_ICON_PACK]).')
w(' *')
w(' * Each icon is stored as its pre-flattened drawing program rather than as SVG markup: every')
w(' * source shape (rect/circle/line/polyline/polygon/ellipse) was converted to plain path data at')
w(' * generation time, so the app never needs an SVG parser — only Compose\'s own PathParser. [ops] is')
w(' * a `;`-separated list of draw operations, each `flags|pathData`, where flags is a `~`-separated')
w(' * set of single-letter directives, all optional and all defaulting to "stroke in the tile\'s')
w(' * current color":')
w(' *')
w(' * - `f`             fill this path instead of stroking it')
w(' * - `o<alpha>`      draw at this alpha (0..1)')
w(' * - `w<width>`      stroke width in viewport units, instead of [BUILTIN_ICON_STROKE]')
w(' * - `a<on>,<off>`   dashed stroke with this on/off pattern')
w(' * - `t<dx>,<dy>`    translate before drawing')
w(' * - `r<deg>,<x>,<y>` rotate by deg around (x, y) before drawing')
w(' *')
w(' * Path coordinates are in a [BUILTIN_ICON_VIEWPORT]-unit square and scaled at draw time.')
w(' */')
w('data class BuiltinIcon(')
w('    /** Stable id persisted in tile icon overrides — never renumber or reuse. */')
w('    val id: String,')
w('    val label: String,')
w('    val category: String,')
w('    val ops: String')
w(')')
w('')
w('/** Coordinate space every icon\'s path data is authored in. */')
w('const val BUILTIN_ICON_VIEWPORT = 48f')
w('')
w('/** Default stroke width, in viewport units. */')
w('const val BUILTIN_ICON_STROKE = 3.2f')
w('')
w('/**')
w(' * Sentinel "icon pack" id for LAUN\'s own set, stored in the same per-app override map real')
w(' * installed icon packs use — it can never collide with a real one, since Android package names')
w(' * can\'t contain a space.')
w(' */')
w('const val BUILTIN_ICON_PACK = "LAUN BUILT-IN"')
w('')
w('val BUILTIN_ICON_CATEGORIES: List<String> = listOf(')
for c in cat_names:
    w(f'    "{esc(c)}",')
w(')')
w('')
w('val BUILTIN_ICONS: List<BuiltinIcon> = listOf(')
for slug, name, cat, ops in icons:
    w(f'    BuiltinIcon("{esc(slug)}", "{esc(name)}", "{esc(cat)}", "{esc(ops)}"),')
w(')')
w('')
w('/** Icons by id — the lookup a persisted per-tile override resolves through. */')
w('val BUILTIN_ICONS_BY_ID: Map<String, BuiltinIcon> = BUILTIN_ICONS.associateBy { it.id }')
w('')

with io.open(OUT, 'w', encoding='utf-8', newline='\n') as f:
    f.write('\n'.join(lines))
print('wrote', OUT, sum(len(l) for l in lines), 'bytes')
