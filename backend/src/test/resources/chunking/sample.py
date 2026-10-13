# Module-level documentation and configuration
import os
import sys

DEFAULT_TIMEOUT = 30


# Class documentation
@decor_class
class Calculator:
    """Performs calculations."""

    @audit
    def add(self, a, b):
        return a + b

    def subtract(self, a, b):
        return a - b

    def multiply(self, factor, items):
        """Multiplies each item in the collection by the specified factor.

        Iterates through the dataset and applies multiplication.
        """
        result = []
        for item in items:
            result.append(item * factor)
        return result


# Top-level decorated function
@timing
def compute_metrics(data):
    """Computes basic metrics."""
    return len(data)


def main():
    calc = Calculator()
    print(calc.add(1, 2))
