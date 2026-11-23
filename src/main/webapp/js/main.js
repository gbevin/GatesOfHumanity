let chart_type = '';
// the URLs of the routes that the scripts use, every page sets them
let routes = {cities: '', zone: '', now: '', people: '', explore: {}};
// what keeps the saved people: the browser, or the app that shows these pages
let kept_in = 'this browser';
// the app that shows the pages instead of a browser, see app.js
let in_app = '';
// where a shared link leads when that isn't where the page is, as in an app
let share_root = '';
// the first chart a page shows is the one it opened with, which isn't a date the visitor chose
let first_show = true;
// the names of the charts of a shared link by kind, the visitor's own names are in storage
let shared_names = {};
// the row that Load fills, 'first' for the top fields and 'second' and 'third' for the fields below them
let load_target = null;
let image_url = null;
// the rows that still show a chart from a shared link, which mustn't change what the visitor has saved
const shared_rows = new Set();
let sharing = false;

const LONDON = {id: '2643743', city: 'London, England, United Kingdom'};
// the city that was found for the time zone the device is set to, kept apart from what the visitor saved
const ZONE_KEY = 'hdapp-zone';
// set when a first visit was welcomed
const WELCOMED_KEY = 'hdapp-welcomed';
// who the pages and an app's other devices both had when they were last the same, see app.js
const SAME_KEY = 'hdapp-same';
// the rows that nobody chose a city for when the page opened, with their fields
const unchosen_rows = new Map();

function browserStorage() {
    try {
        return window.localStorage;
    } catch (e) {
        return null;
    }
}

function requestPersistentStorage() {
    if (navigator.storage && navigator.storage.persist) {
        navigator.storage.persist().catch(() => {});
    }
}

function deviceZone() {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || '';
    } catch (e) {
        return '';
    }
}

// the city found earlier for the zone that the device is in now, null when it has to be asked for
function zoneLocation() {
    try {
        const found = JSON.parse(browserStorage().getItem(ZONE_KEY));
        return found && found.zone === deviceZone() && typeof found.id === 'string' && typeof found.city === 'string' ? found : null;
    } catch (e) {
        return null;
    }
}

/**
 * Where a row starts before the visitor chose a city: the most populated city of the time zone
 * that the device is set to, which has the same time as where they are, and London until the
 * server said which city that is.
 */
function startLocation() {
    return zoneLocation() || LONDON;
}

// Asks once which city that is and moves the rows that nobody chose a city for to it, then goes
// on, also without an answer.
function whenStartKnown(proceed) {
    const zone = deviceZone();
    if (unchosen_rows.size === 0 || zone === '' || !routes.zone || zoneLocation() !== null) {
        proceed();
        return;
    }
    fetch(routes.zone + '?zone=' + encodeURIComponent(zone))
        .then(response => response.ok ? response.text() : Promise.reject(response.status))
        .then(text => {
            const found = JSON.parse(text);
            // a zone without a city, such as UTC, isn't asked about again
            const start = Array.isArray(found) && found.length === 2 ? {zone: zone, id: String(found[1]), city: String(found[0])} : {zone: zone, id: LONDON.id, city: LONDON.city};
            try {
                browserStorage().setItem(ZONE_KEY, JSON.stringify(start));
            } catch (e) {
                // found again on the next page
            }
            for (const [target, [idInput, cityInput]] of unchosen_rows) {
                // unless a shared chart arrived or the fields were filled in while the answer was on its way
                if (idInput.value === LONDON.id && !shared_rows.has(target)) {
                    setLocationInputs(idInput, cityInput, start.id, start.city);
                }
            }
        })
        .catch(() => {})
        .then(proceed);
}

function setLocationInputs(idInput, cityInput, id, city) {
    idInput.value = id;
    idInput.dataset.city = city;
    cityInput.value = city;
}

function isBirthKind(kind) {
    return kind === 'birth' || kind === 'partner';
}

// the fields of a row, the first row's ids have no prefix
function rowPrefix(target) {
    return target === 'first' ? '' : target + '-';
}

// the rows of the page, with their fields
function pageRows() {
    switch (chart_type) {
        case 'birth-transit':
        case 'connection':
            return ['first', 'second'];
        case 'connection-transit':
            return ['first', 'second', 'third'];
        default:
            return ['first'];
    }
}

// the row of a field, from its id
function rowOf(id, field) {
    return ['second', 'third'].find(target => id === rowPrefix(target) + field) || 'first';
}

function rowKind(target) {
    if (target === 'third') {
        return 'partner';
    }
    if (target === 'second') {
        return chart_type === 'connection' ? 'partner' : 'birth';
    }
    return chart_type === 'birth' || chart_type === 'connection' ? 'birth' : 'location';
}

// the row that the single Load and Save of a page act on
function defaultTarget() {
    return chart_type === 'birth-transit' ? 'second' : 'first';
}

function isShared(kind) {
    return [...shared_rows].some(target => rowKind(target) === kind);
}

function rememberLocation(kind, id, city) {
    if (isShared(kind) || String(id).length === 0) {
        return;
    }
    HdStore.update(browserStorage(), data => {
        if (isBirthKind(kind)) {
            HdStore.setBirthLocation(data, id, city, kind);
        } else {
            HdStore.setLocation(data, id, city);
        }
    });
}

// a birth date is only remembered with its city, it would otherwise be shown for another one
function rememberBirthChart(chart, kind) {
    if (chart.locationId.length !== 0 && HdTime.parse(chart.datetime) !== undefined) {
        rememberBirthDatetime(chart.datetime, kind);
        rememberLocation(kind, chart.locationId, chart.locationCity);
    }
}

function rememberBirthDatetime(datetime, kind) {
    if (isShared(kind)) {
        return;
    }
    HdStore.update(browserStorage(), data => HdStore.setBirthDatetime(data, datetime, kind));
}

// kind is 'location' for the place charts are seen from, 'birth' for the visitor's birth place, or
// 'partner' for the birth place of the other person of a connection
function prepareCitiesForElements(selectorLocationId, selectorLocationCity, kind) {
    const target = rowOf(selectorLocationId.substring(1), 'location-id');
    const location_input_id = document.querySelector(selectorLocationId);
    const location_input_city = document.querySelector(selectorLocationCity);

    const data = HdStore.load(browserStorage());
    const stored = isBirthKind(kind) ? (data[kind] && data[kind].location) : data.location;
    if (location_input_id.value.length === 0) {
        const location = stored || startLocation();
        setLocationInputs(location_input_id, location_input_city, location.id, location.city);
        if (!stored) {
            unchosen_rows.set(target, [location_input_id, location_input_city]);
        }
    } else {
        // the browser restored the fields of an earlier visit to this page
        const city = stored && stored.id === location_input_id.value ? stored.city : location_input_city.value;
        setLocationInputs(location_input_id, location_input_city, location_input_id.value, city);
    }

    citySearch(location_input_id, location_input_city, (id, city) => {
        unchosen_rows.delete(target);
        // a city the visitor chooses to see the charts from is theirs, a birth place stays the shared chart's
        if (isBirthKind(kind)) {
            rememberBirthRow(target);
        } else {
            shared_rows.delete(target);
            rememberLocation(kind, id, city);
        }
        if (image_url !== null) {
            drawChart(image_url);
        }
    });
}

/**
 * Suggests cities for what's typed in a field and keeps the one that's chosen, with its id in
 * the hidden field next to it. Text that wasn't chosen from the suggestions isn't a city.
 */
function citySearch(location_input_id, location_input_city, chosen) {
    const awesomplete = new Awesomplete(location_input_city, {
        minChars: 3,
        maxItems: 200,
        // Enter chooses the first city of the list, rather than showing the chart of the city before
        autoFirst: true,
        filter: () => { return true; },
        sort: false,
        item: function (suggestion, input, item_id) {
            return Awesomplete.ITEM.call(this, highlightSuggestion(String(suggestion), input), '', item_id);
        },
        replace: function (suggestion) {
            setLocationInputs(location_input_id, location_input_city, String(suggestion.value), suggestion.label);
            chosen(String(suggestion.value), suggestion.label);
        },
        list: []
    });
    // the action key of a phone's keyboard closes it, rather than stepping on to the next control
    // of the page, the menu
    location_input_city.setAttribute('enterkeyhint', 'done');
    location_input_city.addEventListener('keydown', event => {
        if (event.key !== 'Enter') {
            return;
        }
        // without a list to choose from, the field goes back to the city that was chosen
        location_input_city.value = location_input_id.dataset.city;
        if (window.matchMedia && window.matchMedia('(pointer: coarse)').matches) {
            location_input_city.blur();
        }
    });
    // text that wasn't chosen from the suggestions doesn't change the location
    location_input_city.addEventListener('blur', () => {
        location_input_city.value = location_input_id.dataset.city;
    });
    location_input_city.addEventListener('input', (event) => {
        let ajax = new XMLHttpRequest();
        ajax.open('GET', routes.cities + '?query=' + encodeURIComponent(event.target.value), true);
        ajax.onload = function () {
            if (ajax.status !== 200) {
                return;
            }
            let result;
            try {
                result = JSON.parse(ajax.responseText);
            } catch (e) {
                return;
            }
            if (Array.isArray(result) && result.length >= 1) {
                if (result[0][0] === location_input_city.value) {
                    awesomplete.list = result.slice(1);
                    awesomplete.evaluate();
                }
            }
        };
        ajax.send();
    });
}

