import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const variablesPath = fileURLToPath(new URL('./src/styles/_variables.scss', import.meta.url)).replace(/\\/g, '/')
  const mixinsPath = fileURLToPath(new URL('./src/styles/_mixins.scss', import.meta.url)).replace(/\\/g, '/')

  return {
    plugins: [
      vue({
        template: {
          compilerOptions: {
            compatConfig: { MODE: 2 }
          }
        }
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
        'vue': '@vue/compat'
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `@use "${variablesPath}" as *;\n@use "${mixinsPath}" as *;`
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: 8888,
      proxy: {
        '/api': {
          target: env.VITE_APP_URL || 'http://localhost:8080/admin',
          changeOrigin: true,
          rewrite: (requestPath) => requestPath.replace(/^\/api/, '')
        }
      }
    },
    build: {
      target: 'es2022'
    }
  }
})
