# Collectra PDF fonts

Collectra keeps PDF rendering independent from host/container fonts and runtime network access.

## Font profiles

| Profile | Family | Source | Locales/scripts |
| --- | --- | --- | --- |
| `LATIN_CYRILLIC` | Noto Sans | application resources | `ru`, `kk`, `en`, `uz`, `ky`, `tg`, `az`, `be`, `uk` |
| `ARMENIAN` | Noto Sans Armenian | application resources | `hy` |
| `GEORGIAN` | Noto Sans Georgian | application resources | `ka` |
| `CJK_SC` | Noto Sans SC | pinned Maven dependency `com.helger.font:ph-fonts-noto-sans-sc` | `zh-CN` |

OpenHTMLToPDF 1.0.10 does not support the previously vendored OpenType CJK fonts. `zh-CN` therefore uses the TrueType Noto Sans SC resources packaged inside the pinned `ph-fonts-noto-sans-sc` dependency. They are still classpath resources inside the packaged application; PDF generation performs no runtime network access and does not depend on fonts installed on the host.

The dependency resources used by `FontProfileRegistry` are:

```text
fonts/ttf/NotoSansSC/NotoSansSC-Regular.ttf
fonts/ttf/NotoSansSC/NotoSansSC-Bold.ttf
```

The dependency project validates its font binaries against PDFBox. Collectra additionally verifies actual rendered text in `PdfRendererLocaleUnitTest` by extracting text from the produced PDF, including representative Simplified Chinese glyphs.

## Application-owned font resources

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
├── OFL-1.1.txt
└── SHA256SUMS
```

Latin/Cyrillic, Armenian and Georgian files are pinned to `notofonts/noto-fonts` commit:

`ffebf8c1ee449e544955a7e813c54f9b73848eac`

`scripts/vendor-fonts.sh` downloads only those immutable application-owned resources and records SHA-256 checksums.

## License

Noto fonts are distributed under SIL Open Font License 1.1. The application-owned font license is kept in `OFL-1.1.txt`; dependency licensing is retained in the Maven dependency metadata/package.