function escapeHtml(text) {
    return text.replace(/[&<>"']/g, c => '&#' + c.charCodeAt(0) + ';');
}

// the suggestion as HTML with the typed text marked, Awesomplete would insert the city names as HTML
function highlightSuggestion(label, input) {
    const query = input.trim();
    if (query === '') {
        return escapeHtml(label);
    }
    const parts = label.split(new RegExp('(' + query.replace(/[-\\^$*+?.()|[\]{}]/g, '\\$&') + ')', 'gi'));
    return parts.map((part, i) => i % 2 === 1 ? '<mark>' + escapeHtml(part) + '</mark>' : escapeHtml(part)).join('');
}

function prepareBirthDateForElement(elementId, kind) {
    const birth = HdStore.load(browserStorage())[HdStore.slot(kind)];
    if (birth && birth.datetime) {
        document.getElementById(elementId).value = birth.datetime;
    }
}

function fieldDatetime(elementId) {
    const time = HdTime.parse(document.getElementById(elementId).value);
    return time === undefined ? undefined : HdTime.format(time);
}

function getFieldDate() {
    return fieldDatetime('datetime');
}

function getDateOffset(seconds) {
    return HdTime.add(document.getElementById('datetime').value, seconds);
}

function dateTimeChosen(input) {
    rememberBirthRow(rowOf(input.id, 'datetime'));
}

// the date and city of a birth row, which are only remembered together
function rememberBirthRow(target) {
    const kind = rowKind(target);
    if (isBirthKind(kind)) {
        rememberBirthChart(currentChart(target), kind);
    }
}

function browserNow() {
    const now = new Date();
    return HdTime.format(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate(), now.getHours(), now.getMinutes()));
}

// the browser only knows its own time zone, the server knows the one of the location
function showNow(url) {
    const location_id = document.getElementById('location-id').value.trim();
    const datetime_value = document.getElementById('datetime').value;
    const request = chart_request;
    // a chart or a date chosen while the time was on its way was chosen later, and a city chosen
    // meanwhile has another time
    const show = datetime => {
        if (request !== chart_request || document.getElementById('datetime').value !== datetime_value) {
            return;
        }
        if (document.getElementById('location-id').value.trim() === location_id) {
            showDate(datetime, url);
        } else {
            showNow(url);
        }
    };
    fetch(location_id.length === 0 ? routes.now : routes.now + '/' + encodeURIComponent(location_id))
        .then(response => response.ok ? response.text() : Promise.reject(response.status))
        .then(text => show(HdTime.parse(text) === undefined ? browserNow() : text))
        .catch(() => show(browserNow()));
    return false;
}

/**
 * Sets the transits to a cycle of the birth, such as its Saturn return, at the time of the
 * transit city. The server knows when the planets get there and what time it is in that city.
 */
function showCycle(cycle, url) {
    const birth = HdTime.parse(document.getElementById('second-datetime').value);
    const birth_location_id = document.getElementById('second-location-id').value.trim();
    if (birth === undefined || !/^\d+$/.test(birth_location_id)) {
        return false;
    }
    const location_id = document.getElementById('location-id').value.trim();
    // the time is for this birth and this transit city, which can change while it's on its way
    const asked = () => [document.getElementById('second-datetime').value, document.getElementById('second-location-id').value,
                         document.getElementById('location-id').value].join(' ');
    const asked_for = asked();
    const request = chart_request;
    fetch(routes.cycle + '/' + cycle + '/' + HdTime.formatForImage(birth) + '/' + birth_location_id + (/^\d+$/.test(location_id) ? '/' + location_id : ''))
        .then(response => response.ok ? response.text() : Promise.reject(response.status))
        .then(text => {
            // another chart was asked for while the moment was on its way
            if (request === chart_request && asked() === asked_for && HdTime.parse(text) !== undefined) {
                showDate(text, url);
            }
        })
        .catch(() => {});
    return false;
}

/**
 * Shows the chart that the page opens with. The server starts it at the time of the city it
 * remembers, the browser can remember another one when either was cleared, and a row that nobody
 * chose a city for starts where the device is. The place that charts are seen from and a birth
 * row that nobody filled in both show this moment, so at another city they ask for its time.
 */
function showFirstChart(url, nowLocationId) {
    whenStartKnown(() => {
        const location_id = document.getElementById('location-id').value.trim();
        const moment = rowKind('first') === 'location' || unchosen_rows.has('first');
        if (nowLocationId !== '' && !shared_rows.has('first') && moment && location_id !== nowLocationId) {
            showNow(url);
        } else {
            showDate(getFieldDate(), url);
        }
    });
}

function rememberNamedChart(name, chart, kind) {
    const slot = HdStore.slot(kind);
    HdStore.update(browserStorage(), data => HdStore.setNamed(data, name, chart, slot));
}

// the name of a row's chart while it shows the saved chart that was loaded or saved last
function chartTitle(target) {
    const kind = rowKind(target);
    if (!isBirthKind(kind)) {
        return null;
    }
    // read every time, since another tab can delete or replace the saved chart
    const data = HdStore.load(browserStorage());
    const named = isShared(kind) ? shared_names[kind] || null : (kind === 'partner' ? data.namedPartner : data.named);
    if (named === null) {
        return null;
    }
    const prefix = rowPrefix(target);
    const unchanged = fieldDatetime(prefix + 'datetime') === named.datetime &&
                      document.getElementById(prefix + 'location-id').value.trim() === named.locationId;
    return unchanged ? named.name : null;
}

function imageParameters() {
    const parameters = [];
    const add = (key, value) => {
        if (value !== null) {
            parameters.push(key + '=' + encodeURIComponent(value));
        }
    };
    // the server remembers the city of the first row, unless it's the one of a shared link
    if (shared_rows.has('first')) {
        add('shared', '1');
    }
    const [name, second_name] = chartNames();
    add('name', name ?? null);
    add('secondName', second_name ?? null);
    return parameters.length === 0 ? '' : '?' + parameters.join('&');
}

const NAMED_ROWS = {'birth': ['first'], 'birth-transit': ['second'], 'connection': ['first', 'second'], 'connection-transit': ['second', 'third']};

// the names of the people of the chart, null for a person without one
function chartNames() {
    return (NAMED_ROWS[chart_type] || []).map(chartTitle);
}

function showDate(datetime, url) {
    image_url = url;
    const time = HdTime.parse(datetime);
    if (time === undefined) {
        return false;
    }
    document.getElementById('datetime').value = HdTime.format(time);
    if (!first_show) {
        rememberBirthRow('first');
    }
    drawChart(url);
    return false;
}

/**
 * Draws the chart of the fields as they are, without remembering them, for the rows that were
 * remembered already or that changed in another tab.
 */
function drawChart(url) {
    image_url = url;
    first_show = false;
    const time = HdTime.parse(document.getElementById('datetime').value);
    if (time === undefined) {
        return;
    }
    let image_src = url;

    const births = pageRows().slice(1).map(target => {
        const prefix = rowPrefix(target);
        const birth_time = HdTime.parse(document.getElementById(prefix + 'datetime').value);
        const birth_location_id = document.getElementById(prefix + 'location-id').value.trim();
        return birth_time === undefined || birth_location_id.length === 0 ? null : HdTime.formatForImage(birth_time) + '/' + birth_location_id + '/';
    });
    showHint(births.includes(null));
    if (births.every(birth => birth !== null)) {
        image_src = image_src + births.join('');
    } else if (chart_type !== 'connection') {
        // the transits are only drawn with every birth, a connection shows the first chart until
        // the second has a date and a city
        showChart(image_src.substring(0, image_src.length - 1));
        return;
    }

    const location_id = document.getElementById('location-id').value.trim();
    if (location_id.length === 0) {
        // never let the server choose a city for a chart that has none
        showChart(null);
        return;
    }
    image_src = image_src + HdTime.formatForImage(time) + '/' + location_id + imageParameters();
    showChart(image_src);
}

const BIRTH_HINTS = {
    'birth-transit': 'Enter a birth date, time and city in the Birth row, then press Show, to see the transits on that birth chart.',
    'connection': 'Enter the birth date, time and city of the other person in the With row, then press Show, to see how the two charts meet.',
    'connection-transit': 'Enter the birth date, time and city of both people, then press Show, to see their connection with the transits.'
};

// what a chart still needs, below the fields, while a birth is missing
function showHint(birthMissing) {
    const hint = document.getElementById('chart-hint');
    if (!hint) {
        return;
    }
    let text = '';
    if (chart_type === 'birth') {
        // the Birth Chart opens with the current moment until a birth has been shown
        if (!isShared('birth') && HdStore.load(browserStorage()).birth === null) {
            text = 'This is the chart of this moment. Enter a birth date, time and city above, then press Show.';
        }
    } else if (birthMissing) {
        text = BIRTH_HINTS[chart_type] || '';
    }
    hint.textContent = text;
    hint.hidden = text === '';
}

const CHART_FAILED = '<p class="chart-notice">The chart couldn\'t be loaded. Check the connection and press Show to try again.</p>';

// the chart is drawn in the page, rather than as an image, so that its parts can be tapped, and a
// table is HTML that takes its place
let chart_request = 0;
// hears of the address of the chart that's shown, null when there's none, see app.js
let chart_shown = null;
// told once whether the chart that was asked for last got drawn
let chart_settled = null;

function chartSettled(drawn) {
    const settled = chart_settled;
    chart_settled = null;
    if (settled) {
        settled(drawn);
    }
}

function showChart(url) {
    const chart = document.getElementById('svg-image');
    const request = ++chart_request;
    chart.dataset.src = url === null ? '' : url;
    const download = document.getElementById('chart-download');
    if (download) {
        // an empty link would download the page itself, and a table isn't an image to save
        if (url === null || chart_type === 'table') {
            download.removeAttribute('href');
        } else {
            download.href = url;
        }
    }
    const share = document.getElementById('chart-share');
    if (share) {
        share.hidden = !sharing || url === null;
    }
    showExplore(url);
    chart_shown?.(url);
    // only a drawing that's there is shown full screen
    const zoom = document.getElementById('chart-zoom');
    const zoomable = drawn => {
        if (zoom) {
            zoom.hidden = !drawn || chart_type === 'table';
        }
    };
    if (url === null) {
        chart.innerHTML = '';
        zoomable(false);
        chartSettled(false);
        return;
    }
    fetch(url)
        .then(response => response.ok ? response.text() : Promise.reject(response.status))
        .then(svg => {
            // an answer to a chart that has been replaced since
            if (request === chart_request) {
                chart.innerHTML = svg;
                zoomable(true);
                chartSettled(true);
            }
        })
        .catch(() => {
            if (request === chart_request) {
                chart.innerHTML = CHART_FAILED;
                zoomable(false);
                chartSettled(false);
            }
        });
}

// A button that steps through time and is held down steps on by itself, each time once the chart
// of the step before is there, so that the chart is seen moving through time.
const STEP_BUTTONS = '#change-time button[id]';
const HOLD_BEFORE = 400;
const HOLD_BETWEEN = 80;
let held_step = null;
// the click that a browser makes of letting go of a button, which stepped by itself already
let held_click = false;

function holdStep(button) {
    releaseStep();
    held_click = false;
    const hold = held_step = {button, stepped: false, timer: null};
    hold.timer = setTimeout(() => stepHeld(hold), HOLD_BEFORE);
}

function stepHeld(hold) {
    if (held_step !== hold) {
        return;
    }
    hold.stepped = true;
    const started = Date.now();
    // a chart that can't be drawn ends it, there's nothing to see further on
    chart_settled = drawn => {
        if (held_step === hold && drawn) {
            hold.timer = setTimeout(() => stepHeld(hold), Math.max(0, HOLD_BETWEEN - (Date.now() - started)));
        }
    };
    hold.button.click();
}

function releaseStep() {
    const hold = held_step;
    if (!hold) {
        return;
    }
    held_step = null;
    clearTimeout(hold.timer);
    if (hold.stepped) {
        chart_settled = null;
        held_click = true;
        // no click comes of letting go next to the button
        setTimeout(() => held_click = false, 500);
    }
}

// The chart over the whole screen, where it's made larger and moved around to read its details.
// A view is how large the drawing is and where its corner is, in the room that the screen gives it.
const VIEW_LARGEST = 8;
let chart_view = null;
let viewer_drawing = null;
let viewer_opener = null;
// the fingers or the mouse button that are down, and the tap before for a double tap
const view_pointers = new Map();
let view_tap = null;
// whether the click that's told next ends a tap on the chart, rather than a move of it
let view_tapped = false;
// where the card of a tapped part is on the page, while it's shown with the chart
let card_home = null;

// a drawing smaller than the room is in its middle, a larger one leaves no gap at its edges
function settleView(view) {
    const settle = (position, size, room) => size <= room ? (room - size) / 2 : Math.min(0, Math.max(room - size, position));
    return Object.assign({}, view, {
        x: settle(view.x, view.width * view.scale, view.roomWidth),
        y: settle(view.y, view.height * view.scale, view.roomHeight)
    });
}

// larger or smaller around a point of the screen, which keeps showing the same spot of the drawing
function zoomView(view, factor, x, y) {
    const scale = Math.min(VIEW_LARGEST, Math.max(1, view.scale * factor));
    const ratio = scale / view.scale;
    return settleView(Object.assign({}, view, {scale, x: x - (x - view.x) * ratio, y: y - (y - view.y) * ratio}));
}

function moveView(view, dx, dy) {
    return settleView(Object.assign({}, view, {x: view.x + dx, y: view.y + dy}));
}

// the whole wheel fits the room to begin with, what the drawing has below it is reached by moving it
function firstView(roomWidth, roomHeight, drawingWidth, drawingHeight) {
    const width = Math.min(roomWidth, roomHeight);
    return settleView({scale: 1, x: 0, y: 0, width, height: width * drawingHeight / drawingWidth, roomWidth, roomHeight});
}

// the spot of the drawing that a point of the screen shows
function viewedSpot(view, x, y, drawingWidth) {
    const ratio = drawingWidth / (view.width * view.scale);
    return {x: (x - view.x) * ratio, y: (y - view.y) * ratio};
}

function drawChartView() {
    viewer_drawing.style.width = chart_view.width + 'px';
    viewer_drawing.style.height = chart_view.height + 'px';
    viewer_drawing.style.transform = 'translate(' + chart_view.x + 'px, ' + chart_view.y + 'px) scale(' + chart_view.scale + ')';
}

function roomView() {
    const stage = document.getElementById('chart-viewer-stage');
    const [, , width, height] = viewer_drawing.getAttribute('viewBox').split(/\s+/).map(Number);
    return firstView(stage.clientWidth, stage.clientHeight, width, height);
}

function resetChartView() {
    chart_view = roomView();
    drawChartView();
}

// in another room, when a phone is turned or its browser shows or hides its bars, the chart keeps
// its size and what was in the middle stays there
function refitChartView() {
    const before = chart_view;
    // a chart that wasn't made larger starts again, with its wheel in sight
    if (before.scale === 1) {
        resetChartView();
        return;
    }
    const room = roomView();
    const middle = (position, size, space) => (space / 2 - position) / (size * before.scale);
    chart_view = settleView(Object.assign(room, {
        scale: before.scale,
        x: room.roomWidth / 2 - middle(before.x, before.width, before.roomWidth) * room.width * before.scale,
        y: room.roomHeight / 2 - middle(before.y, before.height, before.roomHeight) * room.height * before.scale
    }));
    drawChartView();
}

function openChartViewer(opener) {
    const viewer = document.getElementById('chart-viewer');
    const drawing = document.querySelector('#svg-image svg');
    if (!viewer || !drawing) {
        return;
    }
    const stage = document.getElementById('chart-viewer-stage');
    viewer_drawing = drawing.cloneNode(true);
    stage.innerHTML = '';
    stage.appendChild(viewer_drawing);
    viewer.hidden = false;
    viewer_opener = opener || document.activeElement;
    view_pointers.clear();
    view_tap = null;
    view_tapped = false;
    // a browser's full screen shows nothing else of the page, so the card of a tapped part comes along
    const card = document.getElementById('explore-card');
    if (card && card.parentNode) {
        hideExploreCard();
        card_home = {parent: card.parentNode, next: card.nextSibling};
        viewer.appendChild(card);
    }
    resetChartView();
    document.getElementById('chart-viewer-close').focus();
    // the browser's own full screen where it has one for a page, a phone shows the chart over the page.
    // The window is resized by it, which fits the chart again.
    if (viewer.requestFullscreen) {
        Promise.resolve(viewer.requestFullscreen()).catch(() => {}).then(() => {
            // closed before the browser got to it
            if (chart_view === null && document.fullscreenElement && document.exitFullscreen) {
                Promise.resolve(document.exitFullscreen()).catch(() => {});
            }
        });
    }
}

function closeChartViewer() {
    if (chart_view === null) {
        return false;
    }
    hideExploreCard();
    if (card_home !== null) {
        card_home.parent.insertBefore(document.getElementById('explore-card'), card_home.next);
        card_home = null;
    }
    chart_view = null;
    viewer_drawing = null;
    document.getElementById('chart-viewer').hidden = true;
    document.getElementById('chart-viewer-stage').innerHTML = '';
    const opener = viewer_opener;
    viewer_opener = null;
    opener?.focus();
    if (document.fullscreenElement && document.exitFullscreen) {
        // the browser takes the focus away while it leaves its full screen
        Promise.resolve(document.exitFullscreen()).catch(() => {}).then(() => opener?.focus());
    }
    return true;
}

// by a step, around the middle of the screen
function stepChartView(factor) {
    chart_view = zoomView(chart_view, factor, chart_view.roomWidth / 2, chart_view.roomHeight / 2);
    drawChartView();
}

// the arrows move the chart and + and - make it larger and smaller, for who has no fingers or mouse on it
const VIEW_KEYS = {ArrowLeft: [60, 0], ArrowRight: [-60, 0], ArrowUp: [0, 60], ArrowDown: [0, -60]};

function viewKeyPressed(event) {
    // with Ctrl, Alt or Cmd they're the browser's
    if (chart_view === null || event.ctrlKey || event.altKey || event.metaKey) {
        return false;
    }
    if (event.key === '+' || event.key === '=') {
        stepChartView(1.5);
    } else if (event.key === '-') {
        stepChartView(1 / 1.5);
    } else if (VIEW_KEYS[event.key]) {
        chart_view = moveView(chart_view, ...VIEW_KEYS[event.key]);
        drawChartView();
    } else {
        return false;
    }
    event.preventDefault();
    return true;
}

function viewPointerDown(event) {
    const stage = chart_view === null ? null : event.target.closest('#chart-viewer-stage');
    // fingers, a pen and the main button of a mouse
    if (!stage || event.button > 0) {
        return;
    }
    view_tapped = false;
    // the end of the gesture is told even when it's outside of the window
    stage.setPointerCapture?.(event.pointerId);
    view_pointers.set(event.pointerId, {x: event.clientX, y: event.clientY, moved: 0});
}

// one finger moves the chart, two make it larger or smaller around the point between them
function viewPointerMoved(event) {
    const before = view_pointers.get(event.pointerId);
    if (chart_view === null || before === undefined) {
        return;
    }
    // a mouse whose button was let go without it being told
    if (event.pointerType === 'mouse' && event.buttons === 0) {
        view_pointers.delete(event.pointerId);
        return;
    }
    const now = {x: event.clientX, y: event.clientY, moved: before.moved + Math.hypot(event.clientX - before.x, event.clientY - before.y)};
    if (view_pointers.size === 1) {
        chart_view = moveView(chart_view, now.x - before.x, now.y - before.y);
    } else if (view_pointers.size === 2) {
        const other = [...view_pointers].find(([id]) => id !== event.pointerId)[1];
        const apart = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
        const from = {x: (before.x + other.x) / 2, y: (before.y + other.y) / 2};
        const to = {x: (now.x + other.x) / 2, y: (now.y + other.y) / 2};
        chart_view = moveView(zoomView(chart_view, apart(now, other) / Math.max(1, apart(before, other)), from.x, from.y), to.x - from.x, to.y - from.y);
    }
    view_pointers.set(event.pointerId, now);
    drawChartView();
}

// a tap shows what the part under it is, a second tap on the same spot makes the chart larger
// there instead, or brings it back to how it began
function viewPointerUp(event) {
    const pointer = view_pointers.get(event.pointerId);
    view_pointers.delete(event.pointerId);
    if (chart_view === null || pointer === undefined || event.type === 'pointercancel' || pointer.moved > 8 || view_pointers.size > 0) {
        view_tap = null;
        return;
    }
    if (view_tap !== null && event.timeStamp - view_tap.time < 350 && Math.hypot(pointer.x - view_tap.x, pointer.y - view_tap.y) < 30) {
        view_tap = null;
        hideExploreCard();
        if (chart_view.scale > 1) {
            resetChartView();
        } else {
            chart_view = zoomView(chart_view, 3, pointer.x, pointer.y);
            drawChartView();
        }
    } else {
        view_tap = {time: event.timeStamp, x: pointer.x, y: pointer.y};
        view_tapped = true;
    }
}

function viewWheelTurned(event) {
    if (chart_view === null) {
        return;
    }
    event.preventDefault();
    chart_view = zoomView(chart_view, Math.exp(-event.deltaY * 0.002), event.clientX, event.clientY);
    drawChartView();
}

// the descriptions of the chart that an image shows, on the pages that have room for them
let explore_request = 0;

function showExplore(image_src) {
    const panel = document.getElementById('explore');
    if (!panel) {
        return;
    }
    const request = ++explore_request;
    const image_route = image_src === null ? undefined : Object.keys(routes.explore).find(route => image_src.startsWith(route));
    if (image_route === undefined) {
        showPanel(panel, '');
        return;
    }
    // the panel of the chart before stays until the next one is there, so the page keeps its height
    fetch(routes.explore[image_route] + image_src.substring(image_route.length))
        .then(response => response.ok ? response.text() : '')
        .catch(() => '')
        .then(html => {
            // an answer to a chart that has been replaced since
            if (request === explore_request) {
                showPanel(panel, html);
            }
        });
}

// the items that were open stay open in the panel of the next chart, and so does the card
function showPanel(panel, html) {
    const open = Array.from(panel.querySelectorAll('details[data-part][open]'), item => item.dataset.part);
    panel.innerHTML = html;
    for (const part of open) {
        const item = panelItem(part);
        if (item) {
            item.open = true;
        }
    }
    const guide = panel.querySelector('.explore-guide');
    if (guide && HdStore.load(browserStorage()).guideOpen) {
        guide.open = true;
    }
    const card = document.getElementById('explore-card');
    if (card && !card.hidden) {
        const items = card_items.map(item => panelItem(item.dataset.part));
        if (items.some(item => !item)) {
            hideExploreCard();
        } else {
            card_history = [];
            card_items = items;
            showCardItem(card_index);
        }
    }
}

// shared links carry the chart after the #, which browsers never send to the server
const SHARE_PREFIX = '#share&';

/**
 * Lets the charts of the page be shared, and shows the chart of a shared link instead of the
 * visitor's own, when the page was opened from one.
 */
function prepareSharing() {
    sharing = true;
    const hash = window.location ? window.location.hash : '';
    if (!hash || !hash.startsWith(SHARE_PREFIX)) {
        return false;
    }
    const parameters = new URLSearchParams(hash.substring(SHARE_PREFIX.length));
    const targets = pageRows();
    const rows = targets.map(target => sharedRow(parameters, target));
    if (rows.every(row => row === null)) {
        return false;
    }
    targets.forEach((target, i) => {
        if (rows[i] !== null) {
            applySharedRow(target, rows[i]);
        } else if (isBirthKind(rowKind(target))) {
            // a birth the link doesn't carry is left empty rather than mixed with the visitor's own
            applySharedRow(target, {datetime: '', location: '', city: '', name: null});
        }
    });
    return true;
}

// the name of a parameter of a shared link, the other rows' are prefixed with their row
function shareKey(target, name) {
    return target === 'first' ? name : target + name.charAt(0).toUpperCase() + name.substring(1);
}

function sharedRow(parameters, target) {
    const datetime = parameters.get(shareKey(target, 'datetime'));
    const location = parameters.get(shareKey(target, 'location'));
    const city = parameters.get(shareKey(target, 'city'));
    if (datetime === null || HdTime.parse(datetime) === undefined || location === null || !/^\d+$/.test(location) || city === null) {
        return null;
    }
    return {datetime: HdTime.normalize(datetime), location: location, city: city, name: parameters.get(shareKey(target, 'name'))};
}

function applySharedRow(target, row) {
    const prefix = rowPrefix(target);
    shared_rows.add(target);
    document.getElementById(prefix + 'datetime').value = row.datetime;
    setLocationInputs(document.getElementById(prefix + 'location-id'), document.getElementById(prefix + 'location'), row.location, row.city);
    const kind = rowKind(target);
    if (isBirthKind(kind)) {
        shared_names[kind] = row.name === null ? null : {name: row.name, datetime: row.datetime, locationId: row.location};
    }
}

/**
 * The link to the page with the charts it shows.
 */
function shareUrl() {
    const parameters = new URLSearchParams();
    for (const target of pageRows()) {
        addSharedRow(parameters, target);
    }
    return (share_root || window.location.origin) + window.location.pathname + SHARE_PREFIX + parameters.toString();
}

function addSharedRow(parameters, target) {
    const prefix = rowPrefix(target);
    const datetime_input = document.getElementById(prefix + 'datetime');
    const datetime = datetime_input ? fieldDatetime(prefix + 'datetime') : undefined;
    const location_input = document.getElementById(prefix + 'location-id');
    if (datetime === undefined || !location_input || location_input.value.trim().length === 0) {
        return;
    }
    parameters.set(shareKey(target, 'datetime'), datetime);
    parameters.set(shareKey(target, 'location'), location_input.value.trim());
    parameters.set(shareKey(target, 'city'), location_input.dataset.city || '');
    const name = chartTitle(target);
    if (name !== null) {
        parameters.set(shareKey(target, 'name'), name);
    }
}

function shareChart() {
    const url = shareUrl();
    const share = document.getElementById('chart-share');
    if (navigator.share) {
        navigator.share({title: document.title, url: url}).catch(() => {});
    } else if (navigator.clipboard) {
        navigator.clipboard.writeText(url).then(() => share.classList.add('copied'), () => window.history.replaceState(null, '', url));
        setTimeout(() => share.classList.remove('copied'), 2000);
    } else {
        // without a secure connection there's no clipboard, the address bar shows the link instead,
        // as it does when the browser refuses to copy
        window.history.replaceState(null, '', url);
    }
}

// the part of the wheel a tap falls on, from its distance to the center, for the rings that are
// drawn as overlapping circles
function wheelPart(x, y) {
    if (y > 960) {
        return null;
    }
    const radius = Math.hypot(x - 480, y - 480);
    if (radius > 480) {
        return null;
    }
    if (radius >= 448) {
        return 'wheel.zodiac';
    }
    if (radius >= 408) {
        return 'wheel.planets';
    }
    // just inside the ring of gates, where the marks of the people of a connection are
    if (radius >= 354) {
        return 'wheel.gates';
    }
    return 'wheel.bodygraph';
}

function explorePart(event) {
    if (!document.getElementById('explore-card')) {
        return;
    }
    // full screen, a tap is told to what holds the copy of the chart rather than to its parts
    const viewed = chart_view !== null && event.target.closest('#chart-viewer-stage');
    if (viewed && !view_tapped) {
        return;
    }
    view_tapped = false;
    const chart = document.getElementById('svg-image');
    const svg = viewed ? viewer_drawing : chart ? chart.querySelector('svg') : null;
    if (!svg || !viewed && !svg.contains(event.target)) {
        return;
    }
    // planets drawn on top of each other can all be reached from one tap
    const under = document.elementsFromPoint ? document.elementsFromPoint(event.clientX, event.clientY) : [event.target];
    const tagged = [];
    for (const element of [event.target, ...under]) {
        const parts = element.closest('[data-explore]');
        if (parts && svg.contains(parts) && !tagged.includes(parts)) {
            tagged.push(parts);
        }
    }
    let items = tagged.length === 0 ? [] : exploreItems(tagged[0].dataset.explore);
    if (items.length !== 0 && isPlanet(items[0])) {
        for (const parts of tagged.slice(1)) {
            for (const item of exploreItems(parts.dataset.explore)) {
                if (isPlanet(item) && !items.includes(item)) {
                    items.push(item);
                }
            }
        }
        // in the order of the panel, rather than the order the symbols are drawn in
        items.sort((a, b) => a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1);
    }
    if (items.length === 0) {
        const point = viewed ? viewedSpot(chart_view, event.clientX, event.clientY, Number(svg.getAttribute('viewBox').split(/\s+/)[2])) :
                      new DOMPoint(event.clientX, event.clientY).matrixTransform(svg.getScreenCTM().inverse());
        items = exploreItems(wheelPart(point.x, point.y));
    }
    showExploreCard(items);
}

/**
 * The items of the panel that a tapped part names, the ones of the same kind as the first that
 * the panel has, such as all the planets in a gate or all the defined channels along a line.
 * The parts after them are what's shown when the panel has none of those.
 */
function exploreItems(parts) {
    const items = [];
    let kind = null;
    for (const part of parts === null ? [] : parts.split(' ')) {
        const item = document.querySelector('#explore details[data-part="' + part + '"]');
        const part_kind = part.split('.')[0];
        if (item && (kind === null || part_kind === kind) && !items.includes(item)) {
            kind = part_kind;
            items.push(item);
        }
    }
    return items;
}

function isPlanet(item) {
    return item.dataset.part.startsWith('activation.');
}

// the items of the open card, which chips at its top step through when there are several
let card_items = [];
let card_index = 0;
// the cards that related links were followed from, which Back returns to
let card_history = [];
// what had the focus before the card opened, which gets it back when the card closes
let card_opener = null;
// hears of the card when it opens, shows something else or closes, see app.js
let card_changed = null;

function showExploreCard(items) {
    const card = document.getElementById('explore-card');
    if (card && card.hidden) {
        card_opener = document.activeElement;
    }
    card_history = [];
    openCard(items, 0);
    // the card comes next for the keyboard and screen readers, rather than after the whole page
    if (card && !card.hidden) {
        card.focus({preventScroll: true});
    }
}

function openCard(items, index) {
    const card = document.getElementById('explore-card');
    if (!card || items.length === 0) {
        hideExploreCard();
        return;
    }
    card_items = items;
    showCardItem(index);
    card.hidden = false;
    card.scrollTop = 0;
    card_changed?.();
}

function panelItem(part) {
    return document.querySelector('#explore details[data-part="' + part + '"]');
}

/**
 * The items of the panel that an item relates to, such as the center and channels of a
 * planet's gate, leaving out the ones that the chips of the card already show.
 */
function relatedItems(item) {
    const related = [];
    for (const part of (item.dataset.related || '').split(' ')) {
        const each = part === '' ? null : panelItem(part);
        if (each && each !== item && !card_items.includes(each) && !related.includes(each)) {
            related.push(each);
        }
    }
    return related.sort((a, b) => a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1);
}

function followRelated(part) {
    const item = panelItem(part);
    if (!item) {
        return;
    }
    card_history.push({items: card_items, index: card_index, part: part});
    openCard([item], 0);
    document.querySelector('#explore-card .explore-card-back')?.focus();
}

function cardBack() {
    const previous = card_history.pop();
    if (!previous) {
        return;
    }
    openCard(previous.items, previous.index);
    // back on the link that was followed, as a browser's Back returns to it
    document.querySelector('#explore-card .explore-card-link[data-part="' + previous.part + '"]')?.focus();
}

function showCardItem(index) {
    card_index = index;
    const item = card_items[index];
    const chip_focused = document.activeElement && document.activeElement.classList &&
                         document.activeElement.classList.contains('explore-card-chip');
    let back = '';
    if (card_history.length !== 0) {
        back = '<button type="button" class="explore-card-back">&lsaquo; Back</button>';
    }
    let chips = '';
    if (card_items.length > 1) {
        chips = '<div class="explore-card-chips" role="group" aria-label="What this part of the chart shows">' + card_items.map((each, i) =>
            '<button type="button" class="explore-card-chip" data-index="' + i + '" aria-pressed="' + (i === index) + '">' +
            escapeHtml(each.querySelector('.explore-label').textContent) + '</button>').join('') + '</div>';
    }
    const paragraphs = Array.from(item.querySelectorAll('p')).map(p => p.outerHTML).join('');
    const related = relatedItems(item);
    let links = '';
    if (related.length !== 0) {
        links = '<div class="explore-card-related"><p class="explore-card-related-title" id="explore-card-related-title">Related</p><div class="explore-card-chips" role="group" aria-labelledby="explore-card-related-title">' + related.map(each =>
            '<button type="button" class="explore-card-link" data-part="' + escapeHtml(each.dataset.part) + '">' +
            escapeHtml(each.querySelector('.explore-label').textContent) + '</button>').join('') + '</div></div>';
    }
    const content = document.getElementById('explore-card-content');
    content.innerHTML = back + chips + '<p class="explore-card-title">' + item.querySelector('summary').innerHTML + '</p>' + paragraphs + links;
    // the chips are drawn again, the one that was used keeps the focus
    if (chip_focused) {
        content.querySelector('.explore-card-chip[data-index="' + index + '"]')?.focus();
    }
    card_changed?.();
}

function hideExploreCard() {
    const card = document.getElementById('explore-card');
    if (!card || card.hidden) {
        return;
    }
    const had_focus = card.contains(document.activeElement);
    card.hidden = true;
    if (had_focus && card_opener && card_opener !== document.body && card_opener.isConnected) {
        card_opener.focus({preventScroll: true});
    }
    card_opener = null;
    card_changed?.();
}

// the parts of what a page shows as text: the summary that phones have below the wheel, and the tables
const TEXT_PARTS = '#chart-summary [data-explore], #range-table [data-explore]';

// the menu opens from its button, and closes from it, with Escape or with a click next to it
function setMenuOpen(open) {
    const button = document.getElementById('menu-button');
    if (!button) {
        return false;
    }
    const was_open = button.getAttribute('aria-expanded') === 'true';
    button.setAttribute('aria-expanded', String(open));
    return was_open !== open;
}

// the top of the page, which scrolls with the window in a browser and inside it in an app
function scrollToTop() {
    const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    for (const scrolled of [window, document.body]) {
        if (scrolled && typeof scrolled.scrollTo === 'function') {
            scrolled.scrollTo({top: 0, behavior: reduced ? 'auto' : 'smooth'});
        }
    }
}

if (typeof document.addEventListener === 'function') {
    document.addEventListener('click', event => {
        const menu_button = event.target.closest('#menu-button');
        if (menu_button) {
            setMenuOpen(menu_button.getAttribute('aria-expanded') !== 'true');
            return;
        }
        const closed = !event.target.closest('#menu') && setMenuOpen(false);
        if (event.target.closest('#title-bar') && !event.target.closest('button, a')) {
            // the bar stays while the page scrolls, so it's where to go back up from, unless the click was to close the menu
            if (!closed) {
                scrollToTop();
            }
        } else if (event.target.closest('#welcome-again')) {
            // a page that has the welcome shows it itself, the link leads to one that does
            if (showWelcome()) {
                event.preventDefault();
            }
        } else if (event.target.closest('#chart-share')) {
            shareChart();
        } else if (event.target.closest('#chart-zoom')) {
            openChartViewer(event.target.closest('#chart-zoom'));
        } else if (event.target.closest('#chart-viewer-close')) {
            closeChartViewer();
        } else if (event.target.closest('#chart-viewer-in')) {
            stepChartView(1.5);
        } else if (event.target.closest('#chart-viewer-out')) {
            stepChartView(1 / 1.5);
        } else if (event.target.closest('#explore-card-close')) {
            hideExploreCard();
        } else if (event.target.closest('.explore-card-chip')) {
            showCardItem(Number(event.target.closest('.explore-card-chip').dataset.index));
        } else if (event.target.closest('.explore-card-link')) {
            followRelated(event.target.closest('.explore-card-link').dataset.part);
        } else if (event.target.closest('.explore-card-back')) {
            cardBack();
        } else if (event.target.closest(TEXT_PARTS)) {
            showExploreCard(exploreItems(event.target.closest(TEXT_PARTS).dataset.explore));
        } else if (!event.target.closest('#explore-card')) {
            explorePart(event);
        }
    });
    document.addEventListener('pointerdown', event => {
        const button = event.button === 0 ? event.target.closest?.(STEP_BUTTONS) : null;
        if (button) {
            holdStep(button);
        }
    });
    document.addEventListener('pointerup', releaseStep);
    // a finger that starts to scroll
    document.addEventListener('pointercancel', releaseStep);
    document.addEventListener('pointerout', event => {
        if (held_step && event.target === held_step.button && !held_step.button.contains(event.relatedTarget)) {
            releaseStep();
        }
    });
    document.addEventListener('visibilitychange', releaseStep);
    // before the button gets it
    document.addEventListener('click', event => {
        if (held_click && event.isTrusted && event.target.closest?.(STEP_BUTTONS)) {
            held_click = false;
            event.preventDefault();
            event.stopPropagation();
        }
    }, true);
    // a long press on a phone is a hold here, not a question of what to do with the button
    document.addEventListener('contextmenu', event => {
        if (held_step) {
            event.preventDefault();
        }
    });
    document.addEventListener('pointerdown', viewPointerDown);
    document.addEventListener('pointermove', viewPointerMoved);
    document.addEventListener('pointerup', viewPointerUp);
    document.addEventListener('pointercancel', viewPointerUp);
    // not passive, the page behind the chart doesn't scroll
    document.addEventListener('wheel', viewWheelTurned, {passive: false});
    // Escape and the Back of a phone leave the browser's full screen by themselves
    document.addEventListener('fullscreenchange', () => {
        if (!document.fullscreenElement) {
            closeChartViewer();
        }
    });
    document.addEventListener('keydown', event => {
        if (viewKeyPressed(event) || event.key !== 'Escape') {
            return;
        }
        // full screen, the card of a tapped part closes before the chart does
        const card = document.getElementById('explore-card');
        if (chart_view !== null && card && !card.hidden) {
            hideExploreCard();
            return;
        }
        if (closeChartViewer()) {
            return;
        }
        // the focus goes back to the button when it was on a link of the menu
        if (setMenuOpen(false) && document.activeElement?.closest?.('#menu')) {
            document.getElementById('menu-button').focus();
        }
        hideExploreCard();
    });
    // the "How to read" introduction stays open or closed for the next charts
    document.addEventListener('toggle', event => {
        if (event.target.classList?.contains('explore-guide')) {
            HdStore.update(browserStorage(), data => data.guideOpen = event.target.open);
        }
    }, true);
}

function currentChart(target) {
    const prefix = rowPrefix(target);
    const location_input_id = document.getElementById(prefix + 'location-id');
    return {
        datetime: fieldDatetime(prefix + 'datetime'),
        locationId: location_input_id.value.trim(),
        locationCity: location_input_id.dataset.city || ''
    };
}

function loadEntry(url, name) {
    const chart = HdStore.load(browserStorage()).charts[name];
    if (chart === undefined) {
        displaySavedEntries(url, load_target);
        return;
    }

    const target = load_target || defaultTarget();
    const prefix = rowPrefix(target);
    const kind = rowKind(target);
    // loading a chart is a choice of the visitor, the shared one is left behind
    shared_rows.delete(target);
    document.getElementById(prefix + 'datetime').value = chart.datetime;
    setLocationInputs(document.getElementById(prefix + 'location-id'), document.getElementById(prefix + 'location'),
        chart.locationId, chart.locationCity);
    if (isBirthKind(kind)) {
        rememberBirthChart(chart, kind);
    } else {
        rememberLocation('location', chart.locationId, chart.locationCity);
    }

    closePopup();

    if (isBirthKind(kind)) {
        rememberNamedChart(name, chart, kind);
    }
    drawChart(url);
}

function deleteEntry(name) {
    const stored = HdStore.update(browserStorage(), data => HdStore.removeChart(data, name));
    showPeople(stored ? '"' + name + '" is deleted.' : NOT_STORED);
}

const TRASH_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M4 7h16M10 4h4M6 7l1 13h10l1-13M10 11v6M14 11v6" ' +
    'fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
let armed_delete = null;

// the first click arms the button, which then asks to be clicked again, and it disarms by itself after a while
function deleteButton(name) {
    const button = document.createElement('button');
    button.setAttribute('type', 'button');
    const disarm = () => {
        clearTimeout(button.disarmTimer);
        button.setAttribute('class', 'delete-entry');
        button.setAttribute('aria-label', 'Delete ' + name);
        button.setAttribute('title', 'Delete');
        button.innerHTML = TRASH_ICON;
        button.armed = false;
    };
    button.disarm = disarm;
    disarm();
    button.onclick = function () {
        if (button.armed) {
            armed_delete = null;
            deleteEntry(name);
            return false;
        }
        armed_delete?.disarm();
        armed_delete = button;
        button.armed = true;
        button.setAttribute('class', 'delete-entry armed');
        button.setAttribute('aria-label', 'Tap or click again to delete ' + name);
        button.setAttribute('title', 'Tap or click again to delete');
        button.innerHTML = TRASH_ICON + '<span>Delete</span>';
        button.disarmTimer = setTimeout(() => {
            disarm();
            if (armed_delete === button) {
                armed_delete = null;
            }
        }, 4000);
        return false;
    };
    return button;
}

function exportEntries() {
    const json = HdStore.exportCharts(HdStore.load(browserStorage()));
    saveFile(new Blob([json], {type: 'application/json'}), 'hd-people-' + new Date().toISOString().slice(0, 10) + '.json');
}

function importEntries(file) {
    file.text().then(text => {
        let result;
        let error;
        const saved = HdStore.update(browserStorage(), data => {
            try {
                result = HdStore.importCharts(data, text);
            } catch (e) {
                error = e;
            }
        });
        let notice;
        if (error !== undefined) {
            notice = error.message;
        } else if (!saved) {
            notice = "The people couldn't be stored.";
        } else {
            notice = importMessage(result);
            requestPersistentStorage();
        }
        showPeople(notice);
    });
}

function importMessage(result) {
    const people = (count, one, many) => count === 1 ? '1 person ' + one : count + ' people ' + many;
    let message = 'Imported ' + (result.added === 1 ? '1 new person.' : result.added + ' new people.');
    if (result.renamed > 0) {
        message += ' ' + people(result.renamed, 'had the name of someone else already saved and was imported with a number after the name.',
            'had the names of others already saved and were imported with a number after their name.');
    }
    if (result.unchanged > 0) {
        message += ' ' + people(result.unchanged, 'was already saved.', 'were already saved.');
    }
    if (result.grouped > 0) {
        message += ' ' + people(result.grouped, 'was already saved and was put in the group the file gives them.', 'were already saved and were put in the groups the file gives them.');
    }
    if (result.invalid > 0) {
        message += ' ' + people(result.invalid, 'was incomplete and skipped.', 'were incomplete and skipped.');
    }
    return message;
}

function actionButton(label, action) {
    const button = document.createElement('button');
    button.textContent = label;
    button.onclick = function () {
        action();
        return false;
    };
    return button;
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

// as the moments below the charts, 1975-06-21T14:30 is June 21, 1975 @ 2:30 PM
function savedMoment(datetime) {
    const [, year, month, day, hour, minute] = datetime.match(/(\d+)-(\d+)-(\d+)T(\d+):(\d+)/).map(Number);
    const hours = hour % 12 === 0 ? 12 : hour % 12;
    return MONTHS[month - 1] + ' ' + day + ', ' + year + ' @ ' + hours + ':' + String(minute).padStart(2, '0') + (hour < 12 ? ' AM' : ' PM');
}

// as human-design-birth-chart-kim-1975-06-21-1430, after the page and the people of the chart
function chartFileName() {
    const slug = text => text.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    const heading = document.getElementById('page-heading');
    const parts = ['human-design', heading ? heading.textContent : 'chart', ...chartNames().filter(name => name !== null)];
    const datetime = HdTime.normalize(document.getElementById('datetime').value);
    if (datetime !== undefined) {
        parts.push(datetime.replace(':', '').replace('T', '-'));
    }
    return parts.map(slug).filter(part => part.length > 0).join('-');
}

function downloadLink(href, file_name) {
    const link = document.createElement('a');
    link.href = href;
    link.download = file_name;
    document.body.appendChild(link);
    link.click();
    link.remove();
}

// What a page saves is a file of its own making. That's kept by every browser and by an app
// that shows the pages, which a link to an address of the pages isn't.
function saveFile(blob, file_name) {
    const href = URL.createObjectURL(blob);
    downloadLink(href, file_name);
    setTimeout(() => URL.revokeObjectURL(href), 1000);
}

// says why the chart wasn't saved, in the window that asked for it when that's still open
function savingFailed(shown, button, notice, problem) {
    return () => {
        if (shown !== popup_shown) {
            return;
        }
        button.disabled = false;
        notice.textContent = problem;
        notice.hidden = false;
    };
}

// the drawing is only saved while its window is still open, once
function saveChartSvg(url, file_name, notice, button) {
    const shown = popup_shown;
    button.disabled = true;
    fetch(url)
        .then(response => response.ok ? response.text() : Promise.reject(new Error(response.status)))
        .then(svg => {
            if (shown !== popup_shown) {
                return;
            }
            saveFile(new Blob([svg], {type: 'image/svg+xml'}), file_name + '.svg');
            closePopup();
        })
        .catch(savingFailed(shown, button, notice, "The drawing couldn't be saved."));
}

// twice the size of the drawing, on white since the chart itself has no background
const PNG_SCALE = 2;

// the image is only saved while its window is still open, once
function saveChartPng(url, file_name, notice, button) {
    const shown = popup_shown;
    button.disabled = true;
    const failed = savingFailed(shown, button, notice, "The image couldn't be made, the SVG drawing can be saved instead.");
    fetch(url)
        .then(response => response.ok ? response.text() : Promise.reject(new Error(response.status)))
        .then(svg => new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve(image);
            image.onerror = reject;
            image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
        }))
        .then(image => {
            const canvas = document.createElement('canvas');
            canvas.width = image.naturalWidth * PNG_SCALE;
            canvas.height = image.naturalHeight * PNG_SCALE;
            const context = canvas.getContext('2d');
            context.fillStyle = 'white';
            context.fillRect(0, 0, canvas.width, canvas.height);
            context.drawImage(image, 0, 0, canvas.width, canvas.height);
            canvas.toBlob(blob => {
                if (shown !== popup_shown) {
                    return;
                }
                if (blob === null) {
                    failed();
                    return;
                }
                saveFile(blob, file_name + '.png');
                closePopup();
            }, 'image/png');
        })
        .catch(failed);
}

