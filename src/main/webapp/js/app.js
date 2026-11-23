// The pages inside an app. RIFE2's own script, which the head of a page includes there, marks
// a page of an app and gives it what a web view doesn't do by itself. What's here is what these
// pages ask of their app beyond that: to keep who they saved where its widgets and the other
// devices of someone find them, and, of an app with room around its window, to show there what
// a tapped part explains and the chart itself. An app is told through RIFE2's script, by the
// names that it says it hears. The pages welcome a first start as they welcome a first visit,
// so an app has no welcome of its own. The site that a browser installed has a window of its
// own too, and scrolls inside it as a page of an app does.
function inApp(app) {
    in_app = app;
    if (!app && installedSite()) {
        document.documentElement.classList.add('in-app', 'installed');
    }
    const told = window.rife2NativeApp;
    if (!app || !told) {
        return;
    }

    if (told.hears('explained')) {
        appCard(shown => told.tell('explained', shown));
    }
    // an app that shows a chart in more ways than its page does hears which chart that is
    if (told.hears('chart')) {
        chart_shown = address => told.tell('chart', address || '');
    }

    if (told.hears('people')) {
        appPeople(saved => told.tell('people', saved), told.hears('elsewhere') ? appSyncing(asked => told.tell('elsewhere', asked)) : null);
    }
}

// An app with room beside its window shows what a tapped part of a chart explains there, in a
// panel, rather than in the card that a page has over itself. The page fills its card as
// ever, without showing it, and the app is told what the card holds whenever that changes,
// and nothing once it's closed. The panel is a page of the site too, with the same card as
// all of it, that shows what it's given: a button that's pressed there is pressed in the
// card of the chart's page, by its place among the buttons.
function appCard(tell) {
    let told = '';
    // a page that just loaded explains nothing yet, whatever the page before it did
    tell(told);
    card_changed = () => {
        const card = document.getElementById('explore-card');
        const shown = card && !card.hidden ? document.getElementById('explore-card-content').innerHTML : '';
        if (shown !== told) {
            told = shown;
            tell(shown);
        }
    };
    // a page that Back returns to as it was left still has its card, and the page that was
    // shown meanwhile told the app that nothing was explained
    window.addEventListener('pageshow', event => {
        if (event.persisted) {
            told = '';
            card_changed();
        }
    });
}

function appPressed(place) {
    document.querySelectorAll('#explore-card-content button')[place]?.click();
}

// Something of the chart was chosen in the app rather than on the page: the parts that it
// names, as a part of the drawing names them. Answers whether the page explains any of them.
function appChose(parts) {
    showExploreCard(exploreItems(parts));
    return appExplains() !== null;
}

// What the card explains now, as a part of the drawing names it, or nothing: an app that
// showed what was chosen in it knows by this whether that's still what's explained.
function appExplains() {
    const card = document.getElementById('explore-card');
    return card && !card.hidden && card_items[card_index] ? card_items[card_index].dataset.part : null;
}

let app_shown = null;

function appShown(shown) {
    if (app_shown) {
        app_shown(shown);
    }
}

function appPanel(tell) {
    // the styles have rules for the card as all of a page
    document.documentElement.classList.add('as-panel');
    const card = document.getElementById('explore-card');
    const content = document.getElementById('explore-card-content');
    card.hidden = false;
    app_shown = shown => {
        content.innerHTML = shown;
        card.scrollTop = 0;
    };
    // before the button hears of it: what it would do is the card's to do
    document.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (button && content.contains(button)) {
            event.stopPropagation();
            event.preventDefault();
            tell(Array.from(content.querySelectorAll('button')).indexOf(button));
        }
    }, true);
}

// The app says who it keeps elsewhere, when a page asks and when that changed there, see
// appSynced: how it changed, the people by name, and how many there's room for. Answers whether
// a page heard it.
let app_elsewhere = null;

function appElsewhere(how, people, most) {
    if (!app_elsewhere) {
        return false;
    }
    app_elsewhere(how, people, most);
    return true;
}

// A widget can't read what the pages keep. So the app is told who the pages saved when a page
// loads and each time that changes: saved, which is each name with the moment of the
// birth, in the order of the names, the one whose chart is shown first: that's who a widget
// shows when nobody was chosen for it. Before that, changed hears of the people as they're
// kept, when the app keeps them the same on other devices.
function appPeople(tell, changed) {
    // a birth is a time on the clock of a place, which the site makes a moment of once
    const moments = new Map();
    let told = null;
    const born = chart => {
        const birth = chart.datetime.replace(/\D/g, '').slice(0, 12) + '/' + encodeURIComponent(chart.locationId);
        if (!moments.has(birth)) {
            moments.set(birth, fetch('/widget/born/' + birth).then(answer => answer.ok ? answer.text() : null).catch(() => null));
        }
        return moments.get(birth);
    };
    const people = async () => {
        const kept = HdStore.load(browserStorage());
        const shown = kept.named === null ? null : kept.named.name;
        // what the pages keep also changes with every step of a chart, which changes nobody who was saved
        const now = JSON.stringify([kept.charts, shown]);
        if (now === told) {
            return;
        }
        told = now;
        // at once, every change by itself: only one person who's gone counts as removed
        if (changed) {
            changed(kept.charts);
        }
        const saved = [];
        for (const name of Object.keys(kept.charts).sort((one, other) => one.localeCompare(other, undefined, {numeric: true}))) {
            const moment = await born(kept.charts[name]);
            // who the site can't make a moment of is left out
            if (moment) {
                saved[name === shown ? 'unshift' : 'push']({name: name, born: moment});
            }
        }
        // unless they changed meanwhile, which the later one tells of
        if (now === told) {
            tell({saved: saved});
        }
    };
    for (const change of ['setItem', 'removeItem']) {
        const stored = Storage.prototype[change];
        Storage.prototype[change] = function(key) {
            const result = stored.apply(this, arguments);
            if (key === 'hdapp') {
                people();
            }
            return result;
        };
    }
    people();
}

