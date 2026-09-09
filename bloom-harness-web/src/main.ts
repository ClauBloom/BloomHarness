import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { initTheme } from './theme/useTheme';

// Style order mirrors dsh ui-theme/styles.ts: base → corner-shape → tokens →
// elevation/typography → scrollbar → shiki, then the global markdown sheet.
// Tailwind (layout utilities only) is loaded first so token sheets win ties.
import './styles/main.css';
import './styles/base.css';
import './styles/corner-shape.css';
import './styles/design-platform.css';
import './styles/elevation-typography.css';
import './styles/scrollbar.css';
import './styles/shiki.css';
import './styles/markdown.css';

initTheme();

const app = createApp(App);
app.use(createPinia());
app.mount('#app');
