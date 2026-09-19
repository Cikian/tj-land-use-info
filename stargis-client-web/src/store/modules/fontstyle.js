const fontstyle = {
    // namespaced:true,
    state: {
        //    fs:[]
        fs: {
            currentFontColor:'rgba(255,0,0,1)',
            currentFontFamily:'宋体',
            currentFontForm:'',
            currentFontSize:20
        }
    },
    mutations: {
        SET_FONTSTYLE: (state, info) => {
            if(info.currentFontColor){
                state.fs.currentFontColor = info.currentFontColor
            }
            if(info.currentFontFamily){
                state.fs.currentFontFamily = info.currentFontFamily
            }
            if(info.currentFontSize){
                state.fs.currentFontSize = info.currentFontSize
            }
            state.fs.currentFontForm = info.currentFontForm
            // state.fs = info
        }
    },
    actions: {

    },
    getters: {

    }
}
export default fontstyle