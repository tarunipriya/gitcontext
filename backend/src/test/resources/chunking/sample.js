// Plain JavaScript sample fixture

class Calculator {
    constructor(base = 0) {
        this.base = base;
    }

    add(n) {
        return this.base + n;
    }

    subtract(n) {
        return this.base - n;
    }
}

// Arrow function assigned to a const
const multiply = (a, b) => {
    return a * b;
};

// Function declaration
function divide(a, b) {
    if (b === 0) {
        throw new Error("Division by zero");
    }
    return a / b;
}
