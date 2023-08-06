import resolve from '@rollup/plugin-node-resolve';
import typescript from '@rollup/plugin-typescript';
import commonjs from '@rollup/plugin-commonjs';

export default {
  input: 'js_src/index.ts',
  output: {
    dir: 'static',
    format: 'es',
  },
  plugins: [
    typescript(),
    resolve(),
    commonjs(),
  ],
  preserveEntrySignatures: 'strict',
};
