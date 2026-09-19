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
