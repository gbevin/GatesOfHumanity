// Writes natalengine's charts for the moments in a file, as JSON lines for compare.py.
//     node Tools/compare/natalengine.mjs /path/to/natalengine moments.txt > natalengine.jsonl
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';

const [library, moments] = process.argv.slice(2);
if (!library || !moments) {
  console.error('Usage: node Tools/compare/natalengine.mjs /path/to/natalengine moments.txt > natalengine.jsonl');
  process.exit(2);
}
const { calculateHumanDesign } = await import(pathToFileURL(join(library, 'src/index.js')).href);

const PLANETS = { sun: 'sun', earth: 'earth', moon: 'moon', northNode: 'north_node', southNode: 'south_node',
  mercury: 'mercury', venus: 'venus', mars: 'mars', jupiter: 'jupiter', saturn: 'saturn', uranus: 'uranus',
  neptune: 'neptune', pluto: 'pluto' };

const imprint = gates => Object.fromEntries(Object.entries(gates).map(([k, g]) =>
  [PLANETS[k], [g.gate, g.line, g.color, g.tone, g.longitude]]));

const lines = [];
for (const moment of readFileSync(moments, 'utf8').split('\n').map(s => s.trim()).filter(Boolean)) {
  const t = new Date(moment);
  const hour = t.getUTCHours() + t.getUTCMinutes() / 60 + t.getUTCSeconds() / 3600;
  const hd = calculateHumanDesign(moment.slice(0, 10), hour, 0);
  const v = hd.variable;
  lines.push(JSON.stringify({
    m: moment,
    dm: hd.positions.design.utc,
    p: imprint(hd.gates.personality),
    d: imprint(hd.gates.design),
    type: hd.type.name,
    authority: hd.authority.name,
    profile: hd.profile.numbers,
    definition: hd.definition,
    angle: hd.incarnationCross.angle ?? '',
    cross_gates: hd.incarnationCross.gates,
    cross: hd.incarnationCross.name ?? '',
    channels: hd.channels.map(c => c.gates.join('-')).sort(),
    centers: [...hd.centers.definedNames].sort(),
    circuits: Object.fromEntries(hd.channels.map(c => [c.gates.join('-'), c.subcircuit])),
    variables: Object.fromEntries(['determination', 'environment', 'motivation', 'perspective']
      .map(k => [k, [v[k].color, v[k].tone]]))
  }));
}
process.stdout.write(lines.join('\n') + '\n');
