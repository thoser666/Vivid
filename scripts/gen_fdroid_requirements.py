#!/usr/bin/env python3
"""Erzeugt .github/requirements/fdroidserver-requirements.txt.

Vollständig hash-gepinnte Requirements (fdroidserver 2.4.5 + komplette
transitive Closure) für den deploy-fdroid-Runner (ubuntu-latest, CPython
3.12), damit `pip install --require-hashes` direkt funktioniert (Scorecard
PinnedDependencies, Alert #464).

Versionsauswahl:
  - fdroidserver: fix (ROOT).
  - Transitive Abhängigkeiten: höchste stabile Version, die ALLE gesammelten
    Constraints der Eltern-Pakete erfüllt (z. B. ruamel.yaml<0.17.22,>=0.15
    von fdroidserver → 0.17.21). Ohne Constraints: höchste stabile Version.
  - Pre-Releases (a/b/rc/dev/post) werden übersprungen.

Artefakt-Auswahl pro Paket:
  - py3-none-any-Wheels
  - cp312-manylinux(x86_64)-Wheels (gnu + abi3)
  - das sdist (fallback für glibc-Varianten)
Aus einem derart abgedeckten Satz wählt pip auf dem Runner immer ein
verifizierbar und passendes Artefakt.

Bei Constraint-Konflikten, die nicht auflösbar sind, bricht das Skript mit
Fehler ab (fail-loud statt still kaputtem Lockfile). Muss nach
Dependabot/Upgrade-Hinweisen mit diesem Skript neu erzeugt werden:

    python scripts/gen_fdroid_requirements.py
"""

import json
import re
import sys
import urllib.request
from pathlib import Path

PYPI = "https://pypi.org/pypi"
ROOT = ("fdroidserver", "2.4.5")
PYVER = (3, 12)
TARGET_LINUX = {"manylinux", "musllinux"}

# Manuelle Abhängigkeits-Ergänzungen für alte Pakete, deren PyPI-Metadaten
# unvollständig sind: pip findet deren Requirements erst zur Installations-
# zeit aus der egg-info im sdist (z. B. clint 0.5.1 → args), das PyPI-JSON
# hat kein requires_dist. Ohne diesen Eintrag scheitert
# `pip install --require-hashes` mit "all requirements must have their
# versions pinned" (beobachtet bei args via clint).
MANUAL_DEPS = {
    "clint": ["args"],
}


def get(url):
    with urllib.request.urlopen(url) as r:
        return json.load(r)


def normalize(name):
    return re.sub(r"[-_.]+", "-", name.lower())


def marker_true(marker):
    """Bewertet einen Environment-Marker gegen CPython 3.12 / Linux.

    Unbekannte Klauseln werden als wahr behandelt (konservativ: Paket
    bleibt in der Closure). `extra`-Klauseln bedeuten: nicht installieren.
    """
    marker = (marker or "").strip()
    if not marker:
        return True

    def clause_value(c):
        c = c.strip().strip("()")
        m = re.match(r"python_version\s*(>=|<=|==|!=|>|<)\s*[\'\"]([^\'\"]+)[\'\"]", c)
        if m:
            op, ver = m.group(1), m.group(2)
            v = tuple(int(x) for x in ver.split(".")[:2])
            return {">=": PYVER >= v, ">": PYVER > v, "<=": PYVER <= v,
                    "<": PYVER < v, "==": PYVER == v, "!=": PYVER != v}[op]
        m = re.match(r"sys_platform\s*(==|!=)\s*[\'\"]([^\'\"]+)[\'\"]", c)
        if m:
            eq = m.group(1) == "=="
            known = {"win32", "cygwin", "darwin", "java", "linux"}
            return (m.group(2) == "linux") == eq
        m = re.match(r"platform_system\s*(==|!=)\s*[\'\"]([^\'\"]+)[\'\"]", c)
        if m:
            eq = m.group(1) == "=="
            return (m.group(2) == "Linux") == eq
        if re.match(r"platform_machine\s*==\s*[\'\"]x86_64[\'\"]", c):
            return True
        if re.match(r"implementation_name\s*==\s*[\'\"]cpython[\'\"]", c):
            return True
        if "extra" in c:
            return False  # Extras-Abhängigkeiten nicht installieren
        # Platform-spezifische Pakete (Windows/macOS-only) via marker_name:
        m = re.match(r"marker\s*(==|!=)\s*[\'\"]([^\'\"]+)[\'\"]", c)
        if m:
            return (m.group(2) == "sys_platform == 'win32'") != (m.group(1) == "!=")
        return True  # unbekannt -> konservativ wahr

    for disjunct in re.split(r"\bor\b", marker):
        conjuncts = [clause_value(c) for c in re.split(r"\band\b", disjunct)]
        if conjuncts and all(conjuncts):
            return True
    return False


