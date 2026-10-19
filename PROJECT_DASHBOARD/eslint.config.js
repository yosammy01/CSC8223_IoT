/**
 * ============================================================================
 * FILE README: eslint.config.js
 * ============================================================================
 * Purpose:
 *   Flat ESLint configuration for TypeScript and React 19 code quality checks.
 *
 * Details:
 *   Enforces recommended JavaScript, TypeScript, React Hooks, and Vite React
 *   Refresh rules across all `.ts` and `.tsx` source files while ignoring
 *   build output in `dist/`.
 * ============================================================================
 */
import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
])
