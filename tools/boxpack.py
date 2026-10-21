def ri(v):
    return max(1, int(round(v)))

def pack(boxes, atlas_w):
    items = sorted(boxes, key=lambda b: -(b[3] + b[2]))
    placed, shelves = {}, []
    for name, k, l, m, _role in items:
        fw, fh = 2 * m + 2 * k, m + l
        if fw > atlas_w:
            raise ValueError("%s footprint %d wider than atlas %d" % (name, fw, atlas_w))
        for sh in shelves:
            if sh[1] >= fh and sh[2] + fw <= atlas_w:
                placed[name] = (sh[2], sh[0])
                sh[2] += fw
                break
        else:
            y = shelves[-1][0] + shelves[-1][1] if shelves else 0
            shelves.append([y, fh, fw])
            placed[name] = (0, y)
    used = shelves[-1][0] + shelves[-1][1] if shelves else 0
    return placed, used

def check_pack(placed, boxes, atlas_w, atlas_h):
    rows = {}
    for name, k, l, m, _role in boxes:
        i, j = placed[name]
        fw, fh = 2 * m + 2 * k, m + l
        if i < 0 or j < 0 or i + fw > atlas_w or j + fh > atlas_h:
            raise AssertionError("%s at (%d,%d) %dx%d leaves a %dx%d sheet"
                                 % (name, i, j, fw, fh, atlas_w, atlas_h))
        for y in range(j, j + fh):
            row = rows.setdefault(y, [])
            for other, oi, ow in row:
                if i < oi + ow and oi < i + fw:
                    raise AssertionError("%s overlaps %s on row %d" % (name, other, y))
            row.append((name, i, fw))
    return True

def utilisation(boxes, atlas_w, atlas_h):
    area = sum((2 * b[3] + 2 * b[1]) * (b[3] + b[2]) for b in boxes)
    return area / float(atlas_w * atlas_h)

def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

class Rnd:

    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFF

    def next(self):
        self.s = (1103515245 * self.s + 12345) & 0x7FFFFFFF
        return self.s

    def frac(self):
        return self.next() / float(0x7FFFFFFF)

    def irange(self, lo, hi):
        return lo if hi <= lo else lo + self.next() % (hi - lo)

def regions(i, j, k, l, m):
    x, y, z = i + m, i + m + k, i + m + 2 * k
    aa, ab = i + 2 * m + k, i + 2 * m + 2 * k
    ac, ad, ae = j, j + m, j + m + l
    return dict(
        back=(x, ac, y, ad),
        belly=(y, ad, z, ac),
        west=(i, ad, x, ae),
        north=(x, ad, y, ae),
        east=(y, ad, aa, ae),
        south=(aa, ad, ab, ae),
    )

def filler(draw):
    def fill(rect, col):
        u0, v0, u1, v1 = rect
        if v1 < v0:
            v0, v1 = v1, v0
        if u1 > u0 and v1 > v0:
            draw.rectangle([u0, v0, u1 - 1, v1 - 1], fill=col)
    return fill