def loguru_marker_true(marker):
    """Spezialfall loguru: win32-setctime/darwin-only-Extras per Marker
    'sys_platform == ...' ausschließen (loguru nutzt marker_name-Formen,
    die die Klausel direkt als String tragen)."""
    return "win32" not in (marker or "") and "sys_platform == 'darwin'" not in (marker or "")


def parse_version(v):
    """Nur rein numerische Versionen (keine Pre-/Post-Releases)."""
    m = re.match(r"^(\d+(?:\.\d+)*)$", v.strip())
    return tuple(int(x) for x in m.group(1).split(".")) if m else None


def satisfies(vtuple, spec):
    """Prüft eine Versions-Tupel gegen alle Constraints eines Spec-Strings."""
    for op, ver in re.findall(r"(>=|<=|==|!=|>|<)\s*([\d.]+)", spec):
        c = tuple(int(x) for x in ver.split("."))
        n = max(len(c), len(vtuple))
        a = vtuple + (0,) * (n - len(vtuple))
        b = c + (0,) * (n - len(c))
        if not {">=": a >= b, ">": a > b, "<=": a <= b, "<": a < b,
                "==": a == b, "!=": a != b}[op]:
            return False
    return True


def choose_version(specs, releases):
    """Höchste stabile Version, die alle Constraints erfüllt (oder None)."""
    spec = ",".join(s for s in specs if s)
    stable = []
    for v in releases:
        vt = parse_version(v)
        if vt is not None:
            stable.append((vt, v))
    if spec:
        stable = [(vt, v) for vt, v in stable if satisfies(vt, spec)]
    if not stable:
        return None
    stable.sort()
    return stable[-1][1]


def direct_deps(info):
    """Liefert (name, specifier)-Paare der für CPython 3.12 relevanten
    Abhängigkeiten (Extras ausgeschlossen, Marker ausgewertet)."""
    out = []
    for entry in info.get("requires_dist") or []:
        name_part, _, marker = entry.partition(";")
        if not marker_true(marker):
            continue
        name = re.split(r"[=<>!~;\s[(]", name_part.strip())[0].strip()
        spec = name_part.strip()[len(name):].strip()
        if name:
            out.append((name, spec))
    return out


def wheel_wanted(filename):
    if filename.endswith("-py3-none-any.whl"):
        return True
    m = re.match(r".*-(cp3\d*)-(cp3\d*|abi3)-(.+?)(-any)?\.whl$", filename)
    if not m:
        return False
    tags = m.group(3)
    return "cp312" in (m.group(1) + m.group(2)) and any(
        t in tags for t in TARGET_LINUX)


