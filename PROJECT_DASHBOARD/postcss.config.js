/**
 * ============================================================================
 * FILE README: postcss.config.js
 * ============================================================================
 * Purpose:
 *   PostCSS configuration for compiling Tailwind CSS v4 utility classes.
 *
 * Details:
 *   Uses `@tailwindcss/postcss` required by Tailwind CSS v4 (`^4.3.3`) to
 *   process `@import "tailwindcss";` in `src/index.css`.
 * ============================================================================
 */
export default {
  plugins: {
    '@tailwindcss/postcss': {},
  },
}

