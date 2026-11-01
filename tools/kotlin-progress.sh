#!/bin/sh
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0
#
# Prints the remaining Java files and lines per package under
# app/src/main/java/org/prauga/phone (excluding voicemail/impl, which is never
# converted), followed by the totals and the top 20 packages by line count.
#
# Usage: tools/kotlin-progress.sh [path-to-Phone-checkout]

set -eu

root=${1:-$(cd "$(dirname "$0")/.." && pwd)}
src="$root/app/src/main/java/org/prauga/phone"

if [ ! -d "$src" ]; then
    echo "kotlin-progress: no such directory: $src" >&2
    exit 1
fi

# One line per Java file: "<package> <lines>", excluding voicemail/impl.
per_file=$(
    find "$src" -name '*.java' -type f \
        ! -path "$src/voicemail/impl/*" \
        -exec wc -l {} + |
        awk -v src="$src/" '
            $2 != "total" {
                path = $2
                sub(src, "", path)
                sub(/\/[^\/]*$/, "", path)
                gsub(/\//, ".", path)
                print path, $1
            }'
)

if [ -z "$per_file" ]; then
    echo "No Java files left (excluding voicemail/impl)."
    exit 0
fi

per_package=$(
    printf '%s\n' "$per_file" |
        awk '{ files[$1]++; lines[$1] += $2 }
             END { for (p in files) printf "%s %d %d\n", p, files[p], lines[p] }' |
        sort
)

echo "Remaining Java by package (excluding voicemail/impl):"
printf '%s\n' "$per_package" |
    awk 'BEGIN { printf "%6s %8s  %s\n", "files", "lines", "package" }
         { printf "%6d %8d  %s\n", $2, $3, $1 }'

echo
printf '%s\n' "$per_package" |
    awk '{ f += $2; l += $3 } END { printf "Total: %d Java files, %d lines\n", f, l }'

echo
echo "Top 20 packages by lines:"
printf '%s\n' "$per_package" |
    sort -k3,3nr -k1,1 |
    head -n 20 |
    awk '{ printf "%6d %8d  %s\n", $2, $3, $1 }'