// Keeps the people that the pages saved the same here and elsewhere, so that someone has them
// on each of their devices. Elsewhere is what the app has for that, the store of iCloud for
// one: it keeps a person under a name and removes one. This is told who the pages have and who
// is elsewhere when that changed, sees to elsewhere itself, and answers who the pages have to
// save and to remove.
//
// Someone who's only on one side is saved on the other. Someone who's another on each side is
// the one of this device when only this device changed them since both sides were the same,
// and the one of elsewhere otherwise. Someone is only removed on the other side when they were
// seen to be removed: by the pages, one at a time as they do it, or by elsewhere saying that it
// changed on another device. Missing alone removes nobody, so a device that lost what it kept,
// or that was set up again from a backup before elsewhere reached it, gets its people back
// rather than taking them away from the other devices.
//
// Who both sides had when they were last the same is what kept gets and sets.
function appSynced(elsewhere, kept) {
    // without a prototype, as the charts of the pages are: someone can be called __proto__
    const named = people => Object.assign(Object.create(null), people);
    const alike = (one, other) => one.datetime === other.datetime && one.locationId === other.locationId &&
        one.locationCity === other.locationCity && (one.group || '') === (other.group || '');
    // who the pages have and who's elsewhere, once each was said, and how many there's room for there
    let here = null;
    let there = null;
    let room = 0;
    let removed_here = new Set();
    let removed_there = new Set();

    const settled = () => {
        const asked = {saved: Object.create(null), removed: []};
        if (here === null || there === null) {
            return asked;
        }
        const same = named(kept.get());
        // whoever was removed there and is gone here too is done with
        removed_there = new Set([...removed_there].filter(name => name in here));
        for (const name of [...new Set([...Object.keys(here), ...Object.keys(there)])].sort()) {
            const mine = name in here ? here[name] : null;
            const theirs = name in there ? there[name] : null;
            if (mine && theirs) {
                if (alike(mine, theirs)) {
                    same[name] = mine;
                } else if (name in same && alike(theirs, same[name])) {
                    elsewhere.keep(name, mine);
                    there[name] = mine;
                    same[name] = mine;
                } else {
                    asked.saved[name] = theirs;
                }
            } else if (mine) {
                if (removed_there.has(name)) {
                    asked.removed.push(name);
                } else if (Object.keys(there).length < room) {
                    elsewhere.keep(name, mine);
                    there[name] = mine;
                    same[name] = mine;
                }
            } else if (removed_here.has(name)) {
                elsewhere.remove(name);
                delete there[name];
                delete same[name];
            } else {
                asked.saved[name] = theirs;
            }
        }
        removed_here = new Set();
        // what's asked of the pages only counts as the same once the pages say that they have it
        for (const name of Object.keys(same)) {
            if (!(name in here) || !(name in there)) {
                delete same[name];
            }
        }
        kept.set(same);
        return asked;
    };

    return {
        // the pages say who they have, when a page loads and each time that changes
        told(people) {
            if (here !== null) {
                // The pages remove one person at a time. More of them gone at once is what the
                // pages keep that was lost, which removes nobody.
                const gone = Object.keys(here).filter(name => !(name in people));
                if (gone.length === 1) {
                    removed_here.add(gone[0]);
                }
            }
            here = named(people);
            return settled();
        },
        // The app says who's elsewhere and how that changed: 'there' on another device, which
        // may have removed someone, 'arrived' when it's there for the first time or again, and
        // 'another' when it's another one, of another account, that never was the same as here.
        changed(how, people, most) {
            there = named(people);
            room = most;
            if (how === 'there') {
                for (const name of Object.keys(kept.get())) {
                    if (!(name in there)) {
                        removed_there.add(name);
                    }
                }
            } else if (how === 'another') {
                kept.set({});
                removed_there = new Set();
            }
            return settled();
        }
    };
}

// Keeps the people of the pages the same with the ones that the app keeps elsewhere: tell asks
// the app to keep someone there, to remove someone, or to say who's there. What's asked of the
// pages is done as if it was done on the page, which the pages then tell of as of any change.
// Answers what hears of the people of the pages.
function appSyncing(tell) {
    const storage = browserStorage();
    const synced = appSynced({
        keep: (name, person) => tell({keep: name, person: person}),
        remove: name => tell({remove: name})
    }, {
        get: () => {
            try {
                return JSON.parse(storage.getItem(SAME_KEY)) || {};
            } catch (e) {
                return {};
            }
        },
        set: same => {
            try {
                storage.setItem(SAME_KEY, JSON.stringify(same));
            } catch (e) {
            }
        }
    });
    const does = asked => {
        if (Object.keys(asked.saved).length > 0 || asked.removed.length > 0) {
            syncPeople(asked);
        }
    };
    app_elsewhere = (how, people, most) => does(synced.changed(how, people, most));
    tell({ask: true});
    return people => does(synced.told(people));
}
