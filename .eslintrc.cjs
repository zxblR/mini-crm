module.exports = {
  globals: {
    ElMessage: 'readonly',
    ElMessageBox: 'readonly',
    ElNotification: 'readonly',
    ElLoading: 'readonly',
  },
  root: true,
  env: {
    es2022: true,
    node: true,
    browser: true,
  },
  extends: ['eslint:recommended', 'plugin:vue/vue3-recommended', 'prettier'],
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  plugins: ['@typescript-eslint'],
  ignorePatterns: ['**/dist/**', '**/node_modules/**', '**/coverage/**'],
  rules: {
    'vue/multi-word-component-names': 'off',
    'vue/no-v-html': 'error',
    '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
  },
};
