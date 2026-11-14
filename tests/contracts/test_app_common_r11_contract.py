#!/usr/bin/env python3
"""APP-COMMON-R1.1: executable contract RED checks, standard library only.

Run: python3 -m unittest discover -s tests/contracts -p 'test_app_common_r11_contract.py' -v
These tests validate a future OpenAPI JSON contract, not live Keycloak/PostgreSQL.
Missing contract is an intentional *contract gate*, NOT a valid infrastructure RED result.
"""
import json
import os
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SPEC = pathlib.Path(os.environ.get("APP_COMMON_OPENAPI", str(ROOT / "contracts" / "app-common-openapi.json")))


class AppCommonR11ContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not SPEC.is_file():
            raise AssertionError(
                f"OpenAPI contract missing: {SPEC}. Publish real OpenAPI JSON before claiming a behavioral RED test."
            )
        cls.spec = json.loads(SPEC.read_text(encoding="utf-8"))
        cls.paths = cls.spec.get("paths", {})

    def assert_endpoint(self, method, path, expected_status):
        operation = self.paths.get(path, {}).get(method)
        self.assertIsInstance(operation, dict, f"Missing {method.upper()} {path}")
        self.assertTrue(operation.get("security"), f"{method.upper()} {path} must declare operation security")
        self.assertIn(expected_status, operation.get("responses", {}),
                      f"{method.upper()} {path} must declare {expected_status}")
        self.assertIn("401", operation.get("responses", {}), "Unauthorized response required")
        self.assertIn("403", operation.get("responses", {}), "Forbidden response required")

    def test_R11_RED_01_register_application(self):
        self.assert_endpoint("post", "/api/v1/platform/applications", "202")

    def test_R11_RED_06_submit_notification(self):
        self.assert_endpoint("post", "/api/v1/notifications", "202")
        operation = self.paths["/api/v1/notifications"]["post"]
        self.assertIn("409", operation["responses"], "Idempotency conflict must be explicit")

    def test_R11_RED_07_suspend_application(self):
        self.assert_endpoint("post", "/api/v1/platform/applications/{id}/suspend", "202")
        self.assert_endpoint("get", "/api/v1/platform/operations/{id}", "200")

    def test_R11_admin_operations_are_private(self):
        for path in ("/api/v1/platform/applications", "/api/v1/platform/applications/{id}/suspend"):
            for op in self.paths.get(path, {}).values():
                if isinstance(op, dict) and "responses" in op:
                    self.assertTrue(op.get("security"), "Platform endpoints must declare auth")
        self.assertIn("securitySchemes", self.spec.get("components", {}))

    def test_R11_openapi_has_real_metadata(self):
        self.assertEqual(self.spec.get("openapi", "").split(".")[0], "3")
        self.assertTrue(self.spec.get("info", {}).get("version"))
        self.assertTrue(self.spec.get("info", {}).get("title"))


if __name__ == "__main__":
    unittest.main()
