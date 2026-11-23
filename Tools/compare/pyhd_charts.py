"""Writes pyhd's charts for the moments in a file, as JSON lines for compare.py.

Run with the Python of a virtual environment that has pyhd installed:

    venv/bin/python -I Tools/compare/pyhd_charts.py moments.txt > pyhd.jsonl
"""

import json
import sys
from datetime import datetime

from pyhd import Chart
from pyhd.constants import Planets


def num(x):
    return getattr(x, 'num', x)


def imprint(imp):
    return {planet._name_.lower(): [num(a.gate), num(a.line), num(a.color), num(a.tone), a.longitude]
            for planet, a in imp.items()}


def key(channel):
    return '-'.join(str(num(g)) for g in channel.gates)


def main():
    for line in open(sys.argv[1]):
        moment = line.strip()
        if not moment:
            continue
        c = Chart(datetime.fromisoformat(moment.replace('Z', '+00:00')))
        variables = {
            'determination': c.design[Planets.SUN], 'environment': c.design[Planets.NORTH_NODE],
            'motivation': c.personality[Planets.SUN], 'perspective': c.personality[Planets.NORTH_NODE]}
        cross = c.cross
        print(json.dumps({
            'm': moment,
            'dm': c.design.dt.isoformat(),
            'p': imprint(c.personality),
            'd': imprint(c.design),
            'type': c.type.name,
            'authority': c.authority.name,
            'profile': f"{num(c.personality[Planets.SUN].line)}/{num(c.design[Planets.SUN].line)}",
            'definition': c.definition_type.name,
            'angle': c.geometry.name,
            'cross_gates': [num(c.personality[Planets.SUN].gate), num(c.personality[Planets.EARTH].gate),
                            num(c.design[Planets.SUN].gate), num(c.design[Planets.EARTH].gate)],
            'cross': getattr(cross, 'title', str(cross)),
            'channels': sorted(key(ch) for ch in c.channels),
            'centers': sorted(ce._name_.lower() for ce in c.centers),
            'circuits': {key(ch): ch.circuit._name_.lower() for ch in c.channels},
            'variables': {k: [num(a.color), num(a.tone)] for k, a in variables.items()},
        }))


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main()
