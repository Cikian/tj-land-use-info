/* eslint-disable eqeqeq */
/* eslint-disable space-infix-ops */
/* eslint-disable no-useless-return */
/* eslint-disable camelcase */
/* eslint-disable func-call-spacing */
/* eslint-disable no-unexpected-multiline */
const fs = require('fs-extra')
const path = require('path')
const readline = require('readline')
let moduleEventName = ''
// 添加模板字符串常量
const TEMPLATE_INDEX = `<template>
  <div class="measure-line-btn" :class="checked ? 'btn-checked' : ''" @click="btnClick">
    <div class="title">{{ configMin ? configMin.name : '新增功能名称' }}</div>
    <div class="btn-img" :style="{ backgroundImage: \`url(\${imageUrl})\` }"></div>
  </div>
</template>

<script>
export default {
  name: 'index',
  props: {
    configMin: Object
  },
  data() {
    return {
      checked: false,
      centerImage:  'map-btns/DefaultComponent',
      imageUrl: ''
    }
  },
  created() {
    if (this.configMin.icon) {
      this.centerImage = this.configMin.icon
    }
    this.imageUrl = require(\`@/assets/\${this.centerImage}/default.png\`)
  },
  methods: {
    btnClick() {
      // 单选
      if (!this.checked) {
        this.$parent.cancelAll()
      }
      this.checked = !this.checked
      this.checked ? this.onCheckedBtn() : this.cancelChecked()
    },
    onCheckedBtn() {
      this.checked = true
      this.imageUrl = require(\`@/assets/\${this.centerImage}/checked.png\`)
      this.$bus.$emit('showFunctionNamePop', true)
    },
    cancelChecked() {
      this.checked = false
      console.log('cancel')
      this.imageUrl = require(\`@/assets/\${this.centerImage}/default.png\`)
      this.$bus.$emit('showFunctionNamePop', false)
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/assets/less/common_btn.less';
</style>`

