#!/usr/bin/env python3
"""Compares the site's charts with another calculator's, field by field.

    Tools/compare/compare.py moments 10000 > moments.txt
    Tools/compare/compare.py diff site.jsonl other.jsonl

A difference in an activation is explained when the two longitudes fall on either side of a
boundary of that level, which happens when a planet sits within the small difference between two
ephemerides. A difference in a derived field only counts when every gate of both charts agrees,
otherwise it follows from the activations.
"""

import random
import sys
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
import json
import re

GATE_41_START = 302.0
SIZES = {'gate': 360 / 64, 'line': 360 / 384, 'color': 360 / 2304, 'tone': 360 / 13824}
LEVELS = list(SIZES)
CENTERS = {'solar_plexus': 'solar', 'splenic': 'spleen'}
CIRCUITS = {'understanding': 'logic', 'sensing': 'abstract'}
EXAMPLES = 8


def moments(count, seed=20261113):
    rng = random.Random(seed)
    start = datetime(1900, 1, 1, tzinfo=timezone.utc)
    span = int((datetime(2100, 1, 1, tzinfo=timezone.utc) - start).total_seconds())
    for _ in range(count):
        print((start + timedelta(seconds=rng.randrange(span))).strftime('%Y-%m-%dT%H:%M:%SZ'))


def cell(longitude, level):
    return int(((longitude - GATE_41_START) % 360) // SIZES[level])


def arc(a, b):
    return abs((a - b + 180) % 360 - 180) * 3600


def instant(s):
    return datetime.fromisoformat(s.replace('Z', '+00:00'))


def kind(field, value):
    v = str(value).lower().replace('-', ' ').replace('_', ' ')
    if field == 'type':
        return 'projector' if 'projector' in v else 'generator' if v in ('generator', 'pure generator') else v
    if field == 'authority':
        for word, name in (('emotion', 'emotional'), ('solar', 'emotional'), ('sacral', 'sacral'), ('splen', 'splenic'),
                           ('lunar', 'lunar'), ('self', 'self projected')):
            if word in v:
                return name
        if 'ego' in v:
            return 'ego manifested' if 'manifest' in v else 'ego projected' if 'project' in v else 'ego'
        return 'outer'
    if field == 'definition':
        for word in ('no', 'single', 'triple', 'quadruple'):
            if v.startswith(word):
                return word
        return 'split simple' if 'simple' in v else 'split wide' if 'wide' in v else 'split'
    if field == 'cross':
        v = re.sub(r'^.*cross of ', '', v)
        return re.sub(r'^the |\s*\d+$', '', v)
    if field == 'angle':
        return 'right' if 'right' in v else 'left' if 'left' in v else 'juxtaposition' if 'juxta' in v else v
    return v


def coarse(a, b):
    # a calculator that only tells ego or split apart is compared at that level
    for general in ('ego', 'split'):
        if general in (a, b) and a.startswith(general) and b.startswith(general):
            return general, general
    return a, b


def diff(site_file, other_file):
    site = {c['m']: c for c in map(json.loads, open(site_file))}
    other = {c['m']: c for c in map(json.loads, open(other_file))}
    common = [m for m in site if m in other]
    counts = defaultdict(Counter)
    examples = defaultdict(list)
    worst_arc = Counter()
    worst_design = 0.0
    circuits = set()

    def note(field, outcome, example=None):
        counts[field][outcome] += 1
        if example and outcome == 'unexplained' and len(examples[field]) < EXAMPLES:
            examples[field].append(example)

    for m in common:
        s, o = site[m], other[m]
        worst_design = max(worst_design, abs((instant(s['dm']) - instant(o['dm'])).total_seconds()))
        gates_agree = True
        for imprint in ('p', 'd'):
            for planet, sv in s[imprint].items():
                ov = o[imprint].get(planet)
                if ov is None:
                    continue
                worst_arc[planet] = max(worst_arc[planet], arc(sv[4], ov[4]))
                for index, level in enumerate(LEVELS):
                    if sv[index] == ov[index]:
                        note(level, 'agree')
                        continue
                    if level == 'gate':
                        gates_agree = False
                    straddles = cell(sv[4], level) != cell(ov[4], level)
                    note(level, 'boundary' if straddles else 'unexplained',
                         f"{m} {imprint}.{planet} site {sv[:4]} other {ov[:4]} at {sv[4]:.6f} and {ov[4]:.6f}")
                    break
        for field in ('type', 'authority', 'profile', 'definition', 'angle', 'cross', 'cross_gates', 'channels', 'centers', 'variables'):
            a, b = s[field], o[field]
            if field == 'centers':
                a, b = a, sorted(CENTERS.get(c, c) for c in b)
            elif field in ('type', 'authority', 'definition', 'angle', 'cross'):
                a, b = coarse(kind(field, a), kind(field, b))
            if a == b:
                note(field, 'agree')
            elif not gates_agree or field in ('profile', 'variables', 'cross_gates', 'angle', 'cross') and not same_lines(s, o):
                note(field, 'follows activations')
            else:
                note(field, 'unexplained', f"{m} site {s[field]} other {o[field]}")
        for channel, circuit in s['circuits'].items():
            theirs = o['circuits'].get(channel)
            if theirs is not None and CIRCUITS.get(theirs, theirs) != circuit:
                circuits.add((channel, circuit, theirs))

    print(f"{len(common)} charts compared, {len(site) - len(common)} only in the site's file, "
          f"{len(other) - len(common)} only in the other\n")
    print(f"design moments differ by at most {worst_design:.1f} s")
    print('largest longitude differences: ' +
          ', '.join(f"{p} {a:.1f}\"" for p, a in sorted(worst_arc.items(), key=lambda x: -x[1])) + '\n')
    for field, counter in counts.items():
        print(f"{field:12} " + ', '.join(f"{outcome} {n}" for outcome, n in counter.most_common()))
        for example in examples[field]:
            print(f"    {example}")
    if circuits:
        print('\nchannels placed in another circuit:')
        for channel, ours, theirs in sorted(circuits, key=lambda c: [int(g) for g in c[0].split('-')]):
            print(f"    {channel}: site {ours}, other {theirs}")


def same_lines(s, o):
    return all(s[i][p][:4] == o[i][p][:4] for i in ('p', 'd') for p in ('sun', 'earth', 'north_node', 'south_node'))


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == 'moments':
        moments(int(sys.argv[2]))
    elif len(sys.argv) == 4 and sys.argv[1] == 'diff':
        diff(sys.argv[2], sys.argv[3])
    else:
        sys.exit(__doc__)
