export default function setMapUrl(url) {
  if (url.indexOf('http') === -1 && url.indexOf('https') === -1 && window._CONFIG.VUE_MAP_URL) {
    return window._CONFIG.VUE_MAP_URL + url
  } else {
    return url
  }
}
