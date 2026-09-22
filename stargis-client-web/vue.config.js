const path = require('path')
const CompressionPlugin = require('compression-webpack-plugin')
/**
 * 路径解析工具函数
 * @param {string} dir - 需要拼接的目录名
 * @returns {string} - 返回绝对路径
 */
function resolve(dir) {
  return path.join(__dirname, dir)
}

module.exports = {
  // 是否在生产环境下生成 source map 文件，关闭可加快打包速度并减少包体积
  productionSourceMap: false,

  // 构建输出目录，打包后的文件将输出到该路径
  outputDir: '../dist/stargisWebGL',

  // 公共路径配置，生产环境下为相对路径，开发环境下为根路径
  publicPath: process.env.NODE_ENV === 'production' ? './' : '/',

  /**
   * 对 webpack 配置进行扩展
   * @param {object} config - webpack 配置对象
   */
  configureWebpack: config => {
    if (process.env.NODE_ENV === 'production') {
      // 生产环境下去除所有 console 语句，减少包体积并提升安全性
      config.optimization.minimizer[0].options.terserOptions.compress.drop_console = true
    }
  },

  /**
   * 使用 webpack-chain 进行更细粒度的 webpack 配置
   * @param {object} config - webpack-chain 配置对象
   */
  chainWebpack: config => {
    config.resolve.alias
      .set('@$', resolve('src')) // src 根目录
      .set('@api', resolve('src/api')) // api 目录
      .set('@assets', resolve('src/assets')) // 静态资源目录
      .set('@comp', resolve('src/components')) // 组件目录
      .set('@views', resolve('src/views')) // 视图目录

    /**
     * 【性能·开发环境】去掉动态导入的 prefetch 预取。
     *
     * @vue/cli 默认给每个 () => import() 加上 webpackPrefetch: true，
     * 本项目有 300+ 个路由分包，实测打开首页时浏览器会额外发起 340 个 .js 请求、
     * 多传 38.6MB；而 dev-server 是单进程，这些低优先级请求会把带宽和事件循环
     * 全占住，导致首屏期间整个页面的输入、下拉、表格都发卡。
     * 关掉之后按需加载（点击路由时才拉对应分包），首屏请求数从 368 降到 30 以内。
     */
    config.plugins.delete('prefetch')

    /**
     * 【性能·开发环境】把开发态 devtool 从默认的 eval-source-map 换成 cheap-module-source-map。
     *
     * 实测开发态 app.js 曾达到 88.9MB，其中约 85MB 是 4930 段以 base64 内联的 sourceMappingURL
     * （vue-cli 默认的 eval-source-map 会把每个模块的源码映射都拼进 bundle 字符串里）。
     * 浏览器每次打开都要下载并解析这 89MB，单这一个请求就要 2.6 秒，是首屏最大的单项开销；
     * 内联 source map 还会让 V8 走 eval 编译路径，进一步拖慢启动。
     *
     * 换成 cheap-module-source-map 后源码映射改为独立 .map 文件，只在打开 DevTools 时才拉取，
     * 断点调试依然可用（行号准确，列号不精确），但日常浏览不再付出这 85MB 的代价。
     * 想恢复原来的调试体验，删掉这段即可。
     */
    if (process.env.NODE_ENV !== 'production') {
      config.devtool('cheap-module-source-map')
    }

    // 生产环境下启用 gzip 压缩，提升资源加载速度
    if (process.env.NODE_ENV === 'production') {
      config.plugin('compressionPlugin').use(
        new CompressionPlugin({
          test: /\.(js|css|less)$/, // 需要压缩的文件类型
          threshold: 10240, // 只压缩大于 10KB 的文件
          deleteOriginalAssets: false // 不删除原始文件
        })
      )
    }

    // 配置 webpack 识别 markdown 为普通的文件
    config.module
      .rule('markdown')
      .test(/\.md$/)
      .use()
      .loader('file-loader')
      .end()

    // 编译vxe-table包里的es6代码，解决IE11兼容问题
    config.module
      .rule('vxe')
      .test(/\.js$/)
      .include.add(resolve('node_modules/vxe-table'))
      .add(resolve('node_modules/vxe-table-plugin-antd'))
      .end()
      .use()
      .loader('babel-loader')
      .end()
  },

  css: {
    loaderOptions: {
      less: {
        modifyVars: {
          /* less 变量覆盖，用于自定义 ant design 主题 */
          // 自定义 less 变量，实现主题色和圆角等样式定制
          'primary-color': '#00b4ff',
          'link-color': '#00b4ff',
          'border-radius-base': '4px'
        },
        javascriptEnabled: true
      }
    }
  },

  devServer: {
    port: 3000,
    proxy: {
      '/jeecg-boot': {
        target: 'http://localhost:8080',
        ws: false,
        changeOrigin: true
      }
    }
  },

  // 是否在保存时进行代码规范检查（eslint），undefined 表示使用默认配置
  lintOnSave: undefined
}