const TEMPLATE_POP = `<!-- eslint-disable no-unused-vars -->
<template>
  <div class="pop pop-left" v-show="isShow">
    <div class="pop-header">
      <div class="pop-title">
        <div class="pop-title-logo"></div>
        <div class="pop-title-font">{{ configMin ? configMin.name : '功能标题' }}</div>
      </div>
      <div class="pop-close" @click="closePop"></div>
    </div>
    <div class="pop-body" style="min-height: 10px">
      <div class="pure-control-group">
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'FunctionName',
  props: {
    configMin: Object
  },
  data() {
    return {
      isShow: false
    }
  },
  created() {
    this.popShowControl()
    this.iconUrl = this.configMin.icon || 'map-btns/DefaultComponent'
  },
  mounted() {
  },
  methods: {
    closePop() {
      this.isShow = false
      this.$bus.$emit('cancelModuleEventNameChecked', true)
    },
    showPop() {
      this.isShow = true
    },
    popShowControl() {
      this.$bus.$once('showFunctionNamePop', val => {
        val === true ? this.showPop() : this.closePop()
      })
    },
  },
  watch: {
    isShow(newVal, oldVal) {
      if (!newVal) {
        this.popShowControl()
      } else {
        this.popShowControl()
      }
    },
  }
}
</script>
<style scoped lang="less">
.pop-close {
  width: 26px;
  height: 26px;
  background-image: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABoAAAAaCAYAAACpSkzOAAAABHNCSVQICAgIfAhkiAAABJNJREFUSIndlkuI3UUWxn/n1P99TXeiSB4GphkawTZRZpdoXDjiQnQRQRiYxWxmGIRBXYguVBAXIoLgIiBEF64GRkF05WPcCD4wuDEBW5Dr0EJrMkGH2KT/z6pTLm5fc+9NgzKLWcyBWlTVqfr4Tp3znYL/N5O5WYyy6/rO7mRH4oL/r/K94nQFRAFdZazTrYzVuA7GZMSZs7oG2jMWTyIJPl7luwO2CKRr4C5xPmnJkyVUAbYwK+j8Xg76nUviGrgtNl1NlV6Dc4ZIgtk2gx+x7TdY8YAhYotACrhDnE+VUeHo8wFNQESx4Mn7nr4r6LwSYkuRZOS50ucBl0QQh/lA1nnadi+X+jGrU7D4c3gAVhlrS544+jyQjc405UOC7BN0X4ItV8Q9xp6RsTwqYY/DlgXdB3Ld6Tae+LTJHxZ8lZNnLYXOEkkWX3EJ1R5JzzTpX6+N8bH1Ojt+pOqegKEDbRxtN/F0WcRX4KoPm+TkwSiPADiGVwY6B+kcCV0Emtqxsvu7h48SOHGuzp8DvS6i1ytuv+L2G7o/otf/s0kemIJclPiiIbveObeYsRq3MFMsKDEcqfone/g4g9vP1elTiu2HeAi4QbAD77bpfb+J+jeATbFTd5TtPxypd1SWMsxm6BxQXAcr6Lwn7yOujYT2aNU922Gf5Ohtn9f5Q4YehHjorTa787emDwJ8I/bS78vwFmhrhK7AD0scDsyk9+Ib2V4O+gv8p0uIDWgT0e7Wajj1eZ1KgR4/W2cy1vjVTSZ/AvhaOX1v4d8DqyGpI9L2/Nj/i31zjK4AiURiZB3sEJ337OkdbacwQIy3VP71s3UmJRw7anIMYF3jq/cX3WdAD9pGfNtCf2DCxn4pGaISohKvSM2klnSs8avpygCbJ4t+LKgJmBCDYuYItr6LBC0CyRq4liJxdJniMkOzCO7NNl87uhMuYCOFw+fq/D4mD+ECmgZcMiJNduRLZmRtBmhHgrbYdBl5DmkZsRFY8U6bnrjJ5M/TcN1Yda802KcFHD9bZ38RJBVCAaGs6fMfWU4XScxOZA20pkqVPo9YBa58v0nvmWbX18rp+4vuY0XO/67yL3fwSQ63nauzxwVXQFI5sqIkS39mtVvoesZyDc55XBrQ9IMmPTmtk2/EXrq36N8GvgX7NhIv3Fr1z0/rbL1On1FC6tC0xyU1IzcbvjlGnkQMEYAzTfLHacVvip26uwxvGHYxohcicj6g/w7EH45Uw9NTBfliu3gBOolsX9XXZusoJvgIZoaFL4mfnUC4KPHFu8ruNUGbSZ34VjATSCCUEalurppHP2qKP6hgjswbanB5PstmkkHXILnI97kjlEpeKj6PIEoWIr6NpG3L0DuCjUiTmj53ZIVjyAAcmYehFZIGLrUbrAzstIk5RutgK1wetliSEhcG+nZCW0KBDC2XhwOT5heXGQ+RZZ8ifU+ZRLbFUMtIfE/Tf8fKnAQtdlhh0sZdzcgFVABSBlvicJhp0dOzusKG68k1oOKwmNHZxgTEpmzmgebBFj8dcWZM5eqXfXf9nFwNOG+zv5//1vd/YT8BORc465+otLcAAAAASUVORK5CYII=");
  position: absolute;
  right: 10px;
  top: 10px;
  cursor: pointer;
}
</style>`

const MODULE_BODY = `<template>
  <div class="home-body">
  </div>
</template>

<script>
export default {
  name: 'HomeModuleNameBody',
  components: {
  },
  data() {
    return {
      commonConfig: {}
    }
  },
  created() {
    this.getCommonConfig()
  },
  methods: {
    getCommonConfig() {
      let arr = this.$store.getters.sysConfig['ModuleName']
      arr.children.map((item, index) => {
        this.commonConfig[item.componentname] = item
      })
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/assets/less/common_btn.less';
@import '~@/assets/less/common_pop.less';

.home-body {
  position: absolute;
  bottom: 88px;
  left: 0;
  width: 100%;
  height: calc(100% - 75px - 80px);
  z-index: 1001;
  display: inline-flex;
  pointer-events: none;
}
</style>`

