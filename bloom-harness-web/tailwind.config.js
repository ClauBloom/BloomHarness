/** @type {import('tailwindcss').Config} */
// Tailwind is used for layout utilities only (flex/grid/gap/spacing/overflow/position).
// Colors, typography, radii, borders, shadows and motion come from the dsh design tokens
// in src/styles/*.css and each component's scoped CSS.
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,ts}",
  ],
  corePlugins: {
    preflight: false,
  },
  theme: {
    extend: {},
  },
  plugins: [],
}
