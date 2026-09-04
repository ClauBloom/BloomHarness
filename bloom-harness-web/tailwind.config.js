/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#f5f3ff',
          500: '#8b5cf6',
          600: '#7c3aed',
          700: '#6d28d9',
        }
      }
    },
  },
  plugins: [],
}
