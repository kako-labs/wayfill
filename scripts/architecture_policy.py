"""Pure policy helper used by architecture checks and their unit tests."""


def validate_edges(allowed, actual):
    errors = []
    for module in sorted(actual):
        if module not in allowed:
            errors.append(f"Declare {module} in allowedEdges before adding the module")
            continue
        for dependency in sorted(actual[module] - allowed[module]):
            errors.append(f"Forbidden module dependency: {module} -> {dependency}")
    return errors
