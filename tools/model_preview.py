import math

import numpy as np
from PIL import Image

class Cube:

    def __init__(self, tex, origin, size, inflate=0.0, mirror=False):
        self.tex = (float(tex[0]), float(tex[1]))
        self.origin = tuple(float(v) for v in origin)
        self.size = tuple(float(v) for v in size)
        if isinstance(inflate, (int, float)):
            self.inflate = (float(inflate),) * 3
        else:
            self.inflate = tuple(float(v) for v in inflate)
        self.mirror = bool(mirror)

    def faces(self):
        f, g, h = self.origin
        k, l, m = self.size
        n, o, p = self.inflate
        s, t, u = f + k, g + l, h + m
        f, g, h = f - n, g - o, h - p
        s, t, u = s + n, t + o, u + p
        if self.mirror:
            f, s = s, f

        verts = [
            (f, g, h),
            (s, g, h),
            (s, t, h),
            (f, t, h),
            (f, g, u),
            (s, g, u),
            (s, t, u),
            (f, t, u),
        ]

        i, j = self.tex
        w = i
        x = i + m
        y = i + m + k
        z = i + m + k + k
        aa = i + m + k + m
        ab = i + m + k + m + k
        ac = j
        ad = j + m
        ae = j + m + l

        quads = [
            ((5, 4, 0, 1), x, ac, y, ad, "down"),
            ((2, 3, 7, 6), y, ad, z, ac, "up"),
            ((0, 4, 7, 3), w, ad, x, ae, "west"),
            ((1, 0, 3, 2), x, ad, y, ae, "north"),
            ((5, 1, 2, 6), y, ad, aa, ae, "east"),
            ((4, 5, 6, 7), aa, ad, ab, ae, "south"),
        ]

        for order, u0, v0, u1, v1, name in quads:
            pts = [verts[q] for q in order]
            uvs = [(u1, v0), (u0, v0), (u0, v1), (u1, v1)]
            if self.mirror:
                pts = pts[::-1]
                uvs = uvs[::-1]
            yield pts, uvs, name

class Part:

    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=(),
                 scale=(1.0, 1.0, 1.0)):
        self.name = name
        self.pivot = tuple(float(v) for v in pivot)
        self.rot = tuple(float(v) for v in rot)
        self.cubes = list(cubes)
        self.children = list(children)
        self.scale = tuple(float(v) for v in scale)

    def add(self, child):
        self.children.append(child)
        return child

def _rot_zyx(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    rxm = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]], dtype=np.float64)
    rym = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
    rzm = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]], dtype=np.float64)
    return rzm @ rym @ rxm

def flatten(part, parent_m=None, parent_t=None, out=None, path=""):
    if out is None:
        out = []
    if parent_m is None:
        parent_m = np.eye(3)
    if parent_t is None:
        parent_t = np.zeros(3)

    here = path + "/" + part.name if path else part.name
    t = parent_t + parent_m @ np.array(part.pivot, dtype=np.float64)
    m = parent_m @ _rot_zyx(*part.rot)
    if part.scale != (1.0, 1.0, 1.0):
        m = m @ np.diag(part.scale)

    for cube in part.cubes:
        for pts, uvs, name in cube.faces():
            wpts = [tuple(t + m @ np.array(p, dtype=np.float64)) for p in pts]
            out.append((here, wpts, uvs, name, cube.mirror))

    for child in part.children:
        flatten(child, m, t, out, here)
    return out

_TO_DISPLAY = np.diag([1.0, -1.0, -1.0])

VIEWS = {
    "side": (-90.0, 0.0),
    "side_left": (90.0, 0.0),
    "top": (-90.0, 90.0),
    "bottom": (-90.0, -90.0),
    "front": (180.0, 0.0),
    "back": (0.0, 0.0),
    "three_quarter": (-128.0, 20.0),
    "low_three_quarter": (-125.0, -12.0),
}

