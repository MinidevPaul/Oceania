import gzip
import os
import struct

__all__ = ["Byte", "Short", "Int", "Long", "Float", "Double", "String",
           "ByteArray", "IntArray", "LongArray", "tag_id",
           "read_nbt", "write_nbt", "from_bytes", "to_bytes"]

class Byte(int): __slots__ = ()
class Short(int): __slots__ = ()
class Int(int): __slots__ = ()
class Long(int): __slots__ = ()
class Float(float): __slots__ = ()
class Double(float): __slots__ = ()
class String(str): __slots__ = ()
class ByteArray(list): __slots__ = ()
class IntArray(list): __slots__ = ()
class LongArray(list): __slots__ = ()

TAG_NAMES = {0: "TAG_End", 1: "TAG_Byte", 2: "TAG_Short", 3: "TAG_Int",
             4: "TAG_Long", 5: "TAG_Float", 6: "TAG_Double",
             7: "TAG_Byte_Array", 8: "TAG_String", 9: "TAG_List",
             10: "TAG_Compound", 11: "TAG_Int_Array", 12: "TAG_Long_Array"}

_WRAPPED = ((Byte, 1), (Short, 2), (Int, 3), (Long, 4), (Float, 5),
            (Double, 6), (ByteArray, 7), (String, 8), (IntArray, 11),
            (LongArray, 12))

def tag_id(value):
    if isinstance(value, bool):
        return 1
    for cls, tid in _WRAPPED:
        if isinstance(value, cls):
            return tid
    if isinstance(value, int):
        return 3
    if isinstance(value, float):
        return 6
    if isinstance(value, str):
        return 8
    if isinstance(value, dict):
        return 10
    if isinstance(value, (list, tuple)):
        return 9
    raise TypeError("cannot store %r as NBT" % (type(value).__name__,))

_WFMT = {2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}

def _put_str(out, text):
    raw = str(text).encode("utf-8")
    if len(raw) > 0xFFFF:
        raise ValueError("string payload is %d bytes, max 65535" % len(raw))
    out.extend(struct.pack(">H", len(raw)))
    out.extend(raw)

def _check_list(value):
    eid = tag_id(value[0]) if len(value) else 0
    for i, item in enumerate(value):
        got = tag_id(item)
        if got != eid:
            raise ValueError(
                "heterogeneous TAG_List: index %d is %s but index 0 is %s"
                % (i, TAG_NAMES[got], TAG_NAMES[eid]))
    return eid

def _put(out, tid, value):
    if tid == 1:
        out.append(int(value) & 0xFF)
    elif tid in _WFMT:
        out.extend(struct.pack(_WFMT[tid], value))
    elif tid == 8:
        _put_str(out, value)
    elif tid == 7:
        out.extend(struct.pack(">i", len(value)))
        out.extend(bytes(int(x) & 0xFF for x in value))
    elif tid == 11 or tid == 12:
        code = "i" if tid == 11 else "q"
        out.extend(struct.pack(">i", len(value)))
        out.extend(struct.pack(">%d%s" % (len(value), code), *value))
    elif tid == 10:
        for key, item in value.items():
            kid = tag_id(item)
            out.append(kid)
            _put_str(out, key)
            _put(out, kid, item)
        out.append(0)
    elif tid == 9:
        eid = _check_list(value)
        out.append(eid)
        out.extend(struct.pack(">i", len(value)))
        for item in value:
            _put(out, eid, item)
    else:
        raise ValueError("cannot write tag id %d" % tid)

def to_bytes(root, compressed=False):
    if not isinstance(root, dict):
        raise TypeError("root must be a dict (TAG_Compound), got %s"
                        % type(root).__name__)
    out = bytearray(b"\x0a\x00\x00")
    _put(out, 10, root)
    data = bytes(out)
    return gzip.compress(data, mtime=0) if compressed else data

def write_nbt(path, root, compressed=True):
    parent = os.path.dirname(os.path.abspath(path))
    if parent:
        os.makedirs(parent, exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(to_bytes(root, compressed))
    return path

_RSCALAR = {1: (">b", 1, Byte), 2: (">h", 2, Short), 3: (">i", 4, Int),
            4: (">q", 8, Long), 5: (">f", 4, Float), 6: (">d", 8, Double)}

def _get_str(data, pos):
    n, = struct.unpack_from(">H", data, pos)
    pos += 2
    return String(data[pos:pos + n].decode("utf-8")), pos + n

def _get(data, pos, tid):
    scalar = _RSCALAR.get(tid)
    if scalar is not None:
        fmt, width, cls = scalar
        value, = struct.unpack_from(fmt, data, pos)
        return cls(value), pos + width
    if tid == 8:
        return _get_str(data, pos)
    if tid == 7:
        n, = struct.unpack_from(">i", data, pos)
        pos += 4
        return ByteArray(struct.unpack_from(">%db" % n, data, pos)), pos + n
    if tid == 11 or tid == 12:
        code, width, cls = ("i", 4, IntArray) if tid == 11 else ("q", 8, LongArray)
        n, = struct.unpack_from(">i", data, pos)
        pos += 4
        values = struct.unpack_from(">%d%s" % (n, code), data, pos)
        return cls(values), pos + n * width
    if tid == 9:
        eid = data[pos]
        n, = struct.unpack_from(">i", data, pos + 1)
        pos += 5
        items = []
        for _ in range(n):
            item, pos = _get(data, pos, eid)
            items.append(item)
        return items, pos
    if tid == 10:
        compound = {}
        while True:
            cid = data[pos]
            pos += 1
            if cid == 0:
                return compound, pos
            name, pos = _get_str(data, pos)
            compound[str(name)], pos = _get(data, pos, cid)
    raise ValueError("unknown tag id %d at offset %d" % (tid, pos))

def from_bytes(data):
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    if not data or data[0] != 10:
        raise ValueError("expected a TAG_Compound root, first byte is %r"
                         % (data[:1],))
    _, pos = _get_str(data, 1)
    root, _ = _get(data, pos, 10)
    return root

def read_nbt(path):
    with open(path, "rb") as fh:
        return from_bytes(fh.read())
