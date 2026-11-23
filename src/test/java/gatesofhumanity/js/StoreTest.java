package gatesofhumanity.js;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StoreTest {
    private ScriptRunner js_;

    @BeforeEach
    void setUp() {
        js_ = new ScriptRunner("", "store.js");
        js_.eval("var storage = new MemoryStorage();");
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
    void timesKeepTheirWallClockAcrossDaylightSavingTime() {
        assertEquals("2024-03-31T02:30", eval("HdTime.format(HdTime.parse('2024-03-31T02:30'))"));
        assertEquals("2024-03-31T12:00", eval("HdTime.add('2024-03-30T12:00', 86400)"));
        assertEquals("2024-10-27T02:30", eval("HdTime.add('2024-10-27T01:30', 3600)"));
        assertEquals("2024-03-31T02:29", eval("HdTime.add('2024-03-31T02:30', -60)"));
    }

    @Test
    void timesAreValidated() {
        assertEquals("undefined", eval("String(HdTime.parse('2024-02-30T10:00'))"));
        assertEquals("undefined", eval("String(HdTime.parse('garbage'))"));
        assertEquals("undefined", eval("String(HdTime.parse('2024-02-29T10:00xyz'))"));
        // the year field of a browser can take more digits than a year has
        assertEquals("undefined", eval("String(HdTime.parse('199010-03-15T03:00'))"));
        assertEquals("2024-02-29T10:00", eval("HdTime.normalize('2024-02-29T10:00:00.000')"));
        assertEquals("undefined", eval("String(HdTime.parse('2024-02-29T10:00:00Z'))"));
        assertEquals("undefined", eval("String(HdTime.parse(undefined))"));
        assertEquals("2024-02-29T10:00", eval("HdTime.format(HdTime.parse('2024-02-29T10:00:59'))"));
        assertEquals("180004080930", eval("HdTime.formatForImage(HdTime.parse('1800-04-08T09:30'))"));
        assertEquals("0099-01-01T00:00", eval("HdTime.format(HdTime.parse('0099-01-01T00:00'))"));
    }

    @Test
    void chartNamesCanContainAnything() {
        eval("""
            HdStore.update(storage, d => {
                HdStore.putChart(d, 'my_mom', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'});
                HdStore.putChart(d, ' Zoë = "x"; y ', {datetime: '1980-01-02T03:04', locationId: '1850147', locationCity: 'Tokyo'});
                HdStore.putChart(d, 'alice', {datetime: '1990-05-06T07:08', locationId: '2988507', locationCity: 'Paris'});
            })""");
        assertEquals("[\"alice\",\"my_mom\",\"Zoë = \\\"x\\\"; y\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(storage)))"));
        assertEquals("London", eval("HdStore.load(storage).charts['my_mom'].locationCity"));
    }

    @Test
    void chartsCanBeNamedLikeTheMembersOfAnObject() {
        eval("""
            HdStore.update(storage, d => {
                HdStore.putChart(d, '__proto__', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'});
            })""");
        assertEquals("[\"__proto__\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(storage)))"));
        assertEquals("undefined", eval("String(HdStore.load(storage).charts['toString'])"));
    }

    @Test
    void storedDatesAndCitiesAreChecked() {
        var imported = eval("""
            JSON.stringify(HdStore.importCharts(HdStore.load(storage), JSON.stringify({format: 'hdapp-charts', charts: {
                a: {datetime: 'not a date', locationId: '1', locationCity: 'x'},
                b: {datetime: '2020-01-01T10:00', locationId: '0/../../about', locationCity: 'x'},
                d: {datetime: '2020-01-01T10:00', locationId: '', locationCity: 'x'},
                c: {datetime: '2020-01-01T10:00:30', locationId: '2643743', locationCity: 'London'}}})))""");
        assertEquals("{\"added\":1,\"renamed\":0,\"unchanged\":0,\"grouped\":0,\"invalid\":3}", imported);
        eval("HdStore.update(storage, d => HdStore.importCharts(d, JSON.stringify({format: 'hdapp-charts', charts: {c: {datetime: '2020-01-01T10:00:30', locationId: '2643743', locationCity: 'London'}}})))");
        assertEquals("2020-01-01T10:00", eval("HdStore.load(storage).charts['c'].datetime"));
    }

    @Test
    void invalidChartsAreNotSaved() {
        assertEquals("false", eval("HdStore.putChart(HdStore.load(storage), '  ', {datetime: '1975-06-21T14:30', locationId: '1', locationCity: 'x'})"));
        assertEquals("false", eval("HdStore.putChart(HdStore.load(storage), 'a', {datetime: '', locationId: '1', locationCity: 'x'})"));
        assertEquals("false", eval("HdStore.putChart(HdStore.load(storage), null, {datetime: '1975-06-21T14:30', locationId: '1', locationCity: 'x'})"));
    }

    @Test
    void changesFromAnotherTabAreKept() {
        eval("""
            HdStore.update(storage, d => HdStore.putChart(d, 'from tab 2', {datetime: '1975-06-21T14:30', locationId: '1', locationCity: 'x'}));
            HdStore.update(storage, d => HdStore.putChart(d, 'from tab 1', {datetime: '1975-06-21T14:30', locationId: '1', locationCity: 'x'}));
            """);
        assertEquals("[\"from tab 1\",\"from tab 2\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(storage)))"));
    }

    @Test
    void refusedWritesAreReported() {
        eval("HdStore.update(storage, d => HdStore.putChart(d, 'kept', {datetime: '1975-06-21T14:30', locationId: '1', locationCity: 'x'}))");
        eval("storage.refuseWrites = true");
        assertEquals("false", eval("HdStore.update(storage, d => HdStore.putChart(d, 'new', {datetime: '1975-06-21T14:30', locationId: '1', locationCity: 'x'}))"));
        assertEquals("[\"kept\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(storage)))"));
        assertEquals("false", eval("HdStore.save(null, HdStore.load(null))"));
    }

    @Test
    void unreadableDataIsKeptAside() {
        eval("storage.setItem('hdapp', '{\"charts\": {broken')");
        assertEquals("0", eval("HdStore.chartNames(HdStore.load(storage)).length"));
        assertEquals("{\"charts\": {broken", eval("storage.getItem(storage.keys().find(k => k.startsWith('hdapp.unreadable.')))"));
    }

    @Test
    void currentLocationAndBirth() {
        eval("""
            HdStore.update(storage, d => {
                HdStore.setLocation(d, 1850147, 'Tokyo, Japan');
                HdStore.setBirthDatetime(d, '1975-06-21T14:30');
                HdStore.setBirthLocation(d, '2643743', 'London, England, United Kingdom');
                HdStore.setBirthDatetime(d, 'garbage');
            })""");
        var data = "HdStore.load(storage)";
        assertEquals("{\"id\":\"1850147\",\"city\":\"Tokyo, Japan\"}", eval("JSON.stringify(" + data + ".location)"));
        assertEquals("{\"datetime\":\"1975-06-21T14:30\",\"location\":{\"id\":\"2643743\",\"city\":\"London, England, United Kingdom\"}}",
            eval("JSON.stringify(" + data + ".birth)"));
    }

    @Test
    void theNamedChartIsValidated() {
        eval("HdStore.update(storage, d => HdStore.setNamed(d, 'mom', {datetime: '1975-06-21T14:30', locationId: '2643743'}))");
        assertEquals("{\"name\":\"mom\",\"datetime\":\"1975-06-21T14:30\",\"locationId\":\"2643743\"}", eval("JSON.stringify(HdStore.load(storage).named)"));
        eval("HdStore.update(storage, d => HdStore.setNamed(d, 'mom', {datetime: '1975-06-21T14:30', locationId: ''}))");
        assertEquals("null", eval("String(HdStore.load(storage).named)"));
    }

    @Test
    void howToReadIsOnlyOpenWhenItWasOpened() {
        assertEquals("false", eval("String(HdStore.load(storage).guideOpen)"));
        eval("storage.setItem('hdapp', JSON.stringify({version: 1, guideOpen: 'yes'}))");
        assertEquals("false", eval("String(HdStore.load(storage).guideOpen)"));
        eval("HdStore.update(storage, d => d.guideOpen = true)");
        assertEquals("true", eval("String(HdStore.load(storage).guideOpen)"));
    }

    @Test
    void aTitleOnlyNamesAChartThatsStillSaved() {
        eval("""
            HdStore.update(storage, d => {
                HdStore.putChart(d, 'me', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'});
                HdStore.setNamed(d, 'me', {datetime: '1975-06-21T14:30', locationId: '2643743'}, 'birth');
                HdStore.putChart(d, 'you', {datetime: '1980-01-02T03:04', locationId: '1850147', locationCity: 'Tokyo'});
                HdStore.setNamed(d, 'you', {datetime: '1980-01-02T03:04', locationId: '1850147'}, 'partner');
            });
            HdStore.update(storage, d => HdStore.putChart(d, 'me', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'}));
            """);
        assertEquals("me", eval("HdStore.load(storage).named.name"));
        eval("HdStore.update(storage, d => HdStore.putChart(d, 'me', {datetime: '2026-01-01T12:00', locationId: '2643743', locationCity: 'London'}))");
        assertEquals("null", eval("String(HdStore.load(storage).named)"));
        eval("HdStore.update(storage, d => HdStore.removeChart(d, 'you'))");
        assertEquals("null", eval("String(HdStore.load(storage).namedPartner)"));
    }

    @Test
    void someoneSavedTakesANameThatIsFree() {
        eval("""
            HdStore.update(storage, d => {
                HdStore.putChart(d, 'me', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London', group: 'Family'});
                HdStore.setNamed(d, 'me', {datetime: '1975-06-21T14:30', locationId: '2643743'}, 'birth');
                HdStore.putChart(d, 'you', {datetime: '1980-01-02T03:04', locationId: '1850147', locationCity: 'Tokyo'});
                HdStore.setNamed(d, 'you', {datetime: '1980-01-02T03:04', locationId: '1850147'}, 'partner');
            });
            var rename = (name, renamed) => { let done; HdStore.update(storage, d => done = HdStore.renameChart(d, name, renamed)); return done; };
            """);
        for (var refused : new String[]{"'me', 'you'", "'me', 'me'", "'me', '  '", "'me', 7", "'nobody', 'someone'"}) {
            assertEquals("false", eval("rename(" + refused + ")"), refused);
        }
        assertEquals("[\"me\",\"you\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(storage)))"));
        assertEquals("1980-01-02T03:04", eval("HdStore.load(storage).charts['you'].datetime"));
        assertEquals("true", eval("rename('me', ' toString ')"));
        assertEquals("[\"toString\",\"you\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(storage)))"));
        assertEquals("{\"datetime\":\"1975-06-21T14:30\",\"locationId\":\"2643743\",\"locationCity\":\"London\",\"group\":\"Family\"}",
            eval("JSON.stringify(HdStore.load(storage).charts['toString'])"));
        // the chart that shows them keeps its title, under the new name
        assertEquals("toString you", eval("HdStore.load(storage).named.name + ' ' + HdStore.load(storage).namedPartner.name"));
        assertEquals("true", eval("rename('you', 'them')"));
        assertEquals("toString them", eval("HdStore.load(storage).named.name + ' ' + HdStore.load(storage).namedPartner.name"));
    }

    @Test
    void exportAndImport() {
        eval("""
            HdStore.update(storage, d => {
                HdStore.putChart(d, 'a', {datetime: '1975-06-21T14:30', locationId: '1', locationCity: 'x'});
                HdStore.putChart(d, 'b', {datetime: '1980-01-02T03:04', locationId: '2', locationCity: 'y'});
            });
            var exported = HdStore.exportCharts(HdStore.load(storage));
            var other = new MemoryStorage();
            HdStore.update(other, d => {
                HdStore.putChart(d, 'a', {datetime: '1975-06-21T14:30', locationId: '1', locationCity: 'x'});
                HdStore.putChart(d, 'b', {datetime: '2000-01-01T00:00', locationId: '3', locationCity: 'z'});
                HdStore.putChart(d, 'b (2)', {datetime: '2001-01-01T00:00', locationId: '3', locationCity: 'z'});
            });
            var result;
            HdStore.update(other, d => result = HdStore.importCharts(d, exported));
            """);
        assertEquals("{\"added\":0,\"renamed\":1,\"unchanged\":1,\"grouped\":0,\"invalid\":0}", eval("JSON.stringify(result)"));
        assertEquals("[\"a\",\"b\",\"b (2)\",\"b (3)\"]", eval("JSON.stringify(HdStore.chartNames(HdStore.load(other)))"));
        assertEquals("2000-01-01T00:00", eval("HdStore.load(other).charts['b'].datetime"));
        assertEquals("1980-01-02T03:04", eval("HdStore.load(other).charts['b (3)'].datetime"));
        assertEquals("This isn't a file of exported people.", eval("(() => { try { HdStore.importCharts(HdStore.load(other), '{\"charts\": {}}'); } catch (e) { return e.message; } })()"));
        assertEquals("This isn't a file of exported people.", eval("(() => { try { HdStore.importCharts(HdStore.load(other), 'not json'); } catch (e) { return e.message; } })()"));
    }

    @Test
    void peopleCanBeInAGroup() {
        eval("""
            HdStore.update(storage, d => {
                HdStore.putChart(d, 'mom', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London', group: ' Family '});
                HdStore.putChart(d, 'dad', {datetime: '1972-01-02T03:04', locationId: '2643743', locationCity: 'London', group: 'Family'});
                HdStore.putChart(d, 'kim', {datetime: '1990-05-06T07:08', locationId: '2988507', locationCity: 'Paris', group: 'clients'});
                HdStore.putChart(d, 'me', {datetime: '1980-01-02T03:04', locationId: '1850147', locationCity: 'Tokyo', group: '  '});
                HdStore.putChart(d, 'sam', {datetime: '1981-01-02T03:04', locationId: '1850147', locationCity: 'Tokyo', group: 7});
            })""");
        assertEquals("[\"clients\",\"Family\"]", eval("JSON.stringify(HdStore.groupNames(HdStore.load(storage)))"));
        assertEquals("Family", eval("HdStore.load(storage).charts['mom'].group"));
        assertEquals("false", eval("'group' in HdStore.load(storage).charts['me']"));
        assertEquals("false", eval("'group' in HdStore.load(storage).charts['sam']"));
        // the group goes along in an export
        eval("var other = new MemoryStorage(); var text = HdStore.exportCharts(HdStore.load(storage)); HdStore.update(other, d => HdStore.importCharts(d, text));");
        assertEquals("clients", eval("HdStore.load(other).charts['kim'].group"));
        // someone saved already takes the group of the file when they have none, and keeps their own
        eval("""
            var third = new MemoryStorage(), result;
            HdStore.update(third, d => {
                HdStore.putChart(d, 'mom', {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London'});
                HdStore.putChart(d, 'kim', {datetime: '1990-05-06T07:08', locationId: '2988507', locationCity: 'Paris', group: 'Friends'});
            });
            HdStore.update(third, d => result = HdStore.importCharts(d, text));""");
        assertEquals("{\"added\":3,\"renamed\":0,\"unchanged\":1,\"grouped\":1,\"invalid\":0}", eval("JSON.stringify(result)"));
        assertEquals("Family", eval("HdStore.load(third).charts['mom'].group"));
        assertEquals("Friends", eval("HdStore.load(third).charts['kim'].group"));
        // a group can be changed and taken away, for someone saved
        eval("HdStore.update(third, d => { HdStore.setGroup(d, 'kim', ' Clients '); HdStore.setGroup(d, 'mom', '  '); })");
        assertEquals("Clients", eval("HdStore.load(third).charts['kim'].group"));
        assertEquals("false", eval("'group' in HdStore.load(third).charts['mom']"));
        assertEquals("false", eval("HdStore.setGroup(HdStore.load(third), 'nobody', 'Family')"));
        assertEquals("Clients", eval("(() => { const d = HdStore.load(third); HdStore.setGroup(d, 'kim', ' Clients '); return d.charts['kim'].group; })()"));
    }
}
