#!/usr/bin/env python3
"""Extracts the planets that exalt each line and the planet in detriment from The Complete Rave
I'Ching, which the tests compare with the dignities in gatesofhumanity.models.Entities.

Requires pdftotext. Only run it when the dignities need to change:

    Tools/generate-dignities.py "the complete rave iching.pdf" > src/test/resources/dignities.tsv
"""

import re
import subprocess
import sys

# the book's symbol font puts the planets on these characters
PLANETS = {'À': 'Sun', 'Á': 'Moon', 'Â': 'Mercury', 'Ã': 'Venus', 'Ä': 'Mars', 'Å': 'Jupiter',
           'Æ': 'Saturn', 'Ç': 'Uranus', 'È': 'Neptune', 'É': 'Pluto', 'Í': 'Earth'}
# every gate lists its lines from 6 down to 1, each starting at the left margin
LINE = re.compile(r'^ {0,6}([1-6]) +\S')
# a planet symbol, then p when it exalts the line and s when it's in detriment, sometimes without a
# space before the text that follows
MARK = re.compile(r'([À-Í]) ([ps])')
ALSO_EXALTED = re.compile(r'(' + '|'.join(PLANETS.values()) + r') is also\s+exalted')


def main():
    text = subprocess.run(['pdftotext', '-layout', sys.argv[1], '-'], capture_output=True, text=True,
                          check=True).stdout
    gate, line, expected = 0, None, 6
    lines = {}
    for row in text.splitlines():
        match = LINE.match(row)
        if match and int(match.group(1)) == expected:
            line = expected
            if line == 6:
                gate += 1
            expected = 6 if line == 1 else line - 1
            lines[(gate, line)] = []
        if line is not None:
            lines[(gate, line)].append(row)
    if gate != 64 or len(lines) != 384:
        sys.exit(f'Found {len(lines)} lines in {gate} gates instead of 384 in 64')

    print("# gate\tline\texalted by\tdetriment, from The Complete Rave I'Ching")
    for gate in range(1, 65):
        for line in range(1, 7):
            rows = lines[(gate, line)]
            joined = ' '.join(row.strip() for row in rows)
            exalted = [PLANETS[symbol] for symbol, kind in MARK.findall(joined) if kind == 'p']
            exalted += ALSO_EXALTED.findall(joined)
            detriment = [PLANETS[symbol] for symbol, kind in MARK.findall(joined) if kind == 's']
            if len(detriment) > 1:
                sys.exit(f'{gate}.{line} has more than one planet in detriment: {detriment}')
            print(f'{gate}\t{line}\t{",".join(exalted)}\t{"".join(detriment)}')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main()