function saveChoice(label, detail, action) {
    const choice = actionButton('', action);
    choice.setAttribute('class', 'saved-entry');
    choice.appendChild(textElement('span', 'name', label));
    choice.appendChild(textElement('span', 'date', detail));
    const row = document.createElement('div');
    row.setAttribute('class', 'saved-row');
    row.appendChild(choice);
    return row;
}

// the chart that the download button points to, as an image, a drawing or on paper
function saveChart(opener) {
    const url = document.getElementById('chart-download')?.href;
    if (!url) {
        return false;
    }
    const file_name = chartFileName();
    const content = openPopup(opener);
    const close = popupHeader(content, 'Save the chart');
    const notice = statusNotice(content);
    const png = saveChoice('PNG image', 'For messages, posts and printing', () => saveChartPng(url, file_name, notice, png.children[0]));
    content.appendChild(png);
    const svg = saveChoice('SVG drawing', 'Stays sharp at any size, for design programs', () => saveChartSvg(url, file_name, notice, svg.children[0]));
    content.appendChild(svg);
    content.appendChild(saveChoice('Print', 'The chart on its own, on one page', () => {
        closePopup();
        window.print();
    }));
    showPopup(png.children[0]);
    return false;
}

let popup_opener = null;
let popup_focus = null;
// counts the windows opened and closed, so that what a window started late can tell it's gone
let popup_shown = 0;

