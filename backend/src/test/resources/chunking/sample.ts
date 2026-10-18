import { Component } from 'framework';

// Service class
export class UserService {
    private endpoint: string;

    constructor(endpoint: string) {
        this.endpoint = endpoint;
    }

    getUser(id: string): string {
        return "user-" + id;
    }
}

// Top-level exported arrow function assigned to const
export const formatName = (first: string, last: string): string => {
    return `${first} ${last}`.trim();
};

// Top-level regular function declaration
function calculateTotal(items: number[]): number {
    return items.reduce((acc, curr) => acc + curr, 0);
}
