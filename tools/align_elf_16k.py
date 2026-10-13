"""Realign an ELF64 shared object's LOAD segments to 16 KB page size.

Usage: python align_elf_16k.py <input.so> [output.so]

Rebuilds the program headers so that every PT_LOAD segment has:
  p_align    = 0x4000
  p_offset   = p_vaddr mod 0x4000   (required congruence)
  p_filesz   = p_memsz - (bss kept as trailing memsz-only tail)

The file is laid out as: ELF header + program headers, then each LOAD's file
content aligned to 0x4000. Non-LOAD program headers are preserved but their
p_offset/p_vaddr are remapped to the new positions.
"""

import struct
import sys

PT_LOAD = 1
PAGE = 0x4000
EHDR_SIZE = 64
PHDR_SIZE = 56


def align_up(value: int, alignment: int) -> int:
    return (value + alignment - 1) // alignment * alignment


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2

    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else sys.argv[1]

    with open(src, "rb") as fh:
        data = bytearray(fh.read())

    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        print("error: expected a 64-bit little-endian ELF")
        return 1

    (e_phoff,) = struct.unpack_from("<Q", data, 0x20)
    (e_phentsize,) = struct.unpack_from("<H", data, 0x36)
    (e_phnum,) = struct.unpack_from("<H", data, 0x38)

    if e_phentsize != PHDR_SIZE:
        print(f"error: unexpected e_phentsize {e_phentsize}")
        return 1

    phdrs = []
    for i in range(e_phnum):
        off = e_phoff + i * PHDR_SIZE
        p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = (
            struct.unpack_from("<IIQQQQQQ", data, off)
        )
        phdrs.append(
            {
                "index": i,
                "type": p_type,
                "flags": p_flags,
                "offset": p_offset,
                "vaddr": p_vaddr,
                "paddr": p_paddr,
                "filesz": p_filesz,
                "memsz": p_memsz,
                "align": p_align,
            }
        )

    loads = [p for p in phdrs if p["type"] == PT_LOAD]
    if not loads:
        print("error: no PT_LOAD segments found")
        return 1

    header_end = EHDR_SIZE + e_phnum * PHDR_SIZE
    new_phoff = align_up(header_end, PAGE)
    # Keep program headers at the very start of the file so PT_PHDR stays valid.
    new_phoff = EHDR_SIZE

    # Assign new file offsets for every LOAD segment, keeping vaddr congruence and
    # preserving the relative ordering / vaddr spacing the linker chose.
    base_vaddr = loads[0]["vaddr"]
    cursor = align_up(EHDR_SIZE + e_phnum * PHDR_SIZE, PAGE)
    # First LOAD normally starts at vaddr 0 and therefore must map file offset 0.
    if base_vaddr == 0:
        cursor = 0

    new_layout = {}
    for seg in loads:
        rel_vaddr = seg["vaddr"] - base_vaddr
        want = align_up(rel_vaddr, PAGE)
        offset = base_vaddr + want
        # p_offset % PAGE must equal p_vaddr % PAGE.
        file_offset = align_up(cursor, PAGE) + (seg["vaddr"] % PAGE)
        # Preserve the segment's bss tail (memsz > filesz) as-is.
        new_layout[seg["index"]] = file_offset
        cursor = file_offset + seg["filesz"]

    out = bytearray(cursor)
    # ELF header + program header table are copied verbatim; segments follow.
    out[0:header_end] = data[0:header_end]

    # Map old file ranges to new positions for non-LOAD segments via their LOAD.
    def find_load(old_off: int):
        best = None
        for seg in loads:
            if seg["offset"] <= old_off < seg["offset"] + seg["filesz"]:
                if best is None or seg["offset"] > best["offset"]:
                    best = seg
        return best

    for seg in loads:
        new_off = new_layout[seg["index"]]
        chunk = data[seg["offset"] : seg["offset"] + seg["filesz"]]
        out[new_off : new_off + len(chunk)] = chunk
        delta = new_off - seg["offset"]
        note = ""
        # Only the first LOAD may start at offset 0 with vaddr 0.
        seg["new_offset"] = new_off
        seg["new_vaddr"] = seg["vaddr"]
        seg["delta"] = delta
        if seg["filesz"] != seg["memsz"]:
            note = f" (bss {seg['memsz'] - seg['filesz']:#x})"
        print(
            f"LOAD{seg['index']}: off {seg['offset']:#x}->{new_off:#x} "
            f"vaddr {seg['vaddr']:#x} filesz {seg['filesz']:#x}{note}"
        )

    # Rewrite program headers.
    for seg in phdrs:
        off = new_phoff + seg["index"] * PHDR_SIZE
        if seg["type"] == PT_LOAD:
            struct.pack_into(
                "<IIQQQQQQ",
                out,
                off,
                seg["type"],
                seg["flags"],
                seg["new_offset"],
                seg["new_vaddr"],
                seg["new_vaddr"],
                seg["filesz"],
                seg["memsz"],
                PAGE,
            )
        else:
            owner = find_load(seg["offset"])
            if owner is not None:
                new_seg_off = seg["offset"] + owner["delta"]
                new_seg_vaddr = seg["vaddr"] + owner["delta"]
            else:
                new_seg_off = seg["offset"]
                new_seg_vaddr = seg["vaddr"]
            struct.pack_into(
                "<IIQQQQQQ",
                out,
                off,
                seg["type"],
                seg["flags"],
                new_seg_off,
                new_seg_vaddr,
                new_seg_vaddr,
                seg["filesz"],
                seg["memsz"],
                seg["align"] if seg["align"] else 8,
            )

    # Point e_phoff at the retained header table.
    struct.pack_into("<Q", out, 0x20, new_phoff)

    # Section headers may reference file offsets; shift any that lived inside a
    # moved LOAD so debuggers/objdump stay consistent.
    (e_shoff,) = struct.unpack_from("<Q", data, 0x28)
    (e_shentsize,) = struct.unpack_from("<H", data, 0x3A)
    (e_shnum,) = struct.unpack_from("<H", data, 0x3C)
    if e_shoff and e_shnum and e_shentsize == 64:
        new_shoff = align_up(len(out), 8)
        out.extend(b"\x00" * (new_shoff + e_shnum * 64 - len(out)))
        out[new_shoff : new_shoff + e_shnum * 64] = data[
            e_shoff : e_shoff + e_shnum * 64
        ]
        struct.pack_into("<Q", out, 0x28, new_shoff)
        print(f"section headers: {e_shoff:#x}->{new_shoff:#x}")
    else:
        struct.pack_into("<Q", out, 0x28, 0)
        struct.pack_into("<H", out, 0x3C, 0)
        struct.pack_into("<H", out, 0x3E, 0)

    with open(dst, "wb") as fh:
        fh.write(out)

    print(f"wrote {dst} ({len(out)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
