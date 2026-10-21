import math
import struct
import wave

import numpy as np

RATE = 44100
DUR = 2.1

def one_pole(x, cutoff):
    a = math.exp(-2.0 * math.pi * cutoff / RATE)
    out = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = a * acc + (1.0 - a) * x[i]
        out[i] = acc
    return out

def main(seed=20261006):
    rng = np.random.default_rng(seed)
    n = int(RATE * DUR)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)

    atk = np.clip(t / 0.008, 0.0, 1.0)
    blast = noise * atk * np.exp(-t / 0.18) * 0.55

    body = one_pole(noise, 420.0) * atk * np.exp(-t / 0.72) * 3.4

    spray = np.zeros(n)
    for _ in range(90):
        at = rng.uniform(0.12, 1.5)
        i = int(at * RATE)
        ln = int(rng.uniform(0.002, 0.010) * RATE)
        if i + ln >= n:
            continue
        env = np.exp(-np.arange(ln) / (ln * 0.35))
        spray[i:i + ln] += rng.standard_normal(ln) * env * rng.uniform(0.05, 0.22)
    spray *= np.exp(-t / 0.9)

    sig = blast + body + spray
    fade = int(0.06 * RATE)
    sig[-fade:] *= np.linspace(1.0, 0.0, fade)
    sig /= np.max(np.abs(sig)) + 1e-9
    sig *= 0.89

    pcm = (sig * 32767.0).astype("<i2")
    with wave.open("whale_blow_raw.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    print("wrote whale_blow_raw.wav  %.2f s, %d samples" % (DUR, n))

if __name__ == "__main__":
    main()
