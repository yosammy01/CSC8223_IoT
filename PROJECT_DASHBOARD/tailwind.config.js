/**
 * ============================================================================
 * FILE README: tailwind.config.js
 * ============================================================================
 * Purpose:
 *   Tailwind CSS content scanning and theme customization configuration.
 *
 * Details:
 *   Specifies template paths (`./index.html` and all files under `./src/`)
 *   for utility class extraction.
 * ============================================================================
 */
/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}

