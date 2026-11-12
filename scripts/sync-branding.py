#!/usr/bin/env python3
"""Generate platform assets from the approved indexed PNG without external packages."""
from pathlib import Path
import argparse
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
SIGNATURE = b'\x89PNG\r\n\x1a\n'


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)


def indexed_pixels(data):
    if not data.startswith(SIGNATURE):
        raise ValueError('Not a PNG')
    offset, palette, transparency, payload = 8, None, None, bytearray()
    header = None
    while offset + 12 <= len(data):
        length = struct.unpack_from('>I', data, offset)[0]
        kind = data[offset + 4:offset + 8]
        body = data[offset + 8:offset + 8 + length]
        if len(body) != length or offset + 12 + length > len(data):
            raise ValueError('Truncated PNG')
        crc = struct.unpack_from('>I', data, offset + 8 + length)[0]
        if zlib.crc32(kind + body) & 0xffffffff != crc:
            raise ValueError('PNG checksum mismatch')
        offset += length + 12
        if kind == b'IHDR': header = struct.unpack('>IIBBBBB', body)
        elif kind == b'PLTE': palette = body
        elif kind == b'tRNS': transparency = body
        elif kind == b'IDAT': payload.extend(body)
        elif kind == b'IEND': break
    if header is None or palette is None:
        raise ValueError('Missing PNG header or palette')
    width, height, depth, color, compression, filtering, interlace = header
    if not (0 < width <= 4096 and 0 < height <= 4096 and (depth, color, compression, filtering, interlace) == (8, 3, 0, 0, 0)):
        raise ValueError('Expected a non-interlaced 8-bit indexed PNG')
    expected = (width + 1) * height
    decoder = zlib.decompressobj()
    raw = decoder.decompress(bytes(payload), expected + 1)
    if len(raw) != expected or not decoder.eof or decoder.unused_data:
        raise ValueError('Invalid PNG pixel data')
    rows, previous = [], bytearray(width)
    for y in range(height):
        start = y * (width + 1)
        method = raw[start]
        row = bytearray(raw[start + 1:start + 1 + width])
        for x in range(width):
            left = row[x - 1] if x else 0
            up = previous[x]
            upper_left = previous[x - 1] if x else 0
            if method == 0: predictor = 0
            elif method == 1: predictor = left
            elif method == 2: predictor = up
            elif method == 3: predictor = (left + up) // 2
            elif method == 4:
                p = left + up - upper_left
                a, b, c = abs(p - left), abs(p - up), abs(p - upper_left)
                predictor = left if a <= b and a <= c else up if b <= c else upper_left
            else: raise ValueError('Unsupported PNG filter')
            row[x] = (row[x] + predictor) & 255
        rows.append(row)
        previous = row
    return width, height, rows, palette, transparency


def resize_indexed(image, size):
    width, height, rows, palette, transparency = image
    if width != height:
        raise ValueError('App icon must be square')
    positions = [min(width - 1, ((2 * x + 1) * width) // (2 * size)) for x in range(size)]
    raw = bytearray()
    for y in positions:
        raw.append(0)
        raw.extend(rows[y][x] for x in positions)
    result = SIGNATURE + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 3, 0, 0, 0)) + chunk(b'PLTE', palette)
    if transparency is not None: result += chunk(b'tRNS', transparency)
    return result + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')


def generated(root=ROOT):
    image = indexed_pixels((root / 'common/branding/icon/app-icon.png').read_bytes())
    icon = resize_indexed(image, 512)
    small = resize_indexed(image, 32)
    ico = struct.pack('<HHHBBBBHHII', 0, 1, 1, 32, 32, 0, 0, 1, 32, len(small), 22) + small
    banner = (root / 'common/branding/tv-banner/tv-banner.png').read_bytes()
    if not banner.startswith(SIGNATURE) or struct.unpack('>II', banner[16:24]) != (320, 180):
        raise ValueError('TV banner must be a 320x180 PNG')
    return {'android/app/src/main/res/drawable-nodpi/app_icon.png': icon,
            'android/app/src/main/res/drawable-nodpi/tv_banner.png': banner,
            'web/vizhi-tivi/public/app-icon.png': icon,
            'web/vizhi-tivi/public/favicon.ico': ico}


def sync(root=ROOT, check=False):
    for name, content in generated(root).items():
        destination = root / name
        if check:
            if not destination.is_file() or destination.read_bytes() != content:
                raise ValueError(f'Outdated branding asset: {name}')
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(content)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    sync(check=args.check)
    print('Branding assets verified' if args.check else 'Branding assets synchronized')
