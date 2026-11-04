/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#fff1f6',
          100: '#ffe3ed',
          600: '#af0f50',
          700: '#930b42',
          900: '#4f0927'
        },
        ink: '#1a1a18'
      }
    }
  },
  plugins: []
};
