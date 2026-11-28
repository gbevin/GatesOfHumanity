package gatesofhumanity.js;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MainTest {
    // the parts of a browser page that main.js uses
    private static final String PAGE = """
        class FakeElement {
            constructor(id) {
                this.id = id; this.value = ''; this.dataset = {}; this.style = {}; this.listeners = {};
                this.children = []; this.src = ''; this.textContent = ''; this.innerHTML = '';
            }
            get innerHTML() { return this.html; }
            set innerHTML(html) { this.html = html; if (html === '') this.children = []; }
            addEventListener(type, listener) { (this.listeners[type] = this.listeners[type] || []).push(listener); }
            dispatch(type) { for (const listener of this.listeners[type] || []) listener({target: this}); }
            appendChild(child) { this.children.push(child); }
            removeChild(child) { this.children = this.children.filter(each => each !== child); }
            insertBefore(child, next) { this.children.splice(next ? this.children.indexOf(next) : this.children.length, 0, child); }
            setAttribute(name, value) { this[name] = value; }
            removeAttribute(name) { delete this[name]; }
            focus(options) { document.activeElement = this; this.focusOptions = options; }
            contains(other) { return other === this || this.children.includes(other); }
            blur() { this.blurs = (this.blurs || 0) + 1; }
            querySelector(selector) { return null; }
            querySelectorAll(selector) { return []; }
        }
        var elements = {};
        for (const id of ['datetime', 'location', 'location-id', 'second-datetime', 'second-location', 'second-location-id', 'svg-image', 'load-entries']) {
            elements[id] = new FakeElement(id);
        }
        var popup = new FakeElement('popup');
        var cookieJar = {};
        var documentListeners = {};
        var document = {
            addEventListener: (type, listener) => (documentListeners[type] = documentListeners[type] || []).push(listener),
            dispatch: (type, event) => (documentListeners[type] || []).forEach(listener => listener(event)),
            getElementById: id => elements[id],
            querySelector: selector => selector === '#load-entries .popup-content' ? popup : elements[selector.slice(1)],
            createElement: tag => new FakeElement(tag),
            get cookie() { return Object.entries(cookieJar).map(([name, value]) => name + '=' + value).join('; '); },
            set cookie(text) {
                const pair = text.split(';')[0];
                const index = pair.indexOf('=');
                if (/expires=Thu, 01 Jan 1970/.test(text)) delete cookieJar[pair.slice(0, index)];
                else cookieJar[pair.slice(0, index)] = pair.slice(index + 1);
            }
        };
        var alerts = [];
        var confirms = [];
        var confirmAnswer = true;
        var windowListeners = {};
        var window = {
            addEventListener: (type, listener) => (windowListeners[type] = windowListeners[type] || []).push(listener),
            dispatch: (type, event) => (windowListeners[type] || []).forEach(listener => listener(event)),
            localStorage: new MemoryStorage(),
            alert: message => alerts.push(message),
            confirm: message => { confirms.push(message); return confirmAnswer; }
        };
        var navigator = {};
        var timers = [];
        function findIn(element, id) {
            if (element.id === id) return element;
            for (const child of element.children) { const found = findIn(child, id); if (found) return found; }
            return null;
        }
        function press(key) { const event = {key, stopped: false, stopPropagation() { this.stopped = true; }}; document.dispatch('keydown', event); return event.stopped; }
        function byClass(element, css) { return (element['class'] === css ? [element] : []).concat(...element.children.map(child => byClass(child, css))); }
        function typeName(name) { const input = findIn(popup, 'save-name'); input.value = name; input.oninput(); }
        function saveAs(name, target, opener) { saveDateLocation(target, opener); typeName(name); findIn(popup, 'save-submit').onclick(); }
        function setTimeout(callback) { timers.push(callback); return timers.length; }
        function clearTimeout(id) { if (id) timers[id - 1] = null; }
        var requests = [];
        class XMLHttpRequest {
            open(method, url) { this.url = url; }
            send() { requests.push(this); }
            respond(status, text) { this.status = status; this.responseText = text; this.onload(); }
        }
        // the browser's, with spaces encoded as + like the standard
        class URLSearchParams {
            constructor(text) {
                this.entries = [];
                for (const pair of (text || '').split('&').filter(p => p.length > 0)) {
                    const index = pair.indexOf('=');
                    const decode = part => decodeURIComponent(part.replace(/\\+/g, ' '));
                    this.entries.push(index < 0 ? [decode(pair), ''] : [decode(pair.slice(0, index)), decode(pair.slice(index + 1))]);
                }
            }
            get(key) { const entry = this.entries.find(e => e[0] === key); return entry ? entry[1] : null; }
            set(key, value) { this.entries = this.entries.filter(e => e[0] !== key); this.entries.push([key, String(value)]); }
            toString() { return this.entries.map(e => encodeURIComponent(e[0]) + '=' + encodeURIComponent(e[1]).replace(/%20/g, '+')).join('&'); }
        }
        // fetches wait until a test answers them
        var fetches = [];
        var fetch = url => new Promise(resolve => fetches.push({url: url, answer: (text, ok = true) => resolve({ok: ok, text: () => Promise.resolve(text)})}));
        var awesompletes = [];
        class Awesomplete {
            constructor(input, options) { this.input = input; this.options = options; this.index = -1; awesompletes.push(this); }
            evaluate() { this.evaluated = (this.evaluated || 0) + 1; }
            goto(index) { this.index = index; }
            static ITEM(html, input, item_id) { return {innerHTML: html, input: input}; }
        }
        """;

    private ScriptRunner js_;

    private void load(String setup) {
        load(setup, "store.js", "main.js");
    }

    private void load(String setup, String... scripts) {
        if (js_ != null) {
            js_.close();
        }
        js_ = new ScriptRunner(PAGE + setup, scripts);
        js_.eval("routes = {cities: 'http://x/cities', now: 'http://x/now', people: 'http://x/people', explore: typeof explore === 'undefined' ? {} : explore}");
    }

    // the People page, with the charts that it opens a person in
    private void loadPeople() {
        load("");
        eval("""
            var page = elements['people'] = new FakeElement('people');
            people_charts = [{label: 'Birth Chart', url: 'http://x/wheel/birth'}, {label: 'Connection Chart', url: 'http://x/wheel/connection'}];
            var notice = () => byClass(page, 'entries-notice')[0];
            var people = () => JSON.stringify(byClass(page, 'saved-row').map(row => row.children[0].children[0].textContent));
            var button = (css, name) => byClass(page, css).find(b => b['aria-label'].endsWith(' ' + name));
            const add = (d, name, group) => HdStore.putChart(d, name, {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London', group});
            """);
    }

    @AfterEach
    void tearDown() {
        if (js_ != null) {
            js_.close();
        }
    }

    private String eval(String code) {
        return js_.eval(code).toString();
    }

    @Test
    void birthDatesAreRemembered() {
        load("");
        eval("""
            chart_type = 'birth-transit';
            elements['second-location-id'].value = '2643743';
            elements['second-location-id'].dataset.city = 'London';
            elements['second-datetime'].value = '1975-06-21T14:30';
            dateTimeChosen(elements['second-datetime']);
            elements['second-datetime'].value = '';
            prepareBirthDateForElement('second-datetime');
            """);
        assertEquals("1975-06-21T14:30", eval("elements['second-datetime'].value"));
    }

    @Test
    void aBirthDateIsOnlyRememberedWithItsOwnCity() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => {
                HdStore.setBirthDatetime(d, '1980-01-02T03:04', 'birth');
                HdStore.setBirthLocation(d, '2643743', 'London', 'birth');
            });
            chart_type = 'birth-transit';
            elements['second-datetime'].value = '1950-05-05T06:00';
            dateTimeChosen(elements['second-datetime']);
            """);
        assertEquals("{\"datetime\":\"1980-01-02T03:04\",\"location\":{\"id\":\"2643743\",\"city\":\"London\"}}",
            eval("JSON.stringify(HdStore.load(window.localStorage).birth)"));
        eval("""
            prepareCitiesForElements('#second-location-id', '#second-location', 'birth');
            awesompletes[0].options.replace({value: 2988507, label: 'Paris'});
            """);
        assertEquals("{\"datetime\":\"1950-05-05T06:00\",\"location\":{\"id\":\"2988507\",\"city\":\"Paris\"}}",
            eval("JSON.stringify(HdStore.load(window.localStorage).birth)"));

        // a city chosen without a date is never paired with the date remembered before
        eval("""
            elements['second-datetime'].value = '';
            awesompletes[0].options.replace({value: 1850147, label: 'Tokyo'});
            """);
        assertEquals("2988507", eval("HdStore.load(window.localStorage).birth.location.id"));
    }

    @Test
    void someoneDeletedElsewhereLeavesTheWindowOnTheRowThatWasChosen() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => {
                HdStore.setBirthDatetime(d, '1980-01-02T03:04', 'birth');
                HdStore.setBirthLocation(d, '2643743', 'London', 'birth');
                HdStore.putChart(d, 'partner', {datetime: '1975-06-21T14:30', locationId: '1850147', locationCity: 'Tokyo'});
                HdStore.putChart(d, 'other', {datetime: '1990-01-01T00:00', locationId: '1850147', locationCity: 'Tokyo'});
            });
            chart_type = 'connection';
            displaySavedEntries('http://x/wheel/image/connection/', 'second');
            HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'other'));
            loadEntry('http://x/wheel/image/connection/', 'other');
            """);
        assertEquals("block", eval("elements['load-entries'].style.display"));
        assertEquals("[\"partner\"]", eval("JSON.stringify(byClass(popup, 'saved-row').map(row => row.children[0].children[0].textContent))"));
        eval("loadEntry('http://x/wheel/image/connection/', 'partner')");
        assertEquals("1975-06-21T14:30", eval("elements['second-datetime'].value"));
        assertEquals("1980-01-02T03:04", eval("HdStore.load(window.localStorage).birth.datetime"));
    }

    @Test
    void aLateAnswerForNowDoesntReplaceATypedDate() {
        load("");
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '1850147';
            elements['location-id'].dataset.city = 'Tokyo';
            showNow('http://x/wheel/image/birth/');
            elements['datetime'].value = '1981-03-03T03:03';
            dateTimeChosen(elements['datetime']);
            fetches[0].answer('2026-11-11T12:30');
            """);
        assertEquals("1981-03-03T03:03", eval("elements['datetime'].value"));
        assertEquals("1981-03-03T03:03", eval("HdStore.load(window.localStorage).birth.datetime"));
    }

    @Test
    void aChartDeletedInAnotherTabNoLongerTitlesTheChart() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'Secret', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            chart_type = 'birth';
            loadEntry('http://x/wheel/image/birth/', 'Secret');
            window.location = {origin: 'http://x', pathname: '/wheel/birth', hash: ''};
            """);
        assertTrue(eval("shareUrl()").contains("name=Secret"));
        eval("HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'Secret'))");
        assertFalse(eval("shareUrl()").contains("Secret"));
    }

    @Test
    void aChartDeletedInAnotherTabLosesItsTitleRightAway() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'Secret', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            chart_type = 'birth';
            loadEntry('http://x/wheel/image/birth/', 'Secret');
            HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'Secret'));
            window.dispatch('storage', {key: 'hdapp'});
            """);
        assertEquals("http://x/wheel/image/birth/197506211430/2643743", eval("elements['svg-image'].dataset.src"));
    }

    @Test
    void aShareLinkOpenedOnTheSamePageIsShown() {
        load("var reloads = 0; window.location = {origin: 'http://x', pathname: '/wheel/birth', hash: '', reload: () => reloads++};");
        eval("window.location.hash = '#other'; window.dispatch('hashchange', {})");
        assertEquals("0", eval("reloads"));
        eval("window.location.hash = '#share&datetime=1955-02-24T19%3A15&location=5391959&city=x'; window.dispatch('hashchange', {})");
        assertEquals("1", eval("reloads"));
    }

    @Test
    void loadingTheSecondChartNeverRemembersTheFirstRowsDefault() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'partner', {datetime: '1975-06-21T14:30', locationId: '1850147', locationCity: 'Tokyo'}));
            chart_type = 'connection';
            elements['location-id'].value = '2643743';
            elements['location-id'].dataset.city = 'London';
            elements['datetime'].value = '2026-11-11T12:00';
            showFirstChart('http://x/wheel/image/connection/', '');
            displaySavedEntries('http://x/wheel/image/connection/', 'second');
            loadEntry('http://x/wheel/image/connection/', 'partner');
            """);
        assertEquals("null", eval("String(HdStore.load(window.localStorage).birth)"));
        assertEquals("1975-06-21T14:30", eval("HdStore.load(window.localStorage).partner.datetime"));
    }

    @Test
    void onlyAClickNextToTheLoadWindowClosesIt() {
        load("");
        eval("displaySavedEntries('http://x/')");
        assertEquals("undefined", eval("String(elements['load-entries'].onclick({target: {}}))"));
        eval("elements['load-entries'].onclick({target: elements['load-entries']})");
        assertEquals("none", eval("elements['load-entries'].style.display"));
    }

    @Test
    void aCityChosenWhileTheTimeLoadsAsksForItsOwnTime() {
        load("");
        eval("""
            chart_type = 'transit';
            elements['location-id'].value = '1850147';
            showNow('http://x/wheel/image/');
            elements['location-id'].value = '5128581';
            fetches[0].answer('2026-11-11T21:00');
            """);
        assertEquals("http://x/now/5128581", eval("fetches[1].url"));
    }

    @Test
    void savingASharedTransitRowRemembersItsCity() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel',
                hash: '#share&datetime=2026-01-01T12%3A00&location=1850147&city=Tokyo'};
            """);
        eval("chart_type = 'transit'; prepareSharing(); saveAs('tokyo')");
        assertEquals("1850147", eval("HdStore.load(window.localStorage).location.id"));
    }

    @Test
    void birthDatesChangedWithTheButtonsAreRemembered() {
        load("");
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            elements['datetime'].value = '1980-01-02T03:04';
            showDate(getFieldDate(), 'http://x/wheel/image/birth/');
            """);
        assertEquals("null", eval("String(HdStore.load(window.localStorage).birth)"));
        eval("showDate(getDateOffset(86400), 'http://x/wheel/image/birth/')");
        assertEquals("1980-01-03T03:04", eval("HdStore.load(window.localStorage).birth.datetime"));
    }

    private static final String CHOOSE_TOKYO = """
        prepareCitiesForElements('#location-id', '#location', 'location');
        awesompletes[0].options.replace({label: 'Tokyo, Tokyo, Japan', value: 1850147});
        elements['datetime'].value = '2024-03-01T10:00';
        """;

    @Test
    void savingNeedsAName() {
        load("");
        eval(CHOOSE_TOKYO);
        eval("saveDateLocation(); typeName('   ');");
        assertEquals("true", eval("findIn(popup, 'save-submit').disabled"));
        eval("""
            var entered = false;
            findIn(popup, 'save-name').onkeydown({key: 'Enter', preventDefault: () => entered = true});
            savePerson('first', currentChart('first'), '', null, null);
            """);
        assertEquals("true", eval("entered"));
        assertEquals("0", eval("HdStore.chartNames(HdStore.load(window.localStorage)).length"));
        assertEquals("block", eval("elements['load-entries'].style.display"));
        eval("typeName('Kim'); findIn(popup, 'save-name').onkeydown({key: 'Enter', preventDefault: () => {}});");
        assertEquals("[\"Kim\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(window.localStorage)))"));
        assertEquals("none", eval("elements['load-entries'].style.display"));
    }

    @Test
    void savingNeedsADateAndAPlace() {
        load("");
        eval("saveDateLocation();");
        assertEquals("Choose a date and a place first, then they can be saved.", eval("popup.children[1].textContent"));
        assertEquals("null", eval("String(findIn(popup, 'save-name'))"));
    }

    @Test
    void savingSaysSoOnTheSaveButtonAndKeepsTheWindowWhenTheBrowserRefuses() {
        load("");
        eval(CHOOSE_TOKYO);
        eval("""
            var classes = new Set();
            var opener = new FakeElement('action-save');
            opener.classList = {add: name => classes.add(name), remove: name => classes.delete(name)};
            saveAs('Kim', 'first', opener);
            """);
        assertEquals("true", eval("classes.has('saved') && document.activeElement === opener"));
        // a second save keeps the word up for its own time
        eval("var first = timers.length; saveAs('Kim', 'first', opener); timers[first - 1] && timers[first - 1]();");
        assertEquals("true", eval("classes.has('saved')"));
        eval("timers.forEach(timer => timer && timer())");
        assertEquals("false", eval("classes.has('saved')"));
        eval("window.localStorage.setItem = () => { throw new Error('full'); }; saveAs('Sam');");
        assertEquals("block", eval("elements['load-entries'].style.display"));
        assertEquals("Saving didn't work, private browsing can prevent it.", eval("popup.children[4].textContent"));
        assertEquals("false", eval("popup.children[4].hidden"));
    }

    @Test
    void savingAgainStartsFromTheNameOfThePersonShown() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'Kim', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            chart_type = 'birth';
            loadEntry('http://x/wheel/image/birth/', 'Kim');
            saveDateLocation();
            """);
        assertEquals("Kim", eval("findIn(popup, 'save-name').value"));
        assertEquals("Replace", eval("findIn(popup, 'save-submit').textContent"));
    }

    @Test
    void anImportTellsHowItWentInTheSavedPeople() {
        loadPeople();
        eval("""
            var other = new MemoryStorage();
            HdStore.update(other, d => HdStore.putChart(d, 'Kim', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            var exported = HdStore.exportCharts(HdStore.load(other));
            showPeople();
            """);
        assertEquals("true", eval("notice().hidden"));
        assertEquals("[\"Import from a file\"]", eval("JSON.stringify(byClass(page, 'entries-actions')[0].children.filter(c => c.id === 'button').map(c => c.textContent))"));
        eval("importEntries({text: () => ({then: answer => answer(exported)})});");
        assertEquals("Imported 1 new person.", eval("notice().textContent"));
        assertEquals("false", eval("notice().hidden"));
        assertEquals("[\"Kim\"]", eval("people()"));
        assertEquals("true", eval("document.activeElement === notice()"));
        assertEquals("[\"Export to a file\",\"Import from a file\"]", eval("JSON.stringify(byClass(page, 'entries-actions')[0].children.filter(c => c.id === 'button').map(c => c.textContent))"));
        eval("importEntries({text: () => ({then: answer => answer('not json')})});");
        assertEquals("This isn't a file of exported people.", eval("notice().textContent"));
        // a refusal to store says so
        eval("window.localStorage.refuseWrites = true; importEntries({text: () => ({then: answer => answer(exported)})});");
        assertEquals("The people couldn't be stored.", eval("notice().textContent"));
    }

    @Test
    void savingUsesTheChosenLocation() {
        load("");
        eval(CHOOSE_TOKYO);
        eval("""
            elements['location'].value = 'Toky';
            saveAs(' Tokyo trip ');
            """);
        assertEquals("none", eval("elements['load-entries'].style.display"));
        assertEquals("{\"datetime\":\"2024-03-01T10:00\",\"locationId\":\"1850147\",\"locationCity\":\"Tokyo, Tokyo, Japan\"}",
            eval("JSON.stringify(HdStore.load(window.localStorage).charts['Tokyo trip'])"));
        eval("elements['location'].dispatch('blur')");
        assertEquals("Tokyo, Tokyo, Japan", eval("elements['location'].value"));
        assertEquals("{\"id\":\"1850147\",\"city\":\"Tokyo, Tokyo, Japan\"}", eval("JSON.stringify(HdStore.load(window.localStorage).location)"));
    }

    @Test
    void citySuggestionsIgnoreFailedAnswers() {
        load("");
        eval("""
            prepareCitiesForElements('#location-id', '#location', 'location');
            elements['location'].value = '50%/';
            elements['location'].dispatch('input');
            requests[0].respond(400, '<html>Ambiguous URI</html>');
            requests[0].respond(200, 'not json');
            requests[0].respond(200, JSON.stringify([['50%/', 0], ['Fifty, Somewhere', 1]]));
            """);
        assertEquals("http://x/cities?query=50%25%2F", eval("requests[0].url"));
        assertEquals("[[\"Fifty, Somewhere\",1]]", eval("JSON.stringify(awesompletes[0].list)"));
    }

    @Test
    void restoredFieldsKeepTheirCity() {
        load("");
        eval("""
            elements['location-id'].value = '1850147';
            elements['location'].value = 'Tokyo, Tokyo, Japan';
            prepareCitiesForElements('#location-id', '#location', 'location');
            elements['location'].value = 'Tok';
            elements['location'].dispatch('blur');
            elements['datetime'].value = '2024-03-01T10:00';
            saveAs('restored');
            """);
        assertEquals("Tokyo, Tokyo, Japan", eval("elements['location'].value"));
        assertEquals("Tokyo, Tokyo, Japan", eval("HdStore.load(window.localStorage).charts['restored'].locationCity"));
    }

    @Test
    void savingTellsWhoItReplaces() {
        load("");
        eval(CHOOSE_TOKYO);
        eval("""
            saveAs('a');
            elements['datetime'].value = '2025-01-01T00:00';
            saveDateLocation();
            typeName('b');
            """);
        assertEquals("Save", eval("findIn(popup, 'save-submit').textContent"));
        assertEquals("true", eval("popup.children[4].hidden"));
        eval("typeName(' a ')");
        assertEquals("Replace", eval("findIn(popup, 'save-submit').textContent"));
        assertEquals("\"a\" is already saved with March 1, 2024 at 10:00 AM in Tokyo, Tokyo, Japan, saving replaces them.",
            eval("popup.children[4].textContent"));
        assertEquals("false", eval("popup.children[4].hidden"));
        eval("findIn(popup, 'save-submit').onclick()");
        assertEquals("2025-01-01T00:00", eval("HdStore.load(window.localStorage).charts['a'].datetime"));
        assertEquals("0", eval("confirms.length + alerts.length"));
    }

    @Test
    void peopleAreSavedInAGroupThatFollowsTheirName() {
        load("");
        eval(CHOOSE_TOKYO);
        eval("""
            saveDateLocation();
            typeName('mom');
            findIn(popup, 'save-group').value = ' Family ';
            findIn(popup, 'save-submit').onclick();
            """);
        assertEquals("Family", eval("HdStore.load(window.localStorage).charts['mom'].group"));
        // the groups are suggested, and the group of the person with the name is filled in
        eval("saveDateLocation(); typeName('dad');");
        assertEquals("[]", eval("var groups = awesompletes.find(list => list.input === findIn(popup, 'save-group')); JSON.stringify(groups.options.list)"));
        eval("findIn(popup, 'save-group').dispatch('focus')");
        assertEquals("[\"Family\"] 1", eval("JSON.stringify(groups.list) + ' ' + groups.evaluated"));
        assertEquals("", eval("findIn(popup, 'save-group').value"));
        eval("typeName('mom')");
        assertEquals("Family", eval("findIn(popup, 'save-group').value"));
        // once changed by hand it stays
        eval("var group = findIn(popup, 'save-group'); group.value = 'Parents'; group.oninput(); typeName('dad'); typeName('mom');");
        assertEquals("Parents", eval("findIn(popup, 'save-group').value"));
        // as does a group that was chosen from the list, which Enter leaves alone unless one is picked
        eval("saveDateLocation(); typeName('dad'); groups = awesompletes.find(list => list.input === findIn(popup, 'save-group'));");
        assertEquals("false", eval("String(groups.options.autoFirst)"));
        eval("groups.options.replace({value: 'Family', label: 'Family'}); typeName('zed'); group = findIn(popup, 'save-group');");
        assertEquals("Family", eval("group.value"));
        eval("typeName('mom')");
        // Enter in the group can pick a suggestion, it doesn't save, and an empty group leaves the person without one
        assertEquals("undefined", eval("String(group.onkeydown)"));
        eval("""
            group.value = ' ';
            findIn(popup, 'save-submit').onclick();
            """);
        assertEquals("false", eval("'group' in HdStore.load(window.localStorage).charts['mom']"));
    }

    @Test
    void savedPeopleAreShownByGroup() {
        load("");
        eval("""
            const add = (d, name, group) => HdStore.putChart(d, name, {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London', group});
            HdStore.update(window.localStorage, d => { add(d, 'me'); add(d, 'zoe'); });
            displaySavedEntries('http://x/');
            """);
        // without groups, the people as before
        assertEquals("0", eval("byClass(popup, 'entries-group').length + byClass(popup, 'entries-filter').length"));
        eval("""
            HdStore.update(window.localStorage, d => { add(d, 'mom', 'Family'); add(d, 'kim', 'Clients'); add(d, 'dad', 'Family'); });
            displaySavedEntries('http://x/');
            var headings = () => JSON.stringify(byClass(popup, 'entries-group').map(h => h.textContent));
            var people = () => JSON.stringify(byClass(popup, 'saved-row').map(row => row.children[0].children[0].textContent));
            var filter = byClass(popup, 'entries-filter')[0].children;
            """);
        assertEquals("[\"Clients\",\"Family\",\"Without a group\"]", eval("headings()"));
        // below the title of the window, which is a second heading of the page
        assertEquals("h3,h3,h3", eval("String(byClass(popup, 'entries-group').map(h => h.id))"));
        assertEquals("[\"kim\",\"dad\",\"mom\",\"me\",\"zoe\"]", eval("people()"));
        assertEquals("[\"Everyone\",\"Clients\",\"Family\",\"Without a group\"]", eval("JSON.stringify(filter.map(b => b.textContent))"));
        assertEquals("[\"true\",\"false\",\"false\",\"false\"]", eval("JSON.stringify(filter.map(b => b['aria-pressed']))"));
        eval("filter[2].onclick()");
        assertEquals("[\"Family\"]", eval("headings()"));
        assertEquals("[\"dad\",\"mom\"]", eval("people()"));
        assertEquals("[\"false\",\"false\",\"true\",\"false\"]", eval("JSON.stringify(filter.map(b => b['aria-pressed']))"));
        // the choice stays for the next time the window opens
        eval("displaySavedEntries('http://x/'); filter = byClass(popup, 'entries-filter')[0].children;");
        assertEquals("[\"dad\",\"mom\"]", eval("people()"));
        eval("filter[3].onclick()");
        assertEquals("[\"me\",\"zoe\"]", eval("people()"));
        eval("displaySavedEntries('http://x/')");
        assertEquals("[\"me\",\"zoe\"]", eval("people()"));
        // once everyone has a group, the people without one are no longer a choice
        eval("""
            HdStore.update(window.localStorage, d => { add(d, 'me', 'Family'); add(d, 'zoe', 'Family'); });
            displaySavedEntries('http://x/');
            filter = byClass(popup, 'entries-filter')[0].children;
            """);
        assertEquals("[\"Everyone\",\"Clients\",\"Family\"]", eval("JSON.stringify(filter.map(b => b.textContent))"));
        assertEquals("[\"kim\",\"dad\",\"me\",\"mom\",\"zoe\"]", eval("people()"));
    }

    @Test
    void enterChoosesTheFirstGroupAsItDoesTheFirstCity() {
        loadPeople();
        eval("""
            HdStore.update(window.localStorage, d => { add(d, 'mom', 'Family'); add(d, 'kim'); });
            showPeople();
            button('group-entry', 'kim').onclick();
            var input = findIn(page, 'group-edit');
            var groups = awesompletes.find(list => list.input === input);
            // what Awesomplete does with the groups that have the typed text: sorts them, opens the list and tells so
            var listed = (typed, names) => {
                input.value = typed;
                groups.index = -1;
                groups.suggestions = names.map(name => ({toString: () => name})).sort(groups.options.sort);
                if (groups.suggestions.length > 0) {
                    input.dispatch('awesomplete-open');
                }
                return groups.suggestions.join(',') + ' ' + groups.index;
            };
            """);
        // Enter chooses what Awesomplete has selected, which is nothing by itself
        assertEquals("false", eval("String(groups.options.autoFirst)"));
        // the first group is selected once something was typed, the one that starts with it before one that has it
        assertEquals("Family,Extended family 0", eval("listed('fam', ['Extended family', 'Family'])"));
        assertEquals("Family,Extended family,Old family 0", eval("listed(' family ', ['Extended family', 'Family', 'Old family'])"));
        assertEquals("Clients,Friends 0", eval("listed('en', ['Clients', 'Friends'])"));
        // a group that was typed whole is there already, so Enter is left to do what it does without a list
        assertEquals("Family,Extended family -1", eval("listed('Family', ['Extended family', 'Family'])"));
        assertEquals("Family,Extended family -1", eval("listed(' Family ', ['Extended family', 'Family'])"));
        // an empty field lists every group in its order, and Enter leaves it empty
        assertEquals("Clients,Family,Friends -1", eval("listed('', ['Clients', 'Family', 'Friends'])"));
        assertEquals("Clients,Family,Friends -1", eval("listed('  ', ['Clients', 'Family', 'Friends'])"));
        // no group has what was typed: the list stays closed and what was typed is a new group
        assertEquals(" -1", eval("listed('Team', [])"));
    }

    @Test
    void theGroupOfSomeoneSavedCanBeChanged() {
        loadPeople();
        eval("""
            HdStore.update(window.localStorage, d => { add(d, 'mom', 'Family'); add(d, 'kim'); });
            showPeople();
            button('group-entry', 'kim').onclick();
            var input = findIn(page, 'group-edit');
            """);
        assertEquals("[\"mom\",\"kim\"]", eval("people()"));
        // the groups are right below the title of the page
        assertEquals("h2,h2 Family,Without a group", eval("byClass(page, 'entries-group').map(h => h.id) + ' ' + byClass(page, 'entries-group').map(h => h.textContent)"));
        assertEquals("true", eval("button('group-entry', 'kim')['aria-expanded']"));
        assertEquals("true", eval("document.activeElement === input"));
        assertEquals("", eval("input.value"));
        // the groups are listed as soon as the field is entered
        assertEquals("[\"Family\"] 1", eval("var groups = awesompletes.find(list => list.input === input); input.dispatch('focus'); JSON.stringify(groups.list) + ' ' + groups.evaluated"));
        // a second click closes the field
        eval("button('group-entry', 'kim').onclick()");
        assertEquals("null", eval("String(findIn(page, 'group-edit'))"));
        assertEquals("false", eval("button('group-entry', 'kim')['aria-expanded']"));
        // a group chosen from the list is a change by itself
        eval("HdStore.update(window.localStorage, d => add(d, 'lee')); showPeople(); button('group-entry', 'lee').onclick();");
        eval("awesompletes.find(list => list.input === findIn(page, 'group-edit')).options.replace({value: 'Family', label: 'Family'})");
        assertEquals("Family null", eval("HdStore.load(window.localStorage).charts['lee'].group + ' ' + findIn(page, 'group-edit')"));
        eval("HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'lee')); showPeople();");
        // a changed field puts the person in the group, and says so in case the group isn't shown
        eval("button('group-entry', 'kim').onclick(); input = findIn(page, 'group-edit'); input.value = ' Family '; input.onchange();");
        assertEquals("Family", eval("HdStore.load(window.localStorage).charts['kim'].group"));
        assertEquals("\"kim\" is in Family.", eval("notice().textContent"));
        assertEquals("false", eval("notice().hidden"));
        assertEquals("[\"kim\",\"mom\"]", eval("people()"));
        assertEquals("true", eval("document.activeElement === button('group-entry', 'kim')"));
        // only right after the change
        eval("document.activeElement = null; showPeople()");
        assertEquals("null", eval("String(document.activeElement)"));
        // Done with an empty field takes them out of it, once
        eval("""
            button('group-entry', 'mom').onclick();
            input = findIn(page, 'group-edit');
            input.value = '';
            var done = byClass(page, 'row-editor')[0].children[1];
            done.onclick();
            """);
        assertEquals("false", eval("'group' in HdStore.load(window.localStorage).charts['mom']"));
        assertEquals("\"mom\" is no longer in a group.", eval("notice().textContent"));
        assertEquals("[\"kim\",\"mom\"]", eval("people()"));
        eval("HdStore.update(window.localStorage, d => add(d, 'mom', 'Friends')); input.onchange();");
        assertEquals("Friends", eval("HdStore.load(window.localStorage).charts['mom'].group"));
    }

    @Test
    void someoneSavedCanBeRenamed() {
        loadPeople();
        eval("""
            HdStore.update(window.localStorage, d => { add(d, 'mom', 'Family'); add(d, 'kim'); });
            showPeople();
            button('rename-entry', 'kim').onclick();
            var input = findIn(page, 'name-edit');
            """);
        assertEquals("kim", eval("input.value"));
        assertEquals("Name of kim", eval("input['aria-label']"));
        assertEquals("true", eval("document.activeElement === input"));
        // one field at a time
        eval("button('group-entry', 'mom').onclick()");
        assertEquals("null", eval("String(findIn(page, 'name-edit'))"));
        assertEquals("false", eval("button('rename-entry', 'kim')['aria-expanded']"));
        assertEquals("1", eval("byClass(page, 'row-editor').length"));
        // the name of someone else stays theirs
        eval("button('rename-entry', 'kim').onclick(); input = findIn(page, 'name-edit'); input.value = ' mom '; byClass(page, 'row-editor')[0].children[1].onclick();");
        assertEquals("Someone else is already saved as \"mom\", so \"kim\" keeps their name.", eval("notice().textContent"));
        assertEquals("[\"kim\",\"mom\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(window.localStorage)))"));
        assertEquals("true", eval("document.activeElement === button('rename-entry', 'kim')"));
        // Enter ends the field, also with the name as it was or without one
        for (var same : new String[]{"kim", "  "}) {
            eval("button('rename-entry', 'kim').onclick(); input = findIn(page, 'name-edit'); input.value = '" + same + "';" +
                 "var entered = {key: 'Enter', prevented: false, preventDefault() { this.prevented = true; }}; input.onkeydown(entered);");
            assertEquals("true", eval("entered.prevented"));
            assertEquals("null", eval("String(findIn(page, 'name-edit'))"));
            assertEquals("true", eval("notice().hidden"));
            assertEquals("[\"kim\",\"mom\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(window.localStorage)))"));
        }
        // a name isn't changed by leaving its field or by closing it, with its button or with Escape
        eval("button('rename-entry', 'mom').onclick(); input = findIn(page, 'name-edit'); input.value = 'Ma'; input.onkeydown({key: 'a'});");
        assertEquals("undefined", eval("typeof input.onchange"));
        eval("button('rename-entry', 'mom').onclick()");
        assertEquals("null", eval("String(findIn(page, 'name-edit'))"));
        eval("""
            button('rename-entry', 'mom').onclick();
            input = findIn(page, 'name-edit');
            input.value = 'Ma';
            var escaped = {key: 'Escape', stopped: false, stopPropagation() { this.stopped = true; }};
            input.onkeydown(escaped);
            var done = byClass(page, 'row-editor');
            """);
        assertEquals("true 0 false", eval("escaped.stopped + ' ' + done.length + ' ' + button('rename-entry', 'mom')['aria-expanded']"));
        assertEquals("true", eval("document.activeElement === button('rename-entry', 'mom')"));
        assertEquals("[\"kim\",\"mom\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(window.localStorage)))"));
        // nor is a group by Escape, even when the browser tells of the change afterwards
        eval("button('group-entry', 'kim').onclick(); input = findIn(page, 'group-edit'); input.value = 'Friends'; input.onkeydown(escaped); input.onchange();");
        assertEquals("false null", eval("('group' in HdStore.load(window.localStorage).charts['kim']) + ' ' + findIn(page, 'group-edit')"));
        assertEquals("true", eval("notice().hidden"));
        // a new name is theirs from then on, with their group, and the focus stays with them
        eval("button('rename-entry', 'mom').onclick(); input = findIn(page, 'name-edit'); input.value = ' Mother '; done = byClass(page, 'row-editor')[0].children[1]; done.onclick(); done.onclick();");
        assertEquals("\"mom\" is now called \"Mother\".", eval("notice().textContent"));
        assertEquals("[\"kim\",\"Mother\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(window.localStorage)))"));
        assertEquals("Family", eval("HdStore.load(window.localStorage).charts['Mother'].group"));
        assertEquals("[\"Mother\",\"kim\"]", eval("people()"));
        assertEquals("true", eval("document.activeElement === button('rename-entry', 'Mother')"));
        // someone deleted in the meantime is only gone
        eval("""
            button('rename-entry', 'kim').onclick();
            input = findIn(page, 'name-edit');
            input.value = 'Kim';
            HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'kim'));
            input.onkeydown(entered);
            """);
        assertEquals("[\"Mother\"]", eval("people()"));
        assertEquals("true", eval("notice().hidden"));
        // a refusal to store says so
        eval("button('rename-entry', 'Mother').onclick(); input = findIn(page, 'name-edit'); input.value = 'Ma'; window.localStorage.refuseWrites = true; input.onkeydown(entered);");
        assertEquals("The change couldn't be stored.", eval("notice().textContent"));
        assertEquals("[\"Mother\"]", eval("people()"));
    }

    @Test
    void thePeoplePageAddsSomeoneWithTheirDetails() {
        loadPeople();
        eval("""
            var field = {};
            for (const id of ['add', 'form', 'name', 'datetime', 'location', 'location-id', 'group', 'submit', 'notice', 'cancel']) {
                field[id] = elements['person-' + id] = new FakeElement('person-' + id);
            }
            // as the page has them
            field.form.hidden = true;
            field.notice.hidden = true;
            field.add['aria-expanded'] = 'false';
            HdStore.update(window.localStorage, d => { add(d, 'mom', 'Family'); add(d, 'zoe', 'Friends'); });
            saved_group = 'Family';
            preparePersonForm();
            showPeople();
            var cities = awesompletes.find(list => list.input === field.location);
            var groups = awesompletes.find(list => list.input === field.group);
            var fill = (name, datetime) => { field.name.value = name; field.name.oninput(); field.datetime.value = datetime; field.datetime.oninput(); };
            var choose = (id, city) => cities.options.replace({value: id, label: city});
            var submit = () => field.form.onsubmit({preventDefault() {}});
            var state = () => [field.form.hidden, field.add['aria-expanded'], field.submit.textContent, field.submit.disabled].join(' ');
            """);
        assertEquals("true false Add true", eval("state()"));
        assertEquals("true", eval("cities.input === field.location"));
        // it opens from its button, with the groups there are, and closes from it
        eval("field.add.onclick()");
        assertEquals("false true Add true", eval("state()"));
        assertEquals("true", eval("document.activeElement === field.name"));
        // the groups there are now are listed when their field is entered
        eval("field.group.dispatch('focus')");
        assertEquals("[\"Family\",\"Friends\"] 1", eval("JSON.stringify(groups.list) + ' ' + groups.evaluated"));
        eval("field.add.onclick()");
        assertEquals("true false Add true", eval("state()"));
        // someone is added once they have a name, a birth and a city that was chosen
        eval("field.add.onclick(); fill(' Kim ', '1990-05-06T07:08');");
        assertEquals("false true Add true", eval("state()"));
        eval("field.location.value = 'Paris'; field.location.dispatch('blur');");
        assertEquals(" true", eval("field.location.value + ' ' + field.submit.disabled"));
        eval("choose(2988507, 'Paris, France')");
        assertEquals("Paris, France 2988507 false", eval("field.location.value + ' ' + field['location-id'].value + ' ' + field.submit.disabled"));
        for (var incomplete : new String[]{"fill('  ', '1990-05-06T07:08')", "fill('Kim', '')", "fill('Kim', 'soon')"}) {
            assertEquals("true", eval(incomplete + "; String(field.submit.disabled)"), incomplete);
            eval("submit()");
            assertEquals("[\"mom\",\"zoe\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(window.localStorage)))"), incomplete);
            // and nothing is said about it, the button couldn't be pressed
            assertEquals("true", eval("String(field.notice.hidden)"), incomplete);
        }
        eval("fill(' Kim ', '1990-05-06T07:08'); field.group.value = ' Friends '; field.group.oninput(); submit();");
        assertEquals("{\"datetime\":\"1990-05-06T07:08\",\"locationId\":\"2988507\",\"locationCity\":\"Paris, France\",\"group\":\"Friends\"}",
            eval("JSON.stringify(HdStore.load(window.localStorage).charts['Kim'])"));
        // the form is closed and empty again, and they're in sight whatever group was shown
        assertEquals("true false Add true", eval("state()"));
        assertEquals("||||", eval("[field.name.value, field.datetime.value, field.location.value, field['location-id'].value, field.group.value].join('|')"));
        assertEquals("\"Kim\" is added.", eval("notice().textContent"));
        assertEquals("[\"mom\",\"Kim\",\"zoe\"]", eval("people()"));
        assertEquals("true", eval("document.activeElement === notice()"));
        // the birth that the charts remember and the place they're seen from are left alone
        assertEquals("null null null", eval("(d => d.birth + ' ' + d.location + ' ' + d.named)(HdStore.load(window.localStorage))"));

        // a name that's taken says who has it and takes their group, adding replaces them
        eval("field.add.onclick(); fill('mom', '1950-01-02T03:04');");
        assertEquals("\"mom\" is already saved with June 21, 1975 at 2:30 PM in London, saving replaces them. false", eval("field.notice.textContent + ' ' + field.notice.hidden"));
        assertEquals("Replace Family", eval("field.submit.textContent + ' ' + field.group.value"));
        eval("fill('mo', '1950-01-02T03:04')");
        assertEquals("Add true ", eval("field.submit.textContent + ' ' + field.notice.hidden + ' ' + field.group.value"));
        // a group that was typed stays, whoever has the name
        eval("field.group.value = 'Clients'; field.group.oninput(); fill('mom', '1950-01-02T03:04');");
        assertEquals("Clients", eval("field.group.value"));
        // until the form is opened again
        eval("field.cancel.onclick(); field.add.onclick(); fill('mom', '1950-01-02T03:04');");
        assertEquals("Family", eval("field.group.value"));
        // a group chosen from the list stays as a typed one does
        eval("groups.options.replace({value: 'Friends', label: 'Friends'}); fill('mom', '1950-01-02T03:04');");
        assertEquals("Friends", eval("field.group.value"));
        eval("field.cancel.onclick(); field.add.onclick();");
        // a refusal to store says so in the form, which stays
        eval("fill('mom', '1950-01-02T03:04'); choose(1850147, 'Tokyo, Japan'); window.localStorage.refuseWrites = true; submit();");
        assertEquals("Saving didn't work, private browsing can prevent it. false false", eval("field.notice.textContent + ' ' + field.notice.hidden + ' ' + field.form.hidden"));
        eval("window.localStorage.refuseWrites = false; submit();");
        assertEquals("\"mom\" is replaced.", eval("notice().textContent"));
        assertEquals("{\"datetime\":\"1950-01-02T03:04\",\"locationId\":\"1850147\",\"locationCity\":\"Tokyo, Japan\",\"group\":\"Family\"}",
            eval("JSON.stringify(HdStore.load(window.localStorage).charts['mom'])"));

        // Cancel leaves the form empty
        eval("field.add.onclick(); fill('Ann', '1950-01-02T03:04'); field.cancel.onclick();");
        assertEquals("true false Add true ", eval("state() + ' ' + field.name.value"));
        assertEquals("true", eval("document.activeElement === field.add"));
        assertEquals("[\"Kim\",\"mom\",\"zoe\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(window.localStorage)))"));
        // Escape doesn't, it's also how the lists of cities and of groups are closed
        eval("field.add.onclick(); fill('Ann', '1950-01-02T03:04'); press('Escape');");
        assertEquals("false Ann 0", eval("field.form.hidden + ' ' + field.name.value + ' ' + (field.form.listeners.keydown || []).length"));
        // Enter in the field of the city doesn't add the person: the city that's being typed isn't chosen yet,
        // and the field goes back to the one that was
        eval("""
            choose(2643743, 'London');
            field.location.value = 'Ber';
            var entered = {key: 'Enter', prevented: 0, preventDefault() { this.prevented++; }, target: field.location};
            for (const listener of field.location.listeners.keydown) listener(entered);
            var typed = {key: 'r', prevented: 0, preventDefault() { this.prevented++; }, target: field.location};
            for (const listener of field.location.listeners.keydown) listener(typed);
            """);
        assertEquals("1 0 London false", eval("entered.prevented + ' ' + typed.prevented + ' ' + field.location.value + ' ' + field.form.hidden"));
    }

    @Test
    void someoneSavedOpensInTheChartsOfThePeoplePage() {
        loadPeople();
        eval("""
            HdStore.update(window.localStorage, d => {
                HdStore.putChart(d, 'kim', {datetime: '1990-05-06T07:08', locationId: '2988507', locationCity: 'Paris'});
                HdStore.setBirthDatetime(d, '1980-01-02T03:04', 'birth');
                HdStore.setBirthLocation(d, '2643743', 'London', 'birth');
                HdStore.setBirthDatetime(d, '1975-06-21T14:30', 'partner');
            });
            window.location = {href: 'http://x/people'};
            showPeople();
            var charts = byClass(page, 'person-charts')[0].children;
            """);
        assertEquals("[\"Birth Chart\",\"Connection Chart\"]", eval("JSON.stringify(charts.map(b => b.textContent))"));
        assertEquals("[\"Birth Chart of kim\",\"Connection Chart of kim\"]", eval("JSON.stringify(charts.map(b => b['aria-label']))"));
        assertEquals("[\"button\",\"button\"]", eval("JSON.stringify(charts.map(b => b.type))"));
        eval("charts[1].onclick()");
        assertEquals("http://x/wheel/connection", eval("window.location.href"));
        assertEquals("{\"datetime\":\"1990-05-06T07:08\",\"location\":{\"id\":\"2988507\",\"city\":\"Paris\"}}", eval("JSON.stringify(HdStore.load(window.localStorage).birth)"));
        assertEquals("{\"name\":\"kim\",\"datetime\":\"1990-05-06T07:08\",\"locationId\":\"2988507\"}", eval("JSON.stringify(HdStore.load(window.localStorage).named)"));
        // the other person of a connection stays who they were
        assertEquals("1975-06-21T14:30", eval("HdStore.load(window.localStorage).partner.datetime"));
        // the page then shows them under their name, as after a Load
        eval("""
            chart_type = 'birth';
            prepareCitiesForElements('#location-id', '#location', 'birth');
            prepareBirthDateForElement('datetime');
            """);
        assertEquals("1990-05-06T07:08 2988507 kim", eval("elements['datetime'].value + ' ' + elements['location-id'].value + ' ' + chartTitle('first')"));
        // a refusal to remember them says so, their chart would show someone else
        eval("""
            window.location.href = 'http://x/people';
            HdStore.update(window.localStorage, d => HdStore.setBirthDatetime(d, '1980-01-02T03:04', 'birth'));
            window.localStorage.refuseWrites = true;
            charts[0].onclick();
            window.localStorage.refuseWrites = false;
            """);
        assertEquals("http://x/people", eval("window.location.href"));
        assertEquals("The change couldn't be stored.", eval("notice().textContent"));
        // someone deleted in the meantime only leaves the list
        eval("""
            charts = byClass(page, 'person-charts')[0].children;
            window.location.href = 'http://x/people';
            HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'kim'));
            charts[0].onclick();
            """);
        assertEquals("http://x/people", eval("window.location.href"));
        assertEquals("[]", eval("people()"));
    }

    @Test
    void thePeoplePageFollowsWhatChangesElsewhere() {
        loadPeople();
        eval("HdStore.update(window.localStorage, d => add(d, 'kim')); showPeople();");
        eval("HdStore.update(window.localStorage, d => add(d, 'mom')); window.dispatch('storage', {key: 'other'});");
        assertEquals("[\"kim\"]", eval("people()"));
        eval("window.dispatch('storage', {key: 'hdapp'})");
        assertEquals("[\"kim\",\"mom\"]", eval("people()"));
        // what's being typed is left alone
        eval("""
            button('rename-entry', 'kim').onclick();
            HdStore.update(window.localStorage, d => add(d, 'zoe'));
            window.dispatch('storage', {key: 'hdapp'});
            """);
        assertEquals("[\"kim\",\"mom\"]", eval("people()"));
        assertEquals("true", eval("findIn(page, 'name-edit') !== null"));
        // and catches up once the field is closed without a change, by its button or with Escape
        eval("button('rename-entry', 'kim').onclick()");
        assertEquals("[\"kim\",\"mom\",\"zoe\"]", eval("people()"));
        assertEquals("true true", eval("(document.activeElement === button('rename-entry', 'kim')) + ' ' + notice().hidden"));
        eval("""
            button('group-entry', 'mom').onclick();
            HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'zoe'));
            window.dispatch('storage', {key: 'hdapp'});
            findIn(page, 'group-edit').onkeydown({key: 'Escape', stopPropagation() {}});
            """);
        assertEquals("[\"kim\",\"mom\"]", eval("people()"));
        assertEquals("true", eval("document.activeElement === button('group-entry', 'mom')"));
        // a field that's closed with nothing changed elsewhere leaves the list as it is
        eval("var row = byClass(page, 'saved-row')[0]; button('rename-entry', 'kim').onclick(); button('rename-entry', 'kim').onclick();");
        assertEquals("true", eval("row === byClass(page, 'saved-row')[0]"));
        eval("HdStore.update(window.localStorage, d => add(d, 'zoe')); window.dispatch('pageshow', {persisted: false});");
        assertEquals("[\"kim\",\"mom\"]", eval("people()"));
        // a page that Back returns to comes back as it was left
        eval("window.dispatch('pageshow', {persisted: true})");
        assertEquals("[\"kim\",\"mom\",\"zoe\"]", eval("people()"));
    }

    @Test
    void theLoadWindowOnlyChoosesSomeoneAndLinksToThePeoplePage() {
        load("");
        eval("displaySavedEntries('http://x/')");
        assertEquals("No people saved yet. Fill in a date and place, then use Save to keep someone here, or import a file of people on the People page.",
            eval("byClass(popup, 'no-entries')[0].textContent"));
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'kim', {datetime: '1990-05-06T07:08', locationId: '2988507', locationCity: 'Paris', group: 'Friends'}));
            displaySavedEntries('http://x/');
            var row = byClass(popup, 'saved-row')[0];
            var link = byClass(popup, 'entries-manage')[0];
            """);
        assertEquals("1", eval("row.children.length"));
        assertEquals("[\"kim\",\"May 6, 1990 @ 7:08 AM\",\"Paris\"]", eval("JSON.stringify(row.children[0].children.map(c => c.textContent))"));
        assertEquals("0", eval("['rename-entry', 'group-entry', 'delete-entry', 'person-charts', 'entries-backup'].reduce((count, css) => count + byClass(popup, css).length, 0)"));
        assertEquals("a Manage people http://x/people", eval("link.id + ' ' + link.textContent + ' ' + link.href"));
        assertEquals("[\"a\"]", eval("JSON.stringify(byClass(popup, 'entries-actions')[0].children.map(c => c.id))"));
    }

    private static final String SAVING = """
        elements['chart-download'] = new FakeElement('chart-download');
        elements['page-heading'] = new FakeElement('page-heading');
        elements['page-heading'].textContent = 'Birth Chart';
        var downloads = [];
        document.body = {appendChild: link => downloads.push(link)};
        FakeElement.prototype.click = function () { this.clicked = true; };
        FakeElement.prototype.remove = function () {};
        var URL = {createObjectURL: blob => 'blob:' + blob.type, revokeObjectURL: () => {}};
        class Blob { constructor(parts, options) { this.parts = parts; this.type = options.type; } }
        var drawn = [];
        var images = [];
        class Image { set src(value) { this.source = value; images.push(this); } }
        const createElement = document.createElement;
        document.createElement = tag => {
            const element = createElement(tag);
            if (tag === 'canvas') {
                element.getContext = () => ({fillRect: (...a) => drawn.push(['fill', element.width, element.height]), drawImage: (image, ...a) => drawn.push(['image', ...a])});
                element.toBlob = (callback, type) => callback({type});
            }
            return element;
        };
        var prints = 0;
        window.print = () => prints++;
        """;

    @Test
    void theChartIsSavedAfterThePageItsPeopleAndItsMoment() {
        load(SAVING);
        eval(CHOOSE_TOKYO);
        eval("chart_type = 'birth'; saveAs('Zoë & Kim')");
        assertEquals("human-design-birth-chart-zoe-kim-2024-03-01-1000", eval("chartFileName()"));
        eval("chart_type = ''; elements['page-heading'].textContent = 'Transits by Month';");
        assertEquals("human-design-transits-by-month-2024-03-01-1000", eval("chartFileName()"));
    }

    @Test
    void theChartIsSavedAsAnImageADrawingOrOnPaper() {
        load(SAVING);
        // nothing to save before a chart is shown
        assertEquals("false", eval("saveChart()"));
        assertEquals("undefined", eval("String(elements['load-entries'].style.display)"));
        eval("""
            elements['chart-download'].href = 'http://x/wheel/image/202403011000/1850147';
            saveChart();
            var choices = byClass(popup, 'saved-entry');
            """);
        assertEquals("Save the chart", eval("popup.children[0].children[0].textContent"));
        assertEquals("[\"PNG image\",\"SVG drawing\",\"Print\"]", eval("JSON.stringify(choices.map(c => c.children[0].textContent))"));
        assertEquals("true", eval("document.activeElement === choices[0]"));

        // the drawing of the server as a file of the page's own, a link to its address isn't saved everywhere
        eval("choices[1].onclick()");
        assertEquals("http://x/wheel/image/202403011000/1850147 true 0", eval("fetches[0].url + ' ' + choices[1].disabled + ' ' + downloads.length"));
        eval("fetches[0].answer('<svg/>')");
        assertEquals("blob:image/svg+xml human-design-birth-chart.svg true", eval("downloads[0].href + ' ' + downloads[0].download + ' ' + downloads[0].clicked"));
        assertEquals("none", eval("elements['load-entries'].style.display"));
        // one that the server doesn't give is said, and can be asked again
        eval("saveChart(); var svg = byClass(popup, 'saved-entry')[1]; svg.onclick(); fetches[1].answer('', false);");
        assertEquals("The drawing couldn't be saved. false false block 1",
            eval("popup.children[1].textContent + ' ' + popup.children[1].hidden + ' ' + svg.disabled + ' ' + elements['load-entries'].style.display + ' ' + downloads.length"));
        eval("closePopup()");

        eval("saveChart(); byClass(popup, 'saved-entry')[2].onclick()");
        assertEquals("1", eval("prints"));
        assertEquals("none", eval("elements['load-entries'].style.display"));

        // the drawing of the server, twice as large on white
        eval("saveChart(); byClass(popup, 'saved-entry')[0].onclick()");
        assertEquals("http://x/wheel/image/202403011000/1850147", eval("fetches[2].url"));
        eval("fetches[2].answer('<svg width=\"960\" height=\"1570\"/>')");
        assertEquals("data:image/svg+xml;charset=utf-8,%3Csvg%20width%3D%22960%22%20height%3D%221570%22%2F%3E", eval("images[0].source"));
        eval("images[0].naturalWidth = 960; images[0].naturalHeight = 1570; images[0].onload();");
        assertEquals("[[\"fill\",1920,3140],[\"image\",0,0,1920,3140]]", eval("JSON.stringify(drawn)"));
        assertEquals("blob:image/png human-design-birth-chart.png", eval("downloads[1].href + ' ' + downloads[1].download"));
        assertEquals("none", eval("elements['load-entries'].style.display"));

        // a browser that can't draw it says what else works
        eval("saveChart(); var png = byClass(popup, 'saved-entry')[0]; png.onclick(); fetches[3].answer('<svg/>');");
        // once at a time
        assertEquals("true", eval("png.disabled"));
        eval("images[1].onerror(new Error('no'))");
        assertEquals("The image couldn't be made, the SVG drawing can be saved instead.", eval("popup.children[1].textContent"));
        assertEquals("false", eval("popup.children[1].hidden"));
        assertEquals("block", eval("elements['load-entries'].style.display"));
        assertEquals("false", eval("png.disabled"));

        // an image that's ready after its window was closed and another opened is left
        eval("png.onclick(); fetches[4].answer('<svg/>');");
        eval("displaySavedEntries('http://x/');");
        eval("images[2].naturalWidth = 1; images[2].naturalHeight = 1; images[2].onload();");
        assertEquals("2", eval("downloads.length"));
        assertEquals("block", eval("elements['load-entries'].style.display"));
        // and one whose window was closed
        eval("saveChart(); byClass(popup, 'saved-entry')[0].onclick(); fetches[5].answer('<svg/>');");
        eval("closePopup();");
        eval("images[3].naturalWidth = 1; images[3].naturalHeight = 1; images[3].onload();");
        assertEquals("2", eval("downloads.length"));
    }

    @Test
    void savedPeopleShowTheirMomentAsBelowTheCharts() {
        load("");
        assertEquals("June 21, 1975 @ 2:30 PM", eval("savedMoment('1975-06-21T14:30')"));
        assertEquals("January 2, 1980 @ 12:04 AM", eval("savedMoment('1980-01-02T00:04')"));
        assertEquals("March 3, 2001 @ 12:00 PM", eval("savedMoment('2001-03-03T12:00')"));
    }

    @Test
    void theSavedPeopleCloseWithTheirButtonAndEscape() {
        load("");
        eval("displaySavedEntries('http://x/'); popup.children[0].children[1].onclick();");
        assertEquals("none", eval("elements['load-entries'].style.display"));
        eval("displaySavedEntries('http://x/'); press('Enter');");
        assertEquals("block", eval("elements['load-entries'].style.display"));
        assertEquals("true", eval("press('Escape')"));
        assertEquals("none", eval("elements['load-entries'].style.display"));
        // a closed window leaves Escape to the Explore card
        assertEquals("false", eval("press('Escape')"));
    }

    @Test
    void theSavedPeopleTakeTheFocusAndGiveItBack() {
        load("");
        eval("""
            var opener = new FakeElement('action-load');
            document.activeElement = opener;
            displaySavedEntries('http://x/');
            var close = popup.children[0].children[1];
            """);
        assertEquals("true", eval("document.activeElement === close"));
        eval("opener.focus(); document.dispatch('focusin', {target: opener})");
        assertEquals("true", eval("document.activeElement === close"));
        assertEquals("true", eval("popup['aria-modal']"));
        eval("displaySavedEntries('http://x/'); press('Escape');");
        assertEquals("true", eval("document.activeElement === opener"));
        eval("document.activeElement = null; press('Escape');");
        assertEquals("null", eval("String(document.activeElement)"));
    }

    @Test
    void escapeOnPagesWithoutSavedPeopleDoesNothing() {
        load("");
        assertDoesNotThrow(() -> eval("delete elements['load-entries']; press('Escape');"));
    }

    @Test
    void theTrashCanDeletesOnASecondClick() {
        loadPeople();
        eval("""
            HdStore.update(window.localStorage, d => {
                HdStore.putChart(d, 'a', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'});
                HdStore.putChart(d, 'b', {datetime: '1980-01-02T03:04', locationId: '2643743', locationCity: 'London'});
            });
            showPeople();
            var trash = byClass(page, 'saved-row').map(row => row.children[3]);
            trash[0].onclick();
            """);
        assertEquals("2", eval("HdStore.chartNames(HdStore.load(window.localStorage)).length"));
        assertEquals("delete-entry armed", eval("trash[0]['class']"));
        assertEquals("Tap or click again to delete a", eval("trash[0]['aria-label']"));
        eval("trash[1].onclick()");
        assertEquals("delete-entry", eval("trash[0]['class']"));
        eval("trash[0].onclick(); timers.forEach(timer => timer && timer());");
        assertEquals("delete-entry", eval("trash[0]['class']"));
        assertEquals("Delete a", eval("trash[0]['aria-label']"));
        eval("trash[0].onclick(); trash[0].onclick();");
        assertEquals("[\"b\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(window.localStorage)))"));
        assertEquals("[\"b\"]", eval("people()"));
        assertEquals("\"a\" is deleted.", eval("notice().textContent"));
        assertEquals("true", eval("document.activeElement === notice()"));
        assertEquals("0", eval("confirms.length"));
        // a refusal to store says so, and they're still listed
        eval("window.localStorage.refuseWrites = true; trash = byClass(page, 'saved-row').map(row => row.children[3]); trash[0].onclick(); trash[0].onclick();");
        assertEquals("The change couldn't be stored.", eval("notice().textContent"));
        assertEquals("[\"b\"]", eval("people()"));
    }

    @Test
    void savedChartsAreShownWithTheirName() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'mom & dad', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            chart_type = 'birth';
            loadEntry('http://x/wheel/image/birth/', 'mom & dad');
            """);
        assertEquals("http://x/wheel/image/birth/197506211430/2643743?name=mom%20%26%20dad", eval("elements['svg-image'].dataset.src"));
        eval("showDate(getDateOffset(60), 'http://x/wheel/image/birth/')");
        assertEquals("http://x/wheel/image/birth/197506211431/2643743", eval("elements['svg-image'].dataset.src"));

        eval("""
            chart_type = 'birth-transit';
            elements['location-id'].value = '1850147';
            elements['datetime'].value = '2024-03-01T10:00';
            loadEntry('http://x/wheel/image/birth-transit/', 'mom & dad');
            showDate(getDateOffset(3600), 'http://x/wheel/image/birth-transit/');
            """);
        assertEquals("http://x/wheel/image/birth-transit/197506211430/2643743/202403011100/1850147?name=mom%20%26%20dad", eval("elements['svg-image'].dataset.src"));
    }

    @Test
    void theTitleIsKeptAcrossPageLoads() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'mom', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            chart_type = 'birth';
            loadEntry('http://x/wheel/image/birth/', 'mom');
            """);
        var stored = eval("window.localStorage.getItem('hdapp')");
        js_.close();
        // a new page in the same browser, showing the remembered birth
        load("window.localStorage.setItem('hdapp', " + quote(stored) + ");");
        eval("""
            chart_type = 'birth';
            prepareCitiesForElements('#location-id', '#location', 'birth');
            prepareBirthDateForElement('datetime');
            showDate(getFieldDate(), 'http://x/wheel/image/birth/');
            """);
        assertEquals("http://x/wheel/image/birth/197506211430/2643743?name=mom", eval("elements['svg-image'].dataset.src"));
        eval("elements['datetime'].value = '1975-06-21T15:30'; showDate(getFieldDate(), 'http://x/wheel/image/birth/')");
        assertEquals("http://x/wheel/image/birth/197506211530/2643743", eval("elements['svg-image'].dataset.src"));
    }

    private static String quote(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    @Test
    void savingShowsTheNameRightAway() {
        load("");
        eval(CHOOSE_TOKYO);
        eval("""
            chart_type = 'birth';
            showDate(getFieldDate(), 'http://x/wheel/image/birth/');
            saveAs('Tokyo');
            """);
        assertEquals("http://x/wheel/image/birth/202403011000/1850147?name=Tokyo", eval("elements['svg-image'].dataset.src"));
    }

    @Test
    void connectionsLoadSaveAndTitleEachRow() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => {
                HdStore.putChart(d, 'Steve Jobs', {datetime: '1955-02-24T19:15', locationId: '5391959', locationCity: 'San Francisco'});
                HdStore.putChart(d, 'Steve Wozniak', {datetime: '1950-08-11T09:45', locationId: '5392171', locationCity: 'San Jose'});
            });
            chart_type = 'connection';
            displaySavedEntries('http://x/wheel/image/connection/', 'first');
            loadEntry('http://x/wheel/image/connection/', 'Steve Jobs');
            """);
        // the first chart alone until the second has a date and a city
        assertEquals("http://x/wheel/image/connection/195502241915/5391959?name=Steve%20Jobs", eval("elements['svg-image'].dataset.src"));
        eval("""
            displaySavedEntries('http://x/wheel/image/connection/', 'second');
            loadEntry('http://x/wheel/image/connection/', 'Steve Wozniak');
            """);
        assertEquals("http://x/wheel/image/connection/195008110945/5392171/195502241915/5391959?name=Steve%20Jobs&secondName=Steve%20Wozniak",
            eval("elements['svg-image'].dataset.src"));
        assertEquals("1955-02-24T19:15", eval("HdStore.load(window.localStorage).birth.datetime"));
        assertEquals("1950-08-11T09:45", eval("HdStore.load(window.localStorage).partner.datetime"));
        assertEquals("Steve Wozniak", eval("HdStore.load(window.localStorage).namedPartner.name"));

        // saving the second row saves the second chart
        eval("""
            elements['second-datetime'].value = '1950-08-11T10:45';
            saveAs('Woz later', 'second');
            """);
        assertEquals("1950-08-11T10:45", eval("HdStore.load(window.localStorage).charts['Woz later'].datetime"));
        assertEquals("5392171", eval("HdStore.load(window.localStorage).charts['Woz later'].locationId"));
    }

    @Test
    void imageAddressesUseTheWallClock() {
        load("");
        eval("""
            chart_type = 'birth-transit';
            elements['second-datetime'].value = '1975-06-21T14:30';
            elements['second-location-id'].value = '2643743';
            elements['location-id'].value = '1850147';
            showDate('2024-03-31T02:30', 'http://x/wheel/image/birth-transit/');
            """);
        assertEquals("http://x/wheel/image/birth-transit/197506211430/2643743/202403310230/1850147", eval("elements['svg-image'].dataset.src"));
        assertEquals("2024-03-31T02:30", eval("elements['datetime'].value"));
        eval("elements['second-location-id'].value = ''; showDate('2024-03-31T02:30', 'http://x/wheel/image/birth-transit/');");
        assertEquals("http://x/wheel/image/birth-transit", eval("elements['svg-image'].dataset.src"));
    }

    @Test
    void loadingAChartFillsTheFields() {
        load("");
        eval("HdStore.update(window.localStorage, d => HdStore.putChart(d, 'mom', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}))");
        eval("chart_type = 'birth'; loadEntry('http://x/wheel/image/birth/', 'mom');");
        assertEquals("http://x/wheel/image/birth/197506211430/2643743?name=mom", eval("elements['svg-image'].dataset.src"));
        assertEquals("London", eval("elements['location'].value"));
        assertEquals("{\"datetime\":\"1975-06-21T14:30\",\"location\":{\"id\":\"2643743\",\"city\":\"London\"}}",
            eval("JSON.stringify(HdStore.load(window.localStorage).birth)"));
    }

    // a page with the Explore panel, whose fetches wait until a test answers them
    private static final String EXPLORE = """
        elements['explore'] = new FakeElement('explore');
        var explore = {'http://x/wheel/image': 'http://x/wheel/explore', 'http://x/range/table': 'http://x/range/explore'};
        var explores = () => fetches.filter(f => f.url.includes('/explore/'));
        """;

    @Test
    void theExplorePanelFollowsTheChart() {
        load(EXPLORE);
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/');
            """);
        assertEquals("http://x/wheel/explore/birth/197506211430/2643743", eval("explores()[0].url"));
        eval("explores()[0].answer('<h2>Explore</h2>')");
        assertEquals("<h2>Explore</h2>", eval("elements['explore'].innerHTML"));

        // a chart without a city has nothing to explore
        eval("elements['location-id'].value = ''; showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/')");
        assertEquals("", eval("elements['explore'].innerHTML"));
        assertEquals("1", eval("explores().length"));
    }

    @Test
    void howToReadStaysOpenOrClosedForTheNextCharts() {
        load(EXPLORE + """
            var guide = {open: false, classList: {contains: name => name === 'explore-guide'}};
            elements['explore'].querySelector = selector => selector === '.explore-guide' ? guide : null;
            """);
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/');
            explores()[0].answer('panel');
            """);
        assertEquals("false", eval("String(guide.open)"));

        eval("guide.open = true; document.dispatch('toggle', {target: guide})");
        assertEquals("true", eval("String(HdStore.load(window.localStorage).guideOpen)"));
        eval("""
            guide = {open: false, classList: guide.classList};
            showDate('1975-06-21T14:31', 'http://x/wheel/image/birth/');
            explores()[1].answer('panel');
            """);
        assertEquals("true", eval("String(guide.open)"));

        // other parts of the page that open and close are left alone
        eval("document.dispatch('toggle', {target: {open: false, classList: {contains: () => false}}})");
        assertEquals("true", eval("String(HdStore.load(window.localStorage).guideOpen)"));
        eval("guide.open = false; document.dispatch('toggle', {target: guide})");
        assertEquals("false", eval("String(HdStore.load(window.localStorage).guideOpen)"));
    }

    @Test
    void anAnswerForAReplacedChartIsIgnored() {
        load(EXPLORE);
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/');
            showDate('1975-06-21T14:31', 'http://x/wheel/image/birth/');
            explores()[1].answer('second');
            """);
        eval("explores()[0].answer('first')");
        assertEquals("second", eval("elements['explore'].innerHTML"));
    }

    @Test
    void theChartIsDrawnInThePage() {
        load("elements['chart-download'] = new FakeElement('chart-download');");
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/');
            showDate('1975-06-21T14:31', 'http://x/wheel/image/birth/');
            fetches[1].answer('<svg>second</svg>');
            """);
        eval("fetches[0].answer('<svg>first</svg>')");
        assertEquals("<svg>second</svg>", eval("elements['svg-image'].innerHTML"));
        assertEquals("http://x/wheel/image/birth/197506211431/2643743", eval("elements['chart-download'].href"));

        eval("elements['location-id'].value = ''; showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/')");
        assertEquals("", eval("elements['svg-image'].innerHTML"));
        assertEquals("false", eval("'href' in elements['chart-download']"));
    }

    @Test
    void theFirstChartIsAtTheTimeOfTheCityInTheField() {
        load("");
        eval("""
            chart_type = 'transit';
            elements['location-id'].value = '1850147';
            elements['datetime'].value = '2026-11-11T08:00';
            showFirstChart('http://x/wheel/image/', '2643743');
            """);
        assertEquals("http://x/now/1850147", eval("fetches[0].url"));
        eval("fetches[0].answer('2026-11-11T17:00')");
        assertEquals("http://x/wheel/image/202611111700/1850147", eval("fetches[1].url"));

        // the server's time is for the same city, or for a date the page was opened at
        for (var now_location : new String[] {"1850147", ""}) {
            load("");
            eval("chart_type = 'transit'; elements['location-id'].value = '1850147'; elements['datetime'].value = '2026-11-11T08:00';");
            eval("showFirstChart('http://x/wheel/image/', '" + now_location + "')");
            assertEquals("http://x/wheel/image/202611110800/1850147", eval("fetches[0].url"));
        }

        // and a shared link keeps its own date
        load("window.location = {origin: 'http://x', pathname: '/wheel', hash: '#share&datetime=2020-01-01T10%3A00&location=1850147&city=Tokyo'};");
        eval("chart_type = 'transit'; prepareSharing(); showFirstChart('http://x/wheel/image/', '2643743')");
        assertEquals("http://x/wheel/image/202001011000/1850147?shared=1", eval("fetches[0].url"));
    }

    // a page of a visitor whose device is set to a time zone, before they chose a city
    private void loadInZone(String zone) {
        load("var Intl = {zone: '" + zone + "', DateTimeFormat: () => ({resolvedOptions: () => ({timeZone: Intl.zone})})};");
        eval("routes.zone = 'http://x/cities/zone'; elements['datetime'].value = '2026-11-11T08:00';");
    }

    @Test
    void aRowWithoutAChosenCityStartsAtTheCityOfTheDevicesTimeZone() {
        loadInZone("America/New_York");
        eval("""
            chart_type = 'transit';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '2643743');
            """);
        // London until the server said which city the zone has, and no chart of it meanwhile
        assertEquals("2643743 London, England, United Kingdom", eval("elements['location-id'].value + ' ' + elements['location'].value"));
        assertEquals("1 http://x/cities/zone?zone=America%2FNew_York", eval("fetches.length + ' ' + fetches[0].url"));
        eval("fetches[0].answer('[\"New York City, New York, United States\",5128581]')");
        assertEquals("5128581 New York City, New York, United States", eval("elements['location-id'].value + ' ' + elements['location'].value"));
        // the page has the time of the server's city, so the time of this one is asked for
        assertEquals("http://x/now/5128581", eval("fetches[1].url"));
        eval("fetches[1].answer('2026-11-11T03:00')");
        assertEquals("http://x/wheel/image/202611110300/5128581", eval("fetches[2].url"));
        // it isn't a city that the visitor chose
        assertEquals("null", eval("JSON.stringify(HdStore.load(window.localStorage).location)"));

        // the next page starts there right away, without asking
        eval("""
            elements['datetime'].value = '2026-11-11T08:00';
            elements['location-id'].value = '';
            prepareCitiesForElements('#location-id', '#location', 'location');
            """);
        assertEquals("5128581", eval("elements['location-id'].value"));
        eval("showFirstChart('http://x/wheel/image/', '5128581')");
        assertEquals("http://x/wheel/image/202611110800/5128581", eval("fetches[3].url"));

        // until the device is in another zone
        eval("""
            Intl.zone = 'Europe/Brussels';
            elements['location-id'].value = '';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '5128581');
            """);
        assertEquals("2643743 http://x/cities/zone?zone=Europe%2FBrussels", eval("elements['location-id'].value + ' ' + fetches[4].url"));
    }

    @Test
    void aBirthRowThatNobodyFilledInShowsThisMomentWhereTheDeviceIs() {
        loadInZone("America/New_York");
        eval("""
            chart_type = 'birth';
            prepareCitiesForElements('#location-id', '#location', 'birth');
            showFirstChart('http://x/wheel/image/birth/', '2643743');
            fetches[0].answer('["New York City, New York, United States",5128581]');
            """);
        // the date of the page is the time in London, which is another moment in New York
        assertEquals("http://x/now/5128581", eval("fetches[1].url"));
        eval("fetches[1].answer('2026-11-11T03:00')");
        assertEquals("http://x/wheel/image/birth/202611110300/5128581", eval("fetches[2].url"));
        // and it's nobody's birth
        assertEquals("null", eval("JSON.stringify(HdStore.load(window.localStorage).birth)"));

        // also without a zone, when the server remembers another city than the row starts at
        load("");
        eval("""
            chart_type = 'birth';
            elements['datetime'].value = '2026-11-11T17:00';
            prepareCitiesForElements('#location-id', '#location', 'birth');
            showFirstChart('http://x/wheel/image/birth/', '1850147');
            """);
        assertEquals("http://x/now/2643743", eval("fetches[0].url"));
    }

    @Test
    void aChosenCityIsNeverReplacedByTheOneOfTheTimeZone() {
        // the city that the visitor saw charts from before
        loadInZone("America/New_York");
        eval("""
            HdStore.update(window.localStorage, d => HdStore.setLocation(d, '1850147', 'Tokyo, Japan'));
            chart_type = 'transit';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '1850147');
            """);
        assertEquals("1850147 http://x/wheel/image/202611110800/1850147", eval("elements['location-id'].value + ' ' + fetches[0].url"));

        // a city chosen while the answer was on its way
        loadInZone("America/New_York");
        eval("""
            chart_type = 'transit';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '2643743');
            awesompletes[0].options.replace({value: 2988507, label: 'Paris'});
            fetches[0].answer('["New York City, New York, United States",5128581]');
            """);
        assertEquals("2988507", eval("elements['location-id'].value"));

        // also when the city chosen meanwhile is the one that rows start at without an answer
        loadInZone("America/New_York");
        eval("""
            chart_type = 'transit';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '2643743');
            awesompletes[0].options.replace({value: 2643743, label: 'London, England, United Kingdom'});
            fetches[0].answer('["New York City, New York, United States",5128581]');
            """);
        assertEquals("2643743", eval("elements['location-id'].value"));

        // or when the fields were filled in another way, by loading a saved person for instance
        loadInZone("America/New_York");
        eval("""
            chart_type = 'transit';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '2643743');
            setLocationInputs(elements['location-id'], elements['location'], '1850147', 'Tokyo, Japan');
            fetches[0].answer('["New York City, New York, United States",5128581]');
            """);
        assertEquals("1850147", eval("elements['location-id'].value"));

        // and the city of a shared chart, also when that's where rows start without one
        load("var Intl = {DateTimeFormat: () => ({resolvedOptions: () => ({timeZone: 'America/New_York'})})};"
            + "window.location = {origin: 'http://x', pathname: '/wheel', hash: '#share&datetime=2020-01-01T10%3A00&location=2643743&city=London'};");
        eval("""
            routes.zone = 'http://x/cities/zone';
            chart_type = 'transit';
            prepareCitiesForElements('#location-id', '#location', 'location');
            prepareSharing();
            showFirstChart('http://x/wheel/image/', '2643743');
            fetches[0].answer('["New York City, New York, United States",5128581]');
            """);
        assertEquals("2643743 http://x/wheel/image/202001011000/2643743?shared=1", eval("elements['location-id'].value + ' ' + fetches[1].url"));
    }

    @Test
    void rowsStartInLondonWhenTheTimeZoneHasNoCity() {
        // the server doesn't answer
        loadInZone("America/New_York");
        eval("""
            chart_type = 'transit';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '2643743');
            fetches[0].answer('', false);
            """);
        assertEquals("2643743 http://x/wheel/image/202611110800/2643743", eval("elements['location-id'].value + ' ' + fetches[1].url"));
        assertEquals("null", eval("String(window.localStorage.getItem('hdapp-zone'))"));

        // a zone without a city, which isn't asked about again
        loadInZone("UTC");
        eval("""
            chart_type = 'transit';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '2643743');
            fetches[0].answer('[]');
            """);
        assertEquals("2643743 http://x/wheel/image/202611110800/2643743", eval("elements['location-id'].value + ' ' + fetches[1].url"));
        eval("""
            elements['location-id'].value = '';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '2643743');
            """);
        assertEquals("3 http://x/wheel/image/202611110800/2643743", eval("fetches.length + ' ' + fetches[2].url"));

        // a browser that doesn't tell its zone
        load("var Intl = undefined;");
        eval("""
            routes.zone = 'http://x/cities/zone';
            chart_type = 'transit';
            elements['datetime'].value = '2026-11-11T08:00';
            prepareCitiesForElements('#location-id', '#location', 'location');
            showFirstChart('http://x/wheel/image/', '2643743');
            """);
        assertEquals("http://x/wheel/image/202611110800/2643743", eval("fetches[0].url"));
    }

    @Test
    void theImportMessageCountsTheCharts() {
        load("");
        assertEquals("Imported 1 new person. 1 person was already saved.", eval("importMessage({added: 1, renamed: 0, unchanged: 1, invalid: 0})"));
        assertEquals("Imported 2 new people. 2 people had the names of others already saved and were imported with a number after their name. "
            + "3 people were incomplete and skipped.", eval("importMessage({added: 2, renamed: 2, unchanged: 0, invalid: 3})"));
        assertEquals("Imported 0 new people. 1 person was already saved and was put in the group the file gives them.", eval("importMessage({added: 0, renamed: 0, unchanged: 0, grouped: 1, invalid: 0})"));
        assertEquals("Imported 0 new people. 2 people were already saved and were put in the groups the file gives them.", eval("importMessage({added: 0, renamed: 0, unchanged: 0, grouped: 2, invalid: 0})"));
    }

    @Test
    void loadingATransitChartKeepsTheNameOfTheBirthChart() {
        load("");
        eval("""
            HdStore.update(window.localStorage, d => {
                HdStore.putChart(d, 'me', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'});
                HdStore.putChart(d, 'mom', {datetime: '1950-01-01T10:00', locationId: '2643743', locationCity: 'London'});
            });
            chart_type = 'birth';
            loadEntry('http://x/wheel/image/birth/', 'me');
            chart_type = 'transit';
            loadEntry('http://x/wheel/image/', 'mom');
            """);
        assertEquals("me", eval("HdStore.load(window.localStorage).named.name"));

        eval("image_url = null; elements['location-id'].value = '2643743'; elements['datetime'].value = '2026-01-01T12:00'; saveAs('later')");
        assertEquals("me", eval("HdStore.load(window.localStorage).named.name"));
    }

    @Test
    void aLateAnswerForNowDoesntApplyToAnotherCity() {
        load("");
        eval("""
            chart_type = 'transit';
            elements['location-id'].value = '1850147';
            elements['datetime'].value = '2026-11-11T08:00';
            showNow('http://x/wheel/image/');
            elements['location-id'].value = '2988507';
            fetches[0].answer('2026-11-11T17:00');
            """);
        // the time of that city is asked for instead
        assertEquals("http://x/now/2988507", eval("fetches[1].url"));
    }

    @Test
    void theFirstBirthChartIsNeverReplacedByNow() {
        load("");
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '1850147';
            elements['datetime'].value = '1975-06-21T14:30';
            showFirstChart('http://x/wheel/image/birth/', '2643743');
            """);
        assertEquals("http://x/wheel/image/birth/197506211430/1850147", eval("fetches[0].url"));
    }

    @Test
    void anErrorPageIsNeverDrawnAsTheChart() {
        load("");
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/');
            fetches[0].answer('<html>Not Found</html>', false);
            """);
        var notice = eval("elements['svg-image'].innerHTML");
        assertFalse(notice.contains("Not Found"), notice);
        assertTrue(notice.contains("The chart couldn't be loaded."), notice);
    }

    @Test
    void aChartThatDoesntArriveSaysSo() {
        load("""
            var rejections = [];
            fetch = url => new Promise((resolve, reject) => rejections.push(reject));
            """);
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/');
            showDate('1975-06-21T14:31', 'http://x/wheel/image/birth/');
            elements['svg-image'].innerHTML = 'the chart before';
            rejections[0](new Error('offline'));
            """);
        // the answer to a chart that has been replaced since changes nothing
        assertEquals("the chart before", eval("elements['svg-image'].innerHTML"));
        eval("rejections[1](new Error('offline'))");
        assertTrue(eval("elements['svg-image'].innerHTML").contains("The chart couldn't be loaded."));
    }

    @Test
    void enterChoosesTheFirstCityAndAChosenCityIsDrawn() {
        load("");
        eval("""
            chart_type = 'transit';
            elements['location-id'].value = '2643743';
            elements['location-id'].dataset.city = 'London';
            elements['datetime'].value = '2026-11-13T12:00';
            prepareCitiesForElements('#location-id', '#location', 'location');
            """);
        assertEquals("true", eval("String(awesompletes[0].options.autoFirst)"));

        // before a chart was shown, a chosen city draws nothing
        eval("awesompletes[0].options.replace({value: 2988507, label: 'Paris'})");
        assertEquals("0", eval("fetches.length"));
        eval("""
            showDate(getFieldDate(), 'http://x/wheel/image/');
            awesompletes[0].options.replace({value: 1850147, label: 'Tokyo'});
            """);
        assertEquals("http://x/wheel/image/202611131200/1850147", eval("fetches[fetches.length - 1].url"));

        // Enter without a city chosen from the list puts the city of the chart back
        eval("""
            elements['location'].value = 'Par';
            elements['location'].listeners.keydown.forEach(listener => listener({key: 'Enter'}));
            """);
        assertEquals("Tokyo", eval("elements['location'].value"));
        eval("""
            elements['location'].value = 'Par';
            elements['location'].listeners.keydown.forEach(listener => listener({key: 'a'}));
            """);
        assertEquals("Par", eval("elements['location'].value"));
    }

    @Test
    void theSavedPeopleSayThatExportIsTheirBackup() {
        loadPeople();
        eval("showPeople()");
        assertEquals("0", eval("byClass(page, 'entries-backup').length"));
        assertEquals("No people saved yet. Add someone here, or use Save on the page of a chart to keep the person that it shows.",
            eval("byClass(page, 'no-entries')[0].textContent"));
        eval("HdStore.update(window.localStorage, d => add(d, 'Ada')); showPeople();");
        assertEquals("Saved people stay in this browser only. Export keeps a copy as a file, which is the only sure backup.",
            eval("byClass(page, 'entries-backup')[0].textContent"));
        // an app that shows the pages keeps them itself
        eval("kept_in = 'this app'; showPeople();");
        assertEquals("Saved people stay in this app only. Export keeps a copy as a file, which is the only sure backup.",
            eval("byClass(page, 'entries-backup')[0].textContent"));
        assertEquals("0", eval("byClass(page, 'no-entries').length"));
    }

    @Test
    void anAppHandsOverWhoWasSavedAndRemovedOnAnotherDevice() {
        loadPeople();
        eval("HdStore.update(window.localStorage, d => { add(d, 'Ada'); add(d, 'Kim'); HdStore.setNamed(d, 'Kim', d.charts['Kim'], 'birth'); }); showPeople();");
        assertEquals("true", eval("""
            String(syncPeople({saved: {'Lee': {datetime: '1990-05-06T07:08', locationId: '2800866', locationCity: 'Brussels', group: 'Friends'},
                                        'Ada': {datetime: '1975-06-21T14:31', locationId: '2643743', locationCity: 'London'},
                                        'Nobody': {datetime: 'never', locationId: '1', locationCity: 'Nowhere'}},
                               removed: ['Kim', 'Someone else']}))"""));
        // saved and removed as if it was done here, and shown at once
        assertEquals("{\"Ada\":{\"datetime\":\"1975-06-21T14:31\",\"locationId\":\"2643743\",\"locationCity\":\"London\"}," +
                     "\"Lee\":{\"datetime\":\"1990-05-06T07:08\",\"locationId\":\"2800866\",\"locationCity\":\"Brussels\",\"group\":\"Friends\"}}",
            eval("JSON.stringify(HdStore.load(window.localStorage).charts)"));
        assertEquals("[\"Lee\",\"Ada\"]", eval("people()"));
        // a chart isn't titled with someone who's gone
        assertEquals("null", eval("String(HdStore.load(window.localStorage).named)"));
        // nothing to hand over changes nothing
        assertEquals("true [\"Lee\",\"Ada\"]", eval("syncPeople({}) + ' ' + people()"));
    }

    @Test
    void theShareButtonSharesCopiesOrShowsTheLink() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel/birth', hash: ''};
            window.history = {replaced: [], replaceState(state, title, url) { this.replaced.push(url); }};
            document.title = 'Birth Chart';
            elements['chart-share'] = new FakeElement('chart-share');
            elements['chart-share'].classList = {names: [], add(name) { this.names.push(name); }, remove(name) { this.names = this.names.filter(each => each !== name); }};
            """);
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            elements['location-id'].dataset.city = 'London';
            elements['datetime'].value = '1975-06-21T14:30';
            """);
        var link = "http://x/wheel/birth#share&datetime=1975-06-21T14%3A30&location=2643743&city=London";

        // a page without a secure connection can only show the link in the address bar
        eval("shareChart()");
        assertEquals(link, eval("window.history.replaced.join()"));

        eval("var copied = []; navigator.clipboard = {writeText: text => { copied.push(text); return Promise.resolve(); }}; shareChart()");
        assertEquals(link, eval("copied.join()"));
        assertEquals("copied", eval("elements['chart-share'].classList.names.join()"));
        eval("timers[timers.length - 1]()");
        assertEquals("", eval("elements['chart-share'].classList.names.join()"));

        // a browser that refuses to copy
        eval("navigator.clipboard = {writeText: () => Promise.reject(new Error('denied'))}; shareChart()");
        assertEquals("2", eval("String(window.history.replaced.length)"));
        assertEquals("", eval("elements['chart-share'].classList.names.join()"));

        // the share sheet of a phone comes first
        eval("var shared = []; navigator.share = data => { shared.push(data); return Promise.resolve(); }; shareChart()");
        assertEquals(link + " Birth Chart", eval("shared.map(data => data.url + ' ' + data.title).join()"));
        assertEquals("2", eval("String(window.history.replaced.length)"));
    }

    @Test
    void exportSavesTheSavedPeopleAsAFile() {
        load("""
            FakeElement.prototype.click = function () { this.clicked = true; };
            FakeElement.prototype.remove = function () { this.removed = true; };
            var blobs = [];
            class Blob { constructor(parts, options) { this.parts = parts; this.type = options.type; blobs.push(this); } }
            var URL = {revoked: [], createObjectURL: blob => 'blob:' + blobs.indexOf(blob), revokeObjectURL(href) { this.revoked.push(href); }};
            var links = [];
            document.body = {appendChild: link => links.push(link)};
            """);
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'Ada', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            exportEntries();
            """);
        assertEquals("application/json", eval("blobs[0].type"));
        assertEquals("1975-06-21T14:30", eval("JSON.parse(blobs[0].parts[0]).charts['Ada'].datetime"));
        assertEquals("blob:0 true true", eval("links[0].href + ' ' + links[0].clicked + ' ' + links[0].removed"));
        assertTrue(eval("links[0].download").matches("hd-people-\\d{4}-\\d\\d-\\d\\d\\.json"));
        // the file is released once the browser has had the time to save it
        assertEquals("", eval("URL.revoked.join()"));
        eval("timers[timers.length - 1]()");
        assertEquals("blob:0", eval("URL.revoked.join()"));
    }

    @Test
    void nowFallsBackOnTheClockOfTheBrowser() {
        load("");
        eval("""
            chart_type = 'transit';
            elements['location-id'].value = '1850147';
            showNow('http://x/wheel/image/');
            fetches[0].answer('', false);
            """);
        assertEquals("http://x/now/1850147", eval("fetches[0].url"));
        var shown = eval("elements['datetime'].value");
        assertTrue(shown.matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d"), shown);
        assertEquals("http://x/wheel/image/" + shown.replaceAll("[-T:]", "") + "/1850147", eval("fetches[1].url"));

        // an answer that isn't a time
        eval("elements['datetime'].value = '2000-01-01T00:00'; showNow('http://x/wheel/image/'); fetches[2].answer('<html>')");
        assertNotEquals("2000-01-01T00:00", eval("elements['datetime'].value"));
    }

    @Test
    void aChartSaysWhatItStillNeeds() {
        load("elements['chart-hint'] = new FakeElement('chart-hint'); elements['chart-hint'].hidden = true;");
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            elements['datetime'].value = '2026-11-13T12:00';
            showDate(getFieldDate(), 'http://x/wheel/image/birth/');
            """);
        assertEquals("false", eval("String(elements['chart-hint'].hidden)"));
        assertTrue(eval("elements['chart-hint'].textContent").startsWith("This is the chart of this moment."));
        // a birth that Show draws is remembered, and the chart is a birth chart from then on
        eval("elements['datetime'].value = '1975-06-21T14:30'; showDate(getFieldDate(), 'http://x/wheel/image/birth/')");
        assertEquals("true", eval("String(elements['chart-hint'].hidden)"));
        assertEquals("", eval("elements['chart-hint'].textContent"));

        eval("chart_type = 'birth-transit'; elements['second-location-id'].value = '2643743'; drawChart('http://x/wheel/image/birth-transit/')");
        assertTrue(eval("elements['chart-hint'].textContent").startsWith("Enter a birth date, time and city in the Birth row"));
        eval("elements['second-datetime'].value = '1975-06-21T14:30'; drawChart('http://x/wheel/image/birth-transit/')");
        assertEquals("true", eval("String(elements['chart-hint'].hidden)"));

        eval("chart_type = 'transit'; drawChart('http://x/wheel/image/')");
        assertEquals("true", eval("String(elements['chart-hint'].hidden)"));
    }

    @Test
    void aClickOnTheTitleBarGoesBackToTheTopOfThePage() {
        load("""
            elements['menu-button'] = new FakeElement('menu-button'); elements['menu-button'].getAttribute = function (name) { return this[name]; };
            var went = [];
            window.scrollTo = how => went.push('window ' + how.top + ' ' + how.behavior);
            document.body = {scrollTo: how => went.push('page ' + how.top + ' ' + how.behavior)};
            var less = false;
            window.matchMedia = query => ({matches: query === '(prefers-reduced-motion: reduce)' && less});
            var click = (...inside) => { went = []; document.dispatch('click', {target: {closest: selector => inside.includes(selector) ? elements['menu-button'] : null}}); return went.join(', '); };
            """);
        // the window scrolls in a browser and the page inside it in an app, whichever it is
        assertEquals("window 0 smooth, page 0 smooth", eval("click('#title-bar')"));
        // at once for who asked for less motion
        assertEquals("window 0 auto, page 0 auto", eval("less = true; click('#title-bar')"));
        // not from what the bar has to press, nor from the rest of the page
        assertEquals("", eval("less = false; click('#title-bar', 'button, a')"));
        assertEquals("", eval("click('#title-bar', '#menu-button')"));
        assertEquals("", eval("click('#content')"));
        // a click on the bar that closes the menu only does that
        assertEquals("true", eval("click('#title-bar', '#menu-button'); elements['menu-button']['aria-expanded']"));
        assertEquals(" false", eval("click('#title-bar') + ' ' + elements['menu-button']['aria-expanded']"));
        assertEquals("window 0 smooth, page 0 smooth", eval("click('#title-bar')"));
    }

    @Test
    void theMenuOpensFromItsButtonAndClosesWithEscapeOrAClickNextToIt() {
        load("elements['menu-button'] = new FakeElement('menu-button'); elements['menu-button'].getAttribute = function (name) { return this[name]; };");
        var button = "{closest: selector => selector === '#menu-button' ? elements['menu-button'] : null}";
        var link = "{closest: selector => selector === '#menu' ? {} : null}";
        var elsewhere = "{closest: selector => null}";
        eval("document.dispatch('click', {target: " + button + "})");
        assertEquals("true", eval("elements['menu-button']['aria-expanded']"));
        eval("document.dispatch('click', {target: " + link + "})");
        assertEquals("true", eval("elements['menu-button']['aria-expanded']"));
        eval("document.dispatch('click', {target: " + button + "})");
        assertEquals("false", eval("elements['menu-button']['aria-expanded']"));

        eval("document.dispatch('click', {target: " + button + "}); document.dispatch('click', {target: " + elsewhere + "})");
        assertEquals("false", eval("elements['menu-button']['aria-expanded']"));

        // the focus goes back to the button from a link of the menu
        eval("document.dispatch('click', {target: " + button + "}); document.activeElement = " + link + "; press('Escape')");
        assertEquals("false", eval("elements['menu-button']['aria-expanded']"));
        assertEquals("true", eval("String(document.activeElement === elements['menu-button'])"));

        // a page that Back returns to
        eval("document.dispatch('click', {target: " + button + "}); window.dispatch('pageshow', {persisted: false})");
        assertEquals("false", eval("elements['menu-button']['aria-expanded']"));
    }

    @Test
    void aCycleSetsTheTransitsToItsMoment() {
        load("");
        eval("""
            routes.cycle = 'http://x/cycle';
            chart_type = 'birth-transit';
            elements['second-datetime'].value = '1955-02-24T19:15';
            elements['second-location-id'].value = '5391959';
            elements['location-id'].value = '1850147';
            showCycle('saturn-return', 'http://x/wheel/image/birth-transit/');
            """);
        eval("var cycles = () => fetches.filter(f => f.url.includes('/cycle/'))");
        assertEquals("http://x/cycle/saturn-return/195502241915/5391959/1850147", eval("cycles()[0].url"));
        eval("cycles()[0].answer('1984-11-29T06:33')");
        assertEquals("1984-11-29T06:33", eval("elements['datetime'].value"));
        assertTrue(eval("elements['svg-image'].dataset.src").endsWith("/195502241915/5391959/198411290633/1850147"), eval("elements['svg-image'].dataset.src"));

        // an answer that comes after another chart was asked for, or that has no moment, changes nothing
        eval("""
            showCycle('chiron-return', 'http://x/wheel/image/birth-transit/');
            showDate('2000-01-01T00:00', 'http://x/wheel/image/birth-transit/');
            cycles()[1].answer('2005-03-30T16:32');
            showCycle('chiron-return', 'http://x/wheel/image/birth-transit/');
            cycles()[2].answer('', false);
            """);
        assertEquals("2000-01-01T00:00", eval("elements['datetime'].value"));

        // nor does an answer for a transit city that was changed meanwhile
        eval("""
            showCycle('saturn-return', 'http://x/wheel/image/birth-transit/');
            elements['location-id'].value = '2643743';
            cycles()[3].answer('1984-11-29T06:34');
            """);
        assertEquals("2000-01-01T00:00", eval("elements['datetime'].value"));

        // without a birth there's nothing to ask for
        eval("elements['second-datetime'].value = ''; showCycle('saturn-return', 'http://x/wheel/image/birth-transit/')");
        assertEquals("4", eval("cycles().length"));
    }

    @Test
    void aLateAnswerForNowDoesntReplaceAChosenDate() {
        load("");
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            showNow('http://x/wheel/image/birth/');
            showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/');
            fetches[0].answer('2026-11-11T12:00');
            """);
        assertEquals("1975-06-21T14:30", eval("elements['datetime'].value"));
    }

    @Test
    void theKeyboardsActionKeyClosesItOnTouchScreens() {
        load("var coarse = true; window.matchMedia = query => ({matches: query === '(pointer: coarse)' && coarse});");
        eval("prepareCitiesForElements('#location-id', '#location', 'location')");
        assertEquals("done", eval("elements['location'].enterkeyhint"));
        eval("elements['location'].listeners.keydown.forEach(listener => listener({key: 'a'}))");
        assertEquals("undefined", eval("String(elements['location'].blurs)"));
        eval("elements['location'].listeners.keydown.forEach(listener => listener({key: 'Enter'}))");
        assertEquals("1", eval("String(elements['location'].blurs)"));
        // with a mouse and a keyboard the field keeps the focus
        eval("coarse = false; elements['location'].listeners.keydown.forEach(listener => listener({key: 'Enter'}))");
        assertEquals("1", eval("String(elements['location'].blurs)"));
    }

    @Test
    void citySuggestionsAreEscaped() {
        load("");
        assertEquals("<mark>A&#38;</mark>B &#60;i&#62;", eval("highlightSuggestion('A&B <i>', 'a&')"));
        assertEquals("<mark>a.b</mark>c", eval("highlightSuggestion('a.bc', 'a.b')"));
        assertEquals("abc", eval("highlightSuggestion('abc', '  ')"));
        eval("prepareCitiesForElements('#location-id', '#location', 'location')");
        assertEquals("<mark>A</mark>&#60;b", eval("awesompletes[0].options.item('A<b', 'a', 0).innerHTML"));
        // Awesomplete would otherwise mark the typed text again, inside the escaped HTML
        assertEquals("", eval("awesompletes[0].options.item('A<b', 'a', 0).input"));
    }

    @Test
    void tapsOnTheWheelFindItsRings() {
        load("");
        assertEquals("wheel.zodiac", eval("wheelPart(480, 10)"));
        assertEquals("wheel.planets", eval("wheelPart(480 + 420, 480)"));
        assertEquals("wheel.gates", eval("wheelPart(480, 480 + 380)"));
        assertEquals("wheel.bodygraph", eval("wheelPart(480, 480)"));
        // the circles of wheel.svg, at radiuses 480, 448, 408 and 361, and the gates reach 354
        assertEquals("wheel.zodiac", eval("wheelPart(480, 0)"));
        assertEquals("wheel.zodiac", eval("wheelPart(480, 480 - 448)"));
        assertEquals("wheel.planets", eval("wheelPart(480, 480 - 447)"));
        assertEquals("wheel.planets", eval("wheelPart(480, 480 - 408)"));
        assertEquals("wheel.gates", eval("wheelPart(480, 480 - 407)"));
        assertEquals("wheel.gates", eval("wheelPart(480, 480 - 361)"));
        // the marks of the people of a connection, just inside the ring
        assertEquals("wheel.gates", eval("wheelPart(480, 480 - 355)"));
        assertEquals("wheel.bodygraph", eval("wheelPart(480, 480 - 353)"));
        assertEquals("null", eval("String(wheelPart(5, 5))"));
        assertEquals("null", eval("String(wheelPart(480, 1000))"));
    }

    // the parts of a page with a chart that the full screen view uses, on a phone held upright
    private static final String VIEWER = """
        for (const id of ['chart-zoom', 'chart-viewer', 'chart-viewer-stage', 'chart-viewer-in', 'chart-viewer-out', 'chart-viewer-close']) elements[id] = new FakeElement(id);
        elements['chart-zoom'].hidden = true;
        elements['chart-viewer'].hidden = true;
        elements['chart-viewer-stage'].clientWidth = 400;
        elements['chart-viewer-stage'].clientHeight = 800;
        var drawing = new FakeElement('svg');
        drawing.getAttribute = name => name === 'viewBox' ? '0 0 960 1440' : null;
        drawing.cloneNode = () => { const copy = new FakeElement('copy'); copy.getAttribute = drawing.getAttribute; return copy; };
        elements['svg-image svg'] = drawing;
        var copy = () => elements['chart-viewer-stage'].children[0];
        var on = target => ({closest: selector => selector === target ? elements[target.slice(1)] : null});
        var click = target => document.dispatch('click', {target: on(target)});
        var finger = (type, id, x, y, time) => document.dispatch(type, {type, pointerId: id, clientX: x, clientY: y, timeStamp: time || 0, target: on('#chart-viewer-stage')});
        var view = () => [chart_view.scale, chart_view.x, chart_view.y].map(n => Math.round(n * 100) / 100).join(' ');
        """;

    @Test
    void theChartFitsTheScreenAndStaysWithinItWhileItIsMadeLargerAndMoved() {
        load("");
        // the wheel fits the narrow side, in the middle of the room that's left
        assertEquals("{\"scale\":1,\"x\":0,\"y\":100,\"width\":400,\"height\":600,\"roomWidth\":400,\"roomHeight\":800}", eval("JSON.stringify(firstView(400, 800, 960, 1440))"));
        // on a wide screen what's below the wheel is out of sight, the chart starts at its top
        assertEquals("200 0 400 600", eval("(() => { const v = firstView(800, 400, 960, 1440); return [v.x, v.y, v.width, v.height].join(' '); })()"));
        eval("var v = firstView(400, 800, 960, 1440)");
        // larger around a point, which keeps showing the same spot of the drawing
        assertEquals("2 -100 -200", eval("(() => { const z = zoomView(v, 2, 100, 400); return [z.scale, z.x, z.y].join(' '); })()"));
        assertEquals("true", eval("(() => { const z = zoomView(v, 2, 100, 400); return (100 - z.x) / z.scale === (100 - v.x) / v.scale && (400 - z.y) / z.scale === (400 - v.y) / v.scale; })()"));
        // never smaller than it began nor larger than eight times that
        assertEquals("1 0 100", eval("(() => { const z = zoomView(v, 0.2, 100, 400); return [z.scale, z.x, z.y].join(' '); })()"));
        assertEquals("8", eval("zoomView(zoomView(v, 6, 0, 0), 6, 0, 0).scale"));
        // moved, it leaves no gap at its edges, and a side that fits stays in the middle
        eval("var z = zoomView(v, 2, 200, 400)");
        assertEquals("-200 -200", eval("z.x + ' ' + z.y"));
        assertEquals("0 0", eval("(() => { const m = moveView(z, 900, 900); return m.x + ' ' + m.y; })()"));
        assertEquals("-400 -400", eval("(() => { const m = moveView(z, -900, -900); return m.x + ' ' + m.y; })()"));
        assertEquals("-150 -230", eval("(() => { const m = moveView(z, 50, -30); return m.x + ' ' + m.y; })()"));
        assertEquals("0 100", eval("(() => { const m = moveView(v, 50, -30); return m.x + ' ' + m.y; })()"));
    }

    @Test
    void theChartOpensFullScreenFromItsButtonAndClosesWithEscape() {
        load(VIEWER);
        eval("""
            var opener = elements['chart-zoom'];
            click('#chart-zoom');
            """);
        assertEquals("false", eval("String(elements['chart-viewer'].hidden)"));
        assertEquals("true", eval("document.activeElement === elements['chart-viewer-close']"));
        // a copy of the drawing, the page keeps its own
        assertEquals("copy 400px 600px translate(0px, 100px) scale(1)", eval("copy().id + ' ' + copy().style.width + ' ' + copy().style.height + ' ' + copy().style.transform"));
        // the focus stays with it
        eval("opener.focus(); document.dispatch('focusin', {target: opener});");
        assertEquals("true", eval("document.activeElement === elements['chart-viewer-close']"));
        eval("elements['chart-viewer'].appendChild(elements['chart-viewer-in']); elements['chart-viewer-in'].focus(); document.dispatch('focusin', {target: elements['chart-viewer-in']});");
        assertEquals("true", eval("document.activeElement === elements['chart-viewer-in']"));
        // a turned phone gives it another room
        eval("elements['chart-viewer-stage'].clientWidth = 800; elements['chart-viewer-stage'].clientHeight = 400; window.dispatch('resize', {});");
        assertEquals("400px 600px translate(200px, 0px) scale(1)", eval("copy().style.width + ' ' + copy().style.height + ' ' + copy().style.transform"));
        // larger, it keeps its size and what was in its middle
        eval("click('#chart-viewer-in'); click('#chart-viewer-in'); finger('pointerdown', 1, 500, 200); finger('pointermove', 1, 400, 150); finger('pointerup', 1, 400, 150);");
        assertEquals("2.25 -100 -300", eval("view()"));
        eval("elements['chart-viewer-stage'].clientWidth = 400; elements['chart-viewer-stage'].clientHeight = 800; window.dispatch('resize', {});");
        assertEquals("2.25 -300 -100 400 800", eval("view() + ' ' + chart_view.roomWidth + ' ' + chart_view.roomHeight"));
        assertTrue(eval("copy().style.transform").startsWith("translate(-300px, -"), eval("copy().style.transform"));
        eval("resetChartView()");
        // Escape closes the card of a tapped part first, then the chart, and gives the focus back
        eval("elements['explore-card'] = new FakeElement('explore-card'); elements['explore-card'].hidden = false; press('Escape');");
        assertEquals("false false true", eval("elements['chart-viewer'].hidden + ' ' + (chart_view === null) + ' ' + elements['explore-card'].hidden"));
        eval("press('Escape')");
        assertEquals("true true", eval("elements['chart-viewer'].hidden + ' ' + (chart_view === null)"));
        assertEquals("0", eval("elements['chart-viewer-stage'].children.length"));
        assertEquals("true", eval("document.activeElement === opener"));
        // its own button closes it too, and a page without a drawing has nothing to show
        eval("click('#chart-zoom'); click('#chart-viewer-close');");
        assertEquals("true", eval("String(elements['chart-viewer'].hidden)"));
        eval("delete elements['svg-image svg']; click('#chart-zoom');");
        assertEquals("true null", eval("elements['chart-viewer'].hidden + ' ' + chart_view"));
        // nothing of it listens while it's closed
        assertDoesNotThrow(() -> eval("finger('pointerdown', 1, 10, 10); finger('pointermove', 1, 20, 20); finger('pointerup', 1, 20, 20); window.dispatch('resize', {});" +
                                      "document.dispatch('wheel', {deltaY: 100, preventDefault() { throw new Error('the page scrolls'); }});"));
    }

    @Test
    void aTapOnTheChartFullScreenShowsWhatItsPartIs() {
        load(VIEWER + ITEMS);
        eval("""
            const explainedPart = document.querySelector;
            document.querySelector = selector => explainedPart(selector) || elements[selector.slice(1)];
            explained = {'center.HEAD': item('center.HEAD', 'Head Open'), 'center.SACRAL': item('center.SACRAL', 'Sacral Defined'), 'wheel.zodiac': item('wheel.zodiac', 'Zodiac')};
            var part = parts => ({dataset: {explore: parts}, closest: function () { return this; }});
            var head = part('center.HEAD centers'), sacral = part('center.SACRAL centers');
            var under = [];
            document.elementsFromPoint = () => under;
            var touch = (x, y, time) => { finger('pointerdown', 1, x, y); finger('pointerup', 1, x, y, time); };
            var told = (x, y) => document.dispatch('click', {target: on('#chart-viewer-stage'), clientX: x, clientY: y});
            var card = elements['explore-card'];
            card.hidden = true;
            var shown = () => card.hidden ? 'none' : (/<p>([^<]+) text<.p>/.exec(elements['explore-card-content'].innerHTML) || [])[1];
            var around = new FakeElement('content'), footer = new FakeElement('site-footer');
            around.children = [elements['explore'] = new FakeElement('explore'), footer];
            card.parentNode = around;
            card.nextSibling = footer;
            click('#chart-zoom');
            copy().children.push(head);
            """);
        // the card comes along, the browser's own full screen shows nothing else of the page
        assertEquals("true", eval("String(elements['chart-viewer'].children.includes(card))"));
        eval("under = [head]; touch(200, 300, 1000); told(200, 300);");
        assertEquals("Head Open", eval("shown()"));
        // a part of the page's own drawing below isn't the chart's, the place in the wheel tells what's there
        eval("under = [sacral]; touch(200, 110, 2000); told(200, 110);");
        assertEquals("Zodiac", eval("shown()"));
        assertEquals("{\"x\":480,\"y\":24}", eval("JSON.stringify(viewedSpot(chart_view, 200, 110, 960))"));
        assertEquals("{\"x\":360,\"y\":420}", eval("JSON.stringify(viewedSpot(zoomView(chart_view, 2, 0, 100), 300, 350, 960))"));
        // moving the chart shows nothing, and neither does a click that no finger made
        eval("""
            hideExploreCard(); under = [head];
            finger('pointerdown', 1, 200, 300); finger('pointermove', 1, 260, 380); finger('pointerup', 1, 260, 380, 3000); told(260, 380);
            told(200, 300);
            """);
        assertEquals("none", eval("shown()"));
        // a second tap takes the card of the first away and makes the chart larger
        eval("touch(200, 300, 4000); told(200, 300);");
        assertEquals("Head Open", eval("shown()"));
        eval("touch(203, 300, 4200); told(203, 300);");
        assertEquals("none 3", eval("shown() + ' ' + chart_view.scale"));
        // closing the chart closes the card and puts it back where it was on the page
        eval("touch(200, 300, 6000); told(200, 300);");
        assertEquals("Head Open", eval("shown()"));
        eval("click('#chart-viewer-close')");
        assertEquals("none explore explore-card site-footer", eval("shown() + ' ' + around.children.map(each => each.id).join(' ')"));
        // and back on the page, a tap on its own drawing is the one that counts
        eval("elements['svg-image'].querySelector = () => ({contains: () => true}); explorePart({target: sacral, clientX: 0, clientY: 0});");
        assertEquals("Sacral Defined", eval("shown()"));
    }

    @Test
    void theBrowsersOwnFullScreenClosesTheChartWhenItIsLeft() {
        load(VIEWER);
        eval("""
            var asked = 0, left = 0;
            elements['chart-viewer'].requestFullscreen = () => { asked++; document.fullscreenElement = elements['chart-viewer']; return {then: () => ({catch: () => {}})}; };
            document.exitFullscreen = () => { left++; document.fullscreenElement = null; return {catch: () => ({then: () => {}})}; };
            click('#chart-zoom');
            """);
        assertEquals("1", eval("asked"));
        // entering it changes nothing
        eval("document.dispatch('fullscreenchange', {})");
        assertEquals("false", eval("String(elements['chart-viewer'].hidden)"));
        // the button of the chart leaves it
        eval("click('#chart-viewer-close')");
        assertEquals("true 1", eval("elements['chart-viewer'].hidden + ' ' + left"));
        // and leaving it with the browser's own key closes the chart
        eval("click('#chart-zoom'); document.fullscreenElement = null; document.dispatch('fullscreenchange', {});");
        assertEquals("true 1", eval("elements['chart-viewer'].hidden + ' ' + left"));
        // a chart that's closed before the browser got to its full screen leaves it once it's there
        eval("""
            var grant;
            elements['chart-viewer'].requestFullscreen = () => new Promise(resolve => grant = resolve);
            click('#chart-zoom');
            click('#chart-viewer-close');
            """);
        assertEquals("1", eval("left"));
        eval("document.fullscreenElement = elements['chart-viewer']; grant();");
        assertEquals("2 null", eval("left + ' ' + document.fullscreenElement"));
        // one that's still open stays in it
        eval("click('#chart-zoom')");
        eval("document.fullscreenElement = elements['chart-viewer']; grant();");
        assertEquals("2 false", eval("left + ' ' + elements['chart-viewer'].hidden"));
    }

    @Test
    void fingersTheWheelAndTheButtonsMoveTheChartAndMakeItLarger() {
        load(VIEWER);
        eval("click('#chart-zoom')");
        assertEquals("1 0 100", eval("view()"));
        // the buttons, around the middle of the screen
        eval("click('#chart-viewer-in')");
        assertEquals("1.5 -100 -50", eval("view()"));
        assertEquals("translate(-100px, -50px) scale(1.5)", eval("copy().style.transform"));
        eval("click('#chart-viewer-out')");
        assertEquals("1 0 100", eval("view()"));
        // two fingers that move apart, around the point between them
        eval("""
            finger('pointerdown', 1, 150, 400); finger('pointerdown', 2, 250, 400);
            finger('pointermove', 1, 100, 400); finger('pointermove', 2, 300, 400);
            """);
        assertEquals("2 -200 -200", eval("view()"));
        // and that move together carry the chart along
        eval("finger('pointermove', 1, 100, 430); finger('pointermove', 2, 300, 430);");
        assertEquals("2 -200 -170", eval("view()"));
        eval("finger('pointerup', 2, 300, 430)");
        // one finger moves it
        eval("finger('pointermove', 1, 160, 400)");
        assertEquals("2 -140 -200", eval("view()"));
        assertEquals("translate(-140px, -200px) scale(2)", eval("copy().style.transform"));
        eval("finger('pointerup', 1, 160, 400)");
        // a finger that went down next to the chart isn't followed
        eval("document.dispatch('pointerdown', {pointerId: 3, clientX: 10, clientY: 10, target: on('#chart-viewer-close')}); finger('pointermove', 3, 90, 90);");
        assertEquals("2 -140 -200", eval("view()"));
        // a second tap on the same spot brings it back to how it began, and then makes it larger there
        eval("finger('pointerdown', 1, 100, 300); finger('pointerup', 1, 100, 300, 1000); finger('pointerdown', 1, 104, 300); finger('pointerup', 1, 104, 300, 1200);");
        assertEquals("1 0 100", eval("view()"));
        eval("finger('pointerdown', 1, 100, 300); finger('pointerup', 1, 100, 300, 2000); finger('pointerdown', 1, 104, 300); finger('pointerup', 1, 104, 300, 2200);");
        assertEquals("3 -208 -300", eval("view()"));
        // taps far apart, long after each other or at the end of a move are each a first tap
        eval("resetChartView(); finger('pointerdown', 1, 100, 300); finger('pointerup', 1, 100, 300, 3000); finger('pointerdown', 1, 200, 300); finger('pointerup', 1, 200, 300, 3100);");
        assertEquals("1 0 100", eval("view()"));
        eval("finger('pointerdown', 1, 200, 300); finger('pointerup', 1, 200, 300, 3600);");
        assertEquals("1 0 100", eval("view()"));
        eval("finger('pointerdown', 1, 200, 300); finger('pointermove', 1, 240, 300); finger('pointermove', 1, 200, 300); finger('pointerup', 1, 200, 300, 3700);");
        assertEquals("1 0 100", eval("view()"));
        // the wheel of a mouse, around its pointer, without scrolling the page behind
        eval("var scrolled = true; document.dispatch('wheel', {deltaY: -Math.log(2) / 0.002, clientX: 100, clientY: 400, preventDefault() { scrolled = false; }});");
        assertEquals("2 -100 -200 false", eval("view() + ' ' + scrolled"));
        // the main button of a mouse moves it, the others don't, and neither does a mouse whose button was let go unseen
        eval("""
            var mouse = (type, x, y, more) => document.dispatch(type, Object.assign({type, pointerId: 9, pointerType: 'mouse', clientX: x, clientY: y, timeStamp: 9000, target: on('#chart-viewer-stage')}, more));
            var captured = [];
            elements['chart-viewer-stage'].setPointerCapture = id => captured.push(id);
            mouse('pointerdown', 100, 400, {button: 2, buttons: 2}); mouse('pointermove', 150, 400, {buttons: 2});
            """);
        assertEquals("2 -100 -200 ", eval("view() + ' ' + captured"));
        eval("mouse('pointerdown', 100, 400, {button: 0, buttons: 1}); mouse('pointermove', 150, 410, {buttons: 1});");
        assertEquals("2 -50 -190 9", eval("view() + ' ' + captured"));
        eval("mouse('pointermove', 200, 410, {buttons: 0}); mouse('pointermove', 250, 410, {buttons: 1});");
        assertEquals("2 -50 -190", eval("view()"));
        // the arrows move it and + and - make it larger and smaller, without scrolling the page either
        eval("""
            var keys = 0;
            var key = name => document.dispatch('keydown', {key: name, preventDefault() { keys++; }, stopPropagation() {}});
            key('ArrowRight'); key('ArrowDown');
            """);
        assertEquals("2 -110 -250 2", eval("view() + ' ' + keys"));
        eval("key('ArrowLeft'); key('ArrowUp'); key('+'); key('=');");
        assertEquals("4.5 -362.5 -927.5 6", eval("view() + ' ' + keys"));
        eval("key('-'); key('-'); key('a');");
        assertEquals("2 -50 -190 8", eval("view() + ' ' + keys"));
        // with Ctrl, Alt or Cmd they stay the browser's
        eval("""
            for (const held of ['ctrlKey', 'altKey', 'metaKey']) {
                for (const name of ['+', '-', 'ArrowLeft']) document.dispatch('keydown', {key: name, [held]: true, preventDefault() { keys++; }, stopPropagation() {}});
            }
            """);
        assertEquals("2 -50 -190 8", eval("view() + ' ' + keys"));
        // and nothing while the chart isn't full screen
        eval("click('#chart-viewer-close'); key('ArrowRight'); key('+');");
        assertEquals("8", eval("keys"));
    }

    @Test
    void onlyADrawingThatIsThereCanBeShownFullScreen() {
        load(VIEWER);
        eval("showChart('http://x/wheel/image/202403011000/2643743')");
        assertEquals("true", eval("String(elements['chart-zoom'].hidden)"));
        eval("fetches[0].answer('<svg></svg>')");
        assertEquals("false", eval("String(elements['chart-zoom'].hidden)"));
        eval("showChart('http://x/wheel/image/202403021000/2643743'); fetches[1].answer('', false);");
        assertEquals("true", eval("String(elements['chart-zoom'].hidden)"));
        eval("showChart('http://x/wheel/image/202403031000/2643743'); fetches[2].answer('<svg></svg>');");
        assertEquals("false", eval("String(elements['chart-zoom'].hidden)"));
        eval("showChart(null)");
        assertEquals("true", eval("String(elements['chart-zoom'].hidden)"));
        eval("chart_type = ''; showChart('http://x/wheel/image/202403031000/2643743'); fetches[3].answer('<svg></svg>');");
        assertEquals("false", eval("String(elements['chart-zoom'].hidden)"));
        eval("chart_type = 'table'; showChart('http://x/range/table/202403011000/2643743'); fetches[4].answer('<div></div>');");
        assertEquals("true", eval("String(elements['chart-zoom'].hidden)"));
    }

    @Test
    void aTableIsShownLikeAChartButIsntAnImageToSave() {
        load("elements['chart-download'] = new FakeElement('chart-download');");
        eval("showChart('http://x/wheel/image/202403011000/2643743')");
        assertEquals("http://x/wheel/image/202403011000/2643743", eval("elements['chart-download'].href"));
        eval("chart_type = 'table'; showChart('http://x/range/table/202403011000/2643743'); fetches[1].answer('<div id=\"range-table\"></div>');");
        assertEquals("http://x/range/table/202403011000/2643743", eval("fetches[1].url"));
        assertEquals("false", eval("'href' in elements['chart-download']"));
        assertEquals("<div id=\"range-table\"></div>", eval("elements['svg-image'].innerHTML"));
    }

    @Test
    void rangeTablesAreExploredFromTheirOwnRoute() {
        load(EXPLORE);
        eval("showChart('http://x/range/table/d/202403011000/2643743')");
        assertEquals("http://x/range/explore/d/202403011000/2643743", eval("explores()[0].url"));
        eval("showChart('http://x/elsewhere/image')");
        assertEquals("1", eval("explores().length"));
    }

    // panel items as the card reads them, by the label of their summary
    private static final String ITEMS = """
        var item = (part, label) => ({
            dataset: {part: part},
            querySelector: selector => selector === 'summary' ? {innerHTML: '<span class="explore-label">' + label + '</span>'} : {textContent: label},
            querySelectorAll: () => [{outerHTML: '<p>' + label + ' text</p>'}]
        });
        var explained = {};
        document.querySelector = selector => {
            const part = /data-part="([^"]+)"/.exec(selector);
            return part ? explained[part[1]] || null : null;
        };
        elements['explore-card'] = new FakeElement('explore-card');
        elements['explore-card-content'] = new FakeElement('explore-card-content');
        """;

    @Test
    void openItemsAndTheCardFollowTheNextChart() {
        load(EXPLORE + ITEMS);
        eval("""
            explained = {'type': item('type', 'Type'), 'center.HEAD': item('center.HEAD', 'Head Open')};
            elements['explore'].innerHTML = 'the panel before';
            const opened = explained['type'];
            elements['explore'].querySelectorAll = selector => selector === 'details[data-part][open]' ? [opened] : [];
            elements['explore-card'].hidden = true;
            showExploreCard([explained['center.HEAD']]);
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            showDate('1975-06-21T14:30', 'http://x/wheel/image/birth/');
            """);
        // the panel and the card of the chart before stay until the next ones are there
        assertEquals("the panel before", eval("elements['explore'].innerHTML"));
        assertEquals("false", eval("String(elements['explore-card'].hidden)"));
        eval("""
            explained = {'type': item('type', 'Type'), 'center.HEAD': item('center.HEAD', 'Head Defined')};
            explores()[0].answer('the next panel');
            """);
        assertEquals("the next panel", eval("elements['explore'].innerHTML"));
        assertEquals("true", eval("String(explained['type'].open)"));
        assertEquals("undefined", eval("String(explained['center.HEAD'].open)"));
        assertEquals("false", eval("String(elements['explore-card'].hidden)"));
        assertTrue(eval("elements['explore-card-content'].innerHTML").contains("<p>Head Defined text</p>"));

        // a panel without the part that the card shows closes it
        eval("""
            elements['explore'].querySelectorAll = () => [];
            showDate('1975-06-21T14:31', 'http://x/wheel/image/birth/');
            explained = {'type': item('type', 'Type')};
            explores()[1].answer('a panel without centers');
            """);
        assertEquals("true", eval("String(elements['explore-card'].hidden)"));

        // and so does a chart without a panel, which is emptied at once
        eval("""
            explained = {'type': item('type', 'Type')};
            showExploreCard([explained['type']]);
            explained = {};
            showChart(null);
            """);
        assertEquals("", eval("elements['explore'].innerHTML"));
        assertEquals("true", eval("String(elements['explore-card'].hidden)"));
    }

    @Test
    void aTapOpensEveryPartOfTheSameKind() {
        load(ITEMS);
        eval("""
            explained = {'activation.1.MOON': item('activation.1.MOON', 'Transit Moon'), 'activation.4.VENUS': item('activation.4.VENUS', 'Design Venus'),
                         'wheel.gates': item('wheel.gates', 'Gates'), 'channel.10-34': item('channel.10-34', '10-34 Exploration'),
                         'channel.10-57': item('channel.10-57', '10-57 Perfected Form'), 'channels': item('channels', 'Channels')};
            """);
        assertEquals("activation.1.MOON activation.4.VENUS",
            eval("exploreItems('activation.1.MOON activation.3.SUN activation.4.VENUS wheel.gates').map(i => i.dataset.part).join(' ')"));
        assertEquals("channel.10-34 channel.10-57",
            eval("exploreItems('channel.10-20 channel.10-34 channel.10-57 channels').map(i => i.dataset.part).join(' ')"));
        // the general description only when the panel has none of the parts
        assertEquals("wheel.gates", eval("exploreItems('activation.2.SUN wheel.gates').map(i => i.dataset.part).join(' ')"));
    }

    @Test
    void chipsStepThroughThePartsOfATap() {
        load(ITEMS);
        eval("showExploreCard([item('activation.1.MOON', 'Transit Moon'), item('activation.4.VENUS', 'Design <Venus>')])");
        var html = eval("elements['explore-card-content'].innerHTML");
        assertTrue(html.contains("data-index=\"0\" aria-pressed=\"true\">Transit Moon</button>"), html);
        assertTrue(html.contains("data-index=\"1\" aria-pressed=\"false\">Design &#60;Venus&#62;</button>"), html);
        assertTrue(html.contains("<p>Transit Moon text</p>"), html);
        eval("showCardItem(1)");
        html = eval("elements['explore-card-content'].innerHTML");
        assertTrue(html.contains("data-index=\"1\" aria-pressed=\"true\""), html);
        assertTrue(html.contains("<p>Design <Venus> text</p>"), html);
        // the chip that switched the card keeps the focus, after the chips are drawn again
        eval("""
            var focused = null;
            document.activeElement = {classList: {contains: name => name === 'explore-card-chip'}};
            elements['explore-card-content'].querySelector = selector => ({focus: () => focused = selector});
            showCardItem(0);
            """);
        assertEquals(".explore-card-chip[data-index=\"0\"]", eval("focused"));
        eval("document.activeElement = null");
        // a single part has no chips
        eval("showExploreCard([item('type', 'Type')])");
        assertFalse(eval("elements['explore-card-content'].innerHTML").contains("explore-card-chip"));
    }

    @Test
    void aTapFallsBackToTheGeneralPart() {
        load("""
            var explained = {'channels': 'all channels', 'channel.10-34': 'exploration'};
            document.querySelector = selector => {
                const part = /data-part="([^"]+)"/.exec(selector);
                return part ? explained[part[1]] || null : null;
            };
            """);
        assertEquals("exploration", eval("exploreItems('channel.10-20 channel.10-34 channel.10-57 channels').join()"));
        assertEquals("all channels", eval("exploreItems('channel.1-8 channels').join()"));
        assertEquals("", eval("exploreItems('center.HEAD centers').join()"));
        assertEquals("", eval("exploreItems(null).join()"));
    }

    // a tap on the drawn chart, over the parts that the point has under it, topmost first
    private static final String TAP = ITEMS + """
        var Node = {DOCUMENT_POSITION_FOLLOWING: 4};
        var ordered = (part, label, order) => Object.assign(item(part, label), {
            order: order,
            compareDocumentPosition: other => other.order > order ? 4 : 2
        });
        var tagged = parts => ({dataset: {explore: parts}, closest: function () { return this; }});
        var svg = {contains: () => true, getScreenCTM: () => ({inverse: () => null})};
        elements['svg-image'].querySelector = () => svg;
        var tap = (...parts) => {
            const under = parts.map(tagged);
            document.elementsFromPoint = () => under;
            explorePart({target: under[0], clientX: 0, clientY: 0});
        };
        var chips = () => (elements['explore-card-content'].innerHTML.match(/>[^<>]+<\\/button>/g) || []).join(' ');
        """;

    @Test
    void overlappingPlanetsAreAllReachedInThePanelsOrder() {
        load(TAP);
        eval("""
            explained = {'activation.1.SUN': ordered('activation.1.SUN', 'Sun', 1), 'activation.1.SATURN': ordered('activation.1.SATURN', 'Saturn', 9),
                         'wheel.gates': ordered('wheel.gates', 'Gates', 0), 'wheel.planets': ordered('wheel.planets', 'Planets', 0)};
            tap('activation.1.SATURN wheel.planets', 'activation.1.SUN wheel.planets');
            """);
        assertEquals(">Sun</button> >Saturn</button>", eval("chips()"));
        // a gate without planets keeps to its own description, whatever planet is drawn next to it
        eval("tap('wheel.gates', 'activation.1.SUN wheel.planets')");
        assertEquals("", eval("chips()"));
        assertTrue(eval("elements['explore-card-content'].innerHTML").contains("Gates text"));

        // a click on a chip shows its part
        eval("""
            tap('activation.1.SATURN wheel.planets', 'activation.1.SUN wheel.planets');
            document.dispatch('click', {target: {closest: selector => selector === '.explore-card-chip' ? {dataset: {index: '1'}} : null}});
            """);
        assertTrue(eval("elements['explore-card-content'].innerHTML").contains("Saturn text"));
    }

    @Test
    void theCardTakesTheFocusAndGivesItBack() {
        load(ITEMS);
        eval("""
            elements['explore-card'].hidden = true;
            var opener = Object.assign(new FakeElement('cell'), {isConnected: true});
            document.activeElement = opener;
            showExploreCard([item('type', 'Type')]);
            """);
        assertEquals("true", eval("document.activeElement === elements['explore-card'] && elements['explore-card'].focusOptions.preventScroll"));
        // another tap while the card is open keeps what to go back to
        eval("showExploreCard([item('profile', 'Profile')]); document.dispatch('keydown', {key: 'Escape'})");
        assertEquals("true", eval("elements['explore-card'].hidden && document.activeElement === opener"));
        // a card closed while the focus is elsewhere leaves it there
        eval("""
            showExploreCard([item('type', 'Type')]);
            var elsewhere = new FakeElement('elsewhere');
            document.activeElement = elsewhere;
            document.dispatch('keydown', {key: 'Escape'});
            """);
        assertEquals("true", eval("document.activeElement === elsewhere"));
    }

    @Test
    void aTapOnTheSummaryBelowTheWheelOpensItsCard() {
        load(TAP);
        eval("""
            explained = {'activation.1.SUN': ordered('activation.1.SUN', 'Personality Sun', 20),
                         'activation.2.SUN': ordered('activation.2.SUN', 'Design Sun', 30),
                         'wheel.rows': item('wheel.rows', 'Rows'),
                         'type': item('type', 'Type')};
            var tap = (place, parts) => document.dispatch('click', {target: {closest: s => s.split(', ').includes(place + ' [data-explore]') ? {dataset: {explore: parts}} : null}});
            var summary = parts => tap('#chart-summary', parts);
            summary('activation.1.SUN activation.2.SUN wheel.rows');
            """);
        var html = eval("elements['explore-card-content'].innerHTML");
        assertTrue(html.contains("<p>Personality Sun text</p>"), html);
        assertTrue(html.contains("data-index=\"1\" aria-pressed=\"false\">Design Sun</button>"), html);
        assertFalse(html.contains("Rows"), html);

        eval("summary('type')");
        html = eval("elements['explore-card-content'].innerHTML");
        assertTrue(html.contains("<p>Type text</p>"), html);
        assertEquals("false", eval("String(elements['explore-card'].hidden)"));

        // the table of a range that phones show as text
        eval("hideExploreCard(); tap('#range-table', 'wheel.rows');");
        assertTrue(eval("elements['explore-card-content'].innerHTML").contains("<p>Rows text</p>"));
        assertEquals("false", eval("String(elements['explore-card'].hidden)"));
    }

    @Test
    void relatedLinksStepToTheirPartsAndBack() {
        load(TAP);
        eval("""
            var focused = null;
            const panelQuery = document.querySelector;
            document.querySelector = selector => selector.startsWith('#explore-card ') ? {focus: () => focused = selector} : panelQuery(selector);
            var related = (part, label, order, parts) => Object.assign(ordered(part, label, order), {dataset: {part: part, related: parts}});
            explained = {'activation.1.SUN': related('activation.1.SUN', 'Personality Sun', 20, 'center.SOLAR channel.39-55 activation.2.MARS activation.1.SATURN channel.37-40'),
                         'activation.1.SATURN': ordered('activation.1.SATURN', 'Personality Saturn', 21),
                         'activation.2.MARS': ordered('activation.2.MARS', 'Design Mars', 30),
                         'center.SOLAR': ordered('center.SOLAR', 'Solar <Plexus>', 5),
                         'channel.39-55': ordered('channel.39-55', 'Emoting', 10)};
            var click = (selector, target) => document.dispatch('click', {target: {closest: s => s === selector ? target : null}});
            tap('activation.1.SATURN wheel.planets', 'activation.1.SUN wheel.planets');
            """);
        var html = eval("elements['explore-card-content'].innerHTML");
        // in the order of the panel, without the parts that aren't in it or that a chip shows
        assertTrue(html.endsWith("<div class=\"explore-card-related\"><p class=\"explore-card-related-title\" id=\"explore-card-related-title\">Related</p><div class=\"explore-card-chips\" role=\"group\" aria-labelledby=\"explore-card-related-title\">" +
                                 "<button type=\"button\" class=\"explore-card-link\" data-part=\"center.SOLAR\">Solar &#60;Plexus&#62;</button>" +
                                 "<button type=\"button\" class=\"explore-card-link\" data-part=\"channel.39-55\">Emoting</button>" +
                                 "<button type=\"button\" class=\"explore-card-link\" data-part=\"activation.2.MARS\">Design Mars</button></div></div>"), html);
        assertFalse(html.contains("explore-card-back"), html);

        eval("click('.explore-card-link', {dataset: {part: 'channel.39-55'}})");
        html = eval("elements['explore-card-content'].innerHTML");
        assertTrue(html.startsWith("<button type=\"button\" class=\"explore-card-back\">&lsaquo; Back</button><p class=\"explore-card-title\">"), html);
        assertTrue(html.contains("<p>Emoting text</p>"), html);
        assertEquals("#explore-card .explore-card-back", eval("focused"));

        eval("click('.explore-card-back', {})");
        html = eval("elements['explore-card-content'].innerHTML");
        assertTrue(html.contains("<p>Personality Sun text</p>"), html);
        assertTrue(html.contains("data-index=\"0\" aria-pressed=\"true\">Personality Sun</button>"), html);
        assertFalse(html.contains("explore-card-back"), html);
        assertEquals("#explore-card .explore-card-link[data-part=\"channel.39-55\"]", eval("focused"));

        // a new tap starts over
        eval("click('.explore-card-link', {dataset: {part: 'center.SOLAR'}}); tap('activation.1.SATURN wheel.planets')");
        assertFalse(eval("elements['explore-card-content'].innerHTML").contains("explore-card-back"));
    }

    @Test
    void aSharedLinkShowsItsChartWithoutChangingWhatTheVisitorSaved() {
        load("""
            elements['chart-share'] = new FakeElement('chart-share');
            window.location = {origin: 'http://x', pathname: '/wheel/birth',
                hash: '#share&datetime=1955-02-24T19%3A15&location=5391959&city=San+Francisco%2C+California&name=Steve+Jobs'};
            """);
        eval("""
            HdStore.update(window.localStorage, d => HdStore.setBirthDatetime(d, '1980-01-02T03:04', 'birth'));
            chart_type = 'birth';
            prepareBirthDateForElement('datetime');
            prepareSharing();
            showDate(getFieldDate(), 'http://x/wheel/image/birth/');
            showDate(getDateOffset(60), 'http://x/wheel/image/birth/');
            """);
        assertEquals("1955-02-24T19:16", eval("elements['datetime'].value"));
        assertEquals("San Francisco, California", eval("elements['location'].value"));
        // the first chart keeps the name, the server doesn't remember the city, the visitor's birth is untouched
        assertEquals("http://x/wheel/image/birth/195502241915/5391959?shared=1&name=Steve%20Jobs", eval("fetches[0].url"));
        // a minute later it's no longer the shared chart, like a loaded one that's changed
        assertEquals("http://x/wheel/image/birth/195502241916/5391959?shared=1", eval("fetches[1].url"));
        assertEquals("1980-01-02T03:04", eval("HdStore.load(window.localStorage).birth.datetime"));
        assertEquals("false", eval("String(elements['chart-share'].hidden)"));
    }

    // the third row of the connection transit chart
    private static final String THIRD_ROW = """
        for (const id of ['third-datetime', 'third-location', 'third-location-id']) {
            elements[id] = new FakeElement(id);
        }
        """;

    @Test
    void connectionTransitsDrawTheTransitsWithBothPeople() {
        load(THIRD_ROW);
        eval("""
            HdStore.update(window.localStorage, d => {
                HdStore.putChart(d, 'Steve Jobs', {datetime: '1955-02-24T19:15', locationId: '5391959', locationCity: 'San Francisco'});
                HdStore.putChart(d, 'Steve Wozniak', {datetime: '1950-08-11T09:45', locationId: '5392171', locationCity: 'San Jose'});
            });
            chart_type = 'connection-transit';
            elements['location-id'].value = '2643743';
            elements['datetime'].value = '2024-03-01T10:00';
            displaySavedEntries('http://x/wheel/image/connection-transit/', 'second');
            loadEntry('http://x/wheel/image/connection-transit/', 'Steve Jobs');
            """);
        // the transits are only drawn once both people have a date and a city
        assertEquals("http://x/wheel/image/connection-transit", eval("elements['svg-image'].dataset.src"));
        eval("""
            displaySavedEntries('http://x/wheel/image/connection-transit/', 'third');
            loadEntry('http://x/wheel/image/connection-transit/', 'Steve Wozniak');
            """);
        assertEquals("http://x/wheel/image/connection-transit/195502241915/5391959/195008110945/5392171/202403011000/2643743?name=Steve%20Jobs&secondName=Steve%20Wozniak",
            eval("elements['svg-image'].dataset.src"));
        assertEquals("1955-02-24T19:15", eval("HdStore.load(window.localStorage).birth.datetime"));
        assertEquals("1950-08-11T09:45", eval("HdStore.load(window.localStorage).partner.datetime"));
        assertEquals("Steve Wozniak", eval("HdStore.load(window.localStorage).namedPartner.name"));

        // a date typed in the third row is the other person's
        eval("elements['third-datetime'].value = '1950-08-11T10:45'; dateTimeChosen(elements['third-datetime'])");
        assertEquals("1950-08-11T10:45", eval("HdStore.load(window.localStorage).partner.datetime"));
        assertEquals("1955-02-24T19:15", eval("HdStore.load(window.localStorage).birth.datetime"));
    }

    @Test
    void aConnectionTransitShareLinkCarriesThreeRows() {
        load(THIRD_ROW + """
            window.location = {origin: 'http://x', pathname: '/wheel/connection-transit', hash: ''};
            elements['datetime'].value = '2024-03-01T10:00';
            elements['location-id'].value = '2643743';
            elements['location-id'].dataset.city = 'London';
            elements['second-datetime'].value = '1955-02-24T19:15';
            elements['second-location-id'].value = '5391959';
            elements['second-location-id'].dataset.city = 'San Francisco';
            elements['third-datetime'].value = '1950-08-11T09:45';
            elements['third-location-id'].value = '5392171';
            elements['third-location-id'].dataset.city = 'San Jose';
            """);
        eval("chart_type = 'connection-transit'; prepareSharing()");
        var url = eval("shareUrl()");
        assertEquals("http://x/wheel/connection-transit#share&datetime=2024-03-01T10%3A00&location=2643743&city=London"
            + "&secondDatetime=1955-02-24T19%3A15&secondLocation=5391959&secondCity=San+Francisco"
            + "&thirdDatetime=1950-08-11T09%3A45&thirdLocation=5392171&thirdCity=San+Jose", url);
        // an app that shows the pages shares where the chart is on the web
        eval("share_root = 'https://charts.example.com'");
        assertEquals(url.replace("http://x/", "https://charts.example.com/"), eval("shareUrl()"));

        load(THIRD_ROW + "window.location = {origin: 'http://x', pathname: '/wheel/connection-transit', hash: '" + url.substring(url.indexOf('#')) + "'};");
        eval("chart_type = 'connection-transit'; prepareSharing()");
        assertEquals("1950-08-11T09:45", eval("elements['third-datetime'].value"));
        assertEquals("5392171", eval("elements['third-location-id'].value"));
        assertEquals("San Jose", eval("elements['third-location'].value"));
        assertEquals("1955-02-24T19:15", eval("elements['second-datetime'].value"));
    }

    @Test
    void aShareLinkCarriesTheChartsOfThePage() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel/connection', hash: ''};
            elements['second-datetime'].value = '1950-08-11T09:45';
            elements['second-location-id'].value = '5391959';
            elements['second-location-id'].dataset.city = 'San Francisco';
            elements['datetime'].value = '1955-02-24T19:15';
            elements['location-id'].value = '5391959';
            elements['location-id'].dataset.city = 'San Francisco';
            """);
        eval("chart_type = 'connection'; prepareSharing()");
        var url = eval("shareUrl()");
        assertEquals("http://x/wheel/connection#share&datetime=1955-02-24T19%3A15&location=5391959&city=San+Francisco"
            + "&secondDatetime=1950-08-11T09%3A45&secondLocation=5391959&secondCity=San+Francisco", url);

        // opening that link fills both rows
        load("window.location = {origin: 'http://x', pathname: '/wheel/connection', hash: '" + url.substring(url.indexOf('#')) + "'};");
        eval("chart_type = 'connection'; prepareSharing()");
        assertEquals("1950-08-11T09:45", eval("elements['second-datetime'].value"));
        assertEquals("5391959", eval("elements['second-location-id'].value"));
    }

    @Test
    void loadingIntoASharedViewOnlyTakesOverItsOwnRow() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel/birth-transit',
                hash: '#share&datetime=2026-01-01T12%3A00&location=1850147&city=Tokyo'};
            """);
        eval("""
            HdStore.update(window.localStorage, d => HdStore.putChart(d, 'me', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            chart_type = 'birth-transit';
            prepareSharing();
            loadEntry('http://x/wheel/image/birth-transit/', 'me');
            """);
        // the transit row is still the shared one, so the server doesn't remember Tokyo
        assertEquals("http://x/wheel/image/birth-transit/197506211430/2643743/202601011200/1850147?shared=1&name=me", eval("fetches[0].url"));
        assertEquals("1975-06-21T14:30", eval("HdStore.load(window.localStorage).birth.datetime"));
        assertEquals("null", eval("String(HdStore.load(window.localStorage).location)"));

        // choosing the city to see the transits from makes that row the visitor's
        eval("""
            prepareCitiesForElements('#location-id', '#location', 'location');
            awesompletes[0].options.replace({value: 2988507, label: 'Paris'});
            showDate(getFieldDate(), 'http://x/wheel/image/birth-transit/');
            """);
        assertEquals("2988507", eval("HdStore.load(window.localStorage).location.id"));
        assertEquals("http://x/wheel/image/birth-transit/197506211430/2643743/202601011200/2988507?name=me", eval("fetches[fetches.length - 1].url"));
    }

    @Test
    void aSharedBirthPlaceStaysTheSharedOnes() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel/birth',
                hash: '#share&datetime=1955-02-24T19%3A15&location=5391959&city=San+Francisco'};
            """);
        eval("""
            chart_type = 'birth';
            prepareSharing();
            prepareCitiesForElements('#location-id', '#location', 'birth');
            awesompletes[0].options.replace({value: 2988507, label: 'Paris'});
            """);
        assertEquals("null", eval("String(HdStore.load(window.localStorage).birth)"));
    }

    @Test
    void aSharedLinkWithoutItsTransitsKeepsTheVisitorsCity() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel/birth-transit',
                hash: '#share&secondDatetime=1955-02-24T19%3A15&secondLocation=5391959&secondCity=San+Francisco&secondName=Steve'};
            elements['datetime'].value = '2026-01-01T12:00';
            elements['location-id'].value = '1850147';
            """);
        // and still starts at the time of that city, which the server didn't know
        eval("chart_type = 'birth-transit'; prepareSharing(); showFirstChart('http://x/wheel/image/birth-transit/', '2643743')");
        assertEquals("http://x/now/1850147", eval("fetches[0].url"));
        eval("fetches[0].answer('2026-01-01T21:00')");
        assertEquals("http://x/wheel/image/birth-transit/195502241915/5391959/202601012100/1850147?name=Steve", eval("fetches[1].url"));
    }

    @Test
    void aSharedTimeWithSecondsKeepsItsName() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel/birth',
                hash: '#share&datetime=1955-02-24T19%3A15%3A00&location=5391959&city=San+Francisco&name=Steve'};
            """);
        eval("chart_type = 'birth'; prepareSharing(); showDate(getFieldDate(), 'http://x/wheel/image/birth/')");
        assertEquals("http://x/wheel/image/birth/195502241915/5391959?shared=1&name=Steve", eval("fetches[0].url"));
    }

    @Test
    void savingASharedChartMakesItTheVisitors() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel/birth',
                hash: '#share&datetime=1955-02-24T19%3A15&location=5391959&city=San+Francisco'};
            """);
        eval("""
            HdStore.update(window.localStorage, d => {
                HdStore.setBirthDatetime(d, '1980-01-02T03:04', 'birth');
                HdStore.setBirthLocation(d, '2643743', 'London', 'birth');
            });
            chart_type = 'birth';
            prepareSharing();
            image_url = 'http://x/wheel/image/birth/';
            saveAs('Steve');
            """);
        assertEquals("http://x/wheel/image/birth/195502241915/5391959?name=Steve", eval("fetches[0].url"));
        // the birth that's remembered is the saved one, its date never goes with another city
        assertEquals("{\"datetime\":\"1955-02-24T19:15\",\"location\":{\"id\":\"5391959\",\"city\":\"San Francisco\"}}",
            eval("JSON.stringify(HdStore.load(window.localStorage).birth)"));
    }

    @Test
    void aSharedLinkWithOneRowLeavesTheOtherEmpty() {
        load("""
            window.location = {origin: 'http://x', pathname: '/wheel/connection',
                hash: '#share&datetime=1955-02-24T19%3A15&location=5391959&city=San+Francisco'};
            """);
        eval("""
            HdStore.update(window.localStorage, d => HdStore.setBirthDatetime(d, '1980-01-02T03:04', 'partner'));
            chart_type = 'connection';
            prepareBirthDateForElement('second-datetime', 'partner');
            prepareSharing();
            showDate(getFieldDate(), 'http://x/wheel/image/connection/');
            """);
        assertEquals("", eval("elements['second-datetime'].value"));
        assertEquals("http://x/wheel/image/connection/195502241915/5391959?shared=1", eval("fetches[0].url"));
    }

    // a page as an app shows it, with what the app hears of it besides who was saved
    private void loadInApp(String app, String... hears) {
        load("""
            var marks = [];
            document.documentElement = {classList: {add: (...names) => marks.push(...names)}};
            var viewport = elements['eta[name=viewport]'] = new FakeElement('viewport');
            var welcome = elements['welcome'] = new FakeElement('welcome');
            welcome.shown = 0;
            welcome.showModal = () => welcome.shown++;
            var Storage = MemoryStorage;
            var heard = [];
            var written = data => typeof data === 'string' ? data : JSON.stringify(data);
            window.rife2NativeApp = {
                hears: name => ['people', '%s'].includes(name),
                tell: (name, data) => heard.push(name === 'people' ? written(data) : name + ' ' + written(data))
            };
            var said = () => { const all = heard.join(' '); heard = []; return all; };
            const add = (d, name, datetime) => HdStore.putChart(d, name, {datetime: datetime || '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'});
            """.formatted(String.join("', '", hears)), "store.js", "main.js", "app.js");
        eval("inApp('" + app + "'); document.dispatch('DOMContentLoaded')");
    }

    @Test
    void aPageInAnAppKnowsItAndIsWelcomedAsAFirstVisitIs() {
        loadInApp("mac");
        // marking the page and what a web view lacks are left to the script of RIFE2
        assertEquals("mac 0 undefined", eval("in_app + ' ' + marks.length + ' ' + viewport.content"));
        assertEquals("1 1", eval("welcome.shown + ' ' + window.localStorage.getItem('hdapp-welcomed')"));
        assertEquals("{\"saved\":[]}", eval("said()"));
    }

    @Test
    void theInstalledSiteScrollsAsAnAppDoesAndIsntWelcomed() {
        loadInApp("");
        eval("""
            window.matchMedia = query => ({matches: query === '(display-mode: standalone)'});
            window.localStorage.removeItem('hdapp-welcomed');
            welcome.shown = 0;
            inApp('');
            document.dispatch('DOMContentLoaded');
            """);
        // it's no app to the pages, which keep what they save in the browser
        assertEquals(" in-app,installed 0 1", eval("in_app + ' ' + marks.join(',') + ' ' + welcome.shown + ' ' + window.localStorage.getItem('hdapp-welcomed')"));
        // the welcome is still shown to whoever asks for it
        eval("window.location = {hash: '#welcome', pathname: '/wheel/birth'}; document.dispatch('DOMContentLoaded')");
        assertEquals("1", eval("String(welcome.shown)"));
        // an app that happens to say that it stands alone is marked by RIFE2's script alone
        eval("marks.length = 0; inApp('mac')");
        assertEquals("0", eval("String(marks.length)"));
    }

    @Test
    void aPageInABrowserIsLeftAsItIs() {
        loadInApp("");
        assertEquals(" 0 undefined 1", eval("in_app + ' ' + marks.length + ' ' + viewport.content + ' ' + welcome.shown"));
        assertEquals("", eval("HdStore.update(window.localStorage, d => add(d, 'Ada')); said()"));
    }

    @Test
    void anAppIsToldWhoThePagesSavedWithTheMomentsOfTheirBirths() {
        loadInApp("ios");
        eval("said(); HdStore.update(window.localStorage, d => { add(d, 'Kim 10'); add(d, 'ada', '1990-05-06T07:08'); add(d, 'Kim 9'); add(d, 'Bo', '2001-02-03T04:05'); add(d, 'Kim 2'); HdStore.setNamed(d, 'Kim 9', d.charts['Kim 9'], 'birth'); })");
        // the site is asked for each birth in the order of the names, and once for a birth that two people share
        assertEquals("/widget/born/199005060708/2643743", eval("fetches.map(f => f.url).join(' ')"));
        eval("fetches[0].answer('199005060508')");
        assertEquals("/widget/born/200102030405/2643743", eval("fetches.slice(1).map(f => f.url).join(' ')"));
        // who the site can't make a moment of is left out
        eval("fetches[1].answer('no such place', false)");
        assertEquals("/widget/born/197506211430/2643743", eval("fetches.slice(2).map(f => f.url).join(' ')"));
        assertEquals("", eval("said()"));
        eval("fetches[2].answer('197506211330')");
        // by name as people read names, the one whose chart is shown first
        assertEquals("{\"saved\":[{\"name\":\"Kim 9\",\"born\":\"197506211330\"},{\"name\":\"ada\",\"born\":\"199005060508\"}," +
                     "{\"name\":\"Kim 2\",\"born\":\"197506211330\"},{\"name\":\"Kim 10\",\"born\":\"197506211330\"}]}",
            eval("said()"));

        // a step of a chart changes what the pages keep and nobody who was saved
        assertEquals("", eval("HdStore.update(window.localStorage, d => HdStore.setBirthDatetime(d, '2026-11-17T10:00')); said()"));
        // someone who's removed is gone for the widgets, and the births that the site made moments of aren't asked again
        eval("HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'ada'))");
        assertEquals("{\"saved\":[{\"name\":\"Kim 9\",\"born\":\"197506211330\"},{\"name\":\"Kim 2\",\"born\":\"197506211330\"}," +
                     "{\"name\":\"Kim 10\",\"born\":\"197506211330\"}]} 3",
            eval("said() + ' ' + fetches.length"));
    }

    @Test
    void anAppIsToldOfTheLastOfTwoChangesThatFollowEachOther() {
        loadInApp("ios");
        eval("said(); HdStore.update(window.localStorage, d => add(d, 'Ada'))");
        eval("HdStore.update(window.localStorage, d => add(d, 'Bo'))");
        assertEquals("", eval("said()"));
        // the widgets hear once, of both
        eval("fetches[0].answer('197506211330')");
        assertEquals("{\"saved\":[{\"name\":\"Ada\",\"born\":\"197506211330\"},{\"name\":\"Bo\",\"born\":\"197506211330\"}]} 1", eval("said() + ' ' + fetches.length"));
    }

    @Test
    void anAppWithAPanelIsToldWhatTheCardOfATappedPartHoldsAndPressesItsButtons() {
        loadInApp("vision", "explained");
        // a page that just loaded explains nothing, so what the page before it explained goes
        assertTrue(eval("heard.join('|')").startsWith("explained |"), eval("heard.join('|')"));
        eval(ITEMS);
        eval("""
            explained = {'activation.1.MOON': item('activation.1.MOON', 'Transit Moon'), 'activation.4.VENUS': item('activation.4.VENUS', 'Design Venus'),
                         'wheel.planets': item('wheel.planets', 'Planets')};
            elements['explore-card'].hidden = true;
            said();
            showExploreCard([explained['activation.1.MOON'], explained['activation.4.VENUS']]);
            """);
        // once, with what the card holds
        var told = eval("heard.length + ' ' + said()");
        assertTrue(told.startsWith("1 explained <div class=\"explore-card-chips\""), told);
        assertTrue(told.contains("<p>Transit Moon text</p>") && !told.contains("<p>Design Venus text</p>"), told);
        assertEquals(told.substring(2), "explained " + eval("elements['explore-card-content'].innerHTML"));
        // again when it shows something else, and not when it shows the same
        told = eval("showCardItem(1); heard.length + ' ' + said()");
        assertTrue(told.startsWith("1 explained ") && told.contains("<p>Design Venus text</p>"), told);
        assertEquals("0", eval("showCardItem(1); String(heard.length)"));
        // A page that Back returns to as it was left tells what its card still holds, since the
        // page that was shown meanwhile said that nothing was explained. One that's shown for
        // the first time already told.
        assertEquals("0", eval("window.dispatch('pageshow', {persisted: false}); String(heard.length)"));
        told = eval("window.dispatch('pageshow', {persisted: true}); heard.length + ' ' + said()");
        assertTrue(told.startsWith("1 explained ") && told.contains("<p>Design Venus text</p>"), told);
        // nothing once it's closed
        assertEquals("explained ", eval("hideExploreCard(); said()"));
        assertEquals("0", eval("hideExploreCard(); String(heard.length)"));
        assertEquals("0", eval("window.dispatch('pageshow', {persisted: true}); String(heard.length)"));

        // a button that's pressed in the panel is the one at the same place in the card
        eval("""
            var clicked = [];
            document.querySelectorAll = selector => selector === '#explore-card-content button' ? [0, 1, 2].map(place => ({click: () => clicked.push(place)})) : [];
            appPressed(2);
            appPressed(0);
            appPressed(7);
            """);
        assertEquals("2,0", eval("String(clicked)"));

        // what's chosen in the app rather than on the page is explained as a tap on that part is
        told = eval("appChose('activation.3.SUN activation.4.VENUS wheel.planets'); said()");
        assertTrue(told.startsWith("explained ") && told.contains("<p>Design Venus text</p>"), told);
        told = eval("appChose('activation.2.SUN wheel.planets'); said()");
        assertTrue(told.contains("<p>Planets text</p>"), told);

        // The page answers whether it explains what was chosen in the app, and says what it
        // explains now, as a part of the drawing names it, so that the app knows whether
        // that's still what was chosen in it.
        assertEquals("true activation.1.MOON", eval("appChose('activation.1.MOON activation.3.SUN activation.4.VENUS wheel.gates') + ' ' + appExplains()"));
        // the next of what the card has for it
        assertEquals("activation.4.VENUS", eval("showCardItem(1); appExplains()"));
        // something else that was chosen on the page
        assertEquals("wheel.planets", eval("showExploreCard([explained['wheel.planets']]); appExplains()"));
        // a part that the page has nothing about, as on a page without a chart, closes the card
        assertEquals("false null", eval("appChose('activation.9.SUN') + ' ' + appExplains()"));
        assertEquals("null", eval("showExploreCard([explained['activation.1.MOON']]); hideExploreCard(); String(appExplains())"));
    }

    @Test
    void anAppThatShowsAChartInMoreWaysHearsWhichChartThePageShows() {
        loadInApp("vision", "chart");
        assertEquals("chart http://x/wheel/image/birth/197506211430/2643743", eval("said(); showChart('http://x/wheel/image/birth/197506211430/2643743'); said()"));
        // also that it shows none
        assertEquals("chart ", eval("showChart(null); said()"));
    }

    @Test
    void anAppWithoutAPanelOrMoreWaysLeavesTheCardAndTheChartToThePage() {
        loadInApp("ios");
        assertEquals("null null", eval("card_changed + ' ' + chart_shown"));
        // nor does a page hear of elsewhere, which the app knows by the answer
        assertEquals("false", eval("String(appElsewhere('arrived', {}, 1000))"));
    }

    @Test
    void thePanelOfAnAppShowsWhatItsGivenAndTellsWhichOfItsButtonsIsPressed() {
        load("", "app.js");
        eval("""
            var marks = [];
            document.documentElement = {classList: {add: (...names) => marks.push(...names)}};
            var content = elements['explore-card-content'] = new FakeElement('explore-card-content');
            var card = elements['explore-card'] = new FakeElement('explore-card');
            card.hidden = true;
            card.scrollTop = 50;
            var pressed = [];
            appPanel(place => pressed.push(place));
            """);
        // the card that a page of a chart hides until a tap is all of this page
        assertEquals("as-panel false", eval("marks + ' ' + card.hidden"));
        assertEquals("<p>shown</p> 0", eval("appShown('<p>shown</p>'); content.innerHTML + ' ' + card.scrollTop"));
        eval("""
            var first = {};
            var second = {};
            content.children = [first, second];
            content.querySelectorAll = selector => selector === 'button' ? [first, second] : [];
            var stopped = 0;
            var click = button => document.dispatch('click', {target: {closest: selector => selector === 'button' ? button : null},
                                                              stopPropagation: () => stopped++, preventDefault: () => stopped++});
            click(second);
            """);
        // the button doesn't hear of it itself: what it does is the card's to do
        assertEquals("1 2", eval("pressed + ' ' + stopped"));
        // a press that's on no button of what's shown is left as it is
        assertEquals("1 2", eval("click(null); click({}); pressed + ' ' + stopped"));
        assertEquals("1,0 4", eval("click(first); pressed + ' ' + stopped"));
    }

    // Devices of someone and what stands in for where an app keeps their people between them.
    // A device's pages do what they're asked, and it hears of elsewhere as it is before it tells.
    private void loadDevices() {
        load("", "store.js", "main.js", "app.js");
        eval("""
            const born = (year, group) => Object.assign({datetime: year + '-01-02T03:04', locationId: '2800866', locationCity: 'Brussels'}, group ? {group: group} : {});
            const stood = people => ({there: people || {}, keep(name, person) { this.there[name] = person; }, remove(name) { delete this.there[name]; }});
            const device = (here, store, same) => {
                const kept = {same: same || {}, get() { return this.same; }, set(same) { this.same = same; }};
                return {
                    here: Object.assign({}, here), kept: kept, synced: appSynced(store, kept),
                    does(asked) { Object.assign(this.here, asked.saved); asked.removed.forEach(name => delete this.here[name]); },
                    tells() { this.does(this.synced.changed('arrived', store.there, 1000)); for (let i = 0; i < 3; i++) this.does(this.synced.told(this.here)); },
                    hears(how) { this.does(this.synced.changed(how || 'there', store.there, 1000)); this.tells(); }
                };
            };
            const names = people => Object.keys(people).sort().join(',');
            const is = person => person ? person.datetime.slice(0, 4) + (person.group ? ' ' + person.group : '') : 'nobody';
            const asks = asked => JSON.stringify([Object.entries(asked.saved).map(([name, person]) => name + ' ' + is(person)), asked.removed]);
            var iCloud = stood();
            var phone = device({Ada: born(1975), Lee: born(1980)}, iCloud);
            phone.tells();
            var tablet = device({}, iCloud);
            """);
    }

    @Test
    void whoIsSavedChangedOrRemovedOnADeviceIsSoOnTheOthers() {
        loadDevices();
        assertEquals("Ada,Lee", eval("names(iCloud.there)"), "who's saved on a device is elsewhere");
        // another device, which has nobody yet
        assertEquals("[[],[]]", eval("asks(tablet.synced.changed('arrived', iCloud.there, 1000))"), "nothing is asked of pages that didn't say who they have yet");
        assertEquals("Ada,Lee", eval("tablet.tells(); names(tablet.here)"), "a new device gets who's elsewhere");
        assertEquals("1990 Family", eval("tablet.here.Kim = born(1990, 'Family'); tablet.tells(); phone.hears(); is(phone.here.Kim)"));
        // someone who's changed on one device
        assertEquals("1976", eval("phone.here.Ada = born(1976); phone.tells(); tablet.hears(); is(tablet.here.Ada)"));
        // and on both while they were apart: elsewhere has the last word, which is the first to reach it
        assertEquals("1977 1977 1977", eval("""
            phone.here.Ada = born(1977); tablet.here.Ada = born(1978);
            phone.tells(); tablet.tells(); phone.hears();
            [phone.here.Ada, tablet.here.Ada, iCloud.there.Ada].map(is).join(' ')"""));
        // someone who's removed, and someone who's given another name
        assertEquals("nobody", eval("delete phone.here.Kim; phone.tells(); is(iCloud.there.Kim)"));
        assertEquals("nobody", eval("tablet.hears(); is(tablet.here.Kim)"));
        assertEquals("Ada,Lee Two Ada,Lee Two", eval("""
            tablet.here['Lee Two'] = tablet.here.Lee; delete tablet.here.Lee;
            tablet.tells(); phone.hears();
            names(phone.here) + ' ' + names(iCloud.there)"""));
    }

    @Test
    void someoneIsOnlyRemovedWhenThatWasSeen() {
        loadDevices();
        // a removal that elsewhere told of before the pages said who they have
        eval("delete iCloud.there.Lee; var late = device(phone.here, iCloud, Object.assign({}, phone.here));");
        assertEquals("[[],[]]", eval("asks(late.synced.changed('there', iCloud.there, 1000))"), "nothing is asked before the pages said who they have");
        assertEquals("Ada", eval("late.tells(); names(late.here)"), "who was removed elsewhere while no page was there is removed once one is");
        eval("iCloud.there.Lee = born(1980); phone.hears()");

        // what was asked of pages that couldn't do it yet is asked again, and doesn't go back as theirs
        eval("iCloud.there.Ada = born(1979); var first = asks(phone.synced.changed('there', iCloud.there, 1000));");
        assertEquals("[[\"Ada 1979\"],[]]", eval("first"));
        assertEquals("true 1979", eval("(asks(phone.synced.changed('arrived', iCloud.there, 1000)) === first) + ' ' + is(iCloud.there.Ada)"));
        eval("phone.hears()");

        // a device that lost what its pages keep is asked to save its people again, who aren't the same on both sides until it did
        assertEquals("[[\"Ada 1979\",\"Lee 1980\"],[]] ", eval("phone.here = {}; asks(phone.synced.told(phone.here)) + ' ' + names(phone.kept.same)"));
        // so it gets them back, and takes them from nobody
        assertEquals("Ada,Lee Ada,Lee Ada,Lee", eval("phone.tells(); names(phone.here) + ' ' + names(iCloud.there) + ' ' + names(phone.kept.same)"));
        // as does one that only lost some, when that's more than one
        assertEquals("Ada,Kim,Lee Ada,Kim,Lee", eval("phone.here.Kim = born(1990); phone.tells(); tablet.hears(); delete tablet.here.Ada; delete tablet.here.Kim; tablet.tells(); names(tablet.here) + ' ' + names(iCloud.there)"));
        // a device that's set up again from a backup, before elsewhere reached it: it's empty there for a moment
        assertEquals("Ada,Kim,Lee Ada,Kim,Lee", eval("""
            var empty = stood();
            var restored = device(phone.here, empty, Object.assign({}, phone.here));
            restored.hears('arrived');
            names(restored.here) + ' ' + names(empty.there)"""));
        // Another account, with people of its own: they're added to the ones here. Where both have
        // someone, it's the account's, whatever was the same with the account before.
        assertEquals("Ada,Lee,Zoe Ada,Lee,Zoe 1900 1900", eval("""
            var other = stood({Zoe: born(2000), Ada: born(1900)});
            var moved = device({Ada: born(1979), Lee: born(1980)}, other, {Ada: born(1900), Lee: born(1980)});
            moved.hears('another');
            names(moved.here) + ' ' + names(other.there) + ' ' + is(moved.here.Ada) + ' ' + is(other.there.Ada)"""));
    }

    @Test
    void noMorePeopleGoElsewhereThanItHasRoomFor() {
        loadDevices();
        assertEquals("1000 1010", eval("""
            var full = stood();
            var all = {};
            for (let i = 0; i < 1010; i++) all['Person ' + i] = born(1900 + i % 100);
            var many = device(all, full);
            many.tells();
            Object.keys(full.there).length + ' ' + Object.keys(many.here).length"""));
        // someone can be called what an object has of itself
        assertEquals("1950 1951 __proto__,toString", eval("""
            var odd = stood(Object.create(null));
            var named = device({}, odd);
            named.here = JSON.parse('{"__proto__": ' + JSON.stringify(born(1950)) + ', "toString": ' + JSON.stringify(born(1951)) + '}');
            named.tells();
            is(odd.there['__proto__']) + ' ' + is(odd.there.toString) + ' ' + names(named.synced.told({}).saved)"""));
    }

    @Test
    void thePagesOfAnAppKeepTheirPeopleTheSameWithTheOnesTheAppKeepsElsewhere() {
        loadInApp("ios", "elsewhere");
        // a page asks who's elsewhere, and saves who it hears of
        assertEquals("elsewhere {\"ask\":true} {\"saved\":[]}", eval("said()"));
        eval("appElsewhere('arrived', {Lee: {datetime: '1980-01-02T03:04', locationId: '2800866', locationCity: 'Brussels', group: 'Family'}}, 1000)");
        assertEquals("{\"Lee\":{\"datetime\":\"1980-01-02T03:04\",\"locationId\":\"2800866\",\"locationCity\":\"Brussels\",\"group\":\"Family\"}}",
            eval("JSON.stringify(HdStore.load(window.localStorage).charts)"));
        assertEquals("", eval("said()"));
        // who's saved on the page is kept elsewhere, and who's removed there is removed
        assertEquals("elsewhere {\"keep\":\"Ada\",\"person\":{\"datetime\":\"1975-06-21T14:30\",\"locationId\":\"2643743\",\"locationCity\":\"London\"}}",
            eval("HdStore.update(window.localStorage, d => add(d, 'Ada')); said()"));
        assertEquals("elsewhere {\"remove\":\"Lee\"}", eval("HdStore.update(window.localStorage, d => HdStore.removeChart(d, 'Lee')); said()"));
        // who both sides had is kept with what the pages keep
        assertEquals("[\"Ada\"]", eval("JSON.stringify(Object.keys(JSON.parse(window.localStorage.getItem('hdapp-same'))))"));
        // who's removed on another device is removed from the page
        assertEquals("true", eval("String(appElsewhere('there', {}, 1000))"));
        assertEquals("{} ", eval("JSON.stringify(HdStore.load(window.localStorage).charts) + ' ' + said().replace(/\\{\"saved\".*/, '')"));
    }

    // a page with the welcome of the charts, as a browser has it when the page is read
    private void loadWithWelcome(String setup) {
        load(setup);
        eval("""
            var welcome = elements['welcome'] = new FakeElement('welcome');
            welcome.shown = 0;
            welcome.showModal = () => welcome.shown++;
            welcome.close = () => welcome.closed = true;
            welcome.getBoundingClientRect = () => ({left: 100, right: 500, top: 50, bottom: 600});
            document.dispatch('DOMContentLoaded');
            """);
    }

    @Test
    void aFirstVisitIsWelcomedOnce() {
        loadWithWelcome("");
        assertEquals("1 1", eval("welcome.shown + ' ' + window.localStorage.getItem('hdapp-welcomed')"));
        // the next page of the same browser
        eval("welcome.shown = 0; document.dispatch('DOMContentLoaded')");
        assertEquals("0", eval("String(welcome.shown)"));

        // a click inside the welcome leaves it, one next to it closes it
        eval("welcome.dispatch = (x, y, target) => welcome.listeners.click.forEach(listener => listener({target: target, clientX: x, clientY: y}))");
        eval("welcome.dispatch(300, 300, welcome); welcome.dispatch(20, 300, {})");
        assertEquals("undefined", eval("String(welcome.closed)"));
        eval("welcome.dispatch(20, 300, welcome)");
        assertEquals("true", eval("String(welcome.closed)"));
    }

    @Test
    void androidGetsTheAppFromGooglePlay() {
        var link = """
            var link = elements['app-link'] = new FakeElement('app-link');
            link.href = 'https://apps.apple.com/app/id6834835560';
            link.dataset.android = 'https://play.google.com/store/apps/details?id=com.uwyn.gatesofhumanity';
            """;
        load(link + "navigator.userAgent = 'Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 Chrome/142.0 Mobile Safari/537.36';");
        eval("document.dispatch('DOMContentLoaded')");
        assertEquals("https://play.google.com/store/apps/details?id=com.uwyn.gatesofhumanity", eval("link.href"));

        for (var agent : new String[]{"'Mozilla/5.0 (iPhone; CPU iPhone OS 26_1 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1'",
                                      "'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/142.0 Safari/537.36'", "undefined"}) {
            load(link + "navigator.userAgent = " + agent + ";");
            eval("document.dispatch('DOMContentLoaded')");
            assertEquals("https://apps.apple.com/app/id6834835560", eval("link.href"), agent);
        }

        // inside an app the pages don't offer it
        load("navigator.userAgent = 'Mozilla/5.0 (Linux; Android 16; wv) Chrome/142.0 Mobile Safari/537.36';");
        eval("document.dispatch('DOMContentLoaded')");
        assertEquals("undefined", eval("typeof elements['app-link']"));
    }

    @Test
    void theWelcomeIsShownAgainForWhoeverAsksWithTheLinkOfTheFooter() {
        // on a page that has the welcome, the link shows it there and leads nowhere
        loadWithWelcome("");
        eval("""
            welcome.showModal = () => { welcome.shown++; welcome.open = true; };
            var prevented = 0;
            var again = {closest: selector => selector === '#welcome-again' ? {} : null};
            var click = () => document.dispatch('click', {target: again, preventDefault: () => prevented++});
            welcome.shown = 0;
            click();
            """);
        assertEquals("1 1", eval("welcome.shown + ' ' + prevented"));
        // it's shown once while it's open, and a click next to it still closes it, once
        eval("click()");
        assertEquals("1 2 1", eval("welcome.shown + ' ' + prevented + ' ' + welcome.listeners.click.length"));

        // on a page without it, the link goes where it leads
        load("");
        eval("var prevented = 0; document.dispatch('click', {target: {closest: selector => selector === '#welcome-again' ? {} : null}, preventDefault: () => prevented++})");
        assertEquals("0", eval("String(prevented)"));
    }

    @Test
    void thePageThatTheLinkOfTheFooterLeadsToShowsTheWelcomeToSomeoneWhoWasWelcomedBefore() {
        loadWithWelcome("""
            window.localStorage.setItem('hdapp-welcomed', '1');
            var replaced = [];
            window.location = {origin: 'http://x', pathname: '/wheel/birth', search: '', hash: '#welcome'};
            window.history = {replaceState: (state, title, url) => { replaced.push(url); window.location.hash = ''; }};
            """);
        // and its address is left without what asked, so that loading it again doesn't welcome again
        assertEquals("1 /wheel/birth", eval("welcome.shown + ' ' + replaced.join(' ')"));
        eval("welcome.shown = 0; document.dispatch('DOMContentLoaded')");
        assertEquals("0", eval("String(welcome.shown)"));

        // the link on the page itself that it leads to only changes what's after the #
        eval("window.location.hash = '#welcome'; window.dispatch('hashchange', {})");
        assertEquals("1 /wheel/birth /wheel/birth", eval("welcome.shown + ' ' + replaced.join(' ')"));
        // another address after the # isn't a request for the welcome
        eval("welcome.shown = 0; window.location.hash = '#other'; window.dispatch('hashchange', {})");
        assertEquals("0", eval("String(welcome.shown)"));

        // a first visit that arrives there is welcomed once, by what it asked for
        loadWithWelcome("""
            window.location = {origin: 'http://x', pathname: '/wheel/birth', search: '', hash: '#welcome'};
            window.history = {replaceState: () => { window.location.hash = ''; }};
            """);
        assertEquals("1 1", eval("welcome.shown + ' ' + window.localStorage.getItem('hdapp-welcomed')"));
        eval("document.dispatch('DOMContentLoaded')");
        assertEquals("1", eval("String(welcome.shown)"));
    }

    @Test
    void aBrowserThatKeepsNothingIsntWelcomedOnEveryPage() {
        loadWithWelcome("window.localStorage.refuseWrites = true;");
        assertEquals("0", eval("String(welcome.shown)"));

        loadWithWelcome("window.localStorage = null;");
        assertEquals("0", eval("String(welcome.shown)"));

        // an old browser without dialogs
        load("");
        eval("elements['welcome'] = new FakeElement('welcome'); document.dispatch('DOMContentLoaded')");
        assertEquals("null", eval("String(window.localStorage.getItem('hdapp-welcomed'))"));
    }

    @Test
    void pagesWithoutAWelcomeLeaveItForTheCharts() {
        load("");
        eval("document.dispatch('DOMContentLoaded')");
        assertEquals("null", eval("String(window.localStorage.getItem('hdapp-welcomed'))"));
    }

    @Test
    void aBrokenSharedLinkIsIgnored() {
        load("window.location = {origin: 'http://x', pathname: '/wheel/birth', hash: '#share&datetime=nonsense&location=abc&city=x'};");
        eval("elements['datetime'].value = '2020-01-01T00:00'; chart_type = 'birth'; prepareSharing()");
        assertEquals("2020-01-01T00:00", eval("elements['datetime'].value"));

        load("window.location = {origin: 'http://x', pathname: '/wheel/birth', hash: '#share&datetime=1955-02-24T19%3A15&location=1%27%3E&city=x'};");
        eval("elements['datetime'].value = '2020-01-01T00:00'; chart_type = 'birth'; prepareSharing()");
        assertEquals("2020-01-01T00:00", eval("elements['datetime'].value"));
    }

    // a chart with a button that steps a day on, as the page has it, and what a browser tells of a press
    private void loadStepButton() {
        load("");
        eval("""
            chart_type = 'birth';
            elements['location-id'].value = '2643743';
            elements['datetime'].value = '1975-06-21T14:30';
            var plus = {id: 'day-plus', closest(selector) { return selector === '#change-time button[id]' ? this : null; }, contains(other) { return other === this; },
                        click() { showDate(getDateOffset(86400), 'http://x/wheel/image/birth/'); }};
            var tell = (type, more) => { const event = Object.assign({target: plus, button: 0, isTrusted: true, stopped: false, prevented: false,
                stopPropagation() { this.stopped = true; }, preventDefault() { this.prevented = true; }}, more); document.dispatch(type, event); return event; };
            var runTimers = () => { const waiting = timers.filter(timer => timer); timers = timers.map(() => null); waiting.forEach(timer => timer()); return waiting.length; };
            var day = () => elements['datetime'].value.slice(8, 10) + ' after ' + fetches.length;
            """);
    }

    @Test
    void aStepButtonThatIsHeldStepsOnEachTimeTheChartIsThere() {
        loadStepButton();
        eval("tell('pointerdown')");
        // not at once, a press that's let go is the step of its click
        assertEquals("21 after 0", eval("day()"));
        assertEquals("1 22 after 1", eval("runTimers() + ' ' + day()"));
        // not before the chart of that step is drawn
        assertEquals("0 22 after 1", eval("runTimers() + ' ' + day()"));
        eval("fetches[0].answer('<svg>22</svg>')");
        assertEquals("<svg>22</svg> 1 23 after 2", eval("elements['svg-image'].innerHTML + ' ' + runTimers() + ' ' + day()"));
        eval("fetches[1].answer('<svg>23</svg>')");
        assertEquals("1 24 after 3", eval("runTimers() + ' ' + day()"));

        // let go, the chart that was asked for is still drawn and nothing steps on
        eval("tell('pointerup'); fetches[2].answer('<svg>24</svg>')");
        assertEquals("<svg>24</svg> 24 after 3", eval("elements['svg-image'].innerHTML + ' ' + day()"));
        // the click that a browser makes of letting go isn't one more step, the next click is
        assertEquals("true true", eval("var let_go = tell('click'); let_go.stopped + ' ' + let_go.prevented"));
        assertEquals("false false", eval("var next = tell('click'); next.stopped + ' ' + next.prevented"));
        assertEquals("24 after 3", eval("runTimers(); day()"));
    }

    @Test
    void aStepButtonThatIsLetGoAtOnceOnlyClicks() {
        loadStepButton();
        eval("tell('pointerdown'); tell('pointerup')");
        assertEquals("0 21 after 0", eval("runTimers() + ' ' + day()"));
        assertEquals("false false", eval("var click = tell('click'); click.stopped + ' ' + click.prevented"));
        // neither does another button of the mouse or what isn't a step button hold anything
        eval("tell('pointerdown', {button: 2}); tell('pointerdown', {target: {closest: selector => null}})");
        assertEquals("0 21 after 0", eval("runTimers() + ' ' + day()"));
    }

    @Test
    void aHeldStepEndsWhenTheFingerScrollsLeavesTheButtonOrTheChartFails() {
        for (var end : new String[] {"tell('pointercancel')", "tell('pointerout', {relatedTarget: null})", "document.dispatch('visibilitychange', {})"}) {
            loadStepButton();
            eval("tell('pointerdown'); runTimers(); fetches[0].answer('<svg>22</svg>')");
            // a script that clicks the button isn't the click of letting go, which is still to come
            assertEquals("false true", eval(end + "; tell('click', {isTrusted: false}).stopped + ' ' + tell('click').stopped"), end);
            assertEquals("22 after 1", eval("runTimers(); day()"), end);
        }
        // moving inside the button isn't leaving it
        loadStepButton();
        eval("tell('pointerdown'); runTimers(); tell('pointerout', {relatedTarget: plus}); fetches[0].answer('<svg>22</svg>')");
        assertEquals("1 23 after 2", eval("runTimers() + ' ' + day()"));
        // a chart that can't be drawn
        eval("fetches[1].answer('', false)");
        assertEquals("0 23 after 2", eval("runTimers() + ' ' + day()"));
        // a long press is no question of what to do with the button while it's held
        assertEquals("true", eval("String(tell('contextmenu').prevented)"));
        assertEquals("false", eval("tell('pointerup'); String(tell('contextmenu').prevented)"));
    }
}
