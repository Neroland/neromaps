#!/usr/bin/env python3
"""
gen_map_sounds.py -- synthesises every NeroMaps sound from scratch (no samples, no downloads).

    python3 tools/art/gen_map_sounds.py            # render, encode mono 32 kHz Ogg Vorbis into the repo, write sounds.json
    python3 tools/art/gen_map_sounds.py --wav DIR  # render WAVs into DIR only (no encoder needed)
    python3 tools/art/gen_map_sounds.py --check    # confirm every key has its .ogg and sounds.json matches
    python3 tools/art/gen_map_sounds.py --lang     # print the subtitle lang entries

"Paper and sonar": soft sine pings on A-minor pentatonic, filtered-noise paper, short low mechanical ticks.
numpy + stdlib wave with a fixed seed; ffmpeg (libvorbis) does the encode.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "neromaps")
MOD = "neromaps"
SR = 32000
SEED = 53
A3, A4, C5, D5, E5, G5, A5 = 220.0, 440.0, 523.25, 587.33, 659.25, 783.99, 880.0


def n(sec):
    return int(SR * sec)


def tt(sec):
    return np.arange(n(sec)) / SR


def ping(freq, sec=0.32, decay=9.0, chirp=0.0, bright=0.18):
    t = tt(sec)
    f = freq * (1.0 - chirp * t / sec)
    ph = 2 * np.pi * np.cumsum(f) / SR
    att = np.minimum(1.0, t / 0.004)
    return att * np.exp(-decay * t) * (np.sin(ph) + bright * np.sin(2 * ph) + 0.05 * np.sin(3 * ph))


def band_noise(rng, sec, lo, hi):
    x = rng.standard_normal(n(sec))
    spec = np.fft.rfft(x)
    fr = np.fft.rfftfreq(len(x), 1.0 / SR)
    spec *= np.exp(-0.5 * ((np.log(np.maximum(fr, 1.0)) - np.log(np.sqrt(lo * hi))) / (np.log(hi / lo) / 2.4)) ** 2)
    y = np.fft.irfft(spec, len(x))
    return y / (np.max(np.abs(y)) + 1e-9)


def hump(sec, peak=0.35, sharp=2.0):
    t = tt(sec) / sec
    return np.where(t < peak, (t / peak) ** sharp, ((1 - t) / (1 - peak)) ** (sharp * 1.3))


def paper(rng, sec=0.22, lo=1500, hi=6500, peak=0.35):
    flutter = 0.75 + 0.25 * np.sin(2 * np.pi * 37 * tt(sec) + rng.uniform(0, 6))
    return band_noise(rng, sec, lo, hi) * hump(sec, peak) * flutter


def tick(sec=0.02, f=190.0, click=0.5):
    t = tt(sec)
    body = np.sin(2 * np.pi * f * t) * np.exp(-t * 160)
    snap = np.sin(2 * np.pi * 2400 * t) * np.exp(-t * 900)
    return body + click * snap


def swell(freq, sec=0.6):
    t = tt(sec)
    env = np.sin(np.pi * t / sec) ** 1.6
    return env * (np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 2 * t) + 0.12 * np.sin(2 * np.pi * freq * 3.01 * t))


def mix(sec, *parts):
    out = np.zeros(n(sec))
    for at, gain, sig in parts:
        i = n(at)
        seg = sig[: max(0, len(out) - i)]
        out[i:i + len(seg)] += gain * seg
    return out


def fade(x, ms=6):
    k = n(ms / 1000.0)
    x = x.copy()
    x[-k:] *= np.linspace(1, 0, k)
    return x


def build():
    rng = np.random.default_rng(SEED)
    S = {}

    def add(key, subtitle, sig, peak_db, recipe, **extra):
        sig = sig / (np.max(np.abs(sig)) + 1e-9) * (10 ** (peak_db / 20.0))
        S[key] = dict(subtitle=subtitle, sig=sig if extra.get("loop") else fade(sig), peak_db=peak_db, recipe=recipe, extra=extra)

    add("ui.map_open", "Map opens", mix(0.42, (0, 0.55, paper(rng, 0.24)), (0.06, 0.6, ping(E5, 0.3)), (0.15, 0.7, ping(A5, 0.27))),
        -9, "paper unroll + rising E5-A5 ping")
    add("ui.map_close", "Map closes", mix(0.42, (0, 0.55, paper(rng, 0.24, peak=0.6)), (0.06, 0.6, ping(A5, 0.3)), (0.15, 0.6, ping(E5, 0.27))),
        -10, "paper roll + falling A5-E5 ping")
    add("ui.click", "Click", tick(0.02), -10, "20 ms low tick")
    add("ui.zoom", "Map zooms", mix(0.05, (0, 1, tick(0.04, 260.0, 0.25)), (0, 0.3, ping(A5, 0.05, 60))), -13,
        "single soft tick; code sets pitch 0.8-1.25 by zoom step")
    add("waypoint.create", "Waypoint set", mix(0.5, (0, 0.8, ping(E5, 0.3)), (0.11, 1.0, ping(A5, 0.39, 8))), -8, "E5 -> A5 ping")
    add("waypoint.remove", "Waypoint removed", mix(0.4, (0, 0.8, ping(A5, 0.2, 16, bright=0.05)), (0.1, 0.9, ping(E5, 0.3, 14, bright=0.05))),
        -13, "A5 -> E5 ping, muted (fast decay, no overtone)")
    add("waypoint.death", "Death point marked", swell(A3, 0.6), -9, "low A3 swell, 0.6 s")
    add("compass.lock", "Compass locks on", mix(0.4, (0, 0.9, tick(0.03)), (0.03, 0.9, ping(G5, 0.37, 9))), -9, "tick + G5 ping")
    add("spyglass.mark", "Distant point marked", mix(0.68, (0, 1.0, ping(D5, 0.4, 9, chirp=0.04)), (0.24, 0.35, ping(D5, 0.44, 8, chirp=0.04))),
        -9, "sonar ping with one echo")
    add("atlas.open", "Atlas opens", paper(rng, 0.3, 1100, 5200, 0.45), -11, "page turn")
    add("atlas.merge", "Atlas pages merged", mix(0.62, (0, 0.6, paper(rng, 0.12)), (0.1, 0.7, paper(rng, 0.12)), (0.2, 0.6, paper(rng, 0.14)),
                                               (0.3, 0.9, ping(C5, 0.32, 9))), -10, "page shuffle + C5 ping")
    t = tt(0.36)
    servo = np.sin(2 * np.pi * np.cumsum(170 + 260 * (t / 0.36)) / SR) * (0.8 + 0.2 * np.sin(2 * np.pi * 48 * t)) * hump(0.36, 0.15, 1.0)
    servo += 0.3 * np.sin(2 * np.pi * np.cumsum(2 * (170 + 260 * (t / 0.36))) / SR) * hump(0.36, 0.15, 1.0)
    add("desk.print", "Map printed", mix(0.7, (0, 0.6, servo), (0.33, 0.8, paper(rng, 0.36, 900, 4200, 0.3))), -10, "servo sweep + paper slide")
    add("desk.copy", "Atlas copied", mix(0.6, (0, 0.8, paper(rng, 0.26, 1100, 5200, 0.45)), (0.31, 0.8, paper(rng, 0.28, 1300, 5600, 0.4))),
        -11, "two page turns")
    tg = tt(0.03)
    glass = (np.sin(2 * np.pi * 2600 * tg) + 0.5 * np.sin(2 * np.pi * 3900 * tg)) * np.exp(-tg * 240)
    add("wall_map.set", "Wall map updates", mix(0.42, (0, 0.6, glass), (0.03, 0.8, ping(D5, 0.39, 8, bright=0.08))), -11, "glass tick + soft D5")
    scratch = mix(0.36, *[(a, g, band_noise(rng, d, 2500, 9000) * hump(d, 0.3, 1.0) * (0.6 + 0.4 * np.sin(2 * np.pi * 90 * tt(d))))
                          for a, g, d in ((0.0, 0.8, 0.09), (0.11, 1.0, 0.12), (0.26, 0.7, 0.08))])
    add("signpost.write", "Signpost labelled", scratch, -13, "stylus scratch, three strokes")
    add("trail_marker.place", "Trail marker placed", mix(0.3, (0, 1.0, tick(0.025, 150.0, 0.7)), (0.02, 0.6, ping(A5, 0.27, 14))), -10,
        "small click + ping")
    th = tt(2.0)
    hum = (np.sin(2 * np.pi * 55 * th) + 0.5 * np.sin(2 * np.pi * 110 * th + 0.4) + 0.22 * np.sin(2 * np.pi * 165 * th + 1.1)
           + 0.06 * np.sin(2 * np.pi * 880 * th)) * (0.85 + 0.15 * np.sin(2 * np.pi * 1.0 * th))
    add("map_table.hum", "Map table hums", hum, -24, "2 s seamless loop (55/110/165 Hz, 1 Hz beat), very quiet", loop=True, attenuation_distance=6)
    add("survey.ping", "Survey complete", mix(0.7, (0, 1.0, ping(G5, 0.3, 10, chirp=0.03)), (0.19, 0.9, ping(E5, 0.3, 10, chirp=0.03)),
                                              (0.38, 0.9, ping(C5, 0.32, 9, chirp=0.03))), -9, "three descending sonar pings G5 E5 C5")
    add("share.request", "Map share requested", mix(0.45, (0, 0.8, ping(C5, 0.25, 11)), (0.12, 0.9, ping(E5, 0.33, 9))), -10, "two-note rising C5 E5")
    add("share.accept", "Map share accepted", mix(0.58, (0, 0.8, ping(C5, 0.22, 12)), (0.11, 0.85, ping(E5, 0.22, 12)), (0.22, 1.0, ping(G5, 0.36, 8))),
        -9, "three-note rising C5 E5 G5")
    add("share.revoke", "Map share ended", mix(0.45, (0, 0.8, ping(E5, 0.25, 11)), (0.12, 0.8, ping(C5, 0.33, 10, bright=0.08))), -11,
        "two-note falling E5 C5")
    return S


def sounds_json(S):
    out = {}
    for key in sorted(S):
        entry = {"name": f"{MOD}:{key.replace('.', '/')}", "stream": False}
        if "attenuation_distance" in S[key]["extra"]:
            entry["attenuation_distance"] = S[key]["extra"]["attenuation_distance"]
        out[key] = {"subtitle": f"subtitles.{MOD}.{key}", "sounds": [entry]}
    return json.dumps(out, indent=2) + "\n"


def lang(S):
    return {f"subtitles.{MOD}.{k}": S[k]["subtitle"] for k in sorted(S)}


def write_wav(path, sig):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(sig, -1, 1) * 32767).astype("<i2").tobytes())


def main():
    S = build()
    sdir = os.path.join(ASSETS, "sounds")
    sjson = os.path.join(ASSETS, "sounds.json")
    if "--lang" in sys.argv:
        print(json.dumps(lang(S), indent=2))
        return
    if "--check" in sys.argv:
        missing = [k for k in S if not os.path.exists(os.path.join(sdir, k.replace(".", os.sep) + ".ogg"))]
        same = os.path.exists(sjson) and open(sjson, encoding="utf-8").read() == sounds_json(S)
        print(len(S), "keys |", "missing: " + ", ".join(missing) if missing else "all .ogg present", "| sounds.json",
              "matches" if same else "DIFFERS")
        sys.exit(0 if same and not missing else 1)
    wav_dir = sys.argv[sys.argv.index("--wav") + 1] if "--wav" in sys.argv else tempfile.mkdtemp(prefix="neromaps_wav_")
    os.makedirs(wav_dir, exist_ok=True)
    for k, v in S.items():
        write_wav(os.path.join(wav_dir, k + ".wav"), v["sig"])
    if "--wav" in sys.argv:
        print(f"rendered {len(S)} WAVs into {wav_dir}")
        return
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print('ffmpeg not found; convert with: ffmpeg -y -i X.wav -ac 1 -ar 32000 -c:a libvorbis -q:a 4 X.ogg ; WAVs in', wav_dir)
        sys.exit(2)
    for k in S:
        tmp = os.path.join(wav_dir, k + ".ogg")
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", os.path.join(wav_dir, k + ".wav"), "-ac", "1", "-ar", str(SR),
                        "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", "-bitexact", tmp], check=True)
        dst = os.path.join(sdir, k.replace(".", os.sep) + ".ogg")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(tmp, "rb") as src, open(dst, "wb") as out:      # write in place: the target may not be unlinkable
            out.write(src.read())
    with open(sjson, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(sounds_json(S))
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "out", "sound_table.json"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump({k: {"subtitle": v["subtitle"], "seconds": round(len(v["sig"]) / SR, 3), "peak_db": v["peak_db"],
                       "recipe": v["recipe"], **{a: b for a, b in v["extra"].items()}} for k, v in sorted(S.items())}, fh, indent=2)
        fh.write("\n")
    print(f"encoded {len(S)} OGGs under {sdir} and wrote sounds.json")


if __name__ == "__main__":
    main()
