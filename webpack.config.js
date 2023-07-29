const path = require('path');

module.exports = {
  mode: 'production',
  entry: './js_src/index.js',
  output: {
    path: path.resolve(__dirname, 'static'),
    filename: 'main.js',
  },
};