const MODULE_FOOTER = `<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
      </div>
    </div>
    <div class="common-btn" style="float: right" @click="controlAll">
      <div class="title"></div>
      <div
        class="btn-img"
        :style="{ backgroundImage: \`url(\${imageUrl})\`, position: 'absolute', top: '21px', left: '16px' }"></div>
    </div>
  </div>
</template>
<script>
export default {
  name: 'HomeModuleNameFooter',
  components: {
  },
  data() {
    return {
      allShow: true,
      imageUrl: '',
      commonConfig: {},
      commonSort: {},
      mode: 3
    }
  },
  created() {
    this.imageUrl = require(\`@/assets/map-btns/BtnsControl/checked.png\`)
    this.$bus.$on('cancelModuleNameChecked', (val) => {
      val && this.cancelAll()
    })
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
    this.$bus.$on('bkRollerCompareshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.RollerCompare &&
        (this.commonConfig.RollerCompare.functionType == '23' ||
          this.commonConfig.RollerCompare.functionType == this.mode)
      ) {
        this.$bus.$emit('bkRollerCompareBK', val)
      } else {
        this.$message.error(\`\${this.mode}维模式不包含此书签绑定的功能\`)
      }
    })
  },
  methods: {
    getCommonConfig() {
      try {
        let arr = this.$store.getters.sysConfig['ModuleName']
        // 兼容旧版本中台可能没有functionType全部无法显示的问题
        for (let i = 0; i < arr.children.length; i++) {
          if (!arr.children[i].functionType) {
            arr.children[i].functionType = 23
          }
        }
        arr.children.map((item, index) => {
          this.commonConfig[item.componentname] = item
          this.commonSort[item.componentname] = index + 1
        })
      } catch (error) { }
    },
    controlAll() {
      // 控制所有按钮的显示隐藏
      this.allShow = !this.allShow
      this.imageUrl = this.allShow
        ? require(\`@/assets/map-btns/BtnsControl/checked.png\`)
        : require(\`@/assets/map-btns/BtnsControl/default.png\`)
    },
    cancelAll() {
    },
    change3D2Dmode(val) {
      this.mode = val
    }
  }
}
</script>

<style scoped lang="less">
.common-btn {
  background-image: url('~@/assets/map-btns/default.png');
  background-repeat: no-repeat;
  background-size: 100%;
  justify-content: center;
  cursor: pointer;
  position: relative;
  pointer-events: initial;

  .title {
    color: #ffffff;
    line-height: 20px;
    background-color: rgba(0, 0, 0, 0.4);
    text-align: center;
    border-radius: 20px;
    padding: 0 6px;
    min-width: 80px;
  }

  .btn-img {
    width: 48px;
    height: 48px;
    background-repeat: no-repeat;
    background-position: center;
  }
}
</style>`

;(async () => {
  const functionAlias = await askQuestion('请输入要新增的功能名称：')
  const functionName = await askQuestion('请输入功能组件名，PascalCase格式：')
  const functionMode = await askQuestion('请输入地图模式(2:二维, 3:三维, 23:二三维)：', '23')
  // 验证输入的模式是否合法
  if (!['2', '3', '23'].includes(functionMode)) {
    console.error('地图模式输入错误，只能是2、3或23！')
    process.exit(1)
  }
  const moduleName = await askQuestion('请输入模块文件夹名，PascalCase格式：')
  // 提取 moduleName 的前半部分赋值给 moduleName_abbr
  const matchResult = moduleName.match(/^[A-Z][a-z]*/)
  const moduleName_abbr = matchResult ? matchResult[0] : moduleName
  const destinationFunPath = path.resolve(__dirname, `./src/views/maps/components/${moduleName}/${functionName}`)
  const destinationModulePath = path.resolve(__dirname, `./src/views/maps/components/${moduleName}`)
  if (fs.existsSync(destinationFunPath)) {
    console.error(`模块文件夹 "${moduleName}" 中已存在 "${functionName}"功能！`)
    process.exit(1)
  }
  if (!fs.existsSync(destinationModulePath)) {
    const isCreateModule = await askQuestion(`不存在模块文件夹${moduleName}，是否创建？(Y/N)`)
    if (isCreateModule == 'Y' || isCreateModule == 'y') {
      createNewModule(destinationModulePath, moduleName)
    } else {
      process.exit(1)
    }
  }
  setTimeout(() => {
    createNewFunction(destinationFunPath, moduleName, moduleName_abbr, functionName, functionAlias, functionMode)
  }, 200)
})()

