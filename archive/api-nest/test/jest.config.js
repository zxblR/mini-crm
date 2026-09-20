module.exports = {
  moduleFileExtensions: ['js', 'json', 'ts'],
  rootDir: '.',
  testRegex: '.*\\.spec\\.ts$',
  transform: { '^.+\\.ts$': ['ts-jest', { tsconfig: './tsconfig.json' }] },
  testEnvironment: 'node',
  moduleNameMapper: { '^@mini-crm/shared$': '<rootDir>/../../../packages/shared/src/index.ts' },
};
