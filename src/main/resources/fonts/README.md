# Collectra bundled fonts

Collectra vendors the fonts required by the supported document locales so PDF rendering never depends on host/container fonts or runtime network access.

## Font profiles

| Profile | Bundled family | Locales/scripts |
| --- | --- | --- |
| `LATIN_CYRILLIC` | Noto Sans | `ru`, `kk`, `en`, `uz`, `ky`, `tg`, `az`, `be`, `uk` |
| `ARMENIAN` | Noto Sans Armenian | `hy` |
| `GEORGIAN` | Noto Sans Georgian | `ka` |
| `CJK_SC` | Noto Sans CJK SC | `zh-CN` |

## Required files

```text
fonts/
├── latin-cyrillic/
│   ├── NotoSans-Regular.ttf
│   ├── NotoSans-Bold.ttf
│   ├── NotoSans-Italic.ttf
│   └── NotoSans-BoldItalic.ttf
├── armenian/
│   ├── NotoSansArmenian-Regular.ttf
│   └── NotoSansArmenian-Bold.ttf
├── georgian/
│   ├── NotoSansGeorgian-Regular.ttf
│   └── NotoSansGeorgian-Bold.ttf
├── cjk-sc/
│   ├── NotoSansCJKsc-Regular.otf
│   └── NotoSansCJKsc-Bold.otf
├── OFL-1.1.txt
└── SHA256SUMS
```

## Upstream provenance

Latin/Cyrillic, Armenian and Georgian files are pinned to `notofonts/noto-fonts` commit:

`ffebf8c1ee449e544955a7e813c54f9b73848eac`

Simplified Chinese files are pinned to `notofonts/noto-cjk` commit:

`f8d157532fbfaeda587e826d4cd5b21a49186f7c`

The provisioning workflow downloads only these immutable commit URLs and records SHA-256 checksums. Once committed, normal builds and runtime do not download fonts.

## License

Noto fonts are distributed under SIL Open Font License 1.1. Keep `OFL-1.1.txt` with every redistributed application/package containing these files.