// the window of the saved people and of saving one, the focus goes back to the button that opened it
function openPopup(opener) {
    const popup = document.getElementById('load-entries');
    if (popup.style.display !== 'block') {
        popup_opener = opener || document.activeElement;
    }
    // only a click on the backdrop closes the window
    popup.onclick = function (event) {
        if (event.target === popup) {
            closePopup();
        }
    };
    const content = document.querySelector('#load-entries .popup-content');
    popup_shown++;
    content.innerHTML = '';
    content.setAttribute('role', 'dialog');
    content.setAttribute('aria-labelledby', 'popup-title');
    content.setAttribute('aria-modal', 'true');
    armed_delete = null;
    return content;
}

function popupShown() {
    const popup = document.getElementById('load-entries');
    return popup && popup.style.display === 'block' ? popup : null;
}

function closePopup() {
    const popup = popupShown();
    if (!popup) {
        return false;
    }
    popup.style.display = 'none';
    popup_shown++;
    popup_opener?.focus();
    popup_opener = null;
    popup_focus = null;
    return true;
}

function showPopup(focus) {
    document.getElementById('load-entries').style.display = 'block';
    popup_focus = focus;
    focus.focus();
}

function textElement(tag, css_class, text) {
    const element = document.createElement(tag);
    element.setAttribute('class', css_class);
    element.textContent = text;
    return element;
}

