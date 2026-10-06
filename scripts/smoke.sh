#!/usr/bin/env bash
# Converts the synthetic smoke set under the same option sets SmokeTest runs, with a built
# dcm2rdf: a native image, or the runnable jar. A native image fails here when its reachability
# metadata misses something an option needs (see config/reachability-metadata.json); the jar,
# when the shading leaves out something the command line needs.
#
# usage: scripts/smoke.sh <dcm2rdf binary | dcm2rdf jar> [fixtures dir] [work dir]
set -u

BIN=$1
SRC=${2:-src/test/resources/smoke}
WORK=${3:-target/smoke}
OUTPUTS=10           # SmokeFixtures.OUTPUTS
OUTPUTS_SNIFFED=12   # SmokeFixtures.OUTPUTS_SNIFFED

dcm2rdf() {
    if [[ "$BIN" == *.jar ]]; then
        java -jar "$BIN" "$@"
    else
        "$BIN" "$@"
    fi
}

rm -rf "$WORK"
mkdir -p "$WORK"
failures=0

# check <name> <expected outputs> <options...>
check() {
    local name=$1 expected=$2; shift 2
    local dest="$WORK/$name"
    dcm2rdf -src "$SRC" -dest "$dest" -logdir "$WORK/logs-$name" "$@" > "$WORK/$name.out" 2>&1
    local code=$?
    local count
    count=$(find "$dest" -type f 2>/dev/null | wc -l)
    if [ "$code" -eq 0 ] && [ "$count" -eq "$expected" ]; then
        echo "ok    $name"
    else
        echo "FAIL  $name (exit $code, $count of $expected outputs)"
        sed 's/^/      /' "$WORK/$name.out" | head -40
        failures=$((failures + 1))
    fi
}

dcm2rdf -version || failures=$((failures + 1))
dcm2rdf -help > /dev/null || failures=$((failures + 1))

check defaults $OUTPUTS
check every-tweak $OUTPUTS -extra -hash -oid -wkt -detlef -cdt -cdtlevel 2 -ptags -padleftzero -includeinlinebinary -t 4 -status -level INFO
check keywords-sha256-nt-gz $OUTPUTS -keywords -naming SHA256 -format NT -c -oid -wkt -detlef -cdt -ptags
check long-form $OUTPUTS -L
check sniff $OUTPUTS_SNIFFED -sniff

# a single file, written to the file -dest names
dcm2rdf -src "$SRC/ct.dcm" -dest "$WORK/single/ct.ttl" -logdir "$WORK/logs-single" > "$WORK/single.out" 2>&1
if [ $? -eq 0 ] && [ -f "$WORK/single/ct.ttl" ]; then
    echo "ok    single-file"
else
    echo "FAIL  single-file"
    sed 's/^/      /' "$WORK/single.out" | head -40
    failures=$((failures + 1))
fi

# a bad option: exit code 1, the reason on stderr
dcm2rdf -src "$SRC" -dest "$WORK/bad" -c false > "$WORK/bad.out" 2> "$WORK/bad.err"
code=$?
if [ "$code" -eq 1 ] && grep -q "false" "$WORK/bad.err"; then
    echo "ok    bad-option"
else
    echo "FAIL  bad-option (exit $code)"
    sed 's/^/      /' "$WORK/bad.err" | head -20
    failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
    echo "$failures smoke check(s) failed"
    exit 1
fi
echo "all smoke checks passed"