def _basis(yaw_deg, pitch_deg):
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    view = np.array([
        math.sin(yaw) * math.cos(pitch),
        -math.sin(pitch),
        math.cos(yaw) * math.cos(pitch),
    ])
    right = np.array([math.cos(yaw), 0.0, -math.sin(yaw)])
    up = np.cross(view, right)
    return view, right, up

def _raster_tri(buf, zbuf, p0, p1, p2, shade, tex, uv0, uv1, uv2, flat_rgb):
    h, w = zbuf.shape
    xs = [p0[0], p1[0], p2[0]]
    ys = [p0[1], p1[1], p2[1]]
    x0, x1 = int(math.floor(min(xs))), int(math.ceil(max(xs)))
    y0, y1 = int(math.floor(min(ys))), int(math.ceil(max(ys)))
    x0, y0 = max(x0, 0), max(y0, 0)
    x1, y1 = min(x1, w - 1), min(y1, h - 1)
    if x1 < x0 or y1 < y0:
        return

    ax, ay = p0[0], p0[1]
    bx, by = p1[0], p1[1]
    cx, cy = p2[0], p2[1]
    det = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
    if abs(det) < 1e-12:
        return

    gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
    px = gx + 0.5
    py = gy + 0.5
    l1 = ((px - ax) * (cy - ay) - (cx - ax) * (py - ay)) / det
    l2 = ((bx - ax) * (py - ay) - (px - ax) * (by - ay)) / det
    l0 = 1.0 - l1 - l2
    eps = -1e-9
    inside = (l0 >= eps) & (l1 >= eps) & (l2 >= eps)
    if not inside.any():
        return

    depth = l0 * p0[2] + l1 * p1[2] + l2 * p2[2]
    sub = zbuf[y0:y1 + 1, x0:x1 + 1]
    win = inside & (depth < sub)
    if not win.any():
        return

    if tex is None:
        rgb = np.empty(win.shape + (3,), dtype=np.float64)
        rgb[..., :] = flat_rgb
        alpha = np.ones(win.shape)
    else:
        th, tw = tex.shape[0], tex.shape[1]
        u = l0 * uv0[0] + l1 * uv1[0] + l2 * uv2[0]
        v = l0 * uv0[1] + l1 * uv1[1] + l2 * uv2[1]
        u_lo = int(math.floor(min(uv0[0], uv1[0], uv2[0])))
        u_hi = max(u_lo, int(math.ceil(max(uv0[0], uv1[0], uv2[0]))) - 1)
        v_lo = int(math.floor(min(uv0[1], uv1[1], uv2[1])))
        v_hi = max(v_lo, int(math.ceil(max(uv0[1], uv1[1], uv2[1]))) - 1)
        ui = np.clip(np.floor(u).astype(np.int64), max(u_lo, 0), min(u_hi, tw - 1))
        vi = np.clip(np.floor(v).astype(np.int64), max(v_lo, 0), min(v_hi, th - 1))
        texel = tex[vi, ui]
        rgb = texel[..., :3].astype(np.float64)
        alpha = texel[..., 3].astype(np.float64) / 255.0

    win = win & (alpha > 0.5)
    if not win.any():
        return

    shaded = np.clip(rgb * shade, 0, 255)
    target = buf[y0:y1 + 1, x0:x1 + 1]
    target[win] = shaded[win]
    sub[win] = depth[win]

PART_PALETTE = [
    (214, 96, 86), (96, 160, 214), (122, 198, 120), (232, 184, 92),
    (168, 128, 206), (96, 198, 192), (226, 142, 96), (150, 160, 178),
    (204, 122, 170), (134, 186, 96), (120, 140, 220), (200, 200, 110),
]

