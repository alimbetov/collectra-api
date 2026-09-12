#!/usr/bin/env bash
set -euo pipefail

ROOT="src/main/resources/fonts"
NOTO_FONTS_COMMIT="ffebf8c1ee449e544955a7e813c54f9b73848eac"
NOTO_FONTS_BASE="https://raw.githubusercontent.com/notofonts/noto-fonts/${NOTO_FONTS_COMMIT}/hinted/ttf"

mkdir -p \
  "${ROOT}/latin-cyrillic" \
  "${ROOT}/armenian" \
  "${ROOT}/georgian"

download() {
  local url="$1"
  local target="$2"
  local tmp="${target}.download"
  echo "Downloading ${url}"
  curl --fail --location --silent --show-error \
       --retry 5 --retry-delay 2 --connect-timeout 20 \
       --output "${tmp}" "${url}"
  test -s "${tmp}"
  mv "${tmp}" "${target}"
}

download "${NOTO_FONTS_BASE}/NotoSans/NotoSans-Regular.ttf" \
  "${ROOT}/latin-cyrillic/NotoSans-Regular.ttf"
download "${NOTO_FONTS_BASE}/NotoSans/NotoSans-Bold.ttf" \
  "${ROOT}/latin-cyrillic/NotoSans-Bold.ttf"
download "${NOTO_FONTS_BASE}/NotoSans/NotoSans-Italic.ttf" \
  "${ROOT}/latin-cyrillic/NotoSans-Italic.ttf"
download "${NOTO_FONTS_BASE}/NotoSans/NotoSans-BoldItalic.ttf" \
  "${ROOT}/latin-cyrillic/NotoSans-BoldItalic.ttf"

download "${NOTO_FONTS_BASE}/NotoSansArmenian/NotoSansArmenian-Regular.ttf" \
  "${ROOT}/armenian/NotoSansArmenian-Regular.ttf"
download "${NOTO_FONTS_BASE}/NotoSansArmenian/NotoSansArmenian-Bold.ttf" \
  "${ROOT}/armenian/NotoSansArmenian-Bold.ttf"

download "${NOTO_FONTS_BASE}/NotoSansGeorgian/NotoSansGeorgian-Regular.ttf" \
  "${ROOT}/georgian/NotoSansGeorgian-Regular.ttf"
download "${NOTO_FONTS_BASE}/NotoSansGeorgian/NotoSansGeorgian-Bold.ttf" \
  "${ROOT}/georgian/NotoSansGeorgian-Bold.ttf"

(
  cd "${ROOT}"
  find latin-cyrillic armenian georgian \
       -type f -name '*.ttf' \
       -print0 \
    | sort -z \
    | xargs -0 sha256sum > SHA256SUMS
)

echo "Bundled font checksums:"
cat "${ROOT}/SHA256SUMS"

echo "CJK Simplified Chinese TrueType resources are supplied by the pinned Maven dependency ph-fonts-noto-sans-sc."
