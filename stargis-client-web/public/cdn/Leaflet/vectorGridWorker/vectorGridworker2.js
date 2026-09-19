/*global importScripts Supercluster */
onmessage = function(e) {
    let that = this;
    getJSON2(e.data.url,e.data.options, (geojson) => {
        postMessage({type:"spot",geojson:geojson});
        that.close();
    });
}

function getJSON2(url,options, callback) {
    const xhr = new XMLHttpRequest();
    xhr.open('post', url, true);
    xhr.responseType = 'json';
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.onload = function () {
        if (xhr.readyState === 4 && xhr.status >= 200 && xhr.status < 300 && xhr.response) {
            callback(xhr.response);
        }
    };
    xhr.send(options);
}