def render(root, texture=None, view="side", size=(1400, 500), scale=None,
           mode="textured", bg=(248, 248, 246), light="view",
           ambient=0.58, margin=0.06, grid_blocks=None, grid_rgb=(226, 230, 234),
           part_colours=None):
    faces = flatten(root)
    if not faces:
        raise ValueError("model has no faces")

    yaw, pitch = VIEWS[view] if isinstance(view, str) else view
    view_dir, right, up = _basis(yaw, pitch)

    pts = np.array([p for _, quad, _, _, _ in faces for p in quad])
    disp = pts @ _TO_DISPLAY.T
    sx = disp @ right
    sy = disp @ up
    sz = disp @ view_dir

    w, h = size
    if scale is None:
        span_x = max(sx.max() - sx.min(), 1e-6)
        span_y = max(sy.max() - sy.min(), 1e-6)
        scale = min(w * (1 - 2 * margin) / span_x, h * (1 - 2 * margin) / span_y)
    cx = 0.5 * (sx.max() + sx.min())
    cy = 0.5 * (sy.max() + sy.min())

    buf = np.empty((h, w, 3), dtype=np.float64)
    buf[:, :] = bg
    zbuf = np.full((h, w), np.inf)

    if grid_blocks:
        step = grid_blocks * scale
        if step >= 4:
            off_x = w * 0.5 - cx * scale
            off_y = h * 0.5 + cy * scale
            k = int(off_x // step)
            while True:
                gx = int(round(off_x - k * step))
                if gx < 0:
                    break
                buf[:, gx] = grid_rgb
                k += 1
            k = 1
            while True:
                gx = int(round(off_x + k * step))
                if gx >= w:
                    break
                buf[:, gx] = grid_rgb
                k += 1
            for sign in (1, -1):
                k = 0
                while True:
                    gy = int(round(off_y + sign * k * step))
                    if gy < 0 or gy >= h:
                        break
                    buf[gy, :] = grid_rgb
                    k += 1

    tex = None
    if mode == "textured":
        if texture is None:
            raise ValueError('mode="textured" needs a texture')
        tex = np.array(texture.convert("RGBA"))

    if isinstance(light, str) and light == "view":
        light_v = -0.62 * view_dir + 0.66 * up - 0.42 * right
    else:
        light_v = np.array(light, dtype=np.float64)
    light_v = light_v / np.linalg.norm(light_v)

    part_names = []
    for name, _, _, _, _ in faces:
        if name not in part_names:
            part_names.append(name)

    for name, quad, uvs, _dirname, mirrored in faces:
        d = np.array(quad) @ _TO_DISPLAY.T
        px = (d @ right - cx) * scale + w * 0.5
        py = h * 0.5 - (d @ up - cy) * scale
        pz = d @ view_dir
        screen = [(px[q], py[q], pz[q]) for q in range(4)]

        e1 = np.array(quad[1]) - np.array(quad[0])
        e2 = np.array(quad[3]) - np.array(quad[0])
        nrm = np.cross(e1, e2)
        ln = np.linalg.norm(nrm)
        if ln < 1e-9:
            continue
        nrm = (nrm / ln) @ _TO_DISPLAY.T

        if mode == "silhouette":
            shade = 1.0
            flat = (24, 26, 30)
        elif mode == "parts":
            shade = ambient + (1 - ambient) * max(0.0, float(np.dot(nrm, light_v)))
            leaf = name.split("/")[-1]
            if part_colours and leaf in part_colours:
                flat = part_colours[leaf]
            else:
                flat = PART_PALETTE[part_names.index(name) % len(PART_PALETTE)]
        else:
            shade = ambient + (1 - ambient) * max(0.0, float(np.dot(nrm, light_v)))
            flat = (170, 170, 170)

        uv_arr = list(uvs)
        _raster_tri(buf, zbuf, screen[0], screen[1], screen[2], shade,
                    tex, uv_arr[0], uv_arr[1], uv_arr[2], flat)
        _raster_tri(buf, zbuf, screen[0], screen[2], screen[3], shade,
                    tex, uv_arr[0], uv_arr[2], uv_arr[3], flat)

    return Image.fromarray(buf.astype(np.uint8), "RGB")

def bounds(root):
    faces = flatten(root)
    pts = np.array([p for _, quad, _, _, _ in faces for p in quad]) @ _TO_DISPLAY.T
    return pts.min(axis=0), pts.max(axis=0)
