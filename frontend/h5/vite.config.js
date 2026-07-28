import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vant embeds its icon font as a data URL and also declares a remote fallback.
// Strip the fallback so the build is self-contained and stays inside our CSP.
const stripVantRemoteFont = {
  name: 'strip-vant-remote-font',
  enforce: 'pre',
  transform(code, id) {
    if (!id.includes('vant') || !id.endsWith('.css')) return
    return code.replace(/,url\(\/\/at\.alicdn\.com\/[^)]+\) format\("woff"\)/g, '')
  }
}

// 构建产物直接输出到后端静态目录,由 8080 一体化托管(/h5/),
// 手机扫桌台二维码即可访问,无需单独的前端服务器
export default defineConfig({
  plugins: [stripVantRemoteFont, vue()],
  base: '/h5/',
  build: {
    outDir: '../../backend/dinehub-server/src/main/resources/static/h5',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: 'index.html',
        ops: 'ops.html'
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/user': { target: 'http://localhost:8080', changeOrigin: true },
      '/admin': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true }
    }
  }
})
