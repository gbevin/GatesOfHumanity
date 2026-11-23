#!/usr/bin/env python3
"""Generates the Swiss Ephemeris reference positions that the ephemeris tests compare against.

Requires a local Swiss Ephemeris build with swetest and its .se1 files. Only run it when the
reference data needs to change. The build itself never depends on the Swiss Ephemeris.

    Tools/generate-oracle.py /path/to/swisseph > src/test/resources/oracle/swisseph.csv
"""

import random
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

BODIES = '0123456789tD'
COLUMNS = ['sun', 'moon', 'mercury', 'venus', 'mars', 'jupiter', 'saturn',
           'uranus', 'neptune', 'pluto', 'true_node', 'chiron']
DESIGN_ARC = 88.0
SAMPLES = 2000
SEED = 20261110
# 1800-04-02 to 2201-02-20, the tests skip the few samples outside of the ephemeris range
JD_START = 2378587.5
JD_END = 2525008.5


def swetest(directory, jd_ut, bodies):
    out = subprocess.run(
        ['./swetest', f"-bj{jd_ut:.9f}", '-ut', f"-p{bodies}", '-fl', '-head', '-eswe'],
        cwd=directory, capture_output=True, text=True, check=True).stdout
    values = [float(line) for line in out.split()]
    if len(values) != len(bodies):
        raise RuntimeError(f"unexpected swetest output for {jd_ut}: {out!r}")
    return values


def normalize(degrees):
    degrees %= 360.0
    return degrees - 360.0 if degrees > 180.0 else degrees


def design_jd(directory, jd_ut, sun):
    target = sun - DESIGN_ARC
    t0, t1 = jd_ut - 89.0, jd_ut - 90.0
    f0 = normalize(swetest(directory, t0, '0')[0] - target)
    f1 = normalize(swetest(directory, t1, '0')[0] - target)
    for _ in range(20):
        t2 = t1 - f1 * (t1 - t0) / (f1 - f0)
        f2 = normalize(swetest(directory, t2, '0')[0] - target)
        t0, f0, t1, f1 = t1, f1, t2, f2
        if abs(f1) < 1e-7:
            return t1
    raise RuntimeError(f"design moment did not converge for {jd_ut}")


def sample(directory, jd_ut):
    positions = swetest(directory, jd_ut, BODIES)
    design = design_jd(directory, jd_ut, positions[0])
    return jd_ut, positions, design, swetest(directory, design, BODIES)


def main():
    directory = sys.argv[1]
    rng = random.Random(SEED)
    instants = [round(rng.uniform(JD_START, JD_END), 6) for _ in range(SAMPLES)]
    with ThreadPoolExecutor(8) as pool:
        rows = list(pool.map(lambda jd: sample(directory, jd), instants))
    print('jd_ut,' + ','.join(COLUMNS) + ',design_jd_ut,' + ','.join('design_' + c for c in COLUMNS))
    for jd_ut, positions, design, design_positions in rows:
        print(f"{jd_ut:.6f}," + ','.join(f"{p:.7f}" for p in positions) + f",{design:.8f},"
              + ','.join(f"{p:.7f}" for p in design_positions))


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main()
