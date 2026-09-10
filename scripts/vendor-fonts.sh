#!/usr/bin/env bash
set -euo pipefail

ROOT="src/main/resources/fonts"
NOTO_FONTS_COMMIT="ffebf8c1ee449e544955a7e813c54f9b73848eac"
NOTO_CJK_COMMIT="f8d157532fbfaeda587e826d4cd5b21a49186f7c"
NOTO_FONTS_BASE="https://raw.githubusercontent.com/notofonts/noto-fonts/${NOTO_FONTS_COMMIT}/hinted/ttf"
NOTO_CJK_BASE="https://raw.githubusercontent.com/notofonts/noto-cjk/${NOTO_CJK_COMMIT}/Sans/OTF/SimplifiedChinese"

mkdir -p \
  "${ROOT}/latin-cyrillic" \
  "${ROOT}/armenian" \
  "${ROOT}/georgian" \
  "${ROOT}/cjk-sc"

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

download "${NOTO_CJK_BASE}/NotoSansCJKsc-Regular.otf" \
  "${ROOT}/cjk-sc/NotoSansCJKsc-Regular.otf"
download "${NOTO_CJK_BASE}/NotoSansCJKsc-Bold.otf" \
  "${ROOT}/cjk-sc/NotoSansCJKsc-Bold.otf"

(
  cd "${ROOT}"
  find latin-cyrillic armenian georgian cjk-sc \
       -type f \( -name '*.ttf' -o -name '*.otf' \) \
       -print0 \
    | sort -z \
    | xargs -0 sha256sum > SHA256SUMS
)

echo "Bundled font checksums:"
cat "${ROOT}/SHA256SUMS"
