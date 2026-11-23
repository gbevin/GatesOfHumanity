// Wall clock date and times as the date fields show them, 'YYYY-MM-DDTHH:mm', in the time
// zone of the chart's location. They're computed in UTC so that the time zone of the browser
// never shifts them.
const HdTime = (() => {
    // the date fields leave out seconds, but can give them
    const FIELD = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/;

    function pad(value, length) {
        return String(value).padStart(length, '0');
    }

    function parse(text) {
        const match = typeof text === 'string' ? FIELD.exec(text) : null;
        if (match === null) {
            return undefined;
        }
        const [year, month, day, hours, minutes] = match.slice(1).map(Number);
        const date = new Date(0);
        date.setUTCFullYear(year, month - 1, day);
        date.setUTCHours(hours, minutes, 0, 0);
        if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day ||
            date.getUTCHours() !== hours || date.getUTCMinutes() !== minutes) {
            return undefined;
        }
        return date.getTime();
    }

    function format(time) {
        const date = new Date(time);
        return pad(date.getUTCFullYear(), 4) + '-' + pad(date.getUTCMonth() + 1, 2) + '-' + pad(date.getUTCDate(), 2) +
               'T' + pad(date.getUTCHours(), 2) + ':' + pad(date.getUTCMinutes(), 2);
    }

    function formatForImage(time) {
        return format(time).replace(/[-T:]/g, '');
    }

    function add(text, seconds) {
        const time = parse(text);
        return time === undefined ? undefined : format(time + seconds * 1000);
    }

    // the text in the form of the date fields, undefined when it isn't a valid date and time
    function normalize(text) {
        const time = parse(text);
        return time === undefined ? undefined : format(time);
    }

    return {parse, format, formatForImage, add, normalize};
})();