// the title of a window with its close button, which it returns
function popupHeader(content, text) {
    const header = document.createElement('div');
    header.setAttribute('class', 'entries-header');
    const title = textElement('h2', 'entries-title', text);
    title.setAttribute('id', 'popup-title');
    header.appendChild(title);
    const close = actionButton('×', closePopup);
    close.setAttribute('class', 'entries-close');
    close.setAttribute('aria-label', 'Close');
    header.appendChild(close);
    content.appendChild(header);
    return close;
}

function statusNotice(content, text) {
    const notice = textElement('p', 'entries-notice', text || '');
    notice.setAttribute('role', 'status');
    notice.hidden = !text;
    content.appendChild(notice);
    return notice;
}

// the group the saved people are filtered by, everyone when null and those without a group when ''
let saved_group = null;
const WITHOUT_GROUP = 'Without a group';

function describePerson(entry, name, chart) {
    entry.setAttribute('class', 'saved-entry');
    entry.appendChild(textElement('span', 'name', name));
    entry.appendChild(textElement('span', 'date', savedMoment(chart.datetime)));
    entry.appendChild(textElement('span', 'location', chart.locationCity));
}

// a person of the Load window, who fills the row of the chart when chosen
function savedRow(url, name, chart) {
    const entry = actionButton('', () => loadEntry(url, name));
    describePerson(entry, name, chart);
    const row = document.createElement('div');
    row.setAttribute('class', 'saved-row');
    row.appendChild(entry);
    return row;
}

