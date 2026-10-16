import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  host: '0.0.0.0',  // 允许局域网内其他机器访问
  port: 5173,
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/reports': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        bypass(req) {
          // 只代理 .html 报告文件，其他走 SPA（避免 F5 刷新 /reports 报 500）
          if (req.url && !/\.html(\?.*)?$/.test(req.url)) {
            return '/index.html'
          }
        },
      },
    },
  },
})
