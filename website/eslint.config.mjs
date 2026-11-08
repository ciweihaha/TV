import { defineConfig } from "eslint/config";
import js from "@eslint/js";
import globals from "globals";
import nextPlugin from "@next/eslint-plugin-next";

// eslint-config-next 已移除：它捆绑的 typescript-eslint 尚不支持 TypeScript 7，
// 加载即报错。这里直接用 @next/eslint-plugin-next 的 core-web-vitals 配置，
// 等 typescript-eslint 支持 TS 7 后再恢复类型感知规则。类型检查仍由 tsc 负责。
export default defineConfig([
  { ignores: ["out/**", ".next/**", "node_modules/**"] },
  js.configs.recommended,
  nextPlugin.configs["core-web-vitals"],
  {
    files: ["scripts/**/*.mjs"],
    languageOptions: { globals: globals.node },
  },
]);
