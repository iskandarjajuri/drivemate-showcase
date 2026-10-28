module.exports = {
  root: true,
  env: {
    es6: true,
    node: true,
  },
  extends: [
    "eslint:recommended",
    "plugin:import/errors",
    "plugin:import/warnings",
    "plugin:import/typescript",
    "google",
    "plugin:@typescript-eslint/recommended",
  ],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    project: ["tsconfig.eslint.json"],
    sourceType: "module",
  },
  ignorePatterns: [
    "/lib/**/*", // Ignore built files.
    "/generated/**/*", // Ignore generated files.
  ],
  plugins: [
    "@typescript-eslint",
    "import",
  ],
  rules: {
    "quotes": ["error", "double"],
    "import/no-unresolved": 0,
    "indent": ["error", 2],
  },
  overrides: [
    {
      files: ["test/**/*.js"],
      env: {
        jest: true,
      },
    },
    {
      // Test and local tooling scripts are not production output — don't
      // enforce production-code style (line length, doc comments, ES-module
      // require ban) on them. Everything else (no-undef, unused vars, etc.)
      // still applies.
      files: ["test/**/*.js", "scripts/**/*.js"],
      rules: {
        "max-len": "off",
        "@typescript-eslint/no-var-requires": "off",
        // v8 mengganti no-var-requires dengan no-require-imports; test dan
        // skrip CommonJS memang memakai require(), cakupannya tetap sama.
        "@typescript-eslint/no-require-imports": "off",
        "require-jsdoc": "off",
        "valid-jsdoc": "off",
      },
    },
  ],
};
