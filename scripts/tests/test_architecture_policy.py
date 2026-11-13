import unittest

from scripts.architecture_policy import validate_edges


class ArchitecturePolicyTest(unittest.TestCase):
    def test_rejects_a_forbidden_module_dependency(self):
        errors = validate_edges(
            allowed={":feature:assist": {":core:model"}},
            actual={":feature:assist": {":core:model", ":data:poi"}},
        )

        self.assertEqual(
            errors,
            ["Forbidden module dependency: :feature:assist -> :data:poi"],
        )

    def test_requires_every_module_to_have_a_policy_entry(self):
        errors = validate_edges(
            allowed={":core:model": set()},
            actual={":core:model": set(), ":data:energy": {":core:model"}},
        )

        self.assertEqual(
            errors,
            ["Declare :data:energy in allowedEdges before adding the module"],
        )


if __name__ == "__main__":
    unittest.main()