// the charts that the People page opens for a person, the page sets them
let people_charts = [];
const NOT_STORED = "The change couldn't be stored.";

// a person of the People page, with the buttons that change them and the charts they can be shown in
function personRow(name, chart) {
    const entry = document.createElement('div');
    describePerson(entry, name, chart);
    const row = document.createElement('div');
    row.setAttribute('class', 'saved-row');
    row.appendChild(entry);
    row.appendChild(renameButton(name, row));
    row.appendChild(groupButton(name, chart, row));
    row.appendChild(deleteButton(name));
    const charts = document.createElement('div');
    charts.setAttribute('class', 'person-charts');
    for (const each of people_charts) {
        const button = actionButton(each.label, () => showPersonChart(each.url, name));
        button.setAttribute('type', 'button');
        button.setAttribute('aria-label', each.label + ' of ' + name);
        charts.appendChild(button);
    }
    row.appendChild(charts);
    return row;
}

// the chart pages open with the birth that was remembered last, as after a Load there
function showPersonChart(url, name) {
    const chart = HdStore.load(browserStorage()).charts[name];
    if (chart === undefined) {
        showPeople();
        return;
    }
    const stored = HdStore.update(browserStorage(), data => {
        HdStore.setBirthDatetime(data, chart.datetime, 'birth');
        HdStore.setBirthLocation(data, chart.locationId, chart.locationCity, 'birth');
        HdStore.setNamed(data, name, chart, 'birth');
    });
    // the page of the chart would show whoever was remembered before
    if (!stored) {
        showPeople(NOT_STORED);
        return;
    }
    window.location.href = url;
}

// closes the field that's open below a person, there's one at a time
let open_field = null;
// the people changed elsewhere while a field was open, which they're listed again after
let people_stale = false;
// the button of the person who just changed, which keeps the focus when the list is drawn again
let changed = null;
let changed_button = null;

// A button that opens a field below the row of a person, whose text is applied with Done. A field
// that's left applies its text too, unless it's only applied when entered, and Escape leaves it as
// if it was never opened.
function fieldButton(row, name, field) {
    const css = field.css;
    const button = document.createElement('button');
    button.setAttribute('type', 'button');
    button.setAttribute('class', css);
    button.setAttribute('aria-label', field.label);
    button.setAttribute('aria-expanded', 'false');
    button.setAttribute('title', field.title);
    button.innerHTML = field.icon;
    let editor = null;
    const close = () => {
        row.removeChild(editor);
        editor = null;
        open_field = null;
        button.setAttribute('aria-expanded', 'false');
    };
    // without a change, the list only has to catch up with what changed elsewhere meanwhile
    const leave = () => {
        close();
        if (people_stale) {
            changed = {name, css};
            showPeople();
        } else {
            button.focus();
        }
    };
    button.onclick = function () {
        if (editor !== null) {
            leave();
            return false;
        }
        open_field?.();
        editor = document.createElement('div');
        editor.setAttribute('class', 'row-editor');
        const input = document.createElement('input');
        input.setAttribute('type', 'text');
        input.setAttribute('autocomplete', 'off');
        editor.appendChild(input);
        let done = false;
        const change = () => {
            if (done) {
                return;
            }
            done = true;
            open_field = null;
            changed = {name, css};
            field.apply(input.value);
        };
        field.prepare(input, editor);
        if (!field.entered) {
            // Enter, a chosen suggestion and leaving the field all end with a change
            input.onchange = change;
        }
        input.onkeydown = event => {
            if (event.key === 'Escape') {
                event.stopPropagation();
                done = true;
                leave();
            } else if (event.key === 'Enter' && field.entered) {
                event.preventDefault();
                change();
            }
        };
        editor.appendChild(actionButton('Done', change));
        row.appendChild(editor);
        open_field = close;
        button.setAttribute('aria-expanded', 'true');
        input.focus();
        return false;
    };
    if (changed !== null && changed.name === name && changed.css === css) {
        changed_button = button;
    }
    return button;
}

