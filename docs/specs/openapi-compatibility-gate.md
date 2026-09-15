# OpenAPI Compatibility Gate

`src/test/resources/openapi/collectra-api-v1-baseline.json` is the reviewed public
contract baseline. The integration test generates the current Springdoc document,
keeps only `/api/v1` paths, removes environment-dependent servers and rejects breaking
changes with OpenAPI Diff.

Additive changes are allowed. A deliberate breaking change requires an explicit API
version/migration decision; do not update the baseline merely to make CI green.

After an approved additive or versioned contract change, regenerate and review:

```bash
bash scripts/update-openapi-baseline.sh
git diff -- src/test/resources/openapi/collectra-api-v1-baseline.json
```

The generated current document is also written to
`target/openapi/collectra-api-v1-current.json` for CI diagnostics.
