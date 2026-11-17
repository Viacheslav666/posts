import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './src/**/*.{ts,tsx}',
    '../../packages/ui/src/**/*.{ts,tsx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Source Serif 4"', 'Georgia', 'serif'],
        sans: ['"IBM Plex Sans"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      colors: {
        ink: {
          950: '#0b1220',
          900: '#121a2b',
          700: '#334155',
        },
        pine: {
          600: '#0f766e',
          500: '#14b8a6',
        },
      },
    },
  },
  plugins: [],
};

export default config;