const PENCIL_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M4 20l1-4.5L16.5 4l3.5 3.5L8.5 19zM14 6.5l3.5 3.5" ' +
    'fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

// a name is only changed on purpose, with Enter or Done
function renameButton(name, row) {
    return fieldButton(row, name, {
        css: 'rename-entry', icon: PENCIL_ICON, label: 'Rename ' + name, title: 'Rename', entered: true,
        prepare: input => {
            input.setAttribute('id', 'name-edit');
            input.setAttribute('aria-label', 'Name of ' + name);
            input.setAttribute('enterkeyhint', 'done');
            input.value = name;
        },
        apply: renamed => renamePerson(name, renamed)
    });
}

// a name that someone else has stays theirs
function renamePerson(name, renamed) {
    const trimmed = renamed.trim();
    if (trimmed === '' || trimmed === name || HdStore.load(browserStorage()).charts[name] === undefined) {
        showPeople();
        return;
    }
    let renamed_person = false;
    if (!HdStore.update(browserStorage(), data => renamed_person = HdStore.renameChart(data, name, trimmed))) {
        showPeople(NOT_STORED);
        return;
    }
    if (renamed_person && changed !== null) {
        changed.name = trimmed;
    }
    showPeople(renamed_person ? '"' + name + '" is now called "' + trimmed + '".' :
        'Someone else is already saved as "' + trimmed + '", so "' + name + '" keeps their name.');
}

const TAG_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M3 12V3h9l9 9-9 9z" ' +
    'fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><circle cx="7.5" cy="7.5" r="1.5" fill="currentColor"/></svg>';

/**
 * Lists the groups that people are in below a group field, all of them when the field is
 * entered and those with the typed text after that, as the city field lists cities. Unlike a
 * city, a group can also be one that isn't in the list yet.
 */
function groupSearch(input, chosen) {
    const sought = () => input.value.trim().toLowerCase();
    // the groups that start with what was typed come before those that have it further on
    const rank = group => String(group).toLowerCase().startsWith(sought()) ? 0 : 1;
    const awesomplete = new Awesomplete(input, {
        minChars: 0,
        maxItems: 50,
        // an empty field is left empty by Enter, see below for a field with text
        autoFirst: false,
        sort: (a, b) => rank(a) - rank(b),
        item: function (suggestion, typed, item_id) {
            return Awesomplete.ITEM.call(this, highlightSuggestion(String(suggestion), typed), '', item_id);
        },
        replace: function (suggestion) {
            input.value = String(suggestion.value);
            chosen();
        },
        list: []
    });
    input.addEventListener('focus', () => {
        // read when the field is entered, since the groups come and go with the people in them
        awesomplete.list = HdStore.groupNames(HdStore.load(browserStorage()));
        awesomplete.evaluate();
    });
    // Enter chooses the first group of the list, as it does the first city, once something was
    // typed that isn't that group already. Without a group in the list, it keeps what was typed.
    input.addEventListener('awesomplete-open', () => {
        if (sought() !== '' && String(awesomplete.suggestions[0]) !== input.value.trim()) {
            awesomplete.goto(0);
        }
    });
}

function groupButton(name, chart, row) {
    return fieldButton(row, name, {
        css: 'group-entry', icon: TAG_ICON, label: 'Change the group of ' + name, title: 'Group', entered: false,
        prepare: (input, editor) => {
            input.setAttribute('id', 'group-edit');
            input.setAttribute('aria-label', 'Group of ' + name);
            input.setAttribute('placeholder', 'No group');
            input.value = chart.group || '';
            // a group chosen from the list is the change, as Enter and leaving the field are
            groupSearch(input, () => input.onchange());
        },
        apply: group => changeGroup(name, group)
    });
}

function changeGroup(name, group) {
    let grouped = false;
    if (!HdStore.update(browserStorage(), data => grouped = HdStore.setGroup(data, name, group))) {
        showPeople(NOT_STORED);
        return;
    }
    const chart = HdStore.load(browserStorage()).charts[name];
    showPeople(!grouped ? '' :
        chart.group === undefined ? '"' + name + '" is no longer in a group.' : '"' + name + '" is in ' + chart.group + '.');
}

// the people of the chosen group, under the heading of their group once some have one, those
// without a group last. The rows and the level of the headings are those of where the list is shown.
function showSavedPeople(list, data, names, groups, shown) {
    list.innerHTML = '';
    armed_delete = null;
    open_field = null;
    for (const group of groups.length === 0 ? [''] : [...groups, '']) {
        if (saved_group !== null && saved_group !== group) {
            continue;
        }
        const members = names.filter(name => (data.charts[name].group || '') === group);
        if (members.length > 0 && groups.length > 0) {
            list.appendChild(textElement(shown.heading, 'entries-group', group === '' ? WITHOUT_GROUP : group));
        }
        members.forEach(name => list.appendChild(shown.row(name, data.charts[name])));
    }
}

// a button for each group and one for everyone, pressed for the group that's shown
function groupFilter(list, data, names, groups, withoutGroup, shown) {
    const filter = document.createElement('div');
    filter.setAttribute('class', 'entries-filter');
    filter.setAttribute('role', 'group');
    filter.setAttribute('aria-label', 'Show group');
    const choices = [[null, 'Everyone'], ...groups.map(group => [group, group])];
    if (withoutGroup) {
        choices.push(['', WITHOUT_GROUP]);
    }
    const buttons = choices.map(([group, label]) => {
        const button = actionButton(label, () => {
            saved_group = group;
            buttons.forEach(other => other.setAttribute('aria-pressed', String(other === button)));
            showSavedPeople(list, data, names, groups, shown);
        });
        button.setAttribute('aria-pressed', String(group === saved_group));
        filter.appendChild(button);
        return button;
    });
    return filter;
}

// the saved people by group, with the buttons that choose a group once there are groups, or what
// to do while nobody is saved, and how many they are
function savedPeople(content, shown, nobody) {
    const data = HdStore.load(browserStorage());
    const names = HdStore.chartNames(data);
    const groups = HdStore.groupNames(data);
    // a choice that no longer has anyone shows everyone
    const without_group = groups.length > 0 && names.some(name => data.charts[name].group === undefined);
    if (saved_group !== null && !groups.includes(saved_group) && !(saved_group === '' && without_group)) {
        saved_group = null;
    }
    const list = document.createElement('div');
    list.setAttribute('class', 'saved-list');
    if (names.length === 0) {
        list.appendChild(textElement('p', 'no-entries', nobody));
    } else {
        if (groups.length > 0) {
            content.appendChild(groupFilter(list, data, names, groups, without_group, shown));
        }
        showSavedPeople(list, data, names, groups, shown);
    }
    content.appendChild(list);
    return names.length;
}

// the window of a chart page, where a saved person is chosen for a row, they're changed on the People page
function displaySavedEntries(url, target, opener) {
    load_target = target || defaultTarget();
    const content = openPopup(opener);
    const close = popupHeader(content, 'Saved people');
    // below the title of the window
    savedPeople(content, {row: (name, chart) => savedRow(url, name, chart), heading: 'h3'},
        'No people saved yet. Fill in a date and place, then use Save to keep someone here, or import a file of people on the People page.');

    const actions = document.createElement('div');
    actions.setAttribute('class', 'entries-actions');
    const manage = textElement('a', 'entries-manage', 'Manage people');
    manage.setAttribute('href', routes.people);
    actions.appendChild(manage);
    content.appendChild(actions);

    showPopup(close);
}

// the People page: everyone who's saved, with what changes them and the file that backs them up
function showPeople(notice) {
    const content = document.getElementById('people');
    content.innerHTML = '';
    people_stale = false;
    const told = statusNotice(content, notice);
    told.setAttribute('tabindex', '-1');
    // below the title of the page
    const count = savedPeople(content, {row: personRow, heading: 'h2'},
        'No people saved yet. Add someone here, or use Save on the page of a chart to keep the person that it shows.');
    if (count > 0) {
        // browsers can clear what a site stores, Safari does after 7 days without a visit
        content.appendChild(textElement('p', 'entries-backup', 'Saved people stay in ' + kept_in + ' only. Export keeps a copy as a file, which is the only sure backup.'));
    }

    const actions = document.createElement('div');
    actions.setAttribute('class', 'entries-actions');
    const file_input = document.createElement('input');
    file_input.type = 'file';
    file_input.accept = 'application/json,.json';
    file_input.style.display = 'none';
    file_input.onchange = function () {
        if (file_input.files.length > 0) {
            importEntries(file_input.files[0]);
        }
    };
    if (count > 0) {
        actions.appendChild(actionButton('Export to a file', exportEntries));
    }
    actions.appendChild(actionButton('Import from a file', () => file_input.click()));
    actions.appendChild(file_input);
    content.appendChild(actions);

    // what was said takes the focus that the button of a change would have kept
    if (changed_button !== null) {
        changed_button.focus();
    } else if (notice) {
        told.focus();
    }
    changed = null;
    changed_button = null;
}

// the People page lists its people again when they changed elsewhere, unless a field is being typed in
function refreshPeople() {
    if (!document.getElementById('people')) {
        return;
    }
    if (open_field === null) {
        showPeople();
    } else {
        people_stale = true;
    }
}

// An app that keeps the saved people the same on someone's devices hands over who was saved
// and who was removed on another one. The page takes them as if they were saved and removed
// here, and says whether it could.
function syncPeople(changed) {
    const stored = HdStore.update(browserStorage(), data => {
        for (const [name, chart] of Object.entries(changed.saved || {})) {
            HdStore.putChart(data, name, chart);
        }
        for (const name of changed.removed || []) {
            HdStore.removeChart(data, name);
        }
    });
    if (stored) {
        // the chart that's shown may be titled with someone who's another one now
        if (image_url !== null) {
            drawChart(image_url);
        }
        refreshPeople();
    }
    return stored;
}