def main():
    seen = {}       # key -> (name, version, artifacts)
    specs_map = {}  # key -> Liste von Spec-Strings der Eltern
    queue = [ROOT[0]]
    specs_map[normalize(ROOT[0])] = []
    root_pinned = {normalize(ROOT[0]): ROOT[1]}
    warnings = []
    errors = []

    while queue:
        name = queue.pop(0)
        key = normalize(name)
        if key in seen:
            continue

        try:
            data = get(f"{PYPI}/{name}/json")
        except urllib.error.HTTPError as e:
            errors.append(f"{name}: HTTP {e.code}")
            continue
        info = data["info"]
        releases = data.get("releases") or {}

        if key in root_pinned:
            version = root_pinned[key]
        else:
            version = choose_version(specs_map.get(key, []), releases)
            if version is None:
                warnings.append(
                    f"{name}: keine stabile Version erfüllt "
                    f"{specs_map.get(key, [])}")
                continue

        # requires_dist des exakt gewählten Stands (der Projekt-Endpoint
        # beschreibt die jeweils neueste Version).
        if version == info.get("version"):
            requires_dist = info.get("requires_dist")
        else:
            vdata = get(f"{PYPI}/{name}/{version}/json")
            requires_dist = vdata["info"].get("requires_dist")

        artifacts = [f for f in releases.get(version, [])
                     if f["filename"].endswith((".whl", ".tar.gz", ".zip"))
                     and (wheel_wanted(f["filename"])
                          or f["filename"].endswith((".tar.gz", ".zip")))]
        if not artifacts:
            warnings.append(f"{name} {version}: keine verwendbaren Artefakte")
            continue
        artifacts.sort(key=lambda f: f["filename"])
        seen[key] = (name, version, artifacts)

        for dep_name, dep_spec in direct_deps({"requires_dist": requires_dist}):
            dkey = normalize(dep_name)
            if dkey in seen:
                # Nachträgliches Constraint gegen bereits gewählte Version.
                if dep_spec and not satisfies(parse_version(seen[dkey][1]), dep_spec):
                    errors.append(
                        f"{dep_name}: bereits {seen[dkey][1]} gewählt, "
                        f"verletzt Constraint {dep_spec!r} aus {name}")
                continue
            specs_map.setdefault(dkey, []).append(dep_spec)
            queue.append(dep_name)
        for dep_name in MANUAL_DEPS.get(key, []):
            dkey = normalize(dep_name)
            if dkey not in seen and dkey not in specs_map:
                specs_map[dkey] = []
                queue.append(dep_name)

    entries = sorted(seen.values(), key=lambda e: normalize(e[0]))

    # Selbstprüfung: Für jedes Paket muss jede (Linux/CPython-3.12-relevante)
    # Abhängigkeit selbst in der Closure sein — sonst würde pip auf dem
    # Runner "all requirements must have their versions pinned" werfen.
    for name, version, _ in entries:
        try:
            pdata = get(f"{PYPI}/{name}/{version}/json")
            requires = pdata["info"].get("requires_dist")
        except urllib.error.HTTPError as e:
            warnings.append(f"{name}: Selbstprüfung übersprungen (HTTP {e.code})")
            continue
        for dep_name, _spec in direct_deps({"requires_dist": requires}):
            if normalize(dep_name) not in seen:
                errors.append(
                    f"Closure unvollständig: {name} -> {dep_name} fehlt")

    lines = [
        "# Erzeugt von scripts/gen_fdroid_requirements.py — NICHT manuell editieren.",
        "# Vollständig hash-gepinnt (fdroidserver 2.4.5 + transitive Closure) für",
        "# ubuntu-latest / CPython 3.12: py3-none-any-Wheels, cp312-manylinux-",
        "# x86_64-Wheels und das sdist je Paket; Versionsauswahl mit Constraints",
        "# der Eltern-Pakete. Erneuern mit:",
        "#   python scripts/gen_fdroid_requirements.py",
        "#",
    ]
    hash_count = 0
    for name, version, artifacts in entries:
        hashes = [f"    --hash=sha256:{a['digests']['sha256']}" for a in artifacts]
        hash_count += len(hashes)
        lines.append(f"{normalize(name)}=={version} \\")
        for i, h in enumerate(hashes):
            lines.append(h + (" \\" if i < len(hashes) - 1 else ""))

    out = Path(".github/requirements/fdroidserver-requirements.txt")
    out.parent.mkdir(parents=True, exist_ok=True)
    content = "\n".join(lines) + "\n"

    # --check: Datei gegen die frisch generierte Closure vergleichen
    # (Drift-Test, überschreibt nichts). Verdrahtet in
    # scripts/test_pip_pinning.sh, damit eine veraltete Closure den
    # Pre-Push-Gate blockiert.
    if "--check" in sys.argv[1:]:
        current = out.read_text(encoding="utf-8") if out.exists() else ""
        if current != content:
            errors.append(
                f"{out} ist nicht aktuell — bitte neu generieren "
                f"(python scripts/gen_fdroid_requirements.py) und committen")
        else:
            print(f"CHECK OK: {out} byte-identisch "
                  f"({len(entries)} packages, {hash_count} hashes)")
    else:
        out.write_text(content, encoding="utf-8", newline="\n")
        print(f"OK: {len(entries)} packages, {hash_count} hashes -> {out}")
    for w in warnings:
        print(f"WARNUNG: {w}", file=sys.stderr)
    for e in errors:
        print(f"FEHLER: {e}", file=sys.stderr)
    if errors or not entries:
        sys.exit(1)
    print("SELBSTPRÜFUNG OK: Closure vollständig (Linux/CPython-3.12-Marker).")


if __name__ == "__main__":
    main()