function createNewModule(modulePath, moduleName) {
  fs.mkdirSync(modulePath)
  // 创建并写入 Body 文件
  const bodyPath = path.join(modulePath, `Home${moduleName}Body.vue`)
  fs.writeFileSync(bodyPath, MODULE_BODY.replace(/ModuleName/g, moduleName), 'utf8')
  // 创建并写入 Footer 文件
  const footerPath = path.join(modulePath, `Home${moduleName}Footer.vue`)
  fs.writeFileSync(footerPath, MODULE_FOOTER.replace(/ModuleName/g, moduleName), 'utf8')
  // 提取事件名称
  moduleEventName = moduleName
  const hyphenName_m = camelToHyphen(moduleName)
  const codecontent_m = [
    {
      findString: `</home-basic-body>`,
      insertString: `    <home-${hyphenName_m}-body ref="home${moduleName}Body" v-if="showFun['${moduleName}']"></home-${hyphenName_m}-body>
    <home-${hyphenName_m}-footer ref="home${moduleName}Footer" v-if="showFun['${moduleName}']"></home-${hyphenName_m}-footer>`
    },
    {
      findString: `<script>`,
      insertString: `import Home${moduleName}Body from './components/${moduleName}/Home${moduleName}Body'
import Home${moduleName}Footer from './components/${moduleName}/Home${moduleName}Footer'`
    },
    {
      findString: `components: {`,
      insertString: `    Home${moduleName}Body,
    Home${moduleName}Footer,`
    },
    {
      findString: `showFun: {`,
      insertString: `        ${moduleName}: false,`
    }
  ]
  const desModulePath_m = path.resolve(__dirname, `./src/views/maps/index.vue`)
  fs.readFile(desModulePath_m, 'utf8', (err, data) => {
    if (err) {
      console.error('Error reading file :', err)
      return
    }
    let modifiedData = data
    codecontent_m.forEach(item => {
      const insertPoint = modifiedData.indexOf(item.findString)
      if (insertPoint === -1) {
        console.error('some content not found')
        return
      }
      modifiedData =
        modifiedData.slice(0, insertPoint + item.findString.length) +
        '\n' +
        item.insertString +
        modifiedData.slice(insertPoint + item.findString.length)
    })

    fs.writeFile(desModulePath_m, modifiedData, 'utf8', err => {
      if (err) {
        console.error('写入文件失败:', err)
        return
      }
    })
  })
}