// The saved charts and the last used location and birth data, as one versioned JSON document in
// localStorage. Every change reads the document again before writing it, so that pages open in
// several tabs don't overwrite each other's changes.
const HdStore = (() => {
    // what's stored is named hdapp, here and in main.js: under another name the people that
    // someone saved would be gone, and the files they exported refused
    const KEY = 'hdapp';
    const VERSION = 1;
    const EXPORT_FORMAT = 'hdapp-charts';

    // without a prototype, so that charts can be named __proto__ or toString
    function namedMap() {
        return Object.create(null);
    }

    function empty() {
        return {version: VERSION, charts: namedMap(), location: null, birth: null, partner: null, named: null, namedPartner: null, guideOpen: false};
    }

    function isText(value) {
        return typeof value === 'string';
    }

    function isObject(value) {
        return value !== null && typeof value === 'object';
    }

    function isLocationId(value) {
        return isText(value) && /^\d+$/.test(value);
    }

    function chartOf(value) {
        if (!isObject(value) || HdTime.parse(value.datetime) === undefined ||
            !isLocationId(value.locationId) || !isText(value.locationCity)) {
            return null;
        }
        const chart = {datetime: HdTime.normalize(value.datetime), locationId: value.locationId, locationCity: value.locationCity};
        if (isText(value.group) && value.group.trim() !== '') {
            chart.group = value.group.trim();
        }
        return chart;
    }

    function locationOf(value) {
        if (!isObject(value) || !isLocationId(value.id) || !isText(value.city)) {
            return null;
        }
        return {id: value.id, city: value.city};
    }

    function birthOf(value) {
        if (!isObject(value)) {
            return null;
        }
        const birth = {};
        if (HdTime.parse(value.datetime) !== undefined) {
            birth.datetime = HdTime.normalize(value.datetime);
        }
        const location = locationOf(value.location);
        if (location !== null) {
            birth.location = location;
        }
        return Object.keys(birth).length === 0 ? null : birth;
    }

    // the saved chart that was loaded or saved last, which titles the chart while it's shown
    function namedOf(value) {
        if (!isObject(value) || !isText(value.name) || value.name.trim() === '' ||
            HdTime.parse(value.datetime) === undefined || !isLocationId(value.locationId)) {
            return null;
        }
        return {name: value.name, datetime: HdTime.normalize(value.datetime), locationId: value.locationId};
    }

    function setNamed(data, name, chart, kind) {
        data[kind === 'partner' ? 'namedPartner' : 'named'] = namedOf({name, datetime: chart.datetime, locationId: chart.locationId});
    }

    function normalize(value) {
        const data = empty();
        if (!isObject(value)) {
            return data;
        }
        if (isObject(value.charts)) {
            for (const [name, chart] of Object.entries(value.charts)) {
                const valid = chartOf(chart);
                if (name.trim() !== '' && valid !== null) {
                    data.charts[name] = valid;
                }
            }
        }
        data.location = locationOf(value.location);
        data.birth = birthOf(value.birth);
        data.partner = birthOf(value.partner);
        data.named = namedOf(value.named);
        data.namedPartner = namedOf(value.namedPartner);
        data.guideOpen = value.guideOpen === true;
        return data;
    }

    function load(storage) {
        let text = null;
        try {
            text = storage === null ? null : storage.getItem(KEY);
        } catch (e) {
            return empty();
        }
        if (text === null) {
            return empty();
        }
        try {
            return normalize(JSON.parse(text));
        } catch (e) {
            // keep what can't be read instead of overwriting it with the next change
            try {
                storage.setItem(KEY + '.unreadable.' + Date.now(), text);
                storage.removeItem(KEY);
            } catch (ignored) {
            }
            return empty();
        }
    }

    function save(storage, data) {
        if (storage === null) {
            return false;
        }
        try {
            storage.setItem(KEY, JSON.stringify(data));
            return true;
        } catch (e) {
            return false;
        }
    }

    function update(storage, change) {
        const data = load(storage);
        change(data);
        return save(storage, data);
    }

    function chartNames(data) {
        return Object.keys(data.charts).sort((a, b) => a.localeCompare(b));
    }

    // the groups the saved people are in, such as Family or Clients
    function groupNames(data) {
        const groups = new Set(Object.values(data.charts).map(chart => chart.group).filter(isText));
        return [...groups].sort((a, b) => a.localeCompare(b));
    }

    function putChart(data, name, chart) {
        const valid = chartOf(chart);
        const trimmed = isText(name) ? name.trim() : '';
        if (trimmed === '' || valid === null) {
            return false;
        }
        data.charts[trimmed] = valid;
        forgetNamed(data, trimmed, valid);
        return true;
    }

    // a blank group takes the person out of theirs
    function setGroup(data, name, group) {
        const chart = data.charts[name];
        if (chart === undefined) {
            return false;
        }
        const trimmed = isText(group) ? group.trim() : '';
        if (trimmed === '') {
            delete chart.group;
        } else {
            chart.group = trimmed;
        }
        return true;
    }

    // a name that someone else has is refused, rather than replacing them
    function renameChart(data, name, renamed) {
        const chart = data.charts[name];
        const trimmed = isText(renamed) ? renamed.trim() : '';
        if (chart === undefined || trimmed === '' || data.charts[trimmed] !== undefined) {
            return false;
        }
        delete data.charts[name];
        data.charts[trimmed] = chart;
        for (const slot of ['named', 'namedPartner']) {
            if (data[slot] !== null && data[slot].name === name) {
                data[slot].name = trimmed;
            }
        }
        return true;
    }

    function removeChart(data, name) {
        delete data.charts[name];
        forgetNamed(data, name, null);
    }

    // a title only names the saved chart while that chart is still saved
    function forgetNamed(data, name, chart) {
        for (const slot of ['named', 'namedPartner']) {
            const named = data[slot];
            if (named !== null && named.name === name &&
                (chart === null || named.datetime !== chart.datetime || named.locationId !== chart.locationId)) {
                data[slot] = null;
            }
        }
    }

    function setLocation(data, id, city) {
        data.location = locationOf({id: String(id), city});
    }

    // kind is 'birth' for the visitor's own birth, or 'partner' for the other person of a connection
    function slot(kind) {
        return kind === 'partner' ? 'partner' : 'birth';
    }

    function setBirthDatetime(data, datetime, kind) {
        if (HdTime.parse(datetime) === undefined) {
            return;
        }
        data[slot(kind)] = Object.assign({}, data[slot(kind)], {datetime: HdTime.normalize(datetime)});
    }

    function setBirthLocation(data, id, city, kind) {
        const location = locationOf({id: String(id), city});
        if (location === null) {
            return;
        }
        data[slot(kind)] = Object.assign({}, data[slot(kind)], {location});
    }

    function exportCharts(data) {
        return JSON.stringify({format: EXPORT_FORMAT, version: VERSION, charts: data.charts}, null, 2);
    }

    function sameChart(a, b) {
        return a.datetime === b.datetime && a.locationId === b.locationId && a.locationCity === b.locationCity;
    }

    function freeName(data, name) {
        let suffix = 2;
        while (data.charts[name + ' (' + suffix + ')'] !== undefined) {
            suffix++;
        }
        return name + ' (' + suffix + ')';
    }

    // imported charts never replace saved ones, a different chart with the same name is added
    // under a new name
    function importCharts(data, text) {
        let value;
        try {
            value = JSON.parse(text);
        } catch (e) {
            value = null;
        }
        if (!isObject(value) || value.format !== EXPORT_FORMAT || !isObject(value.charts)) {
            throw new Error("This isn't a file of exported people.");
        }
        const result = {added: 0, renamed: 0, unchanged: 0, grouped: 0, invalid: 0};
        for (const [name, chart] of Object.entries(value.charts)) {
            const valid = chartOf(chart);
            const trimmed = name.trim();
            if (valid === null || trimmed === '') {
                result.invalid++;
                continue;
            }
            const existing = data.charts[trimmed];
            if (existing === undefined) {
                data.charts[trimmed] = valid;
                result.added++;
            } else if (sameChart(existing, valid)) {
                // a saved person without a group takes the one of the file, a group that's saved stays
                if (existing.group === undefined && valid.group !== undefined) {
                    existing.group = valid.group;
                    result.grouped++;
                } else {
                    result.unchanged++;
                }
            } else {
                data.charts[freeName(data, trimmed)] = valid;
                result.renamed++;
            }
        }
        return result;
    }

    return {
        load, save, update, chartNames, groupNames, putChart, setGroup, renameChart, removeChart, setLocation, setBirthDatetime, setBirthLocation,
        exportCharts, importCharts, setNamed, slot
    };
})();
