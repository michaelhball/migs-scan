#!/usr/bin/env bash
# Re-renders the legacy (API < 26) launcher PNGs from tools/icon.svg.
# Needs rsvg-convert (librsvg) and ImageMagick 7 (`magick`).
#
# The SVG is drawn on the 108x108 adaptive-icon canvas; pre-26 launchers
# show the whole bitmap, so we crop to the inner 72x72 the way the system
# mask would, then round the corners (~18%, matching the adaptive squircle)
# for ic_launcher.png and cut a circle for ic_launcher_round.png.
set -euo pipefail
cd "$(dirname "$0")/.."

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
# Same artwork, viewBox narrowed to the 72x72 the adaptive mask reveals.
sed 's/viewBox="0 0 108 108"/viewBox="18 18 72 72"/' tools/icon.svg > "$tmp/cropped.svg"

render() {  # density size
  local density=$1 size=$2 r
  local dir="app/src/main/res/mipmap-$density"
  r=$(python3 -c "print(round($size * 0.18))")
  rsvg-convert -w "$size" -h "$size" "$tmp/cropped.svg" -o "$tmp/$density.png"
  magick "$tmp/$density.png" \
    \( -size "${size}x${size}" xc:none -fill white \
       -draw "roundrectangle 0,0,$((size - 1)),$((size - 1)),$r,$r" \) \
    -compose DstIn -composite "$dir/ic_launcher.png"
  magick "$tmp/$density.png" \
    \( -size "${size}x${size}" xc:none -fill white \
       -draw "circle $((size / 2)),$((size / 2)) $((size / 2)),0" \) \
    -compose DstIn -composite "$dir/ic_launcher_round.png"
  echo "rendered $dir ($size px)"
}

render mdpi 48
render hdpi 72
render xhdpi 96
render xxhdpi 144
render xxxhdpi 192