function createNewFunction(destinationFunPath, moduleName, moduleName_abbr, functionName, functionAlias, functionMode) {
  fs.mkdirSync(destinationFunPath)
  // 创建并写入 index.vue，替换功能名称和图标路径
  const indexPath = path.join(destinationFunPath, 'index.vue')
  let indexContent = TEMPLATE_INDEX.replace(/FunctionName/g, functionName)
  indexContent = indexContent.replace('新增功能名称', functionAlias)
  fs.writeFileSync(indexPath, indexContent, 'utf8')
  // 创建并写入 Pop.vue
  const popPath = path.join(destinationFunPath, `${functionName}Pop.vue`)
  let popContent = TEMPLATE_POP.replace(/FunctionName/g, functionName)
  popContent = popContent.replace(/ModuleName/g, moduleName)
  // 修正iconUrl默认值为functionName变量
  popContent = popContent.replace(
    "this.iconUrl = this.configMin.icon || 'map-btns/DefaultComponent'",
    `this.iconUrl = this.configMin.icon || 'map-btns/${functionName}'`
  )
  fs.writeFileSync(popPath, popContent, 'utf8')
  const codecontent_b = [
    {
      findString: `<div class="home-body">`,
      insertString: `  <${camelToHyphen(
        functionName
      )}-pop ref="${functionName}Pop" v-if="commonConfig && commonConfig.${functionName}"
      :configMin="commonConfig && commonConfig.${functionName}"></${camelToHyphen(functionName)}-pop>`
    },
    {
      findString: `<script>`,
      insertString: `  import ${functionName}Pop from './${functionName}/${functionName}Pop.vue'`
    },
    {
      findString: `components: {`,
      insertString: `  ${functionName}Pop,`
    }
  ]
  const codecontent_f = [
    {
      findString: `<div class="all-btns" v-show="allShow">`,
      insertString: `        <${camelToHyphen(functionName)} ref="${functionName}"
          :style="{ order: commonSort && commonSort.${functionName} ? commonSort.${functionName} : 1 }"
          v-if="commonConfig && commonConfig.${functionName} && (commonConfig.${functionName}.functionType == '${functionMode}')"
          :configMin="commonConfig && commonConfig.${functionName}"></${camelToHyphen(functionName)}>`
    },
    {
      findString: `<script>`,
      insertString: `import ${functionName} from './${functionName}/index.vue'`
    },
    {
      findString: `components: {`,
      insertString: `    ${functionName},`
    },
    {
      findString: `cancelAll() {`,
      insertString: `      this.$refs.${functionName} && this.$refs.${functionName}.checked && this.$refs.${functionName}.cancelChecked()`
    }
  ]
  const desModulePath_b = path.resolve(__dirname, `./src/views/maps/components/${moduleName}/Home${moduleName}Body.vue`)
  const altModulePath_b = path.resolve(
    __dirname,
    `./src/views/maps/components/${moduleName}/Home${moduleName_abbr}Body.vue`
  )
  fs.access(desModulePath_b, fs.constants.F_OK, err => {
    if (err) {
      fs.access(altModulePath_b, fs.constants.F_OK, fallbackErr => {
        if (fallbackErr) {
          console.error('文件未找到:', desModulePath_b, altModulePath_b)
          return
        }
        fs.readFile(altModulePath_b, 'utf8', (err, data) => {
          if (err) {
            console.error('读取文件失败:', err)
            return
          }
          let modifiedData = data
          codecontent_b.forEach(item => {
            const insertPoint = modifiedData.indexOf(item.findString)
            if (insertPoint === -1) {
              console.error('FunctionNamePop component not found')
              return
            }
            modifiedData =
              modifiedData.slice(0, insertPoint + item.findString.length) +
              '\n' +
              item.insertString +
              modifiedData.slice(insertPoint + item.findString.length)
          })

          fs.writeFile(altModulePath_b, modifiedData, 'utf8', err => {
            if (err) {
              console.error('Error writing file:', err)
              return
            }
          })
        })
      })
    } else {
      fs.readFile(desModulePath_b, 'utf8', (err, data) => {
        if (err) {
          console.error('读取文件失败:', err)
          return
        }
        let modifiedData = data
        codecontent_b.forEach(item => {
          const insertPoint = modifiedData.indexOf(item.findString)
          if (insertPoint === -1) {
            console.error('FunctionNamePop component not found')
            return
          }
          modifiedData =
            modifiedData.slice(0, insertPoint + item.findString.length) +
            '\n' +
            item.insertString +
            modifiedData.slice(insertPoint + item.findString.length)
        })

        fs.writeFile(desModulePath_b, modifiedData, 'utf8', err => {
          if (err) {
            console.error('写入文件失败:', err)
            return
          }
        })
      })
    }
  })

  const desModulePath_f = path.resolve(
    __dirname,
    `./src/views/maps/components/${moduleName}/Home${moduleName}Footer.vue`
  )
  const altModulePath_f = path.resolve(
    __dirname,
    `./src/views/maps/components/${moduleName}/Home${moduleName_abbr}Footer.vue`
  )
  fs.access(desModulePath_f, fs.constants.F_OK, err => {
    if (err) {
      // 原文件不存在，尝试读取备用路径
      fs.access(altModulePath_f, fs.constants.F_OK, fallbackErr => {
        if (fallbackErr) {
          console.error('文件未找到:', desModulePath_f, altModulePath_f)
          return
        }
        // 处理原文件内容
        fs.readFile(altModulePath_f, 'utf8', (err, data) => {
          if (err) {
            console.error('未找到文件:', err)
            return
          }
          let modifiedData = data
          codecontent_f.forEach(item => {
            const insertPoint = modifiedData.indexOf(item.findString)
            if (insertPoint === -1) {
              console.error('FunctionNamePop component not found')
              return
            }
            modifiedData =
              modifiedData.slice(0, insertPoint + item.findString.length) +
              '\n' +
              item.insertString +
              modifiedData.slice(insertPoint + item.findString.length)
          })
          const eventMatch = modifiedData.match(/cancel([A-Za-z]+)Checked/)
          moduleEventName = eventMatch && eventMatch[1]
          if (moduleEventName) {
            popContent = popContent.replace(/ModuleEventName/g, moduleEventName)
            fs.writeFileSync(popPath, popContent, 'utf8')
          } else {
            console.error('未找到cancel' + moduleName + 'Checked或cancel' + eventMatch + 'Checked')
          }
          // 写回文件
          fs.writeFile(altModulePath_f, modifiedData, 'utf8', err => {
            if (err) {
              console.error('写入文件错误:', err)
              return
            } else {
              console.log(` "${functionName}"功能组件已成功创建至 "${moduleName}"模块中！`)
            }
          })
        })
        const footerContent = fs.readFileSync(altModulePath_f, 'utf8')
        const eventMatch = footerContent.match(/cancel([A-Za-z]+)Checked/)
        moduleEventName = eventMatch && eventMatch[1]
      })
    } else {
      // 原文件存在，读取原文件
      fs.readFile(desModulePath_f, 'utf8', (readErr, data) => {
        if (readErr) {
          console.error('读取原文件出错:', readErr)
          return
        }
        // 处理原文件内容
        fs.readFile(desModulePath_f, 'utf8', (err, data) => {
          if (err) {
            console.error('未找到文件:', err)
            return
          }
          let modifiedData = data
          codecontent_f.forEach(item => {
            const insertPoint = modifiedData.indexOf(item.findString)
            if (insertPoint === -1) {
              console.error('FunctionNamePop component not found')
              return
            }
            modifiedData =
              modifiedData.slice(0, insertPoint + item.findString.length) +
              '\n' +
              item.insertString +
              modifiedData.slice(insertPoint + item.findString.length)
          })
          const eventMatch = modifiedData.match(/cancel([A-Za-z]+)Checked/)
          moduleEventName = eventMatch && eventMatch[1]
          if (moduleEventName) {
            popContent = popContent.replace(/ModuleEventName/g, moduleEventName)
            fs.writeFileSync(popPath, popContent, 'utf8')
          } else {
            console.error('未找到cancel' + moduleName + 'Checked或cancel' + eventMatch + 'Checked')
          }
          // 写回文件
          fs.writeFile(desModulePath_f, modifiedData, 'utf8', err => {
            if (err) {
              console.error('写入文件错误:', err)
              return
            } else {
              console.log(` "${functionName}"功能组件已成功创建至 "${moduleName}"模块中！`)
            }
          })
        })
      })
    }
  })
  // 复制图标到style_blue和style_green下新建的functionName文件夹
  const styleBlueDir = path.resolve(__dirname, `./src/assets/style_blue/map-btns/${functionName}`)
  const styleGreenDir = path.resolve(__dirname, `./src/assets/style_green/map-btns/${functionName}`)
  const styleDefaultDir = path.resolve(__dirname, `./src/assets/map-btns/${functionName}`)
  const styleBlueDefaultDir = path.resolve(__dirname, './src/assets/style_blue/map-btns/DefaultComponent')
  const styleGreenDefaultDir = path.resolve(__dirname, './src/assets/style_green/map-btns/DefaultComponent')
  fs.ensureDirSync(styleDefaultDir)
  fs.ensureDirSync(styleBlueDir)
  fs.ensureDirSync(styleGreenDir)
  // 复制checked.png和default.png
  fs.copySync(path.join(styleGreenDefaultDir, 'checked.png'), path.join(styleDefaultDir, 'checked.png'))
  fs.copySync(path.join(styleGreenDefaultDir, 'default.png'), path.join(styleDefaultDir, 'default.png'))
  fs.copySync(path.join(styleBlueDefaultDir, 'checked.png'), path.join(styleBlueDir, 'checked.png'))
  fs.copySync(path.join(styleBlueDefaultDir, 'default.png'), path.join(styleBlueDir, 'default.png'))
  fs.copySync(path.join(styleGreenDefaultDir, 'checked.png'), path.join(styleGreenDir, 'checked.png'))
  fs.copySync(path.join(styleGreenDefaultDir, 'default.png'), path.join(styleGreenDir, 'default.png'))
}

function camelToHyphen(str) {
  return str
    .replace(/([A-Z])/g, '-$1')
    .replace(/^-/, '')
    .toLowerCase()
}

function askQuestion(query, defaultValue = '') {
  return new Promise(resolve => {
    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout
    })
    rl.question(query, answer => {
      rl.close()
      resolve(answer.trim() === '' ? defaultValue : answer)
    })
  })
}
