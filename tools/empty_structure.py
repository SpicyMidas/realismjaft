# Writes a gzip NBT structure template of minecraft:air, DataVersion 3955 (MC 1.21.1).
# usage: empty_structure.py out.nbt [sx sy sz]  (default 3 3 3)
import gzip, struct, sys
def s(x): b = x.encode(); return struct.pack('>H', len(b)) + b
def named(t, name, payload): return bytes([t]) + s(name) + payload
def i(v): return struct.pack('>i', v)
def int_list(vals): return bytes([3]) + i(len(vals)) + b''.join(i(v) for v in vals)
def compound(items): return b''.join(items) + b'\x00'
def comp_list(comps): return bytes([10]) + i(len(comps)) + b''.join(comps)
sx, sy, sz = (int(a) for a in sys.argv[2:5]) if len(sys.argv) > 4 else (3, 3, 3)
blocks = [compound([named(9, 'pos', int_list([x, y, z])), named(3, 'state', i(0))])
          for x in range(sx) for y in range(sy) for z in range(sz)]
root = compound([
    named(3, 'DataVersion', i(3955)),
    named(9, 'size', int_list([sx, sy, sz])),
    named(9, 'palette', comp_list([compound([named(8, 'Name', s('minecraft:air'))])])),
    named(9, 'blocks', comp_list(blocks)),
    named(9, 'entities', bytes([10]) + i(0)),
])
open(sys.argv[1], 'wb').write(gzip.compress(named(10, '', root), mtime=0))
