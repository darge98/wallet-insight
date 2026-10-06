import nx from '@nx/eslint-plugin';
import prettier from 'eslint-config-prettier';

import baseConfig from './eslint.config.mjs';

/**
 * Regole Angular condivise da applicazione e librerie.
 *
 * Un solo file: i progetti la estendono e aggiungono soltanto ciò che è
 * davvero specifico (di norma nulla).
 */
export default [
  ...nx.configs['flat/angular'],
  ...nx.configs['flat/angular-template'],
  ...baseConfig,
  prettier,
  {
    files: ['**/*.ts'],
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],
      '@angular-eslint/prefer-on-push-component-change-detection': 'error',
      '@angular-eslint/prefer-standalone': 'error',
      '@angular-eslint/use-lifecycle-interface': 'error',
    },
  },
  {
    files: ['**/*.html'],
    rules: {
      '@angular-eslint/template/prefer-control-flow': 'error',
    },
  },
];
