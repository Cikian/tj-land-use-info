function HashMap() {
  /** Map 大小 **/
  var size = 0;
  /** 对象 **/
  var entry = new Object();

  /** 存 **/
  this.put = function (key, value) {
    if (!this.containsKey(key)) {
      size++;
    }
    entry[key] = value;
  };

  /** 取 **/
  this.get = function (key) {
    return this.containsKey(key) ? entry[key] : null;
  };

  /** 是否包含 Key **/
  this.containsKey = function (key) {
    return (key in entry);
  };

  /** 所有 Value **/
  this.values = function () {
    var values = new Array();
    for (var prop in entry) {
      values.push(entry[prop]);
    }
    return values;
  };

  /** 所有 Key **/
  this.keys = function () {
    var keys = new Array();
    for (var prop in entry) {
      keys.push(prop);
    }
    return keys;
  };

  /** Map Size **/
  this.size = function () {
    return size;
  };

  /* 清空 */
  this.clear = function () {
    size = 0;
    entry = new Object();
  };
}
 let localDataManager = {
  localLayerList: new HashMap(),
  nameAndId:new HashMap(),
  terrainLayer: null,
  checkedLayers: [],
  checkedPlanLayers: [], // 存储选中的方案
  wkt:'',
  setLayer: function (id, layer) {
    this.localLayerList.put(id, layer);
  },
  getLayer: function (id) {
    return this.localLayerList.get(id);
  },
  deleteLayer: function (id) {
    this.localLayerList.remove(id);
  },
  setLayersIfSelected: function (bool) {
    let layers = window.viewer.scene._layers._layerQueue;
    for(let i in layers){
      layers[i].selectEnabled = bool;
    }
  }
};
export default localDataManager;