// who has the name already, since saving under it replaces them
function replaces(name, saved) {
    return saved === undefined ? '' :
        '"' + name + '" is already saved with ' + savedMoment(saved.datetime).replace(' @ ', ' at ') + ' in ' + saved.locationCity + ', saving replaces them.';
}

/**
 * The form of the People page that adds someone with their name, birth and group, without a
 * chart of them being shown first. Someone who has the name already is replaced, as when saving.
 */
function preparePersonForm() {
    const field = id => document.getElementById('person-' + id);
    const form = field('form');
    const opener = field('add');
    const name = field('name');
    const datetime = field('datetime');
    const location = field('location');
    const location_id = field('location-id');
    const group = field('group');
    const submit = field('submit');
    const notice = field('notice');
    // the group follows the person who has the name, until it's changed by hand
    let group_changed = false;

    const check = () => {
        const trimmed = name.value.trim();
        const saved = HdStore.load(browserStorage()).charts[trimmed];
        submit.disabled = trimmed === '' || HdTime.parse(datetime.value) === undefined || location_id.value === '';
        submit.textContent = saved === undefined ? 'Add' : 'Replace';
        notice.textContent = replaces(trimmed, saved);
        notice.hidden = saved === undefined;
        if (!group_changed) {
            group.value = saved?.group || '';
        }
    };
    const clear = () => {
        name.value = '';
        datetime.value = '';
        setLocationInputs(location_id, location, '', '');
        group.value = '';
        group_changed = false;
        check();
    };
    const close = () => {
        form.hidden = true;
        opener.setAttribute('aria-expanded', 'false');
        clear();
    };
    citySearch(location_id, location, check);
    // Enter in the field of the city chooses one from the list or goes back to the one that was
    // chosen, it never adds the person with another city than the one that's being typed
    location.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            event.preventDefault();
        }
    });
    name.oninput = check;
    datetime.oninput = check;
    datetime.onchange = check;
    group.oninput = () => group_changed = true;
    groupSearch(group, group.oninput);
    clear();

    opener.onclick = () => {
        if (!form.hidden) {
            close();
            return;
        }
        form.hidden = false;
        opener.setAttribute('aria-expanded', 'true');
        name.focus();
    };
    field('cancel').onclick = () => {
        close();
        opener.focus();
    };
    form.onsubmit = event => {
        event.preventDefault();
        const trimmed = name.value.trim();
        if (submit.disabled) {
            return;
        }
        const chart = {datetime: datetime.value, locationId: location_id.value, locationCity: location_id.dataset.city, group: group.value.trim()};
        const replaced = HdStore.load(browserStorage()).charts[trimmed] !== undefined;
        let added = false;
        if (!HdStore.update(browserStorage(), data => added = HdStore.putChart(data, trimmed, chart)) || !added) {
            notice.textContent = "Saving didn't work, private browsing can prevent it.";
            notice.hidden = false;
            return;
        }
        requestPersistentStorage();
        close();
        // whatever group was shown, they're in sight
        saved_group = null;
        showPeople('"' + trimmed + '" is ' + (replaced ? 'replaced' : 'added') + '.');
    };
}

// the moment and place of a person, as in the list of the saved people
function personSummary(chart) {
    const summary = document.createElement('div');
    summary.setAttribute('class', 'save-summary');
    summary.appendChild(textElement('span', 'date', savedMoment(chart.datetime)));
    summary.appendChild(textElement('span', 'location', chart.locationCity));
    return summary;
}

function saveDateLocation(target, opener) {
    const row = target || defaultTarget();
    const chart = currentChart(row);
    const content = openPopup(opener);
    const close = popupHeader(content, 'Save person');
    const actions = document.createElement('div');
    actions.setAttribute('class', 'entries-actions');

    if (chart.datetime === undefined || chart.locationId.length === 0) {
        content.appendChild(textElement('p', 'no-entries', 'Choose a date and a place first, then they can be saved.'));
        actions.appendChild(actionButton('Close', closePopup));
        content.appendChild(actions);
        showPopup(close);
        return;
    }

    content.appendChild(personSummary(chart));
    const label = textElement('label', 'save-label', 'Name');
    label.setAttribute('for', 'save-name');
    content.appendChild(label);
    const input = document.createElement('input');
    input.setAttribute('id', 'save-name');
    input.setAttribute('type', 'text');
    input.setAttribute('autocomplete', 'off');
    input.setAttribute('enterkeyhint', 'done');
    input.value = chartTitle(row) || '';
    content.appendChild(input);
    const notice = statusNotice(content);
    const group_label = textElement('label', 'save-label', 'Group');
    group_label.setAttribute('for', 'save-group');
    content.appendChild(group_label);
    const group_input = document.createElement('input');
    group_input.setAttribute('id', 'save-group');
    group_input.setAttribute('type', 'text');
    group_input.setAttribute('autocomplete', 'off');
    group_input.setAttribute('enterkeyhint', 'done');
    group_input.setAttribute('placeholder', 'Optional, such as Family or Friends');
    content.appendChild(group_input);
    // the group follows the person who has the name, until it's changed by hand
    let group_changed = false;
    group_input.oninput = () => group_changed = true;
    groupSearch(group_input, group_input.oninput);

    const submit = actionButton('Save', () => savePerson(row, Object.assign({}, chart, {group: group_input.value.trim()}), input.value.trim(), opener, notice));
    submit.setAttribute('id', 'save-submit');
    submit.setAttribute('class', 'primary');
    // tells before saving who has the name already, saving replaces them
    const check = () => {
        const name = input.value.trim();
        const saved = HdStore.load(browserStorage()).charts[name];
        submit.disabled = name.length === 0;
        submit.textContent = saved === undefined ? 'Save' : 'Replace';
        notice.textContent = replaces(name, saved);
        notice.hidden = saved === undefined;
        if (!group_changed) {
            group_input.value = saved?.group || '';
        }
    };
    input.oninput = check;
    input.onkeydown = event => {
        if (event.key === 'Enter') {
            event.preventDefault();
            submit.onclick();
        }
    };
    check();
    actions.appendChild(actionButton('Cancel', closePopup));
    actions.appendChild(submit);
    content.appendChild(actions);
    showPopup(input);
}

function savePerson(row, chart, name, opener, notice) {
    if (name.length === 0) {
        return;
    }
    if (!HdStore.update(browserStorage(), data => HdStore.putChart(data, name, chart))) {
        notice.textContent = "Saving didn't work, private browsing can prevent it.";
        notice.hidden = false;
        return;
    }
    requestPersistentStorage();
    shared_rows.delete(row);
    if (isBirthKind(rowKind(row))) {
        rememberBirthChart(chart, rowKind(row));
        rememberNamedChart(name, chart, rowKind(row));
    } else {
        rememberLocation(rowKind(row), chart.locationId, chart.locationCity);
    }
    if (image_url !== null) {
        drawChart(image_url);
    }
    closePopup();
    // a short word on the Save button, as the share button's
    if (opener?.classList) {
        opener.classList.add('saved');
        clearTimeout(opener.savedTimer);
        opener.savedTimer = setTimeout(() => opener.classList.remove('saved'), 2000);
    }
}

// what the footer's link to the welcome has after the # of its address
const WELCOME_HASH = '#welcome';
let welcome_closes = false;

// Shows the welcome of this page, and answers whether it has one to show.
function showWelcome() {
    const welcome = document.getElementById('welcome');
    if (!welcome || !welcome.showModal) {
        return false;
    }
    if (!welcome_closes) {
        welcome_closes = true;
        // a click next to it closes it, as its button and Escape do
        welcome.addEventListener('click', event => {
            const box = welcome.getBoundingClientRect();
            if (event.target === welcome && (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom)) {
                welcome.close();
            }
        });
    }
    if (!welcome.open) {
        welcome.showModal();
    }
    return true;
}

// The welcome again for whoever asked for it with the link of the footer, on a page that it
// led to: the address is left without what asked, so that the page isn't welcomed each time
// that it's loaded again.
function welcomeAsked() {
    if (!window.location || window.location.hash !== WELCOME_HASH || !showWelcome()) {
        return false;
    }
    // a first visit that arrives this way has been welcomed too
    try {
        browserStorage()?.setItem(WELCOMED_KEY, '1');
    } catch (e) {
    }
    if (window.history && window.history.replaceState) {
        window.history.replaceState(null, '', window.location.pathname + (window.location.search || ''));
    }
    return true;
}

// The site as a browser installed it, in a window of its own without the browser's bars.
function installedSite() {
    return !!(window.matchMedia && window.matchMedia('(display-mode: standalone)').matches);
}

// Welcomes a first visit, once. A browser that can't remember that it did isn't welcomed at all,
// rather than on every page. Nor is the site that was installed: whoever installed it has
// visited it, and some browsers give what they install a storage of its own.
function welcomeFirstVisit() {
    const welcome = document.getElementById('welcome');
    const storage = browserStorage();
    if (!welcome || !welcome.showModal || !storage) {
        return;
    }
    try {
        if (storage.getItem(WELCOMED_KEY) !== null) {
            return;
        }
        storage.setItem(WELCOMED_KEY, '1');
    } catch (e) {
        return;
    }
    if (installedSite()) {
        return;
    }
    showWelcome();
}
document.addEventListener('DOMContentLoaded', () => {
    welcomeAsked();
    welcomeFirstVisit();
});

// before the Explore card's, which stays open under the window
document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && closePopup()) {
        event.stopPropagation();
    }
}, true);
// the focus stays in the window while it's shown, and with the chart while it has the whole screen
document.addEventListener('focusin', event => {
    const popup = popupShown();
    if (popup && popup_focus && !popup.contains(event.target)) {
        popup_focus.focus();
    }
    if (chart_view !== null && !document.getElementById('chart-viewer').contains(event.target)) {
        document.getElementById('chart-viewer-close').focus();
    }
});
window.addEventListener('resize', () => {
    if (chart_view !== null) {
        refitChartView();
    }
});

// another tab can delete or replace the saved chart whose name titles this one
window.addEventListener('storage', event => {
    if (event.key !== 'hdapp') {
        return;
    }
    if (image_url !== null) {
        drawChart(image_url);
    }
    refreshPeople();
});
window.addEventListener('pageshow', event => {
    // a page that Back returns to comes back as it was left, with its menu open
    setMenuOpen(false);
    if (event.persisted && image_url !== null) {
        drawChart(image_url);
    }
    if (event.persisted) {
        refreshPeople();
    }
});
// a share link opened on the page that's already showing changes only the address after the #
window.addEventListener('hashchange', () => {
    if (window.location.hash.startsWith(SHARE_PREFIX)) {
        window.location.reload();
    }
    welcomeAsked();
});
